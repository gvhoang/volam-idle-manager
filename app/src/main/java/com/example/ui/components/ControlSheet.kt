package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NetworkDataInfo
import com.example.model.ViewLayoutMode

// =============================================================================
// BẢNG ĐIỀU KHIỂN NHANH (bottom sheet) — overlay, KHÔNG đẩy WebView xuống.
//
// Toàn bộ control từng nằm trong header 44dp + mini-header 28dp của từng tab đều chuyển
// vào đây, để chrome luôn hiện chỉ còn dải 32dp ở đáy. Sheet là overlay nên mở/đóng
// không đổi kích thước WebView ⇒ game không reflow HUD và không đóng `<select>` đang mở.
//
// Thứ tự hàng (đúng §3 của docs/DESIGN-LAYOUT.md):
//   1. per-tab   ▶ Mở / PC / ↻ / ⏹   (tab đích = controlSheetTabIndex)
//   2. view-mode 4 Dọc / 2x2 / 1 Tab
//   3. toggles   FPS 30↔60 / 🌙 Treo máy
//   4. network   MB + ↓speed / reset
//   5. sidebar   ☰ Bảng điều khiển
// =============================================================================

private val SheetBg = Color(0xFF0F172A)
private val SheetBorder = Color(0xFF1E293B)
private val ChipIdleFill = Color(0xFF1E293B)
private val ChipIdleBorder = Color(0xFF334155)
private val ChipIdleText = Color(0xFF94A3B8)
private val ChipSelectedFill = Color(0xFF1D4ED8)
private val ChipSelectedBorder = Color(0xFF38BDF8)
private val Grabber = Color(0xFF334155)
private val TextMuted = Color(0xFF94A3B8)
private val TextStrong = Color(0xFFE2E8F0)

@Composable
fun ControlSheet(
    isOpen: Boolean,
    tabIndex: Int,
    tabLabel: String,
    layoutMode: ViewLayoutMode,
    fps: Int,
    isDesktopMode: Boolean,
    networkData: NetworkDataInfo,
    onOpenTab: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onReloadTab: () -> Unit,
    onStopTab: () -> Unit,
    onChangeLayoutMode: (ViewLayoutMode) -> Unit,
    onToggleFps: () -> Unit,
    onActivateSleepMode: () -> Unit,
    onOpenSidebar: () -> Unit,
    onResetNetworkStats: () -> Unit,
    onDismiss: () -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Cao tối đa 0.46 màn hình dùng được; phần dư cuộn bên trong.
    val configuration = LocalConfiguration.current
    val maxSheetHeight = (configuration.screenHeightDp.dp * 0.46f)

    // Kéo grabber xuống > 40dp để đóng. Chỉ gắn ở vùng grabber/header: gắn lên cả panel sẽ
    // tranh gesture với verticalScroll và với các nút bên trong.
    val dismissThresholdPx = with(LocalDensity.current) { 40.dp.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val panelOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = if (isDragging) snap() else tween(140, easing = FastOutSlowInEasing),
        label = "control_sheet_drag"
    )

    LaunchedEffect(isOpen) {
        if (!isOpen) {
            dragOffset = 0f
            isDragging = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Scrim: chỉ tồn tại trong cây view khi sheet mở, nên khi đóng không ăn chạm của game.
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(interactionSource = null, indication = null) {
                        onInteract()
                        onDismiss()
                    }
                    .testTag("control_sheet_scrim")
            )
        }

        AnimatedVisibility(
            visible = isOpen,
            enter = slideInVertically(
                animationSpec = tween(180, easing = FastOutSlowInEasing),
                initialOffsetY = { it }
            ),
            exit = slideOutVertically(
                animationSpec = tween(140, easing = FastOutLinearInEasing),
                targetOffsetY = { it }
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = panelOffset }
                    .testTag("control_sheet"),
                color = SheetBg,
                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                border = BorderStroke(1.dp, SheetBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxSheetHeight)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ---- Grabber + header (36dp để nút ✕ đạt target tối thiểu) ----
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(isOpen) {
                                detectVerticalDragGestures(
                                    onDragStart = { isDragging = true },
                                    onDragEnd = {
                                        isDragging = false
                                        if (dragOffset > dismissThresholdPx) {
                                            dragOffset = 0f
                                            onInteract()
                                            onDismiss()
                                        } else {
                                            dragOffset = 0f
                                        }
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        dragOffset = 0f
                                    },
                                    onVerticalDrag = { _, dragAmount ->
                                        dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
                                    }
                                )
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Grabber)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⚔️ Điều khiển nhanh",
                                    color = TextStrong,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tab ${tabIndex + 1}: $tabLabel",
                                    color = TextMuted,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ChipIdleFill)
                                    .border(1.dp, ChipIdleBorder, RoundedCornerShape(6.dp))
                                    .clickable {
                                        onInteract()
                                        onDismiss()
                                    }
                                    .semantics { contentDescription = "Đóng bảng điều khiển" }
                                    .testTag("sheet_close_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // ---- Hàng 1: điều khiển per-tab (44dp) ----
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SheetButton(
                            label = "▶ Mở",
                            fill = Color(0xFF15803D),
                            contentColor = Color.White,
                            fontSize = 10.5.sp,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_open_tab"),
                            onClick = {
                                onInteract()
                                onOpenTab()
                            }
                        )
                        SheetButton(
                            label = "PC",
                            fill = if (isDesktopMode) Color(0xFF9333EA) else Color(0xFF4B5563),
                            contentColor = Color.White,
                            fontSize = 10.5.sp,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_pc_toggle"),
                            onClick = {
                                onInteract()
                                onToggleDesktopMode()
                            }
                        )
                        SheetIconButton(
                            icon = Icons.Default.Refresh,
                            contentDescription = "Tải lại tab",
                            fill = Color(0xFF3B82F6),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_reload_tab"),
                            onClick = {
                                onInteract()
                                onReloadTab()
                            }
                        )
                        SheetIconButton(
                            icon = Icons.Default.Stop,
                            contentDescription = "Dừng tab",
                            fill = Color(0xFFB91C1C),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_stop_tab"),
                            onClick = {
                                onInteract()
                                onStopTab()
                            }
                        )
                    }

                    // ---- Hàng 2: chế độ xem (40dp, segment 3 nút) ----
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SegmentButton(
                            label = "⊞ 4 Dọc",
                            selected = layoutMode == ViewLayoutMode.FOUR_COLUMNS,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_view_four"),
                            onClick = {
                                onInteract()
                                onChangeLayoutMode(ViewLayoutMode.FOUR_COLUMNS)
                            }
                        )
                        SegmentButton(
                            label = "⊞ 2x2",
                            selected = layoutMode == ViewLayoutMode.TWO_BY_TWO,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_view_grid"),
                            onClick = {
                                onInteract()
                                onChangeLayoutMode(ViewLayoutMode.TWO_BY_TWO)
                            }
                        )
                        SegmentButton(
                            label = "🔲 1 Tab",
                            selected = layoutMode == ViewLayoutMode.SINGLE_TAB,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_view_single"),
                            onClick = {
                                onInteract()
                                onChangeLayoutMode(ViewLayoutMode.SINGLE_TAB)
                            }
                        )
                    }

                    // ---- Hàng 3: FPS + Treo máy (40dp) ----
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SheetButton(
                            label = "⚡ ${fps}FPS",
                            fill = if (fps == 30) Color(0xFF78350F) else ChipIdleFill,
                            contentColor = if (fps == 30) Color(0xFFFDE68A) else TextStrong,
                            borderColor = if (fps == 30) Color(0xFFD97706) else Color(0xFF475569),
                            fontSize = 10.5.sp,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_fps_toggle"),
                            onClick = {
                                onInteract()
                                onToggleFps()
                            }
                        )
                        SheetButton(
                            label = "🌙 Treo máy",
                            fill = Color(0xFF312E81),
                            contentColor = Color(0xFFE0E7FF),
                            borderColor = Color(0xFF6366F1),
                            fontSize = 10.5.sp,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_sleep_button"),
                            onClick = {
                                onInteract()
                                onActivateSleepMode()
                            }
                        )
                    }

                    // ---- Hàng 4: lưu lượng mạng + reset (36dp) ----
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF022C22))
                                .border(1.dp, Color(0xFF065F46), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "📶 ${networkData.totalBytesFormatted} (↓${networkData.downloadSpeedFormatted})",
                                color = Color(0xFF34D399),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF022C22))
                                .border(1.dp, Color(0xFF065F46), RoundedCornerShape(6.dp))
                                .clickable {
                                    onInteract()
                                    onResetNetworkStats()
                                }
                                .padding(horizontal = 10.dp)
                                .semantics { contentDescription = "Reset mạng" }
                                .testTag("sheet_network_reset"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color(0xFF6EE7B7),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Reset",
                                    color = Color(0xFF34D399),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // ---- Hàng 5: mở bảng điều khiển (drawer) ----
                    SheetButton(
                        label = "☰ Bảng điều khiển",
                        fill = ChipIdleFill,
                        contentColor = TextStrong,
                        borderColor = ChipIdleBorder,
                        fontSize = 10.5.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("sheet_sidebar_button"),
                        onClick = {
                            onInteract()
                            onOpenSidebar()
                        }
                    )

                }
            }
        }
    }
}

/**
 * Nút trong sheet. Dựng bằng Box + clickable thay vì material `Button`:
 * `Button` ép minimumInteractiveComponentSize 48dp nên không giữ được hàng 36/40/44dp.
 */
@Composable
private fun SheetButton(
    label: String,
    fill: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    fontSize: TextUnit = 10.5.sp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .then(
                if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SheetIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    fill: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SegmentButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) ChipSelectedFill else ChipIdleFill)
            .border(1.dp, if (selected) ChipSelectedBorder else ChipIdleBorder, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else ChipIdleText,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
