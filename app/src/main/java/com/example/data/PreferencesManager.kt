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

    fun resetTrafficCounters(currentRx: Long, currentTx: Long) {
        prefs.edit()
            .putLong(KEY_RX_OFFSET, currentRx)
            .putLong(KEY_TX_OFFSET, currentTx)
            .apply()
    }
}