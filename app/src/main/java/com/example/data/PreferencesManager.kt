package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AccountConfig

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("volam_idle_manager_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CLUSTER = "pref_cluster"
        private const val KEY_FPS = "pref_fps"
        private const val KEY_RX_OFFSET = "pref_rx_offset"
        private const val KEY_TX_OFFSET = "pref_tx_offset"
        private const val KEY_LEGACY_USER_PREFIX = "pref_user_tab_"
        private const val KEY_LEGACY_PASS_PREFIX = "pref_pass_tab_"
        private const val KEY_LEGACY_AUTO_PREFIX = "pref_auto_tab_"
        private const val KEY_LEGACY_URL_PREFIX = "pref_url_tab_"

        // Cấu hình "Tự động" (bảng 🤖) lưu NGUYÊN một chuỗi JSON, không phải 18 khoá rời:
        // thêm/bớt một mục sau này không phải sửa bản ghi cũ, và mặc định thiếu khoá đã
        // nằm trong AutoConfig.fromJson().
        private const val KEY_AUTO_CFG = "pref_auto_cfg"

        // ===== Tự động cập nhật (docs/DESIGN-AUTOUPDATE.md) =====
        // ===== Số tab cấu hình được (docs/DESIGN-TAB-COUNT.md §8) =====
        // Ghi bằng commit(): app chạy 24/7 và có thể bị thu hồi bất cứ lúc nào; một lần ghi
        // bất đồng bộ bị mất sẽ trả UI về số tab khác với màn hình đang vẽ.
        private const val KEY_TAB_COUNT = "pref_tab_count"
        private const val KEY_TAB_WARN_SHOWN = "pref_tab_warn_shown"

        /** Sàn 12h của vòng kiểm tra tự động (chống đốt 60 request/giờ/IP của GitHub API). */
        private const val KEY_UPDATE_LAST_ATTEMPT = "update_last_check_attempt"
        private const val KEY_UPDATE_LAST_MANUAL = "update_last_manual_check"
        private const val KEY_UPDATE_ETAG = "update_etag"
        private const val KEY_UPDATE_BLOCKED_UNTIL = "update_blocked_until"
        private const val KEY_UPDATE_CACHED_RELEASE = "update_cached_release"
        private const val KEY_UPDATE_BANNER_SHOWN_FOR = "update_banner_shown_for"

        // Kết quả cài đặt: "status|message|versionName|timestamp". Kết quả thường tới ở
        // process MỚI, nên đây là nguồn duy nhất còn lại khi UI mở lại.
        private const val KEY_UPDATE_INSTALL_RESULT = "update_install_result"
        private const val KEY_UPDATE_PENDING_ACTION = "pending_action"

        /** Host của máy chủ game duy nhất được tin cậy. */
        private const val GAME_HOST = "volamidle.pages.dev"

        /**
         * URL tùy chỉnh chỉ hợp lệ khi trỏ đúng máy chủ game và dùng HTTPS.
         *
         * Vì sao phải chặt: app tự điền tài khoản/mật khẩu vào trang ngay sau khi URL
         * tải xong. Cho phép host lạ nghĩa là một URL gõ nhầm — hoặc bị người khác đưa
         * cho — sẽ nhận được mật khẩu game. Chỉ nhận HTTPS vì trang đăng nhập không được
         * đi qua kênh rõ.
         *
         * Chuỗi rỗng luôn hợp lệ: đó là "dùng máy chủ mặc định".
         */
        fun isTrustedGameUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return true
            val uri = try {
                android.net.Uri.parse(trimmed)
            } catch (_: Exception) {
                return false
            }
            if (uri.scheme?.lowercase() != "https") return false
            val host = uri.host?.lowercase() ?: return false
            return host == GAME_HOST || host.endsWith(".$GAME_HOST")
        }
    }

    /** Chuỗi JSON cấu hình tự động. Rỗng = chưa từng lưu (mọi mặc định, trong đó on = false). */
    fun getAutoConfig(): String = prefs.getString(KEY_AUTO_CFG, "") ?: ""

    fun setAutoConfig(json: String) {
        // commit() như saveAccount: người dùng vừa bấm Lưu thì phải có hiệu lực ngay,
        // kể cả khi tiến trình bị hệ điều hành thu hồi trước lần ghi bất đồng bộ kế tiếp.
        prefs.edit().putString(KEY_AUTO_CFG, json).commit()
    }

    private fun getClusterKey(cluster: String): String {
        return cluster.replace(" ", "_").lowercase()
    }

    fun getCluster(): String {
        return prefs.getString(KEY_CLUSTER, "Cụm 1") ?: "Cụm 1"
    }

    fun setCluster(cluster: String) {
        prefs.edit().putString(KEY_CLUSTER, cluster).commit()
    }

    fun getClusterUrl(cluster: String): String {
        return when (cluster) {
            "Cụm 2" -> "https://volamidle.pages.dev"
            "Cụm 3" -> "https://volamidle.pages.dev"
            else -> "https://volamidle.pages.dev"
        }
    }

    fun getFps(): Int {
        return prefs.getInt(KEY_FPS, 60)
    }

    fun setFps(fps: Int) {
        prefs.edit().putInt(KEY_FPS, fps).apply()
    }

    fun getAccount(tabIndex: Int, cluster: String = getCluster()): AccountConfig {
        val cKey = getClusterKey(cluster)
        val clusterNum = when (cluster) {
            "Cụm 2" -> "2"
            "Cụm 3" -> "3"
            else -> "1"
        }
        val clusterUserKey = "user_cluster_${cKey}_tab_$tabIndex"
        val clusterPassKey = "pass_cluster_${cKey}_tab_$tabIndex"
        val clusterAutoKey = "auto_cluster_${cKey}_tab_$tabIndex"
        val clusterUrlKey = "url_cluster_${cKey}_tab_$tabIndex"

        var user = prefs.getString(clusterUserKey, "") ?: ""
        var pass = prefs.getString(clusterPassKey, "") ?: ""
        var auto = prefs.getBoolean(clusterAutoKey, true)
        var customUrl = prefs.getString(clusterUrlKey, "") ?: ""

        // Check cluster number key (e.g. user_cluster_1_tab_0)
        if (user.isBlank()) {
            val numUser = prefs.getString("user_cluster_${clusterNum}_tab_$tabIndex", "") ?: ""
            if (numUser.isNotBlank()) {
                user = numUser
                pass = prefs.getString("pass_cluster_${clusterNum}_tab_$tabIndex", "") ?: ""
                auto = prefs.getBoolean("auto_cluster_${clusterNum}_tab_$tabIndex", true)
                customUrl = prefs.getString("url_cluster_${clusterNum}_tab_$tabIndex", "") ?: ""
            }
        }

        // Check cluster direct key (e.g. user_cluster_Cụm 1_tab_0)
        if (user.isBlank()) {
            val directUser = prefs.getString("user_cluster_${cluster}_tab_$tabIndex", "") ?: ""
            if (directUser.isNotBlank()) {
                user = directUser
                pass = prefs.getString("pass_cluster_${cluster}_tab_$tabIndex", "") ?: ""
                auto = prefs.getBoolean("auto_cluster_${cluster}_tab_$tabIndex", true)
                customUrl = prefs.getString("url_cluster_${cluster}_tab_$tabIndex", "") ?: ""
            }
        }

        // Fallback to legacy key (pref_user_tab_0)
        if (user.isBlank()) {
            val legacyUser = prefs.getString("$KEY_LEGACY_USER_PREFIX$tabIndex", "") ?: ""
            if (legacyUser.isNotBlank()) {
                user = legacyUser
                pass = prefs.getString("$KEY_LEGACY_PASS_PREFIX$tabIndex", "") ?: ""
                auto = prefs.getBoolean("$KEY_LEGACY_AUTO_PREFIX$tabIndex", true)
                customUrl = prefs.getString("$KEY_LEGACY_URL_PREFIX$tabIndex", "") ?: ""
            }
        }

        // Fallback to bare key (user_tab_0)
        if (user.isBlank()) {
            val bareUser = prefs.getString("user_tab_$tabIndex", "") ?: ""
            if (bareUser.isNotBlank()) {
                user = bareUser
                pass = prefs.getString("pass_tab_$tabIndex", "") ?: ""
            }
        }

        return AccountConfig(tabIndex, user.trim(), pass.trim(), auto, customUrl.trim())
    }

    fun saveAccount(config: AccountConfig, cluster: String = getCluster()) {
        val cKey = getClusterKey(cluster)
        val clusterNum = when (cluster) {
            "Cụm 2" -> "2"
            "Cụm 3" -> "3"
            else -> "1"
        }
        val cleanUser = config.username.trim()
        val cleanPass = config.password.trim()
        val cleanUrl = config.customUrl.trim()

        // Save synchronously to all possible key formats with commit()
        prefs.edit()
            .putString("user_cluster_${cKey}_tab_${config.tabIndex}", cleanUser)
            .putString("pass_cluster_${cKey}_tab_${config.tabIndex}", cleanPass)
            .putBoolean("auto_cluster_${cKey}_tab_${config.tabIndex}", config.autoLoginEnabled)
            .putString("url_cluster_${cKey}_tab_${config.tabIndex}", cleanUrl)
            // Format with cluster number (e.g. user_cluster_1_tab_0)
            .putString("user_cluster_${clusterNum}_tab_${config.tabIndex}", cleanUser)
            .putString("pass_cluster_${clusterNum}_tab_${config.tabIndex}", cleanPass)
            .putBoolean("auto_cluster_${clusterNum}_tab_${config.tabIndex}", config.autoLoginEnabled)
            .putString("url_cluster_${clusterNum}_tab_${config.tabIndex}", cleanUrl)
            // Format with raw cluster string (e.g. user_cluster_Cụm 1_tab_0)
            .putString("user_cluster_${cluster}_tab_${config.tabIndex}", cleanUser)
            .putString("pass_cluster_${cluster}_tab_${config.tabIndex}", cleanPass)
            // Format with legacy prefix (pref_user_tab_0)
            .putString("$KEY_LEGACY_USER_PREFIX${config.tabIndex}", cleanUser)
            .putString("$KEY_LEGACY_PASS_PREFIX${config.tabIndex}", cleanPass)
            .putBoolean("$KEY_LEGACY_AUTO_PREFIX${config.tabIndex}", config.autoLoginEnabled)
            .putString("$KEY_LEGACY_URL_PREFIX${config.tabIndex}", cleanUrl)
            // Format with bare key (user_tab_0)
            .putString("user_tab_${config.tabIndex}", cleanUser)
            .putString("pass_tab_${config.tabIndex}", cleanPass)
            .commit()
    }

    fun getRxOffset(): Long = prefs.getLong(KEY_RX_OFFSET, 0L)
    fun getTxOffset(): Long = prefs.getLong(KEY_TX_OFFSET, 0L)

    // =========================================================================
    // SỐ TAB (1..8)
    // =========================================================================

    /**
     * Clamp CẢ HAI CHIỀU: ghi để chuẩn hoá, đọc để một giá trị sửa tay (adb/backup) không
     * index quá mảng `SLOT_COUNT`.
     */
    fun getTabCount(): Int = com.example.model.TabLimits.clamp(
        prefs.getInt(KEY_TAB_COUNT, com.example.model.TabLimits.DEFAULT)
    )

    fun setTabCount(count: Int) {
        prefs.edit().putInt(KEY_TAB_COUNT, com.example.model.TabLimits.clamp(count)).commit()
    }

    /** Đã hiện cảnh báo "trên 4 tab" một lần chưa. */
    fun isTabWarnShown(): Boolean = prefs.getBoolean(KEY_TAB_WARN_SHOWN, false)

    fun setTabWarnShown(shown: Boolean) {
        prefs.edit().putBoolean(KEY_TAB_WARN_SHOWN, shown).apply()
    }

    // =========================================================================
    // TỰ ĐỘNG CẬP NHẬT
    // =========================================================================

    fun getUpdateLastAttempt(): Long = prefs.getLong(KEY_UPDATE_LAST_ATTEMPT, 0L)

    fun setUpdateLastAttempt(value: Long) {
        prefs.edit().putLong(KEY_UPDATE_LAST_ATTEMPT, value).apply()
    }

    fun getUpdateLastManualCheck(): Long = prefs.getLong(KEY_UPDATE_LAST_MANUAL, 0L)

    fun setUpdateLastManualCheck(value: Long) {
        prefs.edit().putLong(KEY_UPDATE_LAST_MANUAL, value).apply()
    }

    /** ETag của response `releases/latest`; gửi lại bằng `If-None-Match` để nhận 304. */
    fun getUpdateEtag(): String? = prefs.getString(KEY_UPDATE_ETAG, null)

    fun setUpdateEtag(value: String?) {
        prefs.edit().putString(KEY_UPDATE_ETAG, value).apply()
    }

    fun getUpdateBlockedUntil(): Long = prefs.getLong(KEY_UPDATE_BLOCKED_UNTIL, 0L)

    fun setUpdateBlockedUntil(value: Long) {
        prefs.edit().putLong(KEY_UPDATE_BLOCKED_UNTIL, value).apply()
    }

    /** Release đã thấy gần nhất (JSON) — để 304 và lần mở sau vẫn tải/cài được. */
    fun getCachedReleaseJson(): String? = prefs.getString(KEY_UPDATE_CACHED_RELEASE, null)

    fun setCachedReleaseJson(value: String?) {
        prefs.edit().putString(KEY_UPDATE_CACHED_RELEASE, value).apply()
    }

    /** `versionCode` của lần đã hiện banner; banner chỉ hiện tối đa 1 lần / versionCode. */
    fun getUpdateBannerShownFor(): Int = prefs.getInt(KEY_UPDATE_BANNER_SHOWN_FOR, 0)

    fun setUpdateBannerShownFor(value: Int) {
        prefs.edit().putInt(KEY_UPDATE_BANNER_SHOWN_FOR, value).apply()
    }

    fun getInstallResult(): String? = prefs.getString(KEY_UPDATE_INSTALL_RESULT, null)

    /** `commit()`: receiver có thể bị kill ngay sau khi trả về. */
    fun setInstallResult(record: String) {
        prefs.edit().putString(KEY_UPDATE_INSTALL_RESULT, record).commit()
    }

    fun clearInstallResult() {
        prefs.edit().remove(KEY_UPDATE_INSTALL_RESULT).commit()
    }

    fun getPendingAction(): String? = prefs.getString(KEY_UPDATE_PENDING_ACTION, null)

    fun setPendingAction(action: String?) {
        prefs.edit().putString(KEY_UPDATE_PENDING_ACTION, action).commit()
    }

    fun resetTrafficCounters(currentRx: Long, currentTx: Long) {
        prefs.edit()
            .putLong(KEY_RX_OFFSET, currentRx)
            .putLong(KEY_TX_OFFSET, currentTx)
            .apply()
    }
}