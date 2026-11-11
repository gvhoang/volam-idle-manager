package com.example.model

/**
 * Trần và mặc định của số tab (docs/DESIGN-TAB-COUNT.md §1).
 *
 * `SLOT_COUNT` là KÍCH THƯỚC MẢNG, không phải số tab đang chạy. Mọi mảng nội bộ của
 * `GameTabManager` cấp phát đúng `SLOT_COUNT` phần tử một lần và KHÔNG bao giờ cấp lại:
 * `onRenderProcessGone`, các `Runnable` retry và coroutine watchdog giữ index đã chụp trước
 * khi người dùng đổi số tab, nên cấp lại mảng sẽ làm chúng trỏ sai hoặc ném.
 *
 * `DEFAULT = 4` giữ nguyên hành vi hiện tại ⇒ không cần migrate dữ liệu nào.
 */
object TabLimits {

    const val MIN = 1

    /** Hành vi hôm nay; cũng là giá trị đã đo trên thiết bị. */
    const val DEFAULT = 4

    /** Trần khuyến nghị: 4 renderer ≈ 1.2 GB. Vượt mức này mới cần cảnh báo + hạ FPS. */
    const val RECOMMENDED_MAX = 4

    /** Yêu cầu sản phẩm. */
    const val HARD_MAX = 8

    /** Kích thước cố định của mọi mảng theo index tab. */
    const val SLOT_COUNT = 8

    fun clamp(requested: Int): Int = requested.coerceIn(MIN, HARD_MAX)
}
