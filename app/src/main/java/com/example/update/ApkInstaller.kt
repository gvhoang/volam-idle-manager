package com.example.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.R
import java.io.File

/**
 * Cài APK qua `PackageInstaller` session (docs/DESIGN-AUTOUPDATE.md §5).
 *
 * Vì sao KHÔNG dùng `ACTION_VIEW` / `FileProvider`:
 * - `ACTION_VIEW` + `file://` ⇒ `FileUriExposedException` từ targetSdk 24.
 * - `content://` + provider export-grant ⇒ grant tới MỌI activity khớp
 *   `application/vnd.android.package-archive`, kể cả app độc, và thêm một bề mặt vĩnh viễn.
 *   Session API ghi byte vào staging của installer, không đưa URI ra ngoài — không cần provider.
 *
 * Cài im lặng là bất khả thi với app thường (`INSTALL_PACKAGES` là signature-level).
 * `USER_ACTION_REQUIRED` khai báo TƯỜNG MINH để sau này không ai lật thành NOT_REQUIRED.
 */
class ApkInstaller(private val context: Context) {

    /** AppOp "Cài đặt ứng dụng không rõ nguồn gốc". Manifest permission là `normal`. */
    fun canInstallPackages(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.INSTALL_NON_MARKET_APPS,
                0
            ) == 1
        }
    } catch (_: Exception) {
        false
    }

    /**
     * Mở màn hình cấp AppOp. Không có callback kết quả — repository gọi lại
     * [canInstallPackages] trong `onResume`, không tin là người dùng đã bật.
     *
     * Fallback nhiều tầng vì MIUI/EMUI hay đổi tên màn hình.
     */
    fun openInstallPermissionSettings() {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                add(Intent(Settings.ACTION_SECURITY_SETTINGS))
            }
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            add(Intent(Settings.ACTION_SETTINGS))
        }
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // Thử intent kế tiếp.
            }
        }
    }

    /**
     * Tạo session → ghi `base.apk` → fsync → commit. Trả về ngay khi đã commit; kết quả
     * thật (thành công / lý do thất bại) về [InstallResultReceiver] ở process (thường là mới).
     *
     * @throws UpdateFailure PERMISSION khi AppOp bị từ chối, GENERIC cho lỗi khác.
     */
    fun install(apk: File) {
        if (!canInstallPackages()) {
            throw UpdateFailure(R.string.update_state_needs_permission, kind = UpdateFailure.Kind.PERMISSION)
        }
        if (!apk.exists() || apk.length() <= 0L) {
            throw UpdateFailure(R.string.update_error_invalid_apk, kind = UpdateFailure.Kind.INVALID_APK)
        }

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        // Ghim session vào đúng package của ta: installer từ chối mọi thứ khác.
        params.setAppPackageName(context.packageName)
        // `setRequireUserAction` có từ API 31. Khai báo TƯỜNG MINH để sau này không ai lật
        // thành NOT_REQUIRED; dưới 31 platform luôn bắt người dùng xác nhận nên mặc định
        // đã đúng — không cần (và không được) gọi.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        params.setSize(apk.length())

        val sessionId = try {
            installer.createSession(params)
        } catch (_: SecurityException) {
            throw UpdateFailure(R.string.update_state_needs_permission, kind = UpdateFailure.Kind.PERMISSION)
        } catch (t: Throwable) {
            throw UpdateFailure(
                R.string.update_error_generic,
                args = listOf(t.javaClass.simpleName),
                kind = UpdateFailure.Kind.GENERIC
            )
        }

        var committed = false
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0L, apk.length()).use { out ->
                    apk.inputStream().use { input ->
                        input.copyTo(out, 64 * 1024)
                    }
                    session.fsync(out)
                }
                session.commit(resultPendingIntent().intentSender)
                committed = true
            }
        } catch (_: SecurityException) {
            throw UpdateFailure(R.string.update_state_needs_permission, kind = UpdateFailure.Kind.PERMISSION)
        } catch (t: Throwable) {
            throw UpdateFailure(
                R.string.update_error_generic,
                args = listOf(t.javaClass.simpleName),
                kind = UpdateFailure.Kind.GENERIC
            )
        } finally {
            // Mọi nhánh không commit đều phải abandon, nếu không staging rò dần cho tới khi đầy.
            if (!committed) {
                runCatching { installer.abandonSession(sessionId) }
            }
        }
    }

    /**
     * `FLAG_MUTABLE` BẮT BUỘC ở targetSdk 36: hệ thống ghi `EXTRA_STATUS` /
     * `EXTRA_STATUS_MESSAGE` vào base Intent; immutable thì kết quả không bao giờ tới.
     * An toàn vì base intent là explicit component + `setPackage(packageName)`.
     */
    private fun resultPendingIntent(): PendingIntent {
        val base = Intent(context, InstallResultReceiver::class.java)
            .setPackage(context.packageName)
            .setAction(InstallResultReceiver.ACTION_INSTALL_RESULT)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, base, flags)
    }

    companion object {
        private const val REQUEST_CODE = 8421
    }
}
