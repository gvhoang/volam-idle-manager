package com.example.webview

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.KeepAliveService
import com.example.data.PreferencesManager
import com.example.model.CharacterStats
import com.example.model.TabLifecycleState
import com.example.model.TabLimits
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

    // Mọi mảng theo index tab đều cấp phát ĐÚNG TabLimits.SLOT_COUNT phần tử MỘT LẦN và
    // KHÔNG BAO GIỜ cấp lại: onRenderProcessGone, các Runnable retry và coroutine watchdog
    // giữ index đã chụp trước khi người dùng đổi số tab. Cấp lại mảng sẽ làm chúng trỏ sai.
    private val contextWrappers = Array(TabLimits.SLOT_COUNT) {
        android.content.MutableContextWrapper(context)
    }
    private val isDesktopMode = BooleanArray(TabLimits.SLOT_COUNT) { false }

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

    // LUÔN 8 entry, tất cả IDLE. setTabState/updateStatus/onGameStateScanned ghi theo index
    // mà không kiểm biên so với một lần giảm tab đang nằm trong mainHandler.post xếp hàng
    // trước; list luôn đủ dài nên mọi lần ghi index không thể ném. UI mới là nơi ẩn entry ≥ N.
    private val _characterStatsList = MutableStateFlow<List<CharacterStats>>(
        (0 until TabLimits.SLOT_COUNT).map {
            CharacterStats(tabIndex = it, state = TabLifecycleState.IDLE)
        }
    )
    val characterStatsList: StateFlow<List<CharacterStats>> = _characterStatsList.asStateFlow()

    private val webViews = arrayOfNulls<KeepAliveWebView>(TabLimits.SLOT_COUNT)
    private val isTabActive = BooleanArray(TabLimits.SLOT_COUNT) { false }

    // ===== Số tab đang chạy (docs/DESIGN-TAB-COUNT.md) =====
    // Trần thực tế: máy RAM thấp bị chặn ở 4 (quá 4 renderer là OOM).
    val isLowRam: Boolean = isLowRamDevice()
    val maxTabCount: Int = if (isLowRam) TabLimits.RECOMMENDED_MAX else TabLimits.HARD_MAX

    // `@Volatile` vì watchdog/GC/coroutine đọc từ luồng khác. Không cấp lại mảng khi đổi N.
    // Clamp khi ĐỌC: một giá trị 8 do máy khác/backup để lại không được vượt trần low-RAM.
    @Volatile
    private var tabCount: Int = prefs.getTabCount()
        .coerceIn(TabLimits.MIN, maxTabCount)

    private val _tabCountState = MutableStateFlow(tabCount)
    val tabCountState: StateFlow<Int> = _tabCountState.asStateFlow()

    /** VM cần đồng bộ khi manager tự hạ FPS lúc vượt 4 tab. */
    var onFpsChanged: ((Int) -> Unit)? = null

    private var loginQueueJob: Job? = null
    private var periodicWatchdogJob: Job? = null
    private var garbageCollectionJob: Job? = null

    // Đếm số lần tự tải lại khi lỗi máy chủ tạm thời, để không lặp vô hạn
    private val mainFrameRetryCount = IntArray(TabLimits.SLOT_COUNT) { 0 }

    // Mốc thời gian của dòng log gần nhất do script đẩy về (vd "🤖 nhận quà: điểm danh").
    // Watchdog trong trang ghi đè lastStatus bằng "🟢 Đang chạy ổn định" mỗi nhịp quét, nên
    // nếu không giữ lại thì mọi thông báo của bộ tự động chỉ hiện trong <1 giây: người dùng
    // không thể biết auto đang làm gì, và QA cũng không quan sát được.
    private val lastGameLogAtMs = LongArray(TabLimits.SLOT_COUNT) { 0L }
    private val gameLogStickyMs = 8_000L

    // Lỗi main-frame gần nhất của từng tab. null = trang hiện tại không có lỗi.
    // Dùng để onPageFinished biết đây là trang lỗi nội bộ của Chromium, không phải trang thật.
    private val lastMainFrameError = arrayOfNulls<Int>(TabLimits.SLOT_COUNT)

    // Mỗi tab giữ đúng 1 Runnable hẹn giờ tải lại để removeCallbacks được chính xác,
    // tránh các lần postDelayed chồng lên nhau thành vòng lặp reload dày đặc.
    private val retryRunnables = arrayOfNulls<Runnable>(TabLimits.SLOT_COUNT)

    // Callback gắn WebView vào đúng ô hiển thị của tab, do GameWebViewContainer đăng ký 1 lần
    // (ô được tạo trong remember{} nên không tự tạo lại khi WebView bị thay thế).
    private val slotAttachers = arrayOfNulls<(WebView) -> Unit>(TabLimits.SLOT_COUNT)

    // ===== Chặn vòng lặp chết khi vượt giới hạn renderer (§6) =====
    // Mỗi tab giữ mốc các lần tự phục hồi. ≥3 lần trong 120s ⇒ thôi tự phục hồi: mỗi lần
    // reopen lại tạo thêm một renderer, đúng thứ vừa làm máy hết RAM.
    private val recoveryHistory =
        Array(TabLimits.SLOT_COUNT) { java.util.ArrayDeque<Long>() }
    private val recoveryBlocked = BooleanArray(TabLimits.SLOT_COUNT) { false }

    // =========================================================================
    // MODULE 8: TỰ ĐỘNG TRONG GAME
    // Chỉ giữ script sống trên từng tab và đẩy cấu hình; toàn bộ quyết định mua/nhận/đổi
    // nằm trong JS của trang (GameAutoPilot). Vòng lặp chạy vô hại khi cấu hình đang tắt.
    // =========================================================================
    private val autoPilot: GameAutoPilot = GameAutoPilot(
        scope = coroutineScope,
        tabCount = { tabCount },
        initialConfigJson = prefs.getAutoConfig(),
        webViewAt = { i -> webViews.getOrNull(i) },
        isTabActive = { i -> isTabActive.getOrNull(i) == true },
        isSystemRunning = { _isSystemRunning.value }
    )

    /** Người dùng vừa lưu bảng 🤖: ghi nhận và bơm ngay xuống các tab đang mở. */
    fun updateAutoConfig(json: String?) {
        autoPilot.updateConfig(json)
    }

    /** Xoá bộ đếm ngày (localStorage vlm_auto_*) trên các tab đang chạy. */
    fun resetAutoDailyCounters() {
        autoPilot.resetDailyCounters()
    }

    fun registerSlot(tabIndex: Int, attacher: (WebView) -> Unit) {
        if (tabIndex in webViews.indices) {
            slotAttachers[tabIndex] = attacher
        }
    }

    /**
     * Đánh dấu người dùng vừa chạm vào chrome của app (dải trạng thái, sheet, drawer).
     *
     * `lastTouchTimeMs` chỉ tự tiến khi sự kiện chạm đi qua `dispatchTouchEvent` của WebView.
     * Chạm lên Compose chrome không bao giờ tới WebView, nên nếu thiếu hàm này thì:
     * - vòng đăng nhập 850ms vẫn inject script (quét DOM + click tổng hợp) đúng lúc người dùng
     *   đang bấm nút, và
     * - watchdog 1200ms vẫn bấm "Chơi ở đây" / đóng `<select>` đang mở.
     * Đó chính là lớp lỗi làm treo khung HUD ở 4.0.0. Đẩy mốc thời gian cho MỌI slot để mọi
     * vòng quét đều thấy "vừa có tương tác" và tự lùi lại 5 giây.
     */
    fun markUiInteraction() {
        val now = System.currentTimeMillis()
        for (i in webViews.indices) {
            webViews[i]?.lastTouchTimeMs = now
        }
    }

    companion object {
        // Lỗi tạm thời: thử lại tối đa 3 lần, giãn cách tăng dần 2.5s -> 5s -> 7.5s
        private const val MAX_MAIN_FRAME_RETRY = 3
        private const val MAIN_FRAME_RETRY_BASE_MS = 2500L

        // Nhịp quét luân phiên: mỗi nhịp quét 1 tab -> mỗi tab được quét lại sau ~4.8s ở N=4.
        // Tăng số này nếu muốn tiết kiệm pin hơn nữa; giảm nếu cần phát hiện văng game nhanh hơn.
        // KHÔNG giảm theo N: độ trễ watchdog tăng tuyến tính theo N là rủi ro R7 đã chấp nhận.
        private const val WATCHDOG_TICK_MS = 1200L

        // Dọn rác DOM: 90s/nhịp, luân phiên từng tab -> mỗi tab dọn lại sau ~6 phút ở N=4.
        private const val GC_TICK_MS = 90_000L

        // Chặn vòng lặp chết: 3 lần phục hồi trong 120s thì dừng tự phục hồi tab đó.
        private const val MAX_RECOVERIES_IN_WINDOW = 3
        private const val RECOVERY_WINDOW_MS = 120_000L
    }

    private fun isLowRamDevice(): Boolean = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.isLowRamDevice == true
    } catch (_: Exception) {
        false
    }

    init {
        // Pre-create WebViews of the ACTIVE tab count with strict Isolated Profiles, but DO NOT
        // load URLs yet (Idle). Không eager tới SLOT_COUNT: 8 renderer rỗng ngay lần composition
        // đầu là ~2.4 GB cho người dùng chọn 1 tab.
        mainHandler.post {
            for (i in 0 until tabCount) {
                getOrCreateWebView(i)
            }
        }
        startBackgroundLoops()
        // Bắt đầu vòng giữ script tự động. Nếu cấu hình đã lưu đang bật thì tự khôi phục
        // sau khi mở lại app; nếu tắt (mặc định) thì vòng lặp chỉ ngủ.
        autoPilot.start()
    }

    // =========================================================================
    // SỐ TAB — owner duy nhất là hàm này (clamp + persist + apply), trả về giá trị THẬT
    // =========================================================================

    /**
     * Đổi số tab. UI phải lấy StateFlow từ giá trị TRẢ VỀ, không lấy từ yêu cầu.
     *
     * Giảm tab: `isTabActive[i] = false` TRƯỚC (để onRenderProcessGone đang đua không mở lại),
     * rồi mới tháo → destroy → null, reset bộ đếm, đặt OFFLINE.
     *
     * KHÔNG dùng `pauseTimers()` để giảm tab — nó là process-global, dừng một tab sẽ đóng
     * băng tất cả các tab còn lại.
     */
    fun setTabCount(requested: Int): Int {
        val target = requested.coerceIn(TabLimits.MIN, maxTabCount)
        val old = tabCount
        if (target == old) return target

        // Chặn ngay mọi vòng quét/retry chạm vào slot sắp bị tháo.
        if (target < old) {
            for (i in target until old) {
                isTabActive[i] = false
            }
        }

        // Gán trước khi teardown: mọi vòng lặp `0 until tabCount` lập tức thu về N mới.
        tabCount = target
        prefs.setTabCount(target)
        _tabCountState.value = target

        mainHandler.post {
            if (target < old) {
                for (i in target until old) {
                    // N có thể đã tăng lại trước khi block này chạy (người dùng bấm nhanh
                    // 8 → 5 → 7): chỉ tháo slot thật sự còn NGOÀI số tab hiện tại, nếu không
                    // sẽ destroy đúng WebView vừa được bật lại.
                    if (i >= tabCount) {
                        tearDownSlot(i)
                    }
                }
            }

            // Vượt 4 tab: hạ 60 → 30 FPS. intervalMs 33 vs 16 giảm một nửa công JS mỗi tab.
            if (tabCount > TabLimits.RECOMMENDED_MAX && prefs.getFps() != 30) {
                applyFps(30)
                onFpsChanged?.invoke(30)
                runCatching {
                    Toast.makeText(
                        context,
                        "Đã hạ xuống 30 FPS để giữ ổn định khi chạy $tabCount tab.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            // Tăng tab trong lúc hệ thống đang chạy: tự mở + đăng nhập các tab mới, giãn cách
            // 800ms như loginAll. Đây là hành vi đã hứa trong hộp thoại giảm tab
            // ("tăng lại số tab sau sẽ tự đăng nhập lại"); WebView vẫn được tạo lazy, không
            // tạo eager cho slot chưa dùng.
            if (target > old && _isSystemRunning.value) {
                for (i in old until target) {
                    mainHandler.postDelayed({
                        if (i < tabCount) openSingleTab(i)
                    }, 800L * (i - old))
                }
            }

            adjustAllViewports()
        }
        return target
    }

    /** Tháo một slot: cancel retry → detach → destroy → null → reset → OFFLINE. */
    private fun tearDownSlot(i: Int) {
        if (i !in webViews.indices) return
        isTabActive[i] = false
        retryRunnables[i]?.let { mainHandler.removeCallbacks(it) }
        retryRunnables[i] = null
        try {
            val old = webViews[i]
            (old?.parent as? ViewGroup)?.removeView(old)
        } catch (_: Exception) {}
        try {
            webViews[i]?.destroy()
        } catch (_: Exception) {}
        webViews[i] = null
        mainFrameRetryCount[i] = 0
        lastMainFrameError[i] = null
        setTabState(i, TabLifecycleState.OFFLINE)
        updateStatus(i, "Đã dừng (Offline)")
        autoPilot.onTabRemoved(i)
    }

    private fun getClusterId(): String {
        return when (prefs.getCluster()) {
            "Cụm 2" -> "2"
            "Cụm 3" -> "3"
            else -> "1"
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(tabIndex: Int): WebView {
        require(tabIndex in webViews.indices) { "tabIndex $tabIndex ngoài dải slot" }
        val existing = webViews[tabIndex]
        if (existing != null) {
            return existing
        }

        // =========================================================================
        // QUY CHUẨN 1: QUY TẮC SỐNG CÒN KHI KHỞI TẠO WEBVIEW
        // Dòng lệnh ĐẦU TIÊN ngay sau val webView = KeepAliveWebView(context) BẮT BUỘC phải là:
        // WebViewCompat.setProfile(webView, "Isolated_Cluster_${clusterId}_Tab_${tabIndex}")
        // Tuyệt đối KHÔNG được gọi webView.settings, CookieManager, hay bất kỳ hàm nào
        // của webView trước dòng này để tránh bị khóa vào Default Profile!
        //
        // TÊN PROFILE ĐÓNG BĂNG: cookie/localStorage nằm trên đĩa theo đúng chuỗi này.
        // Không suy ra từ tabCount, không "nén" index, cấm đổi thứ tự tab, không deleteProfile.
        // =========================================================================
        val webView = KeepAliveWebView(contextWrappers[tabIndex])
        val clusterId = getClusterId()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            try {
                WebViewCompat.setProfile(webView, "Isolated_Cluster_${clusterId}_Tab_${tabIndex}")
            } catch (_: Exception) {}
        }

        // Sau khi đã cô cô lập profile 100%, mới thiết lập các cấu hình phần cứng và WebSettings
        // WebView vốn đã composite trên GPU. Ép thêm LAYER_TYPE_HARDWARE chỉ tạo
        // thêm một texture lớp phủ cho MỖI WebView (4 texture, vài chục MB) mà không
        // tăng tốc gì — đo trên thiết bị cho thấy bỏ nó giảm cả RAM lẫn CPU compositing.
        // Giữ mặc định (LAYER_TYPE_NONE) để Chromium tự quyết định.
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
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = if (isDesktopMode[tabIndex]) {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
            } else {
                "Mozilla/5.0 (Linux; Android 14; Tablet) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 VoLamIdle/1.0"
            }
            javaScriptCanOpenWindowsAutomatically = true
        }

        webView.addJavascriptInterface(AndroidGameBridge(tabIndex), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Trang mới bắt đầu tải -> xóa dấu lỗi của lần tải trước
                lastMainFrameError[tabIndex] = null
                if (url != "about:blank" && isTabActive[tabIndex]) {
                    updateStatus(tabIndex, "Đang tải trang...")
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val fps = prefs.getFps()
                view?.evaluateJavascript(GameScriptInjector.buildFpsLimiterScript(fps), null)
                view?.evaluateJavascript(GameScriptInjector.buildAdjustViewportScript(), null)

                if (url == null || url == "about:blank") {
                    updateStatus(tabIndex, "Chờ mở tab (Idle)")
                } else if (isTabActive[tabIndex]) {
                    // onPageFinished vẫn được gọi cả khi trang lỗi (Chromium hiển thị trang lỗi nội bộ).
                    // Nếu đang trong chuỗi thử lại thì giữ nguyên thông báo lỗi, không ghi đè thành
                    // "Trang đã sẵn sàng" và không reset bộ đếm (reset sẽ thành thử lại vô hạn).
                    if (lastMainFrameError[tabIndex] == null) {
                        mainFrameRetryCount[tabIndex] = 0
                        updateStatus(tabIndex, "Trang đã sẵn sàng")
                    }
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
                                    if (_isSystemRunning.value && isTabActive[tabIndex]) {
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
                // Đảm bảo không bị nghẽn chứng chỉ trung gian CDN Cloudflare Pages
                handler?.proceed()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame != true || !isTabActive[tabIndex]) return

                val code = error?.errorCode ?: 0
                lastMainFrameError[tabIndex] = code

                // Lỗi phân giải tên miền là lỗi vĩnh viễn: URL gõ sai hoặc domain không tồn tại.
                // Thử lại chỉ tốn pin và data vô ích -> dừng hẳn và báo rõ cho người dùng.
                if (code == WebViewClient.ERROR_HOST_LOOKUP) {
                    updateStatus(tabIndex, "❌ Không tìm thấy máy chủ")
                    return
                }

                // Lỗi tạm thời (mạng chập chờn, máy chủ từ chối): thử lại có giãn cách và có giới hạn,
                // tránh vòng lặp tải lại vô hạn mỗi 2.5 giây khi máy chủ chết hẳn.
                val attempt = mainFrameRetryCount[tabIndex] + 1
                if (attempt > MAX_MAIN_FRAME_RETRY) {
                    updateStatus(tabIndex, "❌ Máy chủ không phản hồi sau $MAX_MAIN_FRAME_RETRY lần thử")
                    return
                }
                mainFrameRetryCount[tabIndex] = attempt

                val delayMs = MAIN_FRAME_RETRY_BASE_MS * attempt
                updateStatus(tabIndex, "Đang kết nối lại máy chủ... (lần $attempt/$MAX_MAIN_FRAME_RETRY)")

                // Hủy lần hẹn giờ cũ của tab này trước khi đặt lần mới, để các postDelayed không chồng nhau
                retryRunnables[tabIndex]?.let { mainHandler.removeCallbacks(it) }
                val retryRunnable = Runnable {
                    retryRunnables[tabIndex] = null
                    // Slot đã bị tháo do giảm số tab: không được tải lại vào WebView đã destroy.
                    if (tabIndex >= tabCount) return@Runnable
                    if (isTabActive[tabIndex] && _isSystemRunning.value) {
                        val account = prefs.getAccount(tabIndex)
                        val targetUrl = account.customUrl.ifBlank { prefs.getClusterUrl(prefs.getCluster()) }
                        try {
                            (webViews[tabIndex] ?: view)?.loadUrl(targetUrl)
                        } catch (_: Exception) {}
                    }
                }
                retryRunnables[tabIndex] = retryRunnable
                mainHandler.postDelayed(retryRunnable, delayMs)
            }

            // QUY CHUẨN 4: Tự cứu khi tiến trình WebView bị thu hồi RAM
            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                updateStatus(tabIndex, "Tiến trình WebView bị thu hồi RAM! Đang tự phục hồi...")

                mainHandler.post {
                    // Hủy lần hẹn giờ tải lại còn treo của tab, tránh reload vào WebView đã chết
                    retryRunnables[tabIndex]?.let { mainHandler.removeCallbacks(it) }
                    retryRunnables[tabIndex] = null
                    // destroy() trên WebView còn gắn trong cây View là không hợp lệ: view đã destroy
                    // vẫn bị measure/layout mỗi khung hình. Phải tháo khỏi cha trước khi destroy.
                    try {
                        (view?.parent as? ViewGroup)?.removeView(view)
                    } catch (_: Exception) {}
                    try {
                        view?.destroy()
                    } catch (_: Exception) {}
                    webViews[tabIndex] = null

                    // Slot đã bị tháo (giảm số tab trong lúc renderer chết): không phục hồi.
                    if (tabIndex >= tabCount) return@post
                    if (!_isSystemRunning.value || !isTabActive[tabIndex]) return@post

                    // Chặn vòng lặp chết: mỗi lần phục hồi lại đẻ thêm một renderer — đúng thứ
                    // vừa làm máy hết RAM. ≥3 lần trong 120s thì thôi, đặt OFFLINE và nói rõ.
                    if (recoveryBlocked[tabIndex]) {
                        setTabState(tabIndex, TabLifecycleState.OFFLINE)
                        return@post
                    }
                    val now = System.currentTimeMillis()
                    val history = recoveryHistory[tabIndex]
                    while (history.isNotEmpty() && now - history.first() > RECOVERY_WINDOW_MS) {
                        history.removeFirst()
                    }
                    history.addLast(now)
                    if (history.size >= MAX_RECOVERIES_IN_WINDOW) {
                        recoveryBlocked[tabIndex] = true
                        setTabState(tabIndex, TabLifecycleState.OFFLINE)
                        updateStatus(tabIndex, "⚠️ Máy hết RAM – hãy giảm số tab")
                        return@post
                    }

                    reopenAndReloginTab(tabIndex)
                }
                return true
            }
        }

        webView.webChromeClient = WebChromeClient()

        webViews[tabIndex] = webView
        // Nếu UI đã đăng ký ô hiển thị của tab này thì gắn ngay WebView vừa tạo vào ô đó
        // (dùng cho trường hợp WebView được tạo lại: bị thu hồi RAM, đổi Cụm...).
        // Lần khởi tạo đầu tiên UI chưa đăng ký -> attacher null, UI tự addView như cũ.
        slotAttachers[tabIndex]?.invoke(webView)
        return webView
    }

    // =========================================================================
    // QUY CHUẨN 3: GIAI ĐOẠN 2 - BẤM [▶ ĐĂNG NHẬP HẾT]
    // =========================================================================
    fun loginAll() {
        _isSystemRunning.value = true
        KeepAliveService.start(context)
        val cluster = prefs.getCluster()
        val defaultUrl = prefs.getClusterUrl(cluster)

        startLoginStepLoop()
        coroutineScope.launch(Dispatchers.Main) {
            val total = tabCount
            for (i in 0 until total) {
                if (!_isSystemRunning.value) break
                // Số tab có thể giảm giữa vòng lặp: không mở slot đã bị tháo.
                if (i >= tabCount) break
                isTabActive[i] = true
                val account = prefs.getAccount(i)
                val targetUrl = account.customUrl.ifBlank { defaultUrl }
                val wv = getOrCreateWebView(i)
                wv.resumeTimers()

                setTabState(i, TabLifecycleState.LOGGING_IN)
                updateStatus(i, "Đang kết nối máy chủ...")

                val currentUrl = wv.url
                val isGamePageLoaded = currentUrl != null &&
                    currentUrl != "about:blank" &&
                    (currentUrl.contains("volamidle") || currentUrl.contains("vlidle") || (account.customUrl.isNotBlank() && currentUrl.startsWith("http")))
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
                if (i < total - 1) {
                    delay(800)
                }
            }
        }
    }

    // Mở và đăng nhập riêng 1 Tab
    fun openSingleTab(tabIndex: Int) {
        if (tabIndex !in 0 until tabCount) return
        // Người dùng chủ động mở lại tab: xoá lịch sử phục hồi để chặn-vòng-lặp không
        // khoá vĩnh viễn một tab chỉ vì máy từng hết RAM (§6).
        recoveryBlocked[tabIndex] = false
        recoveryHistory[tabIndex].clear()

        _isSystemRunning.value = true
        isTabActive[tabIndex] = true
        KeepAliveService.start(context)
        if (loginQueueJob?.isActive != true) {
            startLoginStepLoop()
        }
        val cluster = prefs.getCluster()
        val defaultUrl = prefs.getClusterUrl(cluster)
        val account = prefs.getAccount(tabIndex)
        val targetUrl = account.customUrl.ifBlank { defaultUrl }

        mainHandler.post {
            if (tabIndex >= tabCount) return@post
            val wv = getOrCreateWebView(tabIndex)
            wv.resumeTimers()
            setTabState(tabIndex, TabLifecycleState.LOGGING_IN)
            updateStatus(tabIndex, "Đang mở tab...")

            val currentUrl = wv.url
            val isGamePageLoaded = currentUrl != null &&
                currentUrl != "about:blank" &&
                (currentUrl.contains("volamidle") || currentUrl.contains("vlidle") || (account.customUrl.isNotBlank() && currentUrl.startsWith("http")))
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
        if (tabIndex !in 0 until tabCount) return
        if (!_isSystemRunning.value || !isTabActive[tabIndex]) return
        val cluster = prefs.getCluster()
        val defaultUrl = prefs.getClusterUrl(cluster)
        val account = prefs.getAccount(tabIndex)
        val targetUrl = account.customUrl.ifBlank { defaultUrl }

        mainHandler.post {
            if (tabIndex >= tabCount) return@post
            val wv = getOrCreateWebView(tabIndex)
            wv.resumeTimers()
            setTabState(tabIndex, TabLifecycleState.LOGGING_IN)
            updateStatus(tabIndex, "⚡ Tự động mở lại...")
            wv.loadUrl(targetUrl)
            // Trang mới = ngữ cảnh JS mới: buộc cài lại script tự động ngay nhịp kế tiếp.
            autoPilot.onTabRestarted(tabIndex)
        }
    }

    // =========================================================================
    // QUY CHUẨN 5: NÚT [⏹ DỪNG TẤT CẢ]
    // =========================================================================
    fun stopAll() {
        _isSystemRunning.value = false
        mainHandler.post {
            for (i in 0 until tabCount) {
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
        if (tabIndex !in webViews.indices) return
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

    /**
     * Xoá phiên đăng nhập của MỘT tab: cookie + web storage của ĐÚNG profile Chromium của tab đó,
     * rồi mới tới localStorage/sessionStorage trong trang.
     *
     * Không dùng `deleteProfile()`: tên profile "Isolated_Cluster_x_Tab_y" là địa chỉ tồn tại
     * trên đĩa của tài khoản người dùng (xem QUY CHUẨN 1) — xoá profile là xoá luôn mọi thứ
     * khác của tab, không chỉ phần đăng nhập. Chỉ xoá dữ liệu phiên bên trong profile.
     *
     * MULTI_PROFILE không được hỗ trợ thì TUYỆT ĐỐI không chạm vào cookie: khi đó CookieManager
     * là kho dùng chung toàn app, `removeAllCookies()` sẽ đăng xuất mọi tab còn lại. Trên máy đó
     * chỉ xoá được localStorage/sessionStorage — đúng giới hạn của WebView phiên bản cũ, và
     * người dùng vẫn được đăng xuất khỏi phiên SPA đang chạy.
     *
     * Không reload trước khi xoá xong: `evaluateJavascript` và `removeAllCookies` đều bất đồng bộ.
     * Tải lại ngay sẽ để trang cũ chạy tiếp bằng cookie cũ và tài khoản hiện lại.
     *
     * Tab kết thúc ở IDLE chứ không phải LOGGING_IN: đã xoá tài khoản thì không có gì để tự đăng
     * nhập lại; vòng 850ms chỉ được chạy lại khi người dùng bấm ▶ mở tab.
     */
    fun clearAccountSession(tabIndex: Int) {
        if (tabIndex !in webViews.indices) return
        mainHandler.post {
            // Profile của tab tồn tại trên đĩa cả khi tab chưa được mở trong phiên này, nhưng
            // chỉ lấy được Profile qua một WebView. Dựng WebView rỗng của đúng slot (không
            // loadUrl) để xoá được cookie của lần chạy trước; WebView này chính là thứ UI sẽ
            // dùng khi người dùng bấm ▶.
            val wv = webViews[tabIndex]
                ?: if (tabIndex < tabCount) getOrCreateWebView(tabIndex) else null

            // Dừng mọi vòng quét/inject NGAY, kể cả khi callback xoá bên dưới không bao giờ trả về.
            isTabActive[tabIndex] = false
            setTabState(tabIndex, TabLifecycleState.IDLE)
            updateStatus(tabIndex, "Chờ mở tab (Idle)")

            if (wv == null) {
                // Slot đã bị thu hẹp: không còn profile nào để xoá.
                return@post
            }

            val multiProfile = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
            // Đóng trang đúng MỘT lần, sau khi mọi lần xoá bất đồng bộ đã trả về (hoặc chốt an toàn
            // bên dưới). Đặt lại IDLE sau khi trang bị thay: một lượt quét của trang game cũ có
            // thể chen vào giữa lúc xoá và lúc tải about:blank và đẩy tab về IN_GAME.
            var finished = false
            val closePage = {
                if (!finished) {
                    finished = true
                    try {
                        wv.stopLoading()
                        wv.loadUrl("about:blank")
                    } catch (_: Exception) {}
                    setTabState(tabIndex, TabLifecycleState.IDLE)
                    updateStatus(tabIndex, "Chờ mở tab (Idle)")
                }
            }

            // Hai lần xoá bất đồng bộ (JS + cookie). Chỉ đóng trang khi cả hai đã xong.
            var pending = if (multiProfile) 2 else 1
            val finish = {
                pending--
                if (pending <= 0) closePage()
            }

            if (multiProfile) {
                try {
                    val profile = WebViewCompat.getProfile(wv)
                    profile.cookieManager.removeAllCookies { finish() }
                    profile.webStorage.deleteAllData()
                } catch (_: Exception) {
                    // API có nhưng WebView của máy này không trả Profile: bỏ phần cookie,
                    // phần localStorage bên dưới vẫn chạy — không được treo bộ đếm.
                    finish()
                }
            }

            wv.evaluateJavascript(GameScriptInjector.buildClearStorageScript()) { finish() }

            // Chốt an toàn: renderer chết đúng lúc xoá thì callback có thể không bao giờ trả về,
            // để lại trang game cũ chạy tiếp với phiên trong bộ nhớ. Sau 3 giây vẫn phải đóng
            // trang. Đây chỉ là lưới hứng — đường bình thường luôn đi qua callback ở trên.
            mainHandler.postDelayed({ closePage() }, 3_000L)
        }
    }

    // QUY CHUẨN 1: Đóng băng session riêng biệt khi đổi tài khoản
    fun onAccountChanged(tabIndex: Int) {
        if (tabIndex !in webViews.indices) return
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

    // Căn chỉnh lại Viewport khi đổi layout hoặc đổi số tab
    fun adjustAllViewports() {
        mainHandler.post {
            val script = GameScriptInjector.buildAdjustViewportScript()
            for (i in 0 until tabCount) {
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
        if (tabIndex !in webViews.indices) return false
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
            for (i in 0 until tabCount) {
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
            for (i in 0 until tabCount) {
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
            for (i in 0 until tabCount) {
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

    // Đồng bộ bật/tắt Màn hình đen trong game (Hệ thống -> Hiển Thị -> Màn hình đen)
    fun syncBlackScreen(enabled: Boolean) {
        mainHandler.post {
            val script = GameScriptInjector.buildSyncBlackScreenScript(enabled)
            for (i in 0 until tabCount) {
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
            for (i in 0 until tabCount) {
                webViews[i]?.evaluateJavascript(script, null)
            }
        }
    }

    // Đổi Cụm
    fun switchCluster(newCluster: String) {
        prefs.setCluster(newCluster)
        if (_isSystemRunning.value) {
            // Re-create WebViews with new profile
            mainHandler.post {
                for (i in webViews.indices) {
                    // Hủy lần hẹn giờ tải lại còn treo, tránh reload vào WebView sắp bị destroy
                    retryRunnables[i]?.let { mainHandler.removeCallbacks(it) }
                    retryRunnables[i] = null
                    mainFrameRetryCount[i] = 0
                    lastMainFrameError[i] = null
                    // destroy() chỉ hợp lệ khi WebView đã rời cây View (xem onRenderProcessGone)
                    try {
                        val old = webViews[i]
                        (old?.parent as? ViewGroup)?.removeView(old)
                    } catch (_: Exception) {}
                    try {
                        webViews[i]?.destroy()
                    } catch (_: Exception) {}
                    webViews[i] = null
                }
                loginAll()
            }
        }
    }

    private fun startLoginStepLoop() {
        loginQueueJob?.cancel()
        loginQueueJob = coroutineScope.launch(Dispatchers.Main) {
            while (true) {
                delay(850)
                if (_isSystemRunning.value) {
                    val count = tabCount
                    if (count <= 0) continue
                    for (i in 0 until count) {
                        if (isTabActive[i]) {
                            val currentStats = _characterStatsList.value.getOrNull(i)
                            if (currentStats?.state == TabLifecycleState.LOGGING_IN) {
                                val wv = webViews[i]
                                // Tạm dừng 5 giây nếu người dùng vừa chạm tay vào tab này:
                                // mỗi lần inject là một lượt quét DOM + click tổng hợp, có thể
                                // đóng sập dropdown/select đang mở của người dùng.
                                if (wv != null && System.currentTimeMillis() - wv.lastTouchTimeMs < 5000L) {
                                    continue
                                }
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
        // QUY CHUẨN 4: BỘ CANH GÁC & QUÉT THÔNG SỐ ĐỊNH KỲ (mỗi 1200ms, luân phiên)
        // Chạy trên các tab đang hoạt động để:
        // 1. Tự động bấm "Chơi ở đây" / "Chơi ở tab này" ngay khi xuất hiện thông báo
        // 2. Tự động phục hồi tab khi văng ra màn hình đăng nhập
        // 3. Quét thông số nhân vật (Khu vực 1, 3, 4)
        //
        // Độ trễ phát hiện tăng tuyến tính theo N (4.8s ở N=4 → 9.6s ở N=8). Đây là đánh đổi
        // đã biết (R7): giảm WATCHDOG_TICK_MS sẽ tăng CPU tuyến tính, đúng thứ đang tránh.
        // =========================================================================
        periodicWatchdogJob = coroutineScope.launch(Dispatchers.Main) {
            var nextTab = 0
            while (true) {
                delay(WATCHDOG_TICK_MS)
                if (_isSystemRunning.value) {
                    val count = tabCount
                    if (count <= 0) continue
                    val i = nextTab % count
                    nextTab = (nextTab + 1) % count
                    val wv = webViews[i]
                    // Tạm dừng quét 5 giây nếu người dùng vừa chạm tay vào WebView này
                    if (wv != null && System.currentTimeMillis() - wv.lastTouchTimeMs >= 5000L && isTabActive[i]) {
                        val currentStats = _characterStatsList.value.getOrNull(i)
                        if (currentStats?.state == TabLifecycleState.IN_GAME &&
                            wv.url != null && wv.url != "about:blank"
                        ) {
                            wv.evaluateJavascript(GameScriptInjector.buildPeriodicWatchdogAndScanScript(i), null)
                        }
                    }
                }
            }
        }

        // Dọn rác DOM định kỳ chống tràn RAM.
        // 90s (thay vì 45s) và luân phiên từng tab: script này dùng selector dạng
        // [class*="chat"] — đường chậm của Blink — nên gọi 4 tab cùng lúc rất tốn CPU.
        garbageCollectionJob = coroutineScope.launch(Dispatchers.Main) {
            var nextTab = 0
            while (true) {
                delay(GC_TICK_MS)
                val count = tabCount
                if (count <= 0) continue
                val i = nextTab % count
                nextTab = (nextTab + 1) % count
                val wv = webViews[i] ?: continue
                if (wv.url == null || wv.url == "about:blank") continue
                if (System.currentTimeMillis() - wv.lastTouchTimeMs < 5000L) continue
                wv.evaluateJavascript(GameScriptInjector.buildDomGarbageCollectionScript(), null)
            }
        }
    }

    private fun setTabState(tabIndex: Int, state: TabLifecycleState) {
        val currentList = _characterStatsList.value
        // List luôn có SLOT_COUNT entry; guard chỉ để một index rác không làm ném.
        if (tabIndex !in currentList.indices) return
        val mutable = currentList.toMutableList()
        val existing = mutable[tabIndex] ?: CharacterStats(tabIndex = tabIndex)
        mutable[tabIndex] = existing.copy(
            state = state,
            isOnline = (state == TabLifecycleState.IN_GAME),
            lastUpdateEpoch = System.currentTimeMillis()
        )
        _characterStatsList.value = mutable
    }

    private fun updateStatus(tabIndex: Int, status: String) {
        val currentList = _characterStatsList.value
        if (tabIndex !in currentList.indices) return
        val mutable = currentList.toMutableList()
        val existing = mutable[tabIndex] ?: CharacterStats(tabIndex = tabIndex)
        mutable[tabIndex] = existing.copy(
            lastStatus = status,
            lastUpdateEpoch = System.currentTimeMillis()
        )
        _characterStatsList.value = mutable
    }

    fun cleanup() {
        loginQueueJob?.cancel()
        periodicWatchdogJob?.cancel()
        garbageCollectionJob?.cancel()
        autoPilot.shutdown()
        mainHandler.post {
            for (i in webViews.indices) {
                retryRunnables[i]?.let { mainHandler.removeCallbacks(it) }
                retryRunnables[i] = null
                // Tháo khỏi cây View trước khi destroy (xem onRenderProcessGone)
                try {
                    val old = webViews[i]
                    (old?.parent as? ViewGroup)?.removeView(old)
                } catch (_: Exception) {}
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
                if (tabIndex >= tabCount) return@post
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
                    // Tab đã bị đóng/xoá tài khoản: trang cũ có thể kịp gọi một lượt quét cuối
                    // trong lúc bị thay bằng about:blank. Không cho lượt đó dựng lại IN_GAME.
                    if (!isTabActive[tabIndex]) return@post
                    if (tabIndex !in _characterStatsList.value.indices) return@post
                    val json = JSONObject(jsonStr)
                    val currentList = _characterStatsList.value.toMutableList()
                    val prev = currentList[tabIndex] ?: CharacterStats(tabIndex = tabIndex)

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
                    val tasksCompleted = json.optString("tasksCompleted", prev.tasksCompleted)
                        .replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    // Có log mới trong 8 giây thì giữ log đó, đừng để watchdog ghi đè.
                    val recentLog = System.currentTimeMillis() - lastGameLogAtMs[tabIndex] < gameLogStickyMs
                    val status = if (recentLog) {
                        prev.lastStatus
                    } else {
                        json.optString("lastStatus", prev.lastStatus)
                            .replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    }

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
                        tasksCompleted = if (tasksCompleted.isNotBlank()) tasksCompleted else prev.tasksCompleted,
                        lastStatus = status,
                        lastUpdateEpoch = System.currentTimeMillis()
                    )
                    // Chỉ phát StateFlow khi dữ liệu hiển thị THỰC SỰ đổi.
                    // Mỗi lần phát là một lượt recompose toàn bộ Sidebar;
                    // quét định kỳ mà dữ liệu không đổi thì bỏ qua để đỡ tốn CPU.
                    val updated = currentList[tabIndex]
                    val changed = updated.state != prev.state ||
                        updated.characterName != prev.characterName ||
                        updated.sect != prev.sect ||
                        updated.level != prev.level ||
                        updated.expPercent != prev.expPercent ||
                        updated.daiHongBao != prev.daiHongBao ||
                        updated.hongBao != prev.hongBao ||
                        updated.thapRecord != prev.thapRecord ||
                        updated.bossHoangKim != prev.bossHoangKim ||
                        updated.daTauQuest != prev.daTauQuest ||
                        updated.tasksCompleted != prev.tasksCompleted ||
                        updated.lastStatus != prev.lastStatus
                    if (changed) {
                        _characterStatsList.value = currentList
                    }
                } catch (_: Exception) {}
            }
        }

        @JavascriptInterface
        fun onGameLog(idx: Int, message: String) {
            mainHandler.post {
                if (tabIndex !in _characterStatsList.value.indices) return@post
                lastGameLogAtMs[tabIndex] = System.currentTimeMillis()
                val currentList = _characterStatsList.value.toMutableList()
                val prev = currentList[tabIndex] ?: CharacterStats(tabIndex = tabIndex)
                currentList[tabIndex] = prev.copy(
                    lastStatus = message.replace("\n", " ").replace(Regex("\\s+"), " ").trim(),
                    lastUpdateEpoch = System.currentTimeMillis()
                )
                _characterStatsList.value = currentList
            }
        }
    }
}
