package com.example.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.MainActivity
import com.example.R

/**
 * Sau khi cài, process cũ bị kill và FGS không được khởi động lại (Android 12+). Người dùng
 * không có tín hiệu nào cho tới khi tự mở app — receiver này đăng một notification
 * "Đã cập nhật lên X.Y.Z — chạm để mở".
 *
 * KHÔNG tự start Activity: bị BAL chặn và cũng không cần.
 */
class PackageReplacedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext
        try {
            // API 33+: thiếu POST_NOTIFICATIONS thì notify() bị bỏ trong im lặng. Kiểm tường
            // minh để không phải dựa vào hành vi ngầm của hệ thống.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    app.getString(R.string.update_notif_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                manager.createNotificationChannel(channel)
            }

            val open = Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or PendingIntent.FLAG_IMMUTABLE
            }
            val content = PendingIntent.getActivity(app, REQUEST_CODE, open, flags)

            val notification = NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(app.getString(R.string.app_name))
                .setContentText(
                    app.getString(R.string.update_banner_updated, BuildConfig.VERSION_NAME)
                )
                .setContentIntent(content)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            // POST_NOTIFICATIONS có thể chưa được cấp (API 33+): thiếu quyền thì hệ thống
            // bỏ notification trong im lặng, đúng như mong đợi — không có gì để làm thêm.
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            // Không có quyền / OEM chặn: bỏ qua, app vẫn mở được bằng tay.
        }
    }

    companion object {
        private const val CHANNEL_ID = "volam_idle_update_channel"
        private const val NOTIFICATION_ID = 1002
        private const val REQUEST_CODE = 8422
    }
}
