package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AccountConfig

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("volam_idle_manager_prefs", Context.MODE_PRIVATE)

    companion object {
        // Chỉ còn một máy chủ duy nhất cho toàn bộ ứng dụng
        const val GAME_URL = "https://volamidle.pages.dev"

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

    fun setFps(fps: Int) {
        prefs.edit().putInt(KEY_FPS, fps).apply()
    }

    fun getAccount(tabIndex: Int): AccountConfig {
        val canonical = readCanonical(tabIndex)
        if (canonical.username.isNotBlank()) {
            return canonical
        }
        return migrateLegacyAccount(tabIndex) ?: canonical
    }

    private fun readCanonical(tabIndex: Int) = AccountConfig(
        tabIndex = tabIndex,
        username = (prefs.getString(keyUser(tabIndex), "") ?: "").trim(),
        password = (prefs.getString(keyPass(tabIndex), "") ?: "").trim(),
        autoLoginEnabled = prefs.getBoolean(keyAuto(tabIndex), true),
        customUrl = (prefs.getString(keyUrl(tabIndex), "") ?: "").trim()
    )

    private fun migrateLegacyAccount(tabIndex: Int): AccountConfig? {
        val legacyKeySets = listOf(
            listOf(
                "user_cluster_cụm_1_tab_$tabIndex",
                "pass_cluster_cụm_1_tab_$tabIndex",
                "auto_cluster_cụm_1_tab_$tabIndex",
                "url_cluster_cụm_1_tab_$tabIndex"
            ),
            listOf(
                "user_cluster_1_tab_$tabIndex",
                "pass_cluster_1_tab_$tabIndex",
                "auto_cluster_1_tab_$tabIndex",
                "url_cluster_1_tab_$tabIndex"
            ),
            listOf(
                "user_cluster_Cụm 1_tab_$tabIndex",
                "pass_cluster_Cụm 1_tab_$tabIndex",
                "auto_cluster_Cụm 1_tab_$tabIndex",
                "url_cluster_Cụm 1_tab_$tabIndex"
            ),
            listOf(
                "pref_user_tab_$tabIndex",
                "pref_pass_tab_$tabIndex",
                "pref_auto_tab_$tabIndex",
                "pref_url_tab_$tabIndex"
            )
        )

        for (keys in legacyKeySets) {
            val user = prefs.getString(keys[0], "") ?: ""
            if (user.isBlank()) continue

            val migrated = AccountConfig(
                tabIndex = tabIndex,
                username = user.trim(),
                password = (prefs.getString(keys[1], "") ?: "").trim(),
                autoLoginEnabled = prefs.getBoolean(keys[2], true),
                customUrl = (prefs.getString(keys[3], "") ?: "").trim()
            )
            saveAccount(migrated)
            return migrated
        }
        return null
    }

    private fun firstExisting(vararg keys: String): String? =
        keys.firstOrNull { prefs.contains(it) }

    fun saveAccount(config: AccountConfig) {
        val cleanUser = config.username.trim()
        val cleanPass = config.password.trim()
        val cleanUrl = config.customUrl.trim()

        // Ghi đồng bộ (commit) để không mất tài khoản khi tiến trình bị hệ thống thu hồi
        prefs.edit()
            .putString(keyUser(config.tabIndex), cleanUser)
            .putString(keyPass(config.tabIndex), cleanPass)
            .putBoolean(keyAuto(config.tabIndex), config.autoLoginEnabled)
            .putString(keyUrl(config.tabIndex), cleanUrl)
            .commit()
    }

    fun getRxOffset(): Long = prefs.getLong(KEY_RX_OFFSET, 0L)
    fun getTxOffset(): Long = prefs.getLong(KEY_TX_OFFSET, 0L)

    fun resetTrafficCounters(currentRx: Long, currentTx: Long) {
        prefs.edit()
            .putLong(KEY_RX_OFFSET, currentRx)
            .putLong(KEY_TX_OFFSET, currentTx)
            .apply()
    }
}
