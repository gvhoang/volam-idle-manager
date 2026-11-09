package com.example.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.KeepAliveService
import com.example.data.PreferencesManager
import com.example.model.CharacterStats
import com.example.model.TabLifecycleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * KeepAliveWebView: Ngăn hệ điều hành Android thông báo trạng thái ẩn (View.GONE / View.INVISIBLE)
 * xuống nhân Chromium khi người dùng chuyển sang ứng dụng khác hoặc tắt màn hình.
 * Nhờ đó WebView vẫn xử lý tọa độ nhân vật và gói tin WebSocket theo thời gian thực,
 * triệt tiêu hoàn toàn hiện tượng giật hình, tua nhanh hoặc nhảy vị trí khi quay lại app.
 */
class KeepAliveWebView(context: Context) : WebView(context) {
    var keepAliveInBackground: Boolean = false
    var lastTouchTimeMs: Long = 0L

    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        if (ev != null) {
            lastTouchTimeMs = System.currentTimeMillis()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (keepAliveInBackground && visibility != View.VISIBLE) {
            super.onWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        if (keepAliveInBackground && visibility != View.VISIBLE) {
            super.onVisibilityChanged(changedView, View.VISIBLE)
        } else {
            super.onVisibilityChanged(changedView, visibility)
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        if (keepAliveInBackground) {
            super.onWindowFocusChanged(true)
        } else {
            super.onWindowFocusChanged(hasWindowFocus)
        }
    }
}

class GameTabManager(
    private val context: Context,
    private val prefs: PreferencesManager,
    private val coroutineScope: CoroutineScope
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private val contextWrappers = Array(4) { android.content.MutableContextWrapper(context) }
    private val isDesktopMode = BooleanArray(4) { false }

    fun updateActivityContext(activityContext: Context) {
        for (wrapper in contextWrappers) {
            if (wrapper.baseContext !== activityContext) {
                wrapper.baseContext = activityContext
            }
        }
    }

    // QUY CHUẨN 3: Giai đoạn 1 - Khởi đầu ở trạng thái chờ (isSystemRunning = false)
    private val _isSystemRunning = MutableStateFlow(false)
    val isSystemRunning: StateFlow<Boolean> = _isSystemRunning.asStateFlow()

    private val _characterStatsList = MutableStateFlow<List<CharacterStats>>(
        (0..3).map { CharacterStats(tabIndex = it, state = TabLifecycleState.IDLE) }
    )
    val characterStatsList: StateFlow<List<CharacterStats>> = _characterStatsList.asStateFlow()

    private val webViews = arrayOfNulls<KeepAliveWebView>(4)
    private val isTabActive = BooleanArray(4) { false }

    private var loginQueueJob: Job? = null
    private var periodicWatchdogJob: Job? = null
    private var garbageCollectionJob: Job? = null
    private val mainFrameRetryCount = IntArray(4)
    private val lastMainFrameError = arrayOfNulls<Int>(4)

    private fun isTrustedGameUrl(url: String?): Boolean =
        PreferencesManager.isTrustedGameUrl(url)

    private fun trustedTargetUrl(customUrl: String): String =
        if (customUrl.isBlank() || !isTrustedGameUrl(customUrl)) PreferencesManager.GAME_URL
        else customUrl

    init {
        // Pre-create WebViews with strict Isolated Profiles, but DO NOT load URLs yet (Idle)
        mainHandler.post {
            for (i in 0..3) {
                getOrCreateWebView(i)
            }
        }
        startBackgroundLoops()
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(tabIndex: Int): WebView {
        val existing = webViews[tabIndex]
        if (existing != null) {
            return existing
        }

        // =========================================================================
        // QUY CHUẨN 1: QUY TẮC SỐNG CÒN KHI KHỞI TẠO WEBVIEW
        // Dòng lệnh ĐẦU TIÊN ngay sau val webView = KeepAliveWebView(context) BẮT BUỘC phải là:
        // WebViewCompat.setProfile(webView, "Isolated_Cluster_${prefs.getLegacyProfileId()}_Tab_${tabIndex}")
        // Tuyệt đối KHÔNG được gọi webView.settings, CookieManager, hay bất kỳ hàm nào
        // của webView trước dòng này để tránh bị khóa vào Default Profile!
        // =========================================================================
        val webView = KeepAliveWebView(contextWrappers[tabIndex])
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            try {
                // Preserve the last selected v3 profile so upgrades retain game sessions.
                WebViewCompat.setProfile(
                    webView,
                    "Isolated_Cluster_${prefs.getLegacyProfileId()}_Tab_${tabIndex}"
                )
            } catch (_: Exception) {}
        }

        // Sau khi đã cô cô lập profile 100%, mới thiết lập các cấu hình phần cứng và WebSettings
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        }

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = if (isDesktopMode[tabIndex]) {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
            } else {
                "Mozilla/5.0 (Linux; Android 14; Tablet) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 VoLamIdle/1.0"
            }
            javaScriptCanOpenWindowsAutomatically = true
        }

        webView.addJavascriptInterface(AndroidGameBridge(tabIndex), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return true
                return url != "about:blank" && !PreferencesManager.isTrustedGameUrl(url)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                lastMainFrameError[tabIndex] = null
                if (url != "about:blank" && isTabActive[tabIndex]) {
                    updateStatus(tabIndex, "Đang tải trang...")
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url == null || url == "about:blank") {
                    updateStatus(tabIndex, "Chờ mở tab (Idle)")
                } else if (isTabActive[tabIndex] && isTrustedGameUrl(url)) {
                    if (lastMainFrameError[tabIndex] == null) {
                        mainFrameRetryCount[tabIndex] = 0
                        updateStatus(tabIndex, "Trang đã sẵn sàng")
                    } else {
                        return
                    }
                    val fps = prefs.getFps()
                    view?.evaluateJavascript(GameScriptInjector.buildFpsLimiterScript(fps), null)
                    view?.evaluateJavascript(GameScriptInjector.buildAdjustViewportScript(), null)
                    if (_isSystemRunning.value) {
                        val account = prefs.getAccount(tabIndex)
                        if (account.username.isNotBlank() && account.password.isNotBlank()) {
                            val script = GameScriptInjector.buildAutoLoginStepScript(
                                tabIndex = tabIndex,
                                username = account.username,
                                password = account.password
                            )
                            view?.evaluateJavascript(script, null)
                            // Chạy bồi thêm ở 350ms và 800ms khi SPA (React/Vue) vừa dựng xong khung Đăng nhập mới
                            listOf(350L, 800L).forEach { delayMs ->
                                mainHandler.postDelayed({
                                    if (_isSystemRunning.value && isTabActive[tabIndex] &&
                                        isTrustedGameUrl(view?.url)) {
                                        val currentStats = _characterStatsList.value.getOrNull(tabIndex)
                                        if (currentStats?.state == TabLifecycleState.LOGGING_IN) {
                                            view?.evaluateJavascript(script, null)
                                        }
                                    }
                                }, delayMs)
                            }
                        }
                    }
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: android.webkit.SslErrorHandler?,
                error: android.net.http.SslError?
            ) {
                handler?.cancel()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true && isTabActive[tabIndex] &&
                    PreferencesManager.isTrustedGameUrl(request.url.toString())) {
                    val code = error?.errorCode ?: 0
                    lastMainFrameError[tabIndex] = code
                    if (code == WebViewClient.ERROR_HOST_LOOKUP) {
                        updateStatus(tabIndex, "❌ Không tìm thấy máy chủ game")
                        return
                    }
                    val attempt = mainFrameRetryCount[tabIndex] + 1
                    if (attempt > 3) {
                        updateStatus(tabIndex, "❌ Máy chủ không phản hồi sau 3 lần thử")
                        return
                    }
                    mainFrameRetryCount[tabIndex] = attempt
                    updateStatus(tabIndex, "Đang kết nối lại máy chủ... (lần $attempt/3)")
                    mainHandler.postDelayed({
                        if (isTabActive[tabIndex] && _isSystemRunning.value) {
                            val account = prefs.getAccount(tabIndex)
                            val targetUrl = trustedTargetUrl(account.customUrl)
                            view?.loadUrl(targetUrl)
                        }
                    }, 2500L * attempt)
                }
            }

            // QUY CHUẨN 4: Tự cứu khi tiến trình WebView bị thu hồi RAM
            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                updateStatus(tabIndex, "Tiến trình WebView bị thu hồi RAM! Đang tự phục hồi...")

                mainHandler.post {
                    try {
                        view?.destroy()
                    } catch (_: Exception) {}
                    webViews[tabIndex] = null

                    if (_isSystemRunning.value && isTabActive[tabIndex]) {
                        reopenAndReloginTab(tabIndex)
                    }
                }
                return true
            }
        }

        webView.webChromeClient = WebChromeClient()

        webViews[tabIndex] = webView
        return webView
    }

    // =========================================================================
    // QUY CHUẨN 3: GIAI ĐOẠN 2 - BẤM [▶ ĐĂNG NHẬP HẾT]
    // =========================================================================
    fun loginAll() {
        _isSystemRunning.value = true
        KeepAliveService.start(context)
        startLoginStepLoop()
        coroutineScope.launch(Dispatchers.Main) {
            for (i in 0..3) {
                if (!_isSystemRunning.value) break
                isTabActive[i] = true
                val account = prefs.getAccount(i)
                val targetUrl = trustedTargetUrl(account.customUrl)
                val wv = getOrCreateWebView(i)
                wv.resumeTimers()

                setTabState(i, TabLifecycleState.LOGGING_IN)
                updateStatus(i, "Đang kết nối máy chủ...")

                val currentUrl = wv.url
                val isGamePageLoaded = currentUrl != null &&
                    currentUrl != "about:blank" &&
                    PreferencesManager.isTrustedGameUrl(currentUrl)
                if (!isGamePageLoaded) {
                    wv.loadUrl(targetUrl)
                } else if (account.username.isNotBlank() && account.password.isNotBlank()) {
                    val script = GameScriptInjector.buildAutoLoginStepScript(
                        tabIndex = i,
                        username = account.username,
                        password = account.password
                    )
                    wv.evaluateJavascript(script, null)
                }
                // Giãn cách 800ms giữa các tab để thiết lập kết nối SSL/WebSocket mượt mà, không bị từ chối
                if (i < 3) {
                    delay(800)
                }
            }
        }
    }

    // Mở và đăng nhập riêng 1 Tab
    fun openSingleTab(tabIndex: Int) {
        _isSystemRunning.value = true
        isTabActive[tabIndex] = true
        KeepAliveService.start(context)
        if (loginQueueJob?.isActive != true) {
            startLoginStepLoop()
        }
        val account = prefs.getAccount(tabIndex)
        val targetUrl = trustedTargetUrl(account.customUrl)

        mainHandler.post {
            val wv = getOrCreateWebView(tabIndex)
            wv.resumeTimers()
            setTabState(tabIndex, TabLifecycleState.LOGGING_IN)
            updateStatus(tabIndex, "Đang mở tab...")

            val currentUrl = wv.url
            val isGamePageLoaded = currentUrl != null &&
                currentUrl != "about:blank" &&
                PreferencesManager.isTrustedGameUrl(currentUrl)
            if (!isGamePageLoaded) {
                wv.loadUrl(targetUrl)
            } else if (account.username.isNotBlank() && account.password.isNotBlank()) {
                val script = GameScriptInjector.buildAutoLoginStepScript(
                    tabIndex = tabIndex,
                    username = account.username,
                    password = account.password
                )
                wv.evaluateJavascript(script, null)
            }
        }
    }

    // QUY CHUẨN 4: Tự mở lại Tab và đăng nhập lại khi bị văng
    fun reopenAndReloginTab(tabIndex: Int) {
        if (!_isSystemRunning.value || !isTabActive[tabIndex]) return
        val account = prefs.getAccount(tabIndex)
        val targetUrl = trustedTargetUrl(account.customUrl)

        mainHandler.post {
            val wv = getOrCreateWebView(tabIndex)
            wv.resumeTimers()
            setTabState(tabIndex, TabLifecycleState.LOGGING_IN)
            updateStatus(tabIndex, "⚡ Tự động mở lại...")
            wv.loadUrl(targetUrl)
        }
    }

    // =========================================================================
    // QUY CHUẨN 5: NÚT [⏹ DỪNG TẤT CẢ]
    // =========================================================================
    fun stopAll() {
        _isSystemRunning.value = false
        mainHandler.post {
            for (i in 0..3) {
                isTabActive[i] = false
                val wv = webViews[i]
                if (wv != null) {
                    try {
                        wv.stopLoading()
                        wv.loadUrl("about:blank")
                        wv.pauseTimers()
                    } catch (_: Exception) {}
                }
                setTabState(i, TabLifecycleState.OFFLINE)
                updateStatus(i, "Đã dừng (Offline)")
            }
        }
    }

    // Dừng 1 Tab
    fun stopSingleTab(tabIndex: Int) {
        isTabActive[tabIndex] = false
        mainHandler.post {
            val wv = webViews[tabIndex]
            if (wv != null) {
                try {
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                } catch (_: Exception) {}
            }
            setTabState(tabIndex, TabLifecycleState.OFFLINE)
            updateStatus(tabIndex, "Đã dừng (Offline)")
        }
    }

    // QUY CHUẨN 1: Đóng băng session riêng biệt khi đổi tài khoản
    fun onAccountChanged(tabIndex: Int) {
        mainHandler.post {
            val wv = webViews[tabIndex]
            val account = prefs.getAccount(tabIndex)
            if (wv != null && account.username.isNotBlank() && account.password.isNotBlank()) {
                val script = GameScriptInjector.buildAutoLoginStepScript(
                    tabIndex = tabIndex,
                    username = account.username,
                    password = account.password
                )
                wv.evaluateJavascript(script, null)
            }
            if (_isSystemRunning.value && isTabActive[tabIndex]) {
                reopenAndReloginTab(tabIndex)
            }
        }
    }

    // Căn chỉnh lại Viewport khi đổi layout (4 Dọc <-> Lưới 2x2 <-> 1 Tab)
    fun adjustAllViewports() {
        mainHandler.post {
            val script = GameScriptInjector.buildAdjustViewportScript()
            for (i in 0..3) {
                val wv = webViews[i] ?: continue
                wv.evaluateJavascript(script, null)
                if (_isSystemRunning.value && isTabActive[i]) {
                    val currentStats = _characterStatsList.value.getOrNull(i)
                    if (currentStats?.state == TabLifecycleState.LOGGING_IN && wv.url != null && wv.url != "about:blank") {
                        val account = prefs.getAccount(i)
                        if (account.username.isNotBlank() && account.password.isNotBlank()) {
                            val loginScript = GameScriptInjector.buildAutoLoginStepScript(
                                tabIndex = i,
                                username = account.username,
                                password = account.password
                            )
                            wv.evaluateJavascript(loginScript, null)
                        }
                    }
                }
            }
        }
    }

    // Chuyển đổi giữa chế độ Web PC và Mobile
    fun toggleDesktopMode(tabIndex: Int): Boolean {
        isDesktopMode[tabIndex] = !isDesktopMode[tabIndex]
        val isPC = isDesktopMode[tabIndex]
        mainHandler.post {
            val wv = webViews[tabIndex]
            if (wv != null) {
                wv.settings.userAgentString = if (isPC) {
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                } else {
                    "Mozilla/5.0 (Linux; Android 14; Tablet) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 VoLamIdle/1.0"
                }
                wv.reload()
                updateStatus(tabIndex, if (isPC) "Đã chuyển sang giao diện PC..." else "Đã chuyển sang giao diện Mobile...")
            }
        }
        return isPC
    }

    // Khi người dùng chuyển sang ứng dụng khác (chạy ngầm):
    // Giữ nguyên bộ đếm thời gian, ưu tiên tiến trình Renderer và bật cờ __isAppInBackground
    // để vòng lặp game chạy bằng bộ nhịp ngầm 20 FPS, tránh tích tụ gói tin gây nhảy vị trí.
    fun onAppBackgrounded() {
        if (_isSystemRunning.value || isTabActive.any { it }) {
            KeepAliveService.start(context)
        }
        mainHandler.post {
            for (i in 0..3) {
                val wv = webViews[i] ?: continue
                wv.keepAliveInBackground = true
                if (isTabActive[i]) {
                    try {
                        wv.onResume()
                        wv.resumeTimers()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                        }
                        wv.evaluateJavascript("window.__isAppInBackground = true;", null)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    // Khi người dùng quay trở lại ứng dụng từ app khác:
    // Chuyển mượt về VSync tiền cảnh và dọn các phần tử DOM dư thừa mà không reload trang.
    fun onAppForegrounded() {
        mainHandler.post {
            val gcScript = GameScriptInjector.buildDomGarbageCollectionScript()
            for (i in 0..3) {
                val wv = webViews[i] ?: continue
                wv.keepAliveInBackground = false // TẮT lừa dối hệ điều hành để Select/Dropdown hoạt động bình thường
                if (isTabActive[i]) {
                    try {
                        wv.onResume()
                        wv.resumeTimers()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                        }
                        wv.evaluateJavascript("window.__isAppInBackground = false;", null)
                        wv.evaluateJavascript(gcScript, null)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    // QUY CHUẨN 5: Đồng bộ Chế độ Auto (Nhiệm Vụ, Phó Bản, Luyện Công)
    fun syncAutoMode(modeText: String) {
        mainHandler.post {
            val script = GameScriptInjector.buildSyncAutoModeScript(modeText)
            for (i in 0..3) {
                val wv = webViews[i]
                if (wv != null && isTabActive[i]) {
                    wv.evaluateJavascript(script) { result ->
                        val cleaned = result?.trim('"') ?: ""
                        if (cleaned.startsWith("SELECTED_OPTION:") ||
                            cleaned.startsWith("CLICKED_DIRECT:") ||
                            cleaned.startsWith("ALREADY_IN_MODE:") ||
                            cleaned.startsWith("OPENED_TRIGGER_AND_SELECTING:")
                        ) {
                            updateStatus(i, "⚡ Chế độ: $modeText")
                        }
                    }
                } else {
                    wv?.evaluateJavascript(script, null)
                }
            }
        }
    }

    // Đồng bộ bật/tắt Màn hình đen trong game (Hệ thống -> Hiển Thị -> Màn hình đen) cho cả 4 Tab
    fun syncBlackScreen(enabled: Boolean) {
        mainHandler.post {
            val script = GameScriptInjector.buildSyncBlackScreenScript(enabled)
            for (i in 0..3) {
                val wv = webViews[i]
                if (wv != null && isTabActive[i]) {
                    wv.evaluateJavascript(script) { _ ->
                        updateStatus(i, if (enabled) "🌑 Màn hình đen: Đang bật" else "☀️ Màn hình đen: Đã tắt")
                    }
                } else {
                    wv?.evaluateJavascript(script, null)
                }
            }
        }
    }

    // Khóa FPS
    fun applyFps(fps: Int) {
        prefs.setFps(fps)
        mainHandler.post {
            val script = GameScriptInjector.buildFpsLimiterScript(fps)
            for (i in 0..3) {
                webViews[i]?.evaluateJavascript(script, null)
            }
        }
    }

    private fun startLoginStepLoop() {
        loginQueueJob?.cancel()
        loginQueueJob = coroutineScope.launch(Dispatchers.Main) {
            while (true) {
                delay(850)
                if (_isSystemRunning.value) {
                    for (i in 0..3) {
                        if (isTabActive[i]) {
                            val currentStats = _characterStatsList.value.getOrNull(i)
                            if (currentStats?.state == TabLifecycleState.LOGGING_IN) {
                                val wv = webViews[i]
                                if (wv != null && wv.url != null && wv.url != "about:blank") {
                                    val account = prefs.getAccount(i)
                                    val script = GameScriptInjector.buildAutoLoginStepScript(
                                        tabIndex = i,
                                        username = account.username,
                                        password = account.password
                                    )
                                    wv.evaluateJavascript(script, null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startBackgroundLoops() {
        startLoginStepLoop()

        // =========================================================================
        // QUY CHUẨN 4: BỘ CANH GÁC & QUÉT THÔNG SỐ ĐỊNH KỲ (mỗi 2500ms)
        // Chạy trên các tab đang hoạt động để:
        // 1. Tự động bấm "Chơi ở đây" / "Chơi ở tab này" ngay khi xuất hiện thông báo
        // 2. Tự động phục hồi tab khi văng ra màn hình đăng nhập
        // 3. Quét thông số nhân vật (Khu vực 1, 3, 4)
        // =========================================================================
        periodicWatchdogJob = coroutineScope.launch(Dispatchers.Main) {
            while (true) {
                delay(2500)
                if (_isSystemRunning.value) {
                    for (i in 0..3) {
                        val wv = webViews[i]
                        // Tạm dừng quét 5 giây nếu người dùng vừa chạm tay vào WebView này
                        if (wv != null && System.currentTimeMillis() - wv.lastTouchTimeMs < 5000L) {
                            continue
                        }
                        if (isTabActive[i]) {
                            val currentStats = _characterStatsList.value.getOrNull(i)
                            if (currentStats?.state == TabLifecycleState.IN_GAME) {
                                if (wv != null && wv.url != null && wv.url != "about:blank") {
                                    val script = GameScriptInjector.buildPeriodicWatchdogAndScanScript(i)
                                    wv.evaluateJavascript(script, null)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dọn rác DOM mỗi 45 giây chống tràn RAM
        garbageCollectionJob = coroutineScope.launch(Dispatchers.Main) {
            while (true) {
                delay(45_000)
                val script = GameScriptInjector.buildDomGarbageCollectionScript()
                for (i in 0..3) {
                    webViews[i]?.evaluateJavascript(script, null)
                }
            }
        }
    }

    private fun setTabState(tabIndex: Int, state: TabLifecycleState) {
        val currentList = _characterStatsList.value.toMutableList()
        val existing = currentList.getOrNull(tabIndex) ?: CharacterStats(tabIndex = tabIndex)
        currentList[tabIndex] = existing.copy(
            state = state,
            isOnline = (state == TabLifecycleState.IN_GAME),
            lastUpdateEpoch = System.currentTimeMillis()
        )
        _characterStatsList.value = currentList
    }

    private fun updateStatus(tabIndex: Int, status: String) {
        val currentList = _characterStatsList.value.toMutableList()
        val existing = currentList.getOrNull(tabIndex) ?: CharacterStats(tabIndex = tabIndex)
        currentList[tabIndex] = existing.copy(
            lastStatus = status,
            lastUpdateEpoch = System.currentTimeMillis()
        )
        _characterStatsList.value = currentList
    }

    fun cleanup() {
        loginQueueJob?.cancel()
        periodicWatchdogJob?.cancel()
        garbageCollectionJob?.cancel()
        mainHandler.post {
            for (i in 0..3) {
                try {
                    webViews[i]?.destroy()
                } catch (_: Exception) {}
                webViews[i] = null
            }
        }
    }

    inner class AndroidGameBridge(private val tabIndex: Int) {
        // Khi phát hiện vào màn hình chính của Game -> Chuyển sang IN_GAME
        @JavascriptInterface
        fun onInGameDetected(idx: Int) {
            mainHandler.post {
                setTabState(tabIndex, TabLifecycleState.IN_GAME)
                updateStatus(tabIndex, "🟢 Đã vào game")
            }
        }

        // Khi bộ canh gác phát hiện bị văng ra ngoài hoặc xuất hiện form login -> Tự động đăng nhập lại
        @JavascriptInterface
        fun onGameKickedDetected(idx: Int) {
            mainHandler.post {
                if (idx != tabIndex || !isTrustedGameUrl(webViews[tabIndex]?.url)) return@post
                if (_isSystemRunning.value && isTabActive[tabIndex]) {
                    setTabState(tabIndex, TabLifecycleState.LOGGING_IN)
                    updateStatus(tabIndex, "⚡ Tự động đăng nhập...")
                    val account = prefs.getAccount(tabIndex)
                    if (account.username.isNotBlank() && account.password.isNotBlank()) {
                        val script = GameScriptInjector.buildAutoLoginStepScript(
                            tabIndex = tabIndex,
                            username = account.username,
                            password = account.password
                        )
                        webViews[tabIndex]?.evaluateJavascript(script, null)
                    }
                }
            }
        }

        @JavascriptInterface
        fun onGameStateScanned(idx: Int, jsonStr: String) {
            mainHandler.post {
                try {
                    val json = JSONObject(jsonStr)
                    val currentList = _characterStatsList.value.toMutableList()
                    val prev = currentList.getOrNull(tabIndex) ?: CharacterStats(tabIndex = tabIndex)

                    val charName = json.optString("characterName", prev.characterName)
                        .replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    val sect = json.optString("sect", prev.sect)
                        .replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    val level = json.optString("level", prev.level)
                        .replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    val exp = json.optString("expPercent", prev.expPercent)
                        .replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    val daiHb = json.optString("daiHongBao", prev.daiHongBao)
                        .replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    val hb = json.optString("hongBao", prev.hongBao)
                        .replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    val thap = json.optString("thapRecord", prev.thapRecord)
                        .replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    val boss = json.optString("bossHoangKim", prev.bossHoangKim)
                        .replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    val daTau = json.optString("daTauQuest", prev.daTauQuest)
                        .replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    val status = json.optString("lastStatus", prev.lastStatus)
                        .replace("\n", " ").replace(Regex("\\s+"), " ").trim()

                    currentList[tabIndex] = prev.copy(
                        state = TabLifecycleState.IN_GAME,
                        isOnline = true,
                        characterName = if (charName.isNotBlank() && !charName.matches(Regex("^\\d+$"))) charName else prev.characterName,
                        sect = if (sect.isNotBlank()) sect else prev.sect,
                        level = if (level.isNotBlank()) level else prev.level,
                        expPercent = if (exp.isNotBlank()) exp else prev.expPercent,
                        daiHongBao = if (daiHb.isNotBlank()) daiHb else prev.daiHongBao,
                        hongBao = if (hb.isNotBlank()) hb else prev.hongBao,
                        thapRecord = if (thap.isNotBlank()) thap else prev.thapRecord,
                        bossHoangKim = if (boss.isNotBlank()) boss else prev.bossHoangKim,
                        daTauQuest = if (daTau.isNotBlank()) daTau else prev.daTauQuest,
                        lastStatus = status,
                        lastUpdateEpoch = System.currentTimeMillis()
                    )
                    _characterStatsList.value = currentList
                } catch (_: Exception) {}
            }
        }

        @JavascriptInterface
        fun onGameLog(idx: Int, message: String) {
            mainHandler.post {
                val currentList = _characterStatsList.value.toMutableList()
                val prev = currentList.getOrNull(tabIndex) ?: CharacterStats(tabIndex = tabIndex)
                currentList[tabIndex] = prev.copy(
                    lastStatus = message.replace("\n", " ").replace(Regex("\\s+"), " ").trim(),
                    lastUpdateEpoch = System.currentTimeMillis()
                )
                _characterStatsList.value = currentList
            }
        }
    }
}
