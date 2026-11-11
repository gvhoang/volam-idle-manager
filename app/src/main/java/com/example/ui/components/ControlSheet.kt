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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.NetworkDataInfo
import com.example.model.TabLimits
import com.example.model.ViewLayoutMode
import com.example.update.ByteSize
import com.example.update.UpdateUiState
import java.util.Locale

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
//   3. SỐ TAB    − n +  (+ cảnh báo khi > 4, hai xác nhận)
//   4. toggles   FPS 30↔60 / 🌙 Treo máy
//   5. network   MB + ↓speed / reset
//   6. sync-auto 📜 Nhiệm Vụ / ⚔️ Phó Bản / 🔥 Luyện Công  (chuyển từ drawer sang)
//   7. app-wide  🤖 Tự động  (mở AutoConfigDialog — chuyển từ drawer sang)
//   8. sidebar   ☰ Bảng điều khiển
//   9. version   📦 Phiên bản + action cập nhật (cấp app)
//
// Hàng 6–7 trước đây nằm trong SidebarView: người dùng phải mở drawer mới bấm được.
// Chúng là control cấp app (không phải per-tab) nên thuộc sheet này. Sheet vẫn cuộn
// trong trần 0.46 màn hình — thêm hàng KHÔNG được nới trần đó.
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
    onSyncAutoMode: (String) -> Unit,
    isAutoPilotOn: Boolean,
    onOpenAutoConfig: () -> Unit,
    tabCount: Int,
    maxTabCount: Int,
    isLowRam: Boolean,
    tabCountWarnShown: Boolean,
    hasBusyTabsInRange: (Int, Int) -> Boolean,
    onSetTabCount: (Int) -> Unit,
    onMarkTabCountWarned: () -> Unit,
    appVersionName: String,
    updateState: UpdateUiState,
    onCheckUpdate: () -> Unit,
    onStartDownload: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenReleases: () -> Unit,
    onDismiss: () -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Cao tối đa 0.46 màn hình dùng được; phần dư cuộn bên trong.
    val configuration = LocalConfiguration.current
    val maxSheetHeight = (configuration.screenHeightDp.dp * 0.46f)

    // Sync-auto là 3 lệnh rời (không phải một trạng thái lưu xuống đĩa), nhưng người dùng
    // cần thấy mình vừa bấm cái nào — giữ lựa chọn cuối trong bộ nhớ phiên của sheet.
    var lastSyncMode by remember { mutableStateOf<String?>(null) }

    // Xác nhận cài: modal DUY NHẤT của tính năng update, và chỉ xuất hiện sau một cú chạm.
    var installConfirm by remember { mutableStateOf<UpdateUiState.ReadyToInstall?>(null) }
    LaunchedEffect(updateState) {
        if (updateState !is UpdateUiState.ReadyToInstall) installConfirm = null
    }

    // Hai xác nhận của stepper số tab. Chỉ dựng dialog khi thật sự cần:
    //  - tăng vượt 4 lần đầu (cờ pref_tab_warn_shown),
    //  - giảm mà có tab trong [n, old) đang IN_GAME/LOGGING_IN.
    var pendingGrow by remember { mutableStateOf<Int?>(null) }
    var pendingShrink by remember { mutableStateOf<Int?>(null) }

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

                    // ---- Hàng 3: SỐ TAB 1..8 (40dp) ----
                    TabCountStepper(
                        tabCount = tabCount,
                        maxTabCount = maxTabCount,
                        isLowRam = isLowRam,
                        onRequestIncrease = {
                            onInteract()
                            val next = tabCount + 1
                            if (next <= maxTabCount) {
                                if (next > TabLimits.RECOMMENDED_MAX && !tabCountWarnShown) {
                                    pendingGrow = next
                                } else {
                                    onSetTabCount(next)
                                }
                            }
                        },
                        onRequestDecrease = {
                            onInteract()
                            val next = tabCount - 1
                            if (next >= TabLimits.MIN) {
                                if (hasBusyTabsInRange(next, tabCount)) {
                                    pendingShrink = next
                                } else {
                                    onSetTabCount(next)
                                }
                            }
                        }
                    )

                    // ---- Hàng 4: FPS + Treo máy (40dp) ----
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

                    // ---- Hàng 5: lưu lượng mạng + reset (36dp) ----
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

                    // ---- Hàng 6: đồng bộ chế độ Auto cho mọi tab (chuyển từ drawer) ----
                    // Ba lệnh rời, không có trạng thái lưu: chọn cái vừa bấm để có phản hồi thị giác.
                    Text(
                        text = "ĐỒNG BỘ AUTO CẢ $tabCount TAB",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .testTag("sheet_sync_mode_row"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SegmentButton(
                            label = "📜 Nhiệm Vụ",
                            selected = lastSyncMode == "Nhiệm Vụ",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_sync_quest"),
                            onClick = {
                                onInteract()
                                lastSyncMode = "Nhiệm Vụ"
                                onSyncAutoMode("Nhiệm Vụ")
                            }
                        )
                        SegmentButton(
                            label = "⚔️ Phó Bản",
                            selected = lastSyncMode == "Phó Bản",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_sync_dungeon"),
                            onClick = {
                                onInteract()
                                lastSyncMode = "Phó Bản"
                                onSyncAutoMode("Phó Bản")
                            }
                        )
                        SegmentButton(
                            label = "🔥 Luyện Công",
                            selected = lastSyncMode == "Luyện Công",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_sync_grind"),
                            onClick = {
                                onInteract()
                                lastSyncMode = "Luyện Công"
                                onSyncAutoMode("Luyện Công")
                            }
                        )
                    }

                    // ---- Hàng 7: 🤖 Tự động (mở AutoConfigDialog) ----
                    // Màu giữ nguyên như nút trong drawer cũ: hổ phách khi tắt, xanh + viền khi bật.
                    SheetButton(
                        label = if (isAutoPilotOn) {
                            "🤖 Tự động · 🟢 ĐANG BẬT"
                        } else {
                            "🤖 Tự động · ⚪ Đang tắt"
                        },
                        fill = if (isAutoPilotOn) Color(0xFF064E3B) else Color(0xFF78350F),
                        contentColor = Color.White,
                        borderColor = if (isAutoPilotOn) Color(0xFF10B981) else null,
                        fontSize = 10.5.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("sheet_auto_config"),
                        onClick = {
                            onInteract()
                            onOpenAutoConfig()
                        }
                    )

                    // ---- Hàng 8: mở bảng điều khiển (drawer) ----
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

                    // ---- Hàng 9: phiên bản + cập nhật (cấp app, không phải per-tab) ----
                    UpdateRow(
                        appVersionName = appVersionName,
                        state = updateState,
                        onCheck = {
                            onInteract()
                            onCheckUpdate()
                        },
                        onDownload = {
                            onInteract()
                            onStartDownload()
                        },
                        onInstall = {
                            val ready = updateState as? UpdateUiState.ReadyToInstall
                            if (ready != null) installConfirm = ready
                        },
                        onOpenReleases = {
                            onInteract()
                            onOpenReleases()
                        }
                    )

                }
            }
        }
    }

    // Hộp thoại xác nhận cài: nói rõ app sẽ đóng và các tab sẽ tải lại. Chỉ có ở đây,
    // không có confirm nào khác trong luồng update (§6.3 "modal duy nhất là xác nhận cài").
    installConfirm?.let { ready ->
        AlertDialog(
            onDismissRequest = { installConfirm = null },
            title = {
                Text(text = stringResource(R.string.update_confirm_title, ready.versionName))
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.update_confirm_body,
                        ByteSize.format(ready.sizeBytes)
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        installConfirm = null
                        onInteract()
                        onInstallUpdate()
                    },
                    modifier = Modifier.testTag("update_confirm_ok")
                ) {
                    Text(text = stringResource(R.string.update_confirm_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { installConfirm = null }) {
                    Text(text = stringResource(R.string.update_confirm_cancel))
                }
            }
        )
    }

    // Xác nhận lần đầu vượt 4 tab (§6.3). Cờ pref_tab_warn_shown do ViewModel giữ.
    pendingGrow?.let { next ->
        AlertDialog(
            onDismissRequest = { pendingGrow = null },
            text = {
                Text(
                    text = stringResource(
                        R.string.tab_count_confirm_grow,
                        next,
                        String.format(Locale.US, "%.1f", next * 0.3),
                        next * 50
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingGrow = null
                        onInteract()
                        onMarkTabCountWarned()
                        onSetTabCount(next)
                    },
                    modifier = Modifier.testTag("tab_count_grow_ok")
                ) {
                    Text(text = stringResource(R.string.tab_count_confirm_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingGrow = null }) {
                    Text(text = stringResource(R.string.tab_count_confirm_cancel))
                }
            }
        )
    }

    // Xác nhận giảm khi có tab trong [n, old) đang chạy: giảm sẽ DỪNG chúng.
    pendingShrink?.let { next ->
        AlertDialog(
            onDismissRequest = { pendingShrink = null },
            text = {
                Text(
                    text = stringResource(
                        R.string.tab_count_confirm_shrink,
                        next,
                        next + 1,
                        tabCount
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingShrink = null
                        onInteract()
                        onSetTabCount(next)
                    },
                    modifier = Modifier.testTag("tab_count_shrink_ok")
                ) {
                    Text(text = stringResource(R.string.tab_count_confirm_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingShrink = null }) {
                    Text(text = stringResource(R.string.tab_count_confirm_cancel))
                }
            }
        )
    }
}

// =============================================================================
// STEPPER SỐ TAB (docs/DESIGN-TAB-COUNT.md §6)
// "−" disable ở 1, "+" disable ở trần thực tế (8, hoặc 4 khi máy RAM thấp).
// Target cao 40dp như các hàng khác; hai nút rộng 44dp để đủ vùng chạm.
// =============================================================================

@Composable
private fun TabCountStepper(
    tabCount: Int,
    maxTabCount: Int,
    isLowRam: Boolean,
    onRequestIncrease: () -> Unit,
    onRequestDecrease: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .testTag("sheet_tab_count_row"),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.tab_count_label),
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            StepButton(
                label = "−",
                enabled = tabCount > TabLimits.MIN,
                contentDescription = stringResource(R.string.cd_tab_count_decrease),
                testTag = "sheet_tab_count_minus",
                onClick = onRequestDecrease
            )

            Text(
                text = tabCount.toString(),
                color = TextStrong,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(26.dp)
                    .testTag("sheet_tab_count_value")
            )

            StepButton(
                label = "+",
                enabled = tabCount < maxTabCount,
                contentDescription = stringResource(R.string.cd_tab_count_increase),
                testTag = "sheet_tab_count_plus",
                onClick = onRequestIncrease
            )
        }

        // Cảnh báo inline: chỉ khi thực sự trên 4 tab.
        if (tabCount > TabLimits.RECOMMENDED_MAX) {
            Text(
                text = stringResource(R.string.tab_count_warn_inline),
                color = Color(0xFFFCD34D),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("sheet_tab_count_warning")
            )
        }

        // Bị chặn ở trần low-RAM: nói rõ vì sao "+" không bấm được.
        if (isLowRam && tabCount >= maxTabCount) {
            Text(
                text = stringResource(R.string.tab_count_low_ram_block),
                color = Color(0xFFFCA5A5),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("sheet_tab_count_low_ram")
            )
        }
    }
}

@Composable
private fun StepButton(
    label: String,
    enabled: Boolean,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(40.dp)
            .clip(shape)
            .background(if (enabled) ChipIdleFill else Color(0xFF111827))
            .border(1.dp, if (enabled) ChipIdleBorder else Color(0xFF1F2937), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) TextStrong else Color(0xFF475569),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
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

// =============================================================================
// Hàng cập nhật (§6.1): cao 40dp, trái là phiên bản đang chạy, phải là MỘT target
// 40dp mang nhãn/action theo state. Dựng bằng Box + clickable (không dùng material
// `Button` — nó ép minimumInteractiveComponentSize 48dp và làm hàng cao lên).
//
// Không state nào tự chuyển sang state kế: `Available` chỉ hiện "Tải về", `ReadyToInstall`
// chỉ hiện "Cài đặt". Việc đi tiếp luôn cần một cú chạm mới.
// =============================================================================

private data class UpdateRowSpec(
    val label: String,
    val onClick: (() -> Unit)?,
    val fill: Color,
    val border: Color,
    val content: Color,
    val contentDescription: String?,
    val detail: String?
)

@Composable
private fun UpdateRow(
    appVersionName: String,
    state: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenReleases: () -> Unit
) {
    val spec = when (state) {
        UpdateUiState.Idle -> UpdateRowSpec(
            label = stringResource(R.string.update_btn_check),
            onClick = onCheck,
            fill = ChipSelectedFill,
            border = ChipSelectedBorder,
            content = Color.White,
            contentDescription = stringResource(R.string.cd_update_check),
            detail = null
        )

        UpdateUiState.Checking -> UpdateRowSpec(
            label = stringResource(R.string.update_state_checking),
            onClick = null,
            fill = ChipIdleFill,
            border = ChipIdleBorder,
            content = ChipIdleText,
            contentDescription = null,
            detail = null
        )

        is UpdateUiState.UpToDate -> UpdateRowSpec(
            label = stringResource(R.string.update_btn_check),
            onClick = onCheck,
            fill = ChipIdleFill,
            border = ChipIdleBorder,
            content = TextStrong,
            contentDescription = stringResource(R.string.cd_update_check),
            detail = stringResource(R.string.update_state_uptodate)
        )

        is UpdateUiState.Available -> UpdateRowSpec(
            label = stringResource(R.string.update_btn_download),
            onClick = onDownload,
            fill = ChipSelectedFill,
            border = ChipSelectedBorder,
            content = Color.White,
            contentDescription = stringResource(R.string.cd_update_download),
            detail = stringResource(
                R.string.update_state_available,
                state.versionName,
                ByteSize.format(state.sizeBytes)
            )
        )

        is UpdateUiState.Downloading -> {
            val percent = if (state.total > 0L) {
                ((state.bytes * 100L) / state.total).toInt().coerceIn(0, 100)
            } else {
                0
            }
            UpdateRowSpec(
                label = stringResource(R.string.update_state_downloading, percent),
                onClick = null,
                fill = ChipIdleFill,
                border = ChipIdleBorder,
                content = TextStrong,
                contentDescription = null,
                detail = null
            )
        }

        UpdateUiState.Verifying -> UpdateRowSpec(
            label = stringResource(R.string.update_state_verifying),
            onClick = null,
            fill = ChipIdleFill,
            border = ChipIdleBorder,
            content = TextStrong,
            contentDescription = null,
            detail = null
        )

        is UpdateUiState.ReadyToInstall -> UpdateRowSpec(
            label = stringResource(R.string.update_btn_install),
            onClick = onInstall,
            fill = Color(0xFF15803D),
            border = Color(0xFF22C55E),
            content = Color.White,
            contentDescription = stringResource(R.string.cd_update_install),
            detail = null
        )

        UpdateUiState.NeedsPermission -> UpdateRowSpec(
            label = stringResource(R.string.update_btn_open_settings),
            onClick = onInstall,
            fill = Color(0xFF78350F),
            border = Color(0xFFD97706),
            content = Color(0xFFFFF7ED),
            contentDescription = stringResource(R.string.update_btn_open_settings),
            detail = stringResource(R.string.update_state_needs_permission)
        )

        UpdateUiState.Installing -> UpdateRowSpec(
            label = stringResource(R.string.update_btn_install),
            onClick = null,
            fill = ChipIdleFill,
            border = ChipIdleBorder,
            content = ChipIdleText,
            contentDescription = null,
            detail = stringResource(R.string.update_state_installing)
        )

        UpdateUiState.NoNetwork -> UpdateRowSpec(
            label = stringResource(R.string.update_btn_check),
            onClick = onCheck,
            fill = ChipIdleFill,
            border = ChipIdleBorder,
            content = TextStrong,
            contentDescription = stringResource(R.string.cd_update_check),
            detail = stringResource(R.string.update_state_nonetwork)
        )

        is UpdateUiState.RateLimited -> UpdateRowSpec(
            label = stringResource(R.string.update_state_ratelimited),
            onClick = null,
            fill = ChipIdleFill,
            border = ChipIdleBorder,
            content = ChipIdleText,
            contentDescription = null,
            detail = stringResource(R.string.update_state_ratelimited)
        )

        is UpdateUiState.Unparsable -> UpdateRowSpec(
            label = stringResource(R.string.update_btn_open_releases),
            onClick = onOpenReleases,
            fill = ChipIdleFill,
            border = ChipIdleBorder,
            content = TextStrong,
            contentDescription = stringResource(R.string.update_btn_open_releases),
            detail = stringResource(R.string.update_state_unparsable)
        )

        is UpdateUiState.Failed -> UpdateRowSpec(
            label = stringResource(R.string.update_btn_check),
            onClick = onCheck,
            fill = Color(0xFF7F1D1D),
            border = Color(0xFFDC2626),
            content = Color(0xFFFEE2E2),
            contentDescription = stringResource(R.string.cd_update_check),
            detail = state.reason
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .testTag("sheet_update_row"),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, ChipIdleBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = stringResource(R.string.update_version_row, appVersionName),
                    color = TextStrong,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val actionShape = RoundedCornerShape(6.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(actionShape)
                    .background(spec.fill)
                    .border(1.dp, spec.border, actionShape)
                    .clickable(
                        enabled = spec.onClick != null,
                        role = Role.Button,
                        onClick = { spec.onClick?.invoke() }
                    )
                    .semantics {
                        spec.contentDescription?.let { contentDescription = it }
                    }
                    .padding(horizontal = 6.dp)
                    .testTag("sheet_update_action"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = spec.label,
                    color = spec.content,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        spec.detail?.let { detail ->
            Text(
                text = detail,
                color = TextMuted,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("sheet_update_detail")
            )
        }
    }
}
