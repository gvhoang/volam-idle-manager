package com.example.update

import android.content.Context
import com.example.BuildConfig
import com.example.R
import com.example.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Điều phối toàn bộ luồng cập nhật (docs/DESIGN-AUTOUPDATE.md §3–§5).
 *
 * RÀNG BUỘC TRUNG TÂM — tự kiểm tra thì có, tự tải thì không, tự cài thì KHÔNG BAO GIỜ:
 * `checkForUpdate()` chạy nền (12h/lần, im lặng khi lỗi). Tải chỉ xảy ra trong [download]
 * và cài chỉ trong [install] — cả hai chỉ được gọi từ một cú chạm trong ControlSheet đang mở,
 * và [install] còn tự chặn nếu app không ở foreground.
 *
 * App này giữ mật khẩu game dạng plaintext và tự điền vào trang. Một lỗi "cập nhật im lặng"
 * không phải "app khởi động lại" mà là "ai kiểm soát kênh cập nhật lấy được mọi tài khoản".
 */
class UpdateRepository(
    context: Context,
    private val scope: CoroutineScope
) {

    private val appContext = context.applicationContext
    private val prefs = PreferencesManager(appContext)

    private val checker = UpdateChecker(BuildConfig.VERSION_NAME)
    private val downloader = UpdateDownloader(appContext)
    private val inspector = ApkInspector(appContext)
    private val installer = ApkInstaller(appContext)

    /** Single-flight: một lần check / một lần tải / một lần cài tại một thời điểm. */
    private val mutex = Mutex()

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /** Thông điệp một lần cho banner đầu phiên (kết quả cài của process trước). */
    private val _launchMessage = MutableStateFlow<String?>(null)
    val launchMessage: StateFlow<String?> = _launchMessage.asStateFlow()

    /** Release đã thấy gần nhất — cần cho `304` và cho lần mở app sau. */
    private var currentRelease: UpdateRelease? = readCachedRelease()

    val appVersionName: String = BuildConfig.VERSION_NAME

    init {
        restoreInstallResult()
        downloader.cleanupStale()
    }

    // =========================================================================
    // KIỂM TRA
    // =========================================================================

    /**
     * Gọi API `releases/latest`. Không ném — mọi lỗi thành state hoặc bị nuốt (nền).
     *
     * @param manual true khi người dùng bấm tay: được throttle 60s và ĐƯỢC hiện lỗi.
     *               false là vòng nền: 12h/lần và im lặng khi lỗi (không bung dialog đè game).
     */
    suspend fun checkForUpdate(manual: Boolean) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (mutex.isLocked) return@withContext
        if (manual) {
            if (now - prefs.getUpdateLastManualCheck() < MANUAL_CHECK_THROTTLE_MS) return@withContext
        } else {
            if (now - prefs.getUpdateLastAttempt() < AUTO_CHECK_INTERVAL_MS) return@withContext
        }
        val blockedUntil = prefs.getUpdateBlockedUntil()
        if (blockedUntil > now) {
            if (manual) _state.value = UpdateUiState.RateLimited(blockedUntil)
            return@withContext
        }

        mutex.withLock {
            val startedAt = System.currentTimeMillis()
            prefs.setUpdateLastAttempt(startedAt)
            if (manual) prefs.setUpdateLastManualCheck(startedAt)
            if (manual) _state.value = UpdateUiState.Checking

            try {
                when (val outcome = checker.fetchLatest(prefs.getUpdateEtag())) {
                    is UpdateChecker.Outcome.NotModified -> {
                        val cached = currentRelease
                        if (cached != null) {
                            publishAvailability(cached)
                        } else {
                            _state.value = UpdateUiState.UpToDate(BuildConfig.VERSION_NAME)
                        }
                    }

                    is UpdateChecker.Outcome.Found -> {
                        prefs.setUpdateEtag(outcome.etag)
                        currentRelease = outcome.release
                        prefs.setCachedReleaseJson(outcome.release.toJson().toString())
                        publishAvailability(outcome.release)
                    }

                    is UpdateChecker.Outcome.Unparsable -> {
                        // Không tự tải: hiện thông báo + để người dùng mở trang phát hành.
                        if (manual) _state.value = UpdateUiState.Unparsable(outcome.releasesUrl)
                    }
                }
            } catch (f: UpdateFailure) {
                if (f.kind == UpdateFailure.Kind.RATE_LIMITED) {
                    val until = f.retryAtMillis.takeIf { it > 0 }
                        ?: (System.currentTimeMillis() + DEFAULT_RATE_LIMIT_MS)
                    prefs.setUpdateBlockedUntil(until)
                    if (manual) _state.value = UpdateUiState.RateLimited(until)
                } else if (manual) {
                    _state.value = if (f.kind == UpdateFailure.Kind.NETWORK) {
                        UpdateUiState.NoNetwork
                    } else {
                        UpdateUiState.Failed(format(f))
                    }
                }
                // Nền: im lặng hoàn toàn. Không đổi state, không toast, không dialog.
            }
        }
    }

    private fun publishAvailability(release: UpdateRelease) {
        _state.value = if (release.versionCode > BuildConfig.VERSION_CODE) {
            UpdateUiState.Available(release.versionName, release.assetSize, release.notes)
        } else {
            UpdateUiState.UpToDate(release.versionName)
        }
    }

    // =========================================================================
    // TẢI VỀ — chỉ từ cú chạm
    // =========================================================================

    suspend fun download() = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext
        try {
            val release = currentRelease
            if (release == null) {
                _state.value = UpdateUiState.Idle
                return@withContext
            }
            var attempt = 0
            while (true) {
                attempt++
                try {
                    _state.value = UpdateUiState.Downloading(0L, release.assetSize)
                    // Callback chạy mỗi 64 KB; chỉ phát state khi phần trăm đổi để Compose
                    // không recompose cả sheet ~350 lần cho một tệp 22 MB.
                    var lastPercent = -1
                    val file = downloader.download(release) { received, total ->
                        val percent = if (total > 0L) ((received * 100L) / total).toInt() else 0
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _state.value = UpdateUiState.Downloading(received, total)
                        }
                    }
                    _state.value = UpdateUiState.Verifying
                    // §3.3: versionCode đọc từ chính APK phải lớn hơn — giá trị suy từ tag
                    // KHÔNG bao giờ là căn cứ để cài.
                    inspector.preflight(file, BuildConfig.VERSION_CODE)
                    _state.value = UpdateUiState.ReadyToInstall(release.versionName, release.assetSize)
                    return@withContext
                } catch (f: UpdateFailure) {
                    val retryable = f.kind == UpdateFailure.Kind.SHA ||
                        f.kind == UpdateFailure.Kind.NETWORK
                    if (retryable && attempt < MAX_DOWNLOAD_ATTEMPTS) continue
                    downloader.deleteApk()
                    _state.value = if (f.kind == UpdateFailure.Kind.NETWORK) {
                        UpdateUiState.NoNetwork
                    } else {
                        UpdateUiState.Failed(format(f))
                    }
                    return@withContext
                }
            }
        } finally {
            mutex.unlock()
        }
    }

    // =========================================================================
    // CÀI ĐẶT — cú chạm thứ hai, và chỉ khi app đang visible
    // =========================================================================

    suspend fun install() = withContext(Dispatchers.IO) {
        if (!AppVisibility.isForeground) {
            // Android 10+ chặn start Activity từ background (BAL): commit xong sẽ im lặng
            // không làm gì và người dùng chỉ thấy "Đang mở trình cài đặt…" mãi.
            _state.value = UpdateUiState.Failed(appContext.getString(R.string.update_error_generic, "BAL"))
            return@withContext
        }

        if (!installer.canInstallPackages()) {
            prefs.setPendingAction(PENDING_ACTION_INSTALL)
            _state.value = UpdateUiState.NeedsPermission
            withContext(Dispatchers.Main) { installer.openInstallPermissionSettings() }
            return@withContext
        }

        val apk = downloader.apkFile()
        if (!apk.exists() || apk.length() <= 0L) {
            _state.value = UpdateUiState.Failed(appContext.getString(R.string.update_error_invalid_apk))
            return@withContext
        }
        if (!mutex.tryLock()) return@withContext
        try {
            // Kiểm lại ngay trước khi cài, không chỉ lúc tải xong. Giữa hai thời điểm đó người
            // dùng có thể rời app để cấp quyền "Cài ứng dụng không rõ nguồn gốc" rồi quay lại —
            // tệp nằm im trong cacheDir suốt khoảng thời gian ấy. Cùng một preflight, không
            // thêm logic mới; UpdateFailure rơi vào catch bên dưới như mọi lỗi khác.
            inspector.preflight(apk, BuildConfig.VERSION_CODE)
            val versionName = currentRelease?.versionName ?: BuildConfig.VERSION_NAME
            _state.value = UpdateUiState.Installing
            installer.install(apk)
            // Installer đã copy sang staging của nó: tệp trong cacheDir hết vai trò.
            downloader.deleteApk()
            scheduleInstallTimeout(versionName)
        } catch (f: UpdateFailure) {
            _state.value = if (f.kind == UpdateFailure.Kind.PERMISSION) {
                UpdateUiState.NeedsPermission
            } else {
                UpdateUiState.Failed(format(f))
            }
        } finally {
            mutex.unlock()
        }
    }

    /** §7 #10: nếu 20s không có gì xảy ra thì state không được treo ở "Đang mở trình cài đặt…". */
    private fun scheduleInstallTimeout(versionName: String) {
        scope.launch {
            delay(INSTALL_TIMEOUT_MS)
            if (_state.value is UpdateUiState.Installing) {
                _state.value = UpdateUiState.Failed(
                    appContext.getString(R.string.update_error_generic, versionName)
                )
            }
        }
    }

    /**
     * Gọi từ `MainActivity.onResume`. KHÔNG tin "người dùng đã bật quyền": kiểm lại bằng
     * `canRequestPackageInstalls()`. Khi đã có quyền, chỉ đưa UI về `ReadyToInstall` để
     * người dùng bấm "Cài đặt" — không tự commit (một tap mỗi lần cài là bất biến).
     */
    fun onAppResumed() {
        if (prefs.getPendingAction() != PENDING_ACTION_INSTALL) return
        val granted = installer.canInstallPackages()
        if (!granted) return
        prefs.setPendingAction(null)
        if (_state.value is UpdateUiState.NeedsPermission) {
            _state.value = if (downloader.apkFile().exists()) {
                UpdateUiState.ReadyToInstall(
                    currentRelease?.versionName ?: BuildConfig.VERSION_NAME,
                    currentRelease?.assetSize ?: 0L
                )
            } else {
                UpdateUiState.Idle
            }
        }
    }

    /** Kết quả install tới muộn (process còn sống) — receiver gọi trực tiếp. */
    fun onInstallResultReceived(message: String) {
        _state.value = UpdateUiState.Failed(message)
    }

    // =========================================================================
    // BANNER
    // =========================================================================

    /** Banner chỉ hiện tối đa 1 lần cho mỗi `versionCode` (§6.3). */
    fun shouldShowBanner(): Boolean {
        val versionCode = currentRelease?.versionCode ?: return false
        return prefs.getUpdateBannerShownFor() != versionCode
    }

    fun markBannerShown() {
        val versionCode = currentRelease?.versionCode ?: return
        prefs.setUpdateBannerShownFor(versionCode)
    }

    /** True khi release đang thấy mới hơn bản đang chạy (dùng cho banner). */
    fun hasNewerRelease(): Boolean =
        (currentRelease?.versionCode ?: 0) > BuildConfig.VERSION_CODE

    /** Banner đầu phiên (kết quả cài của process trước). Chỉ đọc một lần. */
    fun consumeLaunchMessage(): String? {
        val message = _launchMessage.value
        _launchMessage.value = null
        return message
    }

    /**
     * URL mở bằng ACTION_VIEW. `html_url` đến từ response của GitHub, không phải từ code, nên
     * chỉ nhận khi đúng tiền tố repo này — nếu không thì một release bị sửa có thể đẩy người
     * dùng sang một trang bất kỳ trong trình duyệt.
     */
    fun releasesPageUrl(): String {
        val fallback = "https://github.com/gvhoang/volam-idle-manager/releases"
        val url = currentRelease?.htmlUrl ?: return fallback
        return if (url.startsWith("https://github.com/gvhoang/volam-idle-manager/")) url else fallback
    }

    // =========================================================================
    // TIỆN ÍCH
    // =========================================================================

    private fun format(f: UpdateFailure): String =
        if (f.args.isEmpty()) {
            appContext.getString(f.messageRes)
        } else {
            appContext.getString(f.messageRes, *f.args.toTypedArray())
        }

    /**
     * Đọc kết quả cài của process trước. Kết quả này thường được ghi bởi
     * [InstallResultReceiver] trong một process đã bị kill — prefs là nguồn duy nhất.
     */
    private fun restoreInstallResult() {
        val raw = prefs.getInstallResult() ?: return
        prefs.clearInstallResult()
        val parts = raw.split('|')
        val status = parts.getOrNull(0)?.toIntOrNull()
        val message = parts.getOrNull(1)?.replace('¦', '|') ?: return
        if (status == android.content.pm.PackageInstaller.STATUS_SUCCESS) {
            _launchMessage.value = message
            _state.value = UpdateUiState.UpToDate(BuildConfig.VERSION_NAME)
        } else {
            _state.value = UpdateUiState.Failed(message)
        }
    }

    private fun readCachedRelease(): UpdateRelease? {
        val json = prefs.getCachedReleaseJson() ?: return null
        return try {
            UpdateRelease.fromJson(JSONObject(json))
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val AUTO_CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L
        const val AUTO_CHECK_JITTER_MS = 30L * 60L * 1000L
        private const val MANUAL_CHECK_THROTTLE_MS = 60L * 1000L
        private const val DEFAULT_RATE_LIMIT_MS = 15L * 60L * 1000L
        private const val MAX_DOWNLOAD_ATTEMPTS = 2
        private const val INSTALL_TIMEOUT_MS = 20_000L
        const val PENDING_ACTION_INSTALL = "install"
    }
}

// =============================================================================
// (De)serialize release đã lưu — dùng org.json để không thêm dependency.
// =============================================================================

private fun UpdateRelease.toJson(): JSONObject = JSONObject().apply {
    put("versionCode", versionCode)
    put("versionName", versionName)
    put("tagName", tagName)
    put("notes", notes)
    put("htmlUrl", htmlUrl)
    put("assetName", assetName)
    put("assetUrl", assetUrl)
    put("assetSize", assetSize)
    put("sha256", sha256 ?: JSONObject.NULL)
}

private fun UpdateRelease.Companion.fromJson(o: JSONObject): UpdateRelease = UpdateRelease(
    versionCode = o.optInt("versionCode", 0),
    versionName = o.optString("versionName", ""),
    tagName = o.optString("tagName", ""),
    notes = o.optString("notes", ""),
    htmlUrl = o.optString("htmlUrl", ""),
    assetName = o.optString("assetName", ""),
    assetUrl = o.optString("assetUrl", ""),
    assetSize = o.optLong("assetSize", 0L),
    sha256 = o.optString("sha256", "").takeIf { it.isNotBlank() && it != "null" }
)
