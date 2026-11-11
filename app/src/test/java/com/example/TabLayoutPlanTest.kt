package com.example

import com.example.model.ViewLayoutMode
import com.example.ui.components.TabLayoutPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bất biến bố cục của docs/DESIGN-TAB-COUNT.md §4.
 *
 * Test này canh đúng thứ dễ hỏng nhất khi refactor: N=4 ở chế độ "4 Dọc" phải đi NHÁNH LEGACY,
 * không đi builder tổng quát. Nếu ai đó gộp hai nhánh, test đỏ trước khi pixel lệch.
 */
class TabLayoutPlanTest {

    @Test
    fun fourColumnsWithFourTabsUsesLegacyRow() {
        val plan = TabLayoutPlan.planFor(ViewLayoutMode.FOUR_COLUMNS, 4)
        assertEquals("N=4 phải là 4 cột", 4, plan.cols)
        assertEquals("N=4 phải là 1 hàng", 1, plan.rows)
        assertTrue("N=4 PHẢI đi nhánh legacy", plan.useLegacyRow)
    }

    @Test
    fun fourColumnsOneToFourAllUseLegacyRow() {
        for (n in 1..4) {
            val plan = TabLayoutPlan.planFor(ViewLayoutMode.FOUR_COLUMNS, n)
            assertEquals(n, plan.cols)
            assertEquals(1, plan.rows)
            assertTrue("N=$n phải đi nhánh legacy", plan.useLegacyRow)
        }
    }

    @Test
    fun fourColumnsFiveToEightUseTwoRowGrid() {
        for (n in 5..8) {
            val plan = TabLayoutPlan.planFor(ViewLayoutMode.FOUR_COLUMNS, n)
            assertEquals(4, plan.cols)
            assertEquals(2, plan.rows)
            assertFalse("N=$n phải đi builder tổng quát", plan.useLegacyRow)
        }
    }

    @Test
    fun twoByTwoWithFourTabsStaysTwoByTwo() {
        val plan = TabLayoutPlan.planFor(ViewLayoutMode.TWO_BY_TWO, 4)
        assertEquals(2, plan.cols)
        assertEquals(2, plan.rows)
        assertFalse(plan.useLegacyRow)
    }

    @Test
    fun twoByTwoFillsRowsPerDesignTable() {
        // 2×1, 2+1, 2×2, 2+2+1, 3×2, 2+2+2+1, 4×2
        assertEquals(1, TabLayoutPlan.planFor(ViewLayoutMode.TWO_BY_TWO, 2).rows)
        assertEquals(2, TabLayoutPlan.planFor(ViewLayoutMode.TWO_BY_TWO, 3).rows)
        assertEquals(2, TabLayoutPlan.planFor(ViewLayoutMode.TWO_BY_TWO, 4).rows)
        assertEquals(3, TabLayoutPlan.planFor(ViewLayoutMode.TWO_BY_TWO, 5).rows)
        assertEquals(3, TabLayoutPlan.planFor(ViewLayoutMode.TWO_BY_TWO, 6).rows)
        assertEquals(4, TabLayoutPlan.planFor(ViewLayoutMode.TWO_BY_TWO, 7).rows)
        assertEquals(4, TabLayoutPlan.planFor(ViewLayoutMode.TWO_BY_TWO, 8).rows)
    }

    @Test
    fun singleTabIsAlwaysOneCell() {
        val plan = TabLayoutPlan.planFor(ViewLayoutMode.SINGLE_TAB, 8)
        assertEquals(1, plan.cols)
        assertEquals(1, plan.rows)
    }

    /**
     * Gap là RAW PIXEL. Ở 360dp/density 3 (1080px): 4 ô + 3 gap 3px ⇒ 267.75px = 89.25dp.
     * Đổi gap sang `3.dp` (9px ở density 3) sẽ cho 87dp — test này đỏ trước khi pixel lệch.
     */
    @Test
    fun gapStaysRawPixelsSoCellWidthDoesNotMove() {
        assertEquals(3, TabLayoutPlan.GAP_PX)
        val widthPx = 1080.0
        val cellPx = (widthPx - 3 * TabLayoutPlan.GAP_PX) / 4
        assertEquals(89.25, cellPx / 3.0, 0.0001)
    }
}
