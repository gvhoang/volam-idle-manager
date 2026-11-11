package com.example.ui.components

import com.example.model.TabLimits
import com.example.model.ViewLayoutMode

/**
 * Quyết định bố cục cho N tab (docs/DESIGN-TAB-COUNT.md §4), tách khỏi code dựng View để
 * unit test kiểm được đúng thứ quan trọng nhất: **N ≤ 4 ở chế độ 4 Dọc PHẢI đi nhánh legacy**.
 *
 * Nếu N=4 rơi vào builder tổng quát, cây view vẫn "trông giống" nhưng không còn là nguyên văn
 * của bản đang chạy — và mọi khác biệt nhỏ (gap raw px, weightSum) sẽ đổi bề rộng ô CSS, đẩy
 * game qua nhánh `@media (max-width: 280px)`.
 */
internal object TabLayoutPlan {

    /**
     * Gap giữa các ô là **RAW PIXEL**, không phải dp. Đổi sang `3.dp` ở density 3 sẽ biến
     * 3px thành 9px, làm ô N=4 rộng 87dp thay vì 89.25dp.
     */
    const val GAP_PX = 3

    data class Plan(
        val cols: Int,
        val rows: Int,
        /** true ⇒ dùng nguyên văn nhánh 4-cột cũ (1 hàng, weightSum = N, leftMargin = GAP_PX). */
        val useLegacyRow: Boolean
    )

    fun planFor(mode: ViewLayoutMode, tabCount: Int): Plan {
        val n = tabCount.coerceIn(TabLimits.MIN, TabLimits.HARD_MAX)
        return when (mode) {
            ViewLayoutMode.FOUR_COLUMNS -> {
                val cols = minOf(n, 4)
                val rows = (n + cols - 1) / cols
                Plan(cols = cols, rows = rows, useLegacyRow = rows == 1)
            }

            ViewLayoutMode.TWO_BY_TWO -> {
                val cols = minOf(n, 2)
                val rows = (n + cols - 1) / cols
                Plan(cols = cols, rows = rows, useLegacyRow = false)
            }

            ViewLayoutMode.SINGLE_TAB -> Plan(cols = 1, rows = 1, useLegacyRow = false)
        }
    }
}
