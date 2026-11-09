package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.example.model.AccountConfig

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("volam_idle_manager_prefs", Context.MODE_PRIVATE)

    companion object {
        // Chỉ còn một máy chủ duy nhất cho toàn bộ ứng dụng
        const val GAME_URL = "https://volamidle.pages.dev"

        fun isTrustedGameUrl(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            val uri = Uri.parse(url)
            return uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("volamidle.pages.dev", ignoreCase = true) &&
                uri.userInfo == null && (uri.port == -1 || uri.port == 443)
        }

        private const val KEY_LEGACY_CLUSTER = "pref_cluster"

        private const val KEY_FPS = "pref_fps"
        private const val KEY_RX_OFFSET = "pref_rx_offset"
        private const val KEY_TX_OFFSET = "pref_tx_offset"
    }

    // Khóa chuẩn (không còn chiều Cụm): user_tab_0 / pass_tab_0 / auto_tab_0 / url_tab_0
    private fun keyUser(tabIndex: Int) = "user_tab_$tabIndex"
    private fun keyPass(tabIndex: Int) = "pass_tab_$tabIndex"
    private fun keyAuto(tabIndex: Int) = "auto_tab_$tabIndex"
    private fun keyUrl(tabIndex: Int) = "url_tab_$tabIndex"

    fun getFps(): Int {
        return prefs.getInt(KEY_FPS, 60)
    }

    /** Keep the last v3 profile identity so upgrades retain its WebView cookies and session. */
    fun getLegacyProfileId(): String = when (prefs.getString(KEY_LEGACY_CLUSTER, "Cụm 1")) {
        "Cụm 2" -> "2"
        "Cụm 3" -> "3"
        else -> "1"
    }

    fun setFps(fps: Int) {
        prefs.edit().putInt(KEY_FPS, fps).apply()
    }

    fun getAccount(tabIndex: Int): AccountConfig {
        val canonical = readCanonical(tabIndex)

        // Bản cũ chỉ ghi user_tab_N/pass_tab_N, KHÔNG hề ghi auto_tab_N/url_tab_N.
        // Vì vậy sự tồn tại của cả auto_tab_N lẫn url_tab_N là dấu hiệu duy nhất cho biết
        // tab này đã được bản mới ghi đè -> không cần bù dữ liệu cũ nữa.
        // (Không dùng "username rỗng" làm điều kiện: người dùng xóa tài khoản cũng để lại username rỗng,
        //  khi đó migrate sẽ hồi sinh tài khoản đã xóa.)
        val writtenByNewBuild =
            prefs.contains(keyAuto(tabIndex)) && prefs.contains(keyUrl(tabIndex))
        if (writtenByNewBuild) {
            return canonical
        }

        return migrateLegacyAccount(tabIndex, canonical)
    }

    private fun readCanonical(tabIndex: Int) = AccountConfig(
        tabIndex = tabIndex,
        username = (prefs.getString(keyUser(tabIndex), "") ?: "").trim(),
        password = (prefs.getString(keyPass(tabIndex), "") ?: "").trim(),
        autoLoginEnabled = prefs.getBoolean(keyAuto(tabIndex), true),
        customUrl = (prefs.getString(keyUrl(tabIndex), "") ?: "").trim()
    )

    /**
     * Bù dữ liệu từ định dạng khóa của bản cũ (thời còn chọn Cụm).
     *
     * Bản cũ ghi 5 nhóm khóa mỗi lần lưu:
     *   user_cluster_<cụm>_tab_N / pass_cluster_<cụm>_tab_N / auto_... / url_...   (đủ 4 trường)
     *   user_cluster_<số>_tab_N  / pass_cluster_<số>_tab_N  / auto_... / url_...   (đủ 4 trường)
     *   user_cluster_<thô>_tab_N / pass_cluster_<thô>_tab_N                        (chỉ user/pass)
     *   pref_user_tab_N / pref_pass_tab_N / pref_auto_tab_N / pref_url_tab_N       (đủ 4 trường)
     *   user_tab_N / pass_tab_N                                                    (chỉ user/pass, trùng khóa chuẩn)
     *
     * Nhóm pref_* được ghi đè ở MỌI lần lưu bất kể đang ở Cụm nào, nên nó luôn phản ánh lần lưu cuối.
     * Các nhóm theo Cụm thì KHÔNG: đổi sang Cụm 2 rồi xóa tài khoản sẽ để lại tài khoản cũ nằm nguyên
     * trong khóa của Cụm 1. Vì vậy:
     *   - pref_user_tab_N TỒN TẠI  -> chỉ đọc pref_*, tuyệt đối không dò khóa Cụm (tránh hồi sinh tài khoản đã xóa)
     *   - pref_user_tab_N VẮNG MẶT -> dữ liệu từ bản cũ hơn, mới được phép dò khóa Cụm
     */
    private fun migrateLegacyAccount(tabIndex: Int, canonical: AccountConfig): AccountConfig {
        val prefUserKey = "pref_user_tab_$tabIndex"
        val prefPassKey = "pref_pass_tab_$tabIndex"
        val hasLastSaveRecord = prefs.contains(prefUserKey)

        var username = canonical.username
        var password = canonical.password

        if (username.isBlank()) {
            val userPairs = if (hasLastSaveRecord) {
                listOf(prefUserKey to prefPassKey)
            } else {
                listOf(
                    "user_cluster_cụm_1_tab_$tabIndex" to "pass_cluster_cụm_1_tab_$tabIndex",
                    "user_cluster_1_tab_$tabIndex" to "pass_cluster_1_tab_$tabIndex",
                    "user_cluster_Cụm 1_tab_$tabIndex" to "pass_cluster_Cụm 1_tab_$tabIndex"
                )
            }
            for ((userKey, passKey) in userPairs) {
                val legacyUser = (prefs.getString(userKey, "") ?: "").trim()
                if (legacyUser.isNotBlank()) {
                    username = legacyUser
                    password = (prefs.getString(passKey, "") ?: "").trim()
                    break
                }
            }
        }

        // auto/url chỉ có ở nhóm pref_* và hai nhóm Cụm đủ 4 trường.
        // Khóa thô "Cụm 1" không có auto/url nên không đưa vào danh sách.
        val autoKey = if (hasLastSaveRecord) {
            firstExisting("pref_auto_tab_$tabIndex")
        } else {
            firstExisting(
                "auto_cluster_cụm_1_tab_$tabIndex",
                "auto_cluster_1_tab_$tabIndex"
            )
        }
        val urlKey = if (hasLastSaveRecord) {
            firstExisting("pref_url_tab_$tabIndex")
        } else {
            firstExisting(
                "url_cluster_cụm_1_tab_$tabIndex",
                "url_cluster_1_tab_$tabIndex"
            )
        }

        val migrated = AccountConfig(
            tabIndex = tabIndex,
            username = username,
            password = password,
            autoLoginEnabled = autoKey?.let { prefs.getBoolean(it, canonical.autoLoginEnabled) }
                ?: canonical.autoLoginEnabled,
            customUrl = urlKey?.let { (prefs.getString(it, "") ?: "").trim() }
                ?: canonical.customUrl
        )

        // Ghi lại khóa chuẩn + dọn khóa cũ: chỉ chạy một lần, và bảo đảm tài khoản đã xóa không hồi sinh.
        // Dùng apply() để không chặn UI thread (getAccount được gọi từ trong composition).
        persistAccount(migrated).apply()
        return migrated
    }

    private fun firstExisting(vararg keys: String): String? =
        keys.firstOrNull { prefs.contains(it) }

    fun saveAccount(config: AccountConfig) {
        // Ghi đồng bộ (commit) để không mất tài khoản khi tiến trình bị hệ thống thu hồi
        persistAccount(config).commit()
    }

    /** Trả về editor đã ghi khóa chuẩn và xóa toàn bộ khóa định dạng cũ của tab này. */
    private fun persistAccount(config: AccountConfig): SharedPreferences.Editor {
        val n = config.tabIndex
        val editor = prefs.edit()
            .putString(keyUser(n), config.username.trim())
            .putString(keyPass(n), config.password.trim())
            .putBoolean(keyAuto(n), config.autoLoginEnabled)
            .putString(keyUrl(n), config.customUrl.trim())

        legacyKeysForTab(n).forEach { editor.remove(it) }
        return editor
    }

    private fun legacyKeysForTab(n: Int) = listOf(
        "pref_user_tab_$n",
        "pref_pass_tab_$n",
        "pref_auto_tab_$n",
        "pref_url_tab_$n",
        "user_cluster_cụm_1_tab_$n",
        "pass_cluster_cụm_1_tab_$n",
        "auto_cluster_cụm_1_tab_$n",
        "url_cluster_cụm_1_tab_$n",
        "user_cluster_1_tab_$n",
        "pass_cluster_1_tab_$n",
        "auto_cluster_1_tab_$n",
        "url_cluster_1_tab_$n",
        "user_cluster_Cụm 1_tab_$n",
        "pass_cluster_Cụm 1_tab_$n",
        "auto_cluster_Cụm 1_tab_$n",
        "url_cluster_Cụm 1_tab_$n",
    )

    fun getRxOffset(): Long = prefs.getLong(KEY_RX_OFFSET, 0L)
    fun getTxOffset(): Long = prefs.getLong(KEY_TX_OFFSET, 0L)

    fun resetTrafficCounters(currentRx: Long, currentTx: Long) {
        prefs.edit()
            .putLong(KEY_RX_OFFSET, currentRx)
            .putLong(KEY_TX_OFFSET, currentTx)
            .apply()
    }
}
