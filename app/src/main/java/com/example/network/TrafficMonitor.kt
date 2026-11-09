package com.example.network

import android.content.Context
import android.net.TrafficStats
import android.os.Process
import com.example.model.NetworkDataInfo

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