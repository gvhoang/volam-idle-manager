package com.example.update

/**
 * Luật phiên bản của kênh cập nhật (docs/DESIGN-AUTOUPDATE.md §3.3).
 *
 * Toàn bộ hàm ở đây là pure Kotlin (không Android API) để unit test JVM thuần kiểm được:
 * allowlist/redirect và so version là hai kiểm soát thật của tính năng này.
 */
internal object VersionRules {

    /**
     * Body release phải khai báo `versionCode` tường minh. Nhờ vậy đổi scheme tag
     * (`v4.0.0` → `4.0.0` → `release-2026-01`) không phá gì.
     */
    private val BODY_HINT = Regex(
        "version(?:\\s|-)?code[^0-9]{0,16}(\\d+)",
        setOf(RegexOption.IGNORE_CASE)
    )

    private val TAG_TRIPLE = Regex(
        "^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[-+].*)?$",
        setOf(RegexOption.IGNORE_CASE)
    )

    /** `major*10000 + minor*100 + patch`, thiếu patch thì coi là 0. */
    fun versionCodeFromTag(tag: String): Int? {
        val t = tag.trim()
        val m = TAG_TRIPLE.matchEntire(t) ?: return null
        val major = m.groupValues[1].toIntOrNull() ?: return null
        val minor = m.groupValues[2].toIntOrNull() ?: return null
        val patch = m.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        if (major < 0 || minor < 0 || patch < 0) return null
        if (minor > 99 || patch > 99) return null
        return major * 10_000 + minor * 100 + patch
    }

    fun versionCodeFromBody(body: String?): Int? {
        if (body.isNullOrBlank()) return null
        val m = BODY_HINT.find(body) ?: return null
        val code = m.groupValues[1].toIntOrNull() ?: return null
        return if (code > 0) code else null
    }

    /** Tên hiển thị: bỏ tiền tố `v` của tag (`v4.0.0` → `4.0.0`). */
    fun versionNameFromTag(tag: String): String =
        tag.trim().removePrefix("v").removePrefix("V").ifBlank { tag.trim() }

    /**
     * Chọn `versionCode` để so với bản đang chạy.
     *
     * `codeHint` (body) được ưu tiên khi có: đó là con số do người phát hành khai báo và
     * là con số duy nhất đúng trong giai đoạn `versionCode` chưa theo công thức
     * (`4` với tag `v4.0.0`, trong khi triple của tag là `40000`). Tag chỉ là fallback khi
     * body không khai báo — đúng tinh thần "codeHint nếu có; candidate nếu body không có hint".
     *
     * Trả `null` ⇒ `UNPARSED`: UI hiện thông báo và nút mở trang phát hành, KHÔNG tự tải.
     */
    fun resolveVersionCode(tag: String, body: String?): Int? =
        versionCodeFromBody(body) ?: versionCodeFromTag(tag)

    /** So tuple số học, không so chuỗi: `4.10.0 > 4.9.0`, `10.0.0 > 9.99.99`. */
    fun isNewer(remote: Int, local: Int): Boolean = remote > local
}
