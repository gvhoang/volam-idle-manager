package com.example.update

import com.example.R

/**
 * Bản phát hành đọc từ `GET /repos/{owner}/{repo}/releases/latest`.
 *
 * `versionCode` là giá trị dùng để QUYẾT ĐỊNH (so với `BuildConfig.VERSION_CODE`); nó đến từ
 * body của release (khai báo tường minh) hoặc suy từ tag — xem [VersionRules]. Sau khi tải,
 * `versionCode` đọc từ chính tệp APK phải lớn hơn lần nữa (ApkInspector) thì mới được cài.
 */
data class UpdateRelease(
    val versionCode: Int,
    val versionName: String,
    val tagName: String,
    val notes: String,
    val htmlUrl: String,
    val assetName: String,
    val assetUrl: String,
    val assetSize: Long,
    val sha256: String?
) {
    companion object
}

/**
 * Trạng thái UI của tính năng cập nhật (docs/DESIGN-AUTOUPDATE.md §6.1).
 *
 * Bất biến quan trọng: KHÔNG trạng thái nào tự tải và không trạng thái nào tự cài.
 * `Downloading`/`Verifying` chỉ xuất hiện sau một cú chạm "Tải về"; `ReadyToInstall` chỉ
 * xuất hiện sau khi tệp đã verify, và `Installing` chỉ sau cú chạm "Cài đặt" thứ hai.
 */
sealed interface UpdateUiState {

    /** Chưa kiểm tra trong phiên này (hoặc vừa xong một lần cài bị huỷ). */
    data object Idle : UpdateUiState

    /** Đang gọi API (chỉ hiện khi người dùng bấm tay; kiểm tra nền im lặng). */
    data object Checking : UpdateUiState

    /** Bản đang cài đã là mới nhất. */
    data class UpToDate(val versionName: String) : UpdateUiState

    /** Có bản mới. Đây là trạng thái duy nhất dựng banner nhắc. */
    data class Available(
        val versionName: String,
        val sizeBytes: Long,
        val notes: String
    ) : UpdateUiState

    /** Đang stream APK về `cacheDir/updates/update.apk.part`. */
    data class Downloading(val bytes: Long, val total: Long) : UpdateUiState

    /** Đã tải xong, đang tính/so SHA-256 và đọc archive. */
    data object Verifying : UpdateUiState

    /**
     * Tệp đã verify, chờ cú chạm "Cài đặt" của người dùng.
     *
     * `sizeBytes` mang theo để hộp thoại xác nhận (`update_confirm_body`) hiện đúng dung lượng
     * mà không phải tra lại release đã lưu.
     */
    data class ReadyToInstall(val versionName: String, val sizeBytes: Long = 0L) : UpdateUiState

    /** Thiếu AppOp "Cài đặt ứng dụng không rõ nguồn gốc". */
    data object NeedsPermission : UpdateUiState

    /** Đã commit session, đang chờ hệ thống (và người dùng) xác nhận. */
    data object Installing : UpdateUiState

    /** Lỗi có thông điệp tiếng Việt đã resolve sẵn để hiện thẳng. */
    data class Failed(val reason: String) : UpdateUiState

    /** Không có mạng / captive portal. */
    data object NoNetwork : UpdateUiState

    /** 403/429: không retry tới `retryAtMillis`. */
    data class RateLimited(val retryAtMillis: Long) : UpdateUiState

    /**
     * Tag không đọc được và body không có khai báo `versionCode` (§3.3).
     *
     * Không nằm trong danh sách §6.1 nhưng §3.3 buộc phải có nhánh này: hiện thông báo
     * và nút mở `html_url`, tuyệt đối không tự tải.
     */
    data class Unparsable(val releasesUrl: String) : UpdateUiState
}

/**
 * Lỗi của toàn bộ luồng cập nhật. Mang `messageRes` (khoá chuỗi tiếng Việt trong
 * `res/values/strings.xml`) thay vì chuỗi đã format, để tầng logic không cần Context và
 * unit test thuần JVM vẫn chạy được.
 */
class UpdateFailure(
    val messageRes: Int,
    val args: List<Any> = emptyList(),
    val kind: Kind = Kind.GENERIC,
    val retryAtMillis: Long = 0L
) : Exception("update-failure:$kind") {

    enum class Kind {
        /** IOException / DNS / timeout — retry được (2s → 8s → 30s, tối đa 3). */
        NETWORK,

        /** 403/429 — KHÔNG retry, chờ `retryAtMillis`. */
        RATE_LIMITED,

        /** 5xx — retry được. */
        SERVER,

        /** 4xx khác (404 = chưa có release / repo đổi tên). */
        HTTP,

        /** Redirect ra ngoài allowlist, scheme không phải https, quá 3 hop. */
        BAD_HOST,

        /** Release không kèm APK (hoặc kèm nhiều APK mà không có tên cố định). */
        NO_ASSET,

        /** SHA-256 lệch. */
        SHA,

        /** Không đủ dung lượng (StatFs / trần 200 MB). */
        STORAGE,

        /** `getPackageArchiveInfo` null / packageName lạ. */
        INVALID_APK,

        /** Chữ ký khác bản đang cài — cần gỡ + cài lại. */
        SIGNATURE,

        /** versionCode không lớn hơn bản đang chạy. */
        DOWNGRADE,

        /** Thiếu quyền cài / AppOp bị từ chối. */
        PERMISSION,

        GENERIC
    }
}
