package com.example.network

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.os.Process
import android.provider.Settings
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.example.model.NetworkDataInfo
import java.util.concurrent.Executors

class VpnManager(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun checkVpnActive(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    fun registerVpnCallback(onStatusChanged: (Boolean) -> Unit) {
        unregisterVpnCallback()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onStatusChanged(true)
            }

            override fun onLost(network: Network) {
                onStatusChanged(checkVpnActive())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val isVpn = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                onStatusChanged(isVpn)
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (_: Exception) {}
    }

    fun unregisterVpnCallback() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
            networkCallback = null
        }
    }

    fun launchProtonVpn(): Boolean {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage("ch.protonvpn.android")
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            true
        } else {
            openVpnSettings()
            false
        }
    }

    fun openVpnSettings() {
        try {
            val intent = Intent(Settings.ACTION_VPN_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    fun applyInternalProxy(host: String, port: Int, onSuccess: () -> Unit = {}, onError: () -> Unit = {}) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            onError()
            return
        }

        val proxyUrl = "$host:$port"
        val proxyConfig = ProxyConfig.Builder()
            .addProxyRule(proxyUrl)
            .addDirect()
            .build()

        ProxyController.getInstance().setProxyOverride(
            proxyConfig,
            Executors.newSingleThreadExecutor()
        ) {
            onSuccess()
        }
    }

    fun clearProxy(onComplete: () -> Unit = {}) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            ProxyController.getInstance().clearProxyOverride(
                Executors.newSingleThreadExecutor()
            ) {
                onComplete()
            }
        }
    }
}

class TrafficMonitor(private val context: Context) {
    private val myUid = Process.myUid()
    private var lastRxBytes = TrafficStats.getUidRxBytes(myUid)
    private var lastTxBytes = TrafficStats.getUidTxBytes(myUid)
    private var lastTimestamp = System.currentTimeMillis()

    fun sample(rxOffset: Long, txOffset: Long): NetworkDataInfo {
        val now = System.currentTimeMillis()
        val currentRx = TrafficStats.getUidRxBytes(myUid)
        val currentTx = TrafficStats.getUidTxBytes(myUid)

        val validRx = if (currentRx != TrafficStats.UNSUPPORTED.toLong()) currentRx else 0L
        val validTx = if (currentTx != TrafficStats.UNSUPPORTED.toLong()) currentTx else 0L

        val elapsedSec = ((now - lastTimestamp).coerceAtLeast(100L)) / 1000.0

        val deltaRx = (validRx - lastRxBytes).coerceAtLeast(0L)
        val deltaTx = (validTx - lastTxBytes).coerceAtLeast(0L)

        val rxSpeedBps = (deltaRx / elapsedSec).toLong()
        val txSpeedBps = (deltaTx / elapsedSec).toLong()

        lastRxBytes = validRx
        lastTxBytes = validTx
        lastTimestamp = now

        val totalUsedBytes = ((validRx - rxOffset).coerceAtLeast(0L)) + ((validTx - txOffset).coerceAtLeast(0L))

        return NetworkDataInfo(
            totalBytesFormatted = formatBytes(totalUsedBytes),
            downloadSpeedFormatted = formatSpeed(rxSpeedBps),
            uploadSpeedFormatted = formatSpeed(txSpeedBps),
            totalRxBytes = validRx,
            totalTxBytes = validTx
        )
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec < 1024L -> "$bytesPerSec B/s"
            bytesPerSec < 1024L * 1024L -> String.format(java.util.Locale.US, "%.0f KB/s", bytesPerSec / 1024.0)
            else -> String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
        }
    }
}
