package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CharacterStats
import com.example.model.TabLifecycleState
import com.example.webview.GameTabManager

// =============================================================================
// DẢI TRẠNG THÁI 32dp — sibling của AndroidView trong Column của GameWebViewContainer.
//
// Vì sao phải là sibling chứ không phải overlay Compose: WebView là View con thật, nó nhận
// dispatchTouchEvent trước lớp vẽ Compose, nên overlay không đảm bảo thắng touch. Là sibling
// nghĩa là dải không bao giờ đè lên WebView ⇒ không thể ăn mất một cú chạm của game.
//
// Nội dung: có mấy tab, đang xem tab nào. 1 chấm 7dp + 1 nhãn 9.5sp, + ô ☰ + ô ⋯.
//
// Với N > 4 KHÔNG thể nhồi chip 44dp vào 360dp (104 + 4N + 44N > 360 từ N = 6), nên hàng
// chip đổi sang LazyRow ghim ☰ và ⋯ ở hai đầu, chip chọn cố định 76dp. Chiều cao dải KHÔNG
// đổi (32dp + inset) — đó là bất biến, không phải chi tiết.
// =============================================================================

private val StripBg = Color(0xFF0F172A)
private val StripBorder = Color(0xFF1E293B)
private val CellIdleFill = Color(0xFF1E293B)
private val CellIdleBorder = Color(0xFF334155)
private val CellIdleText = Color(0xFF94A3B8)
private val CellSelectedFill = Color(0xFF1D4ED8)
private val CellSelectedBorder = Color(0xFF38BDF8)
private val IconTint = Color(0xFF38BDF8)
private val IconTintActive = Color(0xFF34D399)

@Composable
fun GameStatusStrip(
    tabManager: GameTabManager,
    tabCount: Int,
    characterStatsList: List<CharacterStats>,
    selectedTabIndex: Int,
    isDownloading: Boolean,
    onSelectTab: (Int) -> Unit,
    onOpenSidebar: () -> Unit,
    onOpenControlSheet: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // §6: cao 32dp, giãn theo font scale để nhãn không bị cắt — nhưng chặn trần.
    // 32dp khi fontScale 1.0, 38dp ở 1.5, 44dp ở 2.0. Trần 2f vì trên mức đó dải sẽ ăn
    // vào viewport game, đúng thứ đang muốn tối đa.
    val rawFontScale = density.fontScale
    val heightScale = rawFontScale.coerceIn(1f, 2f)
    val stripHeight = 32.dp + ((heightScale - 1f) * 12f).dp

    // §9: nhãn trong dải là HUD trạng thái, không phải bề mặt đọc → clamp font scale 1.3f.
    // Sheet thì KHÔNG clamp vì nó cuộn được.
    val labelDensity = Density(
        density = density.density,
        fontScale = rawFontScale.coerceAtMost(1.3f)
    )

    val safeCount = tabCount.coerceAtLeast(0)
    val safeSelected = if (safeCount == 0) 0 else selectedTabIndex.coerceIn(0, safeCount - 1)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .testTag("game_status_strip"),
        color = StripBg,
        border = BorderStroke(1.dp, StripBorder)
    ) {
        CompositionLocalProvider(LocalDensity provides labelDensity) {
            if (safeCount <= 4) {
                // ===== NHÁNH LEGACY — NGUYÊN VĂN (N ≤ 4) =====
                // chip đang chọn co giãn (weight 1f), các chip còn lại cố định 44dp.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stripHeight)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MenuCell(
                        tabManager = tabManager,
                        onOpenSidebar = onOpenSidebar,
                        modifier = Modifier
                            .width(44.dp)
                            .fillMaxHeight()
                    )

                    for (i in 0 until safeCount) {
                        val stats = characterStatsList.getOrElse(i) { CharacterStats(tabIndex = i) }
                        val isSelected = safeSelected == i
                        val chipModifier = if (isSelected) {
                            Modifier
                                .weight(1f)
                                .widthIn(min = 44.dp)
                                .fillMaxHeight()
                        } else {
                            Modifier
                                .width(44.dp)
                                .fillMaxHeight()
                        }

                        TabChip(
                            tabIndex = i,
                            stats = stats,
                            isSelected = isSelected,
                            tabManager = tabManager,
                            onSelectTab = onSelectTab,
                            onOpenControlSheet = onOpenControlSheet,
                            modifier = chipModifier
                        )
                    }

                    OverflowCell(
                        tabManager = tabManager,
                        selectedTabIndex = safeSelected,
                        isDownloading = isDownloading,
                        onOpenControlSheet = onOpenControlSheet,
                        modifier = Modifier
                            .width(44.dp)
                            .fillMaxHeight()
                    )
                }
            } else {
                val listState = rememberLazyListState()
                // Chọn tab ngoài vùng nhìn thấy phải tự cuộn tới. animateScrollToItem giữ
                // chiều cao dải (LazyRow không có chiều cao nội tại).
                LaunchedEffect(safeSelected, safeCount) {
                    listState.animateScrollToItem(safeSelected)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stripHeight)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MenuCell(
                        tabManager = tabManager,
                        onOpenSidebar = onOpenSidebar,
                        modifier = Modifier
                            .width(44.dp)
                            .fillMaxHeight()
                    )

                    LazyRow(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        state = listState,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(count = safeCount) { i ->
                            val stats = characterStatsList.getOrElse(i) {
                                CharacterStats(tabIndex = i)
                            }
                            val isSelected = safeSelected == i
                            // Target ≥ 44dp (accessibility); chip chọn rộng 76dp để chứa
                            // "Tab N: Tên" mà không phải dùng biến thể chỉ-chấm 18dp.
                            TabChip(
                                tabIndex = i,
                                stats = stats,
                                isSelected = isSelected,
                                tabManager = tabManager,
                                onSelectTab = onSelectTab,
                                onOpenControlSheet = onOpenControlSheet,
                                modifier = Modifier
                                    .width(if (isSelected) 76.dp else 44.dp)
                                    .fillMaxHeight()
                            )
                        }
                    }

                    OverflowCell(
                        tabManager = tabManager,
                        selectedTabIndex = safeSelected,
                        isDownloading = isDownloading,
                        onOpenControlSheet = onOpenControlSheet,
                        modifier = Modifier
                            .width(44.dp)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuCell(
    tabManager: GameTabManager,
    onOpenSidebar: () -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .clickable(
                role = Role.Button,
                onClick = {
                    tabManager.markUiInteraction()
                    onOpenSidebar()
                }
            )
            .semantics { contentDescription = "Mở bảng điều khiển" }
            .testTag("strip_sidebar_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = null,
            tint = IconTint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TabChip(
    tabIndex: Int,
    stats: CharacterStats,
    isSelected: Boolean,
    tabManager: GameTabManager,
    onSelectTab: (Int) -> Unit,
    onOpenControlSheet: (Int) -> Unit,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = modifier
            .background(
                color = if (isSelected) CellSelectedFill else CellIdleFill,
                shape = shape
            )
            .border(
                width = 1.dp,
                color = if (isSelected) CellSelectedBorder else CellIdleBorder,
                shape = shape
            )
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                onClick = {
                    tabManager.markUiInteraction()
                    // Tap chip đang chọn = mở bảng điều khiển cho tab đó
                    // (§4 vòng đời sheet); tap chip khác = chuyển tab.
                    if (isSelected) onOpenControlSheet(tabIndex) else onSelectTab(tabIndex)
                }
            )
            .semantics {
                contentDescription = "Tab ${tabIndex + 1}"
                stateDescription = buildString {
                    append(
                        when (stats.state) {
                            TabLifecycleState.IN_GAME -> "đang chơi"
                            TabLifecycleState.LOGGING_IN -> "đang vào game"
                            TabLifecycleState.IDLE -> "chờ mở"
                            TabLifecycleState.OFFLINE -> "đã dừng"
                        }
                    )
                    if (stats.characterName.isNotBlank() &&
                        stats.characterName != "Chưa mở tab"
                    ) {
                        append(", ")
                        append(stats.characterName)
                    }
                }
            }
            .testTag("strip_tab_chip_$tabIndex"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 3.dp)
        ) {
            StatusDot(state = stats.state)
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = if (isSelected) {
                    "Tab ${tabIndex + 1}" + if (stats.characterName.isNotBlank() &&
                        stats.characterName != "Chưa mở tab"
                    ) {
                        ": ${stats.characterName}"
                    } else {
                        ""
                    }
                } else {
                    "T${tabIndex + 1}"
                },
                color = if (isSelected) Color.White else CellIdleText,
                fontSize = 9.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OverflowCell(
    tabManager: GameTabManager,
    selectedTabIndex: Int,
    isDownloading: Boolean,
    onOpenControlSheet: (Int) -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .clickable(
                role = Role.Button,
                onClick = {
                    tabManager.markUiInteraction()
                    onOpenControlSheet(selectedTabIndex)
                }
            )
            .semantics { contentDescription = "Mở bảng điều khiển nhanh" }
            .testTag("strip_overflow_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MoreHoriz,
            contentDescription = null,
            tint = if (isDownloading) IconTintActive else IconTint,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Chấm trạng thái 7dp. Trạng thái không chỉ phân biệt bằng màu (§9):
 *   IN_GAME   = chấm đặc
 *   LOGGING_IN= vòng rỗng, stroke 2dp
 *   IDLE      = chấm đặc alpha 55%
 *   OFFLINE   = chấm đặc có khấc 1dp
 */
@Composable
private fun StatusDot(state: TabLifecycleState) {
    Canvas(modifier = Modifier.size(7.dp)) {
        val radius = size.minDimension / 2f
        when (state) {
            TabLifecycleState.IN_GAME -> {
                drawCircle(color = Color(0xFF10B981), radius = radius)
            }

            TabLifecycleState.LOGGING_IN -> {
                val stroke = 2.dp.toPx()
                drawCircle(
                    color = Color(0xFFF59E0B),
                    radius = (radius - stroke / 2f).coerceAtLeast(0.5f),
                    style = Stroke(width = stroke)
                )
            }

            TabLifecycleState.IDLE -> {
                drawCircle(color = Color(0xFF64748B).copy(alpha = 0.55f), radius = radius)
            }

            TabLifecycleState.OFFLINE -> {
                drawCircle(color = Color(0xFFEF4444), radius = radius)
                // Khấc 1dp màu nền dải, cắt chéo qua chấm.
                drawLine(
                    color = StripBg,
                    start = Offset(size.width * 0.18f, size.height * 0.82f),
                    end = Offset(size.width * 0.82f, size.height * 0.18f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Butt
                )
            }
        }
    }
}

/**
 * Có lưu lượng tải về hay không, đọc từ chuỗi đã format của TrafficMonitor
 * ("0 B/s" | "512 KB/s" | "1.2 MB/s"). Dùng để đổi màu ô ⋯ mà không phải giữ
 * thêm số liệu thô trong NetworkDataInfo.
 */
internal fun hasDownloadTraffic(downloadSpeedFormatted: String): Boolean =
    (downloadSpeedFormatted.substringBefore(' ').toFloatOrNull() ?: 0f) > 0f
