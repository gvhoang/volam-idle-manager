package com.example.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.WebAsset
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NetworkDataInfo
import com.example.model.ViewLayoutMode

@Composable
fun HeaderBar(
    isSidebarVisible: Boolean,
    onToggleSidebar: () -> Unit,
    currentCluster: String,
    onChangeCluster: (String) -> Unit,
    networkData: NetworkDataInfo,
    onResetNetworkStats: () -> Unit,
    fps: Int,
    onToggleFps: () -> Unit,
    onActivateSleepMode: () -> Unit,
    layoutMode: ViewLayoutMode,
    onChangeLayoutMode: (ViewLayoutMode) -> Unit,
    selectedTabIndex: Int = 0,
    onSelectTab: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    var clusterMenuExpanded by remember { mutableStateOf(false) }
    var viewMenuExpanded by remember { mutableStateOf(false) }
    val headerScrollState = rememberScrollState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("app_header_bar"),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        // =========================================================================
        // THANH HEADER 1 HÀNG HỖ TRỢ VUỐT NGANG (HORIZONTAL SCROLL) MƯỢT MÀ
        // Tối ưu cho cả màn hình dọc điện thoại (RedMagic 8 Pro / Vivo X100 Ultra)
        // lẫn màn hình ngang PC/Tablet mà không làm biến dạng hay mất nút bấm.
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(headerScrollState)
                .padding(horizontal = 8.dp)
                .testTag("header_bar_scroll_row"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Cụm Trái: Nút đóng/mở Sidebar + Tên Game + Chọn Cụm Server
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isSidebarVisible || isPortrait) {
                    IconButton(
                        onClick = onToggleSidebar,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("expand_sidebar_button")
                    ) {
                        Icon(
                            imageVector = if (isPortrait) Icons.Default.Menu else Icons.Default.ChevronRight,
                            contentDescription = "Hiện bảng điều khiển",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Text(
                    text = "⚔️ VÕ LÂM IDLE",
                    color = Color(0xFFF1F5F9),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    softWrap = false
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Cụm Server Dropdown (MODULE 3)
                Box {
                    Surface(
                        modifier = Modifier
                            .clickable { clusterMenuExpanded = true }
                            .testTag("cluster_selector"),
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Text(
                            text = "🌐 $currentCluster ▼",
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = clusterMenuExpanded,
                        onDismissRequest = { clusterMenuExpanded = false },
                        modifier = Modifier.background(Color(0xFF1E293B))
                    ) {
                        listOf("Cụm 1", "Cụm 2", "Cụm 3").forEach { cluster ->
                            DropMenuItemCluster(
                                cluster = cluster,
                                currentCluster = currentCluster,
                                onSelect = {
                                    onChangeCluster(cluster)
                                    clusterMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Cụm Phải: Giám sát Mạng + Khóa FPS + Treo Máy + Chế độ VIEW (4 Dọc / Lưới 2x2 / 1 Tab)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NetworkTrafficBadge(
                    networkData = networkData,
                    onResetNetworkStats = onResetNetworkStats
                )

                FpsLockButton(
                    fps = fps,
                    onToggleFps = onToggleFps
                )

                SleepModeButton(
                    onActivateSleepMode = onActivateSleepMode
                )

                ViewModeButton(
                    layoutMode = layoutMode,
                    onChangeLayoutMode = onChangeLayoutMode,
                    viewMenuExpanded = viewMenuExpanded,
                    onToggleViewMenu = { viewMenuExpanded = it }
                )

                if (layoutMode == ViewLayoutMode.SINGLE_TAB) {
                    SingleTabQuickChips(
                        selectedTabIndex = selectedTabIndex,
                        onSelectTab = onSelectTab
                    )
                }
            }
        }
    }
}

@Composable
private fun DropMenuItemCluster(
    cluster: String,
    currentCluster: String,
    onSelect: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = cluster,
                color = if (cluster == currentCluster) Color(0xFF38BDF8) else Color.White,
                fontSize = 11.sp,
                fontWeight = if (cluster == currentCluster) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false
            )
        },
        onClick = onSelect
    )
}

@Composable
private fun NetworkTrafficBadge(
    networkData: NetworkDataInfo,
    onResetNetworkStats: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFF022C22),
        border = BorderStroke(1.dp, Color(0xFF065F46)),
        modifier = Modifier.testTag("network_traffic_badge")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                text = "📶 ${networkData.totalBytesFormatted} (↓${networkData.downloadSpeedFormatted})",
                color = Color(0xFF34D399),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Reset mạng",
                tint = Color(0xFF6EE7B7),
                modifier = Modifier
                    .size(13.dp)
                    .clickable { onResetNetworkStats() }
            )
        }
    }
}

@Composable
private fun FpsLockButton(
    fps: Int,
    onToggleFps: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clickable { onToggleFps() }
            .testTag("fps_toggle_button"),
        shape = RoundedCornerShape(4.dp),
        color = if (fps == 30) Color(0xFF78350F) else Color(0xFF1E293B),
        border = BorderStroke(1.dp, if (fps == 30) Color(0xFFD97706) else Color(0xFF475569))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = "FPS",
                tint = if (fps == 30) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "⚡ ${fps}FPS",
                color = if (fps == 30) Color(0xFFFDE68A) else Color(0xFFE2E8F0),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun SleepModeButton(
    onActivateSleepMode: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clickable { onActivateSleepMode() }
            .testTag("sleep_mode_button"),
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFF312E81),
        border = BorderStroke(1.dp, Color(0xFF6366F1))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = "Treo máy",
                tint = Color(0xFFA5B4FC),
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "🌙 Treo máy",
                color = Color(0xFFE0E7FF),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun ViewModeButton(
    layoutMode: ViewLayoutMode,
    onChangeLayoutMode: (ViewLayoutMode) -> Unit,
    viewMenuExpanded: Boolean,
    onToggleViewMenu: (Boolean) -> Unit
) {
    Box {
        Surface(
            modifier = Modifier
                .clickable {
                    val next = when (layoutMode) {
                        ViewLayoutMode.FOUR_COLUMNS -> ViewLayoutMode.TWO_BY_TWO
                        ViewLayoutMode.TWO_BY_TWO -> ViewLayoutMode.SINGLE_TAB
                        ViewLayoutMode.SINGLE_TAB -> ViewLayoutMode.FOUR_COLUMNS
                    }
                    onChangeLayoutMode(next)
                }
                .testTag("view_mode_toggle_button"),
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF0284C7),
            border = BorderStroke(1.dp, Color(0xFF38BDF8))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Icon(
                    imageVector = when (layoutMode) {
                        ViewLayoutMode.FOUR_COLUMNS -> Icons.Default.ViewColumn
                        ViewLayoutMode.TWO_BY_TWO -> Icons.Default.GridView
                        ViewLayoutMode.SINGLE_TAB -> Icons.Default.WebAsset
                    },
                    contentDescription = "Chế độ hiển thị",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = when (layoutMode) {
                        ViewLayoutMode.FOUR_COLUMNS -> "VIEW: 4 Dọc"
                        ViewLayoutMode.TWO_BY_TWO -> "VIEW: Lưới 2x2"
                        ViewLayoutMode.SINGLE_TAB -> "VIEW: 1 Tab"
                    },
                    color = Color.White,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "▼",
                    color = Color(0xFFBAE6FD),
                    fontSize = 8.5.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.clickable { onToggleViewMenu(true) }
                )
            }
        }

        DropdownMenu(
            expanded = viewMenuExpanded,
            onDismissRequest = { onToggleViewMenu(false) },
            modifier = Modifier.background(Color(0xFF1E293B))
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "⊞ 4 Cột Dọc (1x4)",
                        color = if (layoutMode == ViewLayoutMode.FOUR_COLUMNS) Color(0xFF38BDF8) else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (layoutMode == ViewLayoutMode.FOUR_COLUMNS) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = {
                    onChangeLayoutMode(ViewLayoutMode.FOUR_COLUMNS)
                    onToggleViewMenu(false)
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "⊞ Lưới 2x2 (4 Ngang)",
                        color = if (layoutMode == ViewLayoutMode.TWO_BY_TWO) Color(0xFF38BDF8) else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (layoutMode == ViewLayoutMode.TWO_BY_TWO) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = {
                    onChangeLayoutMode(ViewLayoutMode.TWO_BY_TWO)
                    onToggleViewMenu(false)
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "🔲 1 Tab (Toàn màn hình)",
                        color = if (layoutMode == ViewLayoutMode.SINGLE_TAB) Color(0xFF38BDF8) else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (layoutMode == ViewLayoutMode.SINGLE_TAB) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = {
                    onChangeLayoutMode(ViewLayoutMode.SINGLE_TAB)
                    onToggleViewMenu(false)
                }
            )
        }
    }
}

@Composable
private fun SingleTabQuickChips(
    selectedTabIndex: Int,
    onSelectTab: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        (0..3).forEach { index ->
            Surface(
                modifier = Modifier
                    .clickable { onSelectTab(index) }
                    .testTag("header_tab_select_$index"),
                shape = RoundedCornerShape(3.dp),
                color = if (selectedTabIndex == index) Color(0xFF0284C7) else Color(0xFF1E293B),
                border = BorderStroke(1.dp, if (selectedTabIndex == index) Color(0xFF38BDF8) else Color(0xFF475569))
            ) {
                Text(
                    text = "T${index + 1}",
                    color = if (selectedTabIndex == index) Color.White else Color(0xFF94A3B8),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                )
            }
        }
    }
}