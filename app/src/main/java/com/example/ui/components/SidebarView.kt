package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CharacterStats
import com.example.model.TabLifecycleState

@Composable
fun SidebarView(
    isMasterAutoRunning: Boolean,
    characterStatsList: List<CharacterStats>,
    selectedTabIndex: Int,
    onSelectTab: (Int) -> Unit,
    onLoginAll: () -> Unit,
    onStopAll: () -> Unit,
    onEditAccount: (Int) -> Unit,
    onSyncAutoMode: (String) -> Unit,
    onToggleSidebar: () -> Unit,
    modifier: Modifier = Modifier.width(265.dp),
    getAccount: ((Int) -> com.example.model.AccountConfig)? = null,
    isBlackScreenSynced: Boolean = false,
    onSyncBlackScreen: (Boolean) -> Unit = {}
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .testTag("control_sidebar"),
        color = Color(0xFF131924),
        border = BorderStroke(1.dp, Color(0xFF222F42))
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Header with Collapse Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BẢNG ĐIỀU KHIỂN",
                    color = Color(0xFF93C5FD),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                IconButton(
                    onClick = onToggleSidebar,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("toggle_sidebar_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Ẩn bảng",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // MODULE 2: MASTER CONTROLS [▶ ĐĂNG NHẬP TẤT CẢ] & [⏹ DỪNG TẤT CẢ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("master_control_row"),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onLoginAll,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .testTag("master_login_all_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF15803D),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Đăng nhập",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "ĐĂNG NHẬP HẾT",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = onStopAll,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .testTag("master_stop_all_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB91C1C),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Dừng",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "DỪNG TẤT CẢ",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // MODULE 6: SECTION 1 (4 CHARACTER SLOTS)
            // STRICT SIZE CONSTRAINT: Fixed height 74dp - 80dp, maxLines = 1, singleLine = true, cleaned string
            Text(
                text = "DANH SÁCH NHÂN VẬT (4 TAB)",
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (tabIdx in 0..3) {
                    val stats = characterStatsList.getOrElse(tabIdx) { CharacterStats(tabIndex = tabIdx) }
                    val isSelected = selectedTabIndex == tabIdx
                    val account = getAccount?.invoke(tabIdx)

                    CharacterCard(
                        tabIndex = tabIdx,
                        stats = stats,
                        account = account,
                        isSelected = isSelected,
                        onSelect = { onSelectTab(tabIdx) },
                        onEditAccount = { onEditAccount(tabIdx) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // MODULE 6: SECTION 3 & 4 (INVENTORY & QUEST STATS OF SELECTED TAB)
            // 5 stats: 🧧 Đại hồng bao, 🧧 Hồng bao, 🗼 Kỷ lục tầng tháp, 👹 Boss Hoàng Kim đã hạ, 📜 Nhiệm vụ Dã Tẩu
            val activeStats = characterStatsList.getOrElse(selectedTabIndex) { CharacterStats(tabIndex = selectedTabIndex) }
            Text(
                text = "THÔNG SỐ TAB ${selectedTabIndex + 1}: ${activeStats.characterName}",
                color = Color(0xFFFBBF24),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inventory_quest_panel"),
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatRow(label = "🧧 Đại hồng bao", value = activeStats.daiHongBao, valueColor = Color(0xFFF87171))
                    StatRow(label = "🧧 Hồng bao", value = activeStats.hongBao, valueColor = Color(0xFFFCA5A5))
                    StatRow(label = "🗼 Kỷ lục tháp", value = activeStats.thapRecord, valueColor = Color(0xFF67E8F9))
                    StatRow(label = "👹 Boss HK hạ", value = activeStats.bossHoangKim, valueColor = Color(0xFFFDE047))
                    StatRow(label = "📜 Dã Tẩu", value = activeStats.daTauQuest, valueColor = Color(0xFF86EFAC))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // MODULE 7: SECTION 5 (SYNC AUTO MODE - NHIỆM VỤ, PHÓ BẢN, LUYỆN CÔNG)
            Text(
                text = "ĐỒNG BỘ AUTO CẢ 4 TAB",
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sync_mode_row"),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { onSyncAutoMode("Nhiệm Vụ") },
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .testTag("sync_quest_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(text = "📜 Nhiệm Vụ", fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                Button(
                    onClick = { onSyncAutoMode("Phó Bản") },
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .testTag("sync_dungeon_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9333EA)),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(text = "⚔️ Phó Bản", fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                Button(
                    onClick = { onSyncAutoMode("Luyện Công") },
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .testTag("sync_grind_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(text = "🔥 Luyện Công", fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Ô chức năng "Màn hình đen" có ô trắng tích chọn phía trước (Hệ thống -> Hiển Thị -> Màn hình đen)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clickable { onSyncBlackScreen(!isBlackScreenSynced) }
                    .testTag("sync_black_screen_checkbox"),
                shape = RoundedCornerShape(5.dp),
                color = if (isBlackScreenSynced) Color(0xFF064E3B) else Color(0xFF0F172A),
                border = BorderStroke(
                    1.dp,
                    if (isBlackScreenSynced) Color(0xFF10B981) else Color(0xFF334155)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Ô trắng để tích chọn phía trước "Màn hình đen"
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White)
                                .border(
                                    width = 1.dp,
                                    color = if (isBlackScreenSynced) Color(0xFF059669) else Color(0xFF64748B),
                                    shape = RoundedCornerShape(3.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isBlackScreenSynced) {
                                Text(
                                    text = "✓",
                                    color = Color(0xFF047857),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Màn hình đen",
                            color = Color.White,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    Text(
                        text = if (isBlackScreenSynced) "🟢 Đang bật (4 Tab)" else "⚙️ Hệ thống ➔ Hiển Thị",
                        color = if (isBlackScreenSynced) Color(0xFF6EE7B7) else Color(0xFF94A3B8),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// MODULE 6: Character Card with STRICT 74dp - 80dp height and 3 lines
@Composable
fun CharacterCard(
    tabIndex: Int,
    stats: CharacterStats,
    account: com.example.model.AccountConfig?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEditAccount: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp) // Strictly 74-80dp constraint
            .clickable { onSelect() }
            .testTag("char_card_tab_$tabIndex"),
        shape = RoundedCornerShape(5.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1E293B) else Color(0xFF0F172A)
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E293B)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 7.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Line 1: 🟢 [Tên] + Trạng thái Online / Đã dừng
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val dotColor = when (stats.state) {
                        TabLifecycleState.IN_GAME -> Color(0xFF10B981)
                        TabLifecycleState.LOGGING_IN -> Color(0xFFF59E0B)
                        TabLifecycleState.IDLE -> Color(0xFF64748B)
                        TabLifecycleState.OFFLINE -> Color(0xFFEF4444)
                    }

                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Tab ${tabIndex + 1}: ${stats.characterName}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = when (stats.state) {
                        TabLifecycleState.IN_GAME -> "🟢 Online"
                        TabLifecycleState.LOGGING_IN -> "🟡 Đang vào"
                        TabLifecycleState.IDLE -> "⚪ Chờ mở"
                        TabLifecycleState.OFFLINE -> "🔴 Đã dừng"
                    },
                    color = when (stats.state) {
                        TabLifecycleState.IN_GAME -> Color(0xFF34D399)
                        TabLifecycleState.LOGGING_IN -> Color(0xFFFBBF24)
                        TabLifecycleState.IDLE -> Color(0xFF94A3B8)
                        TabLifecycleState.OFFLINE -> Color(0xFFEF4444)
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }

            // Line 2: Phái: [Tên Môn Phái] | Cấp [Số Cấp] - [% EXP]
            Text(
                text = "Phái: ${stats.sect} | Cấp ${stats.level} - ${stats.expPercent}",
                color = Color(0xFFCBD5E1),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Line 3: Button [🔑 TK: {user} (Đổi)]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stats.lastStatus,
                    color = Color(0xFF64748B),
                    fontSize = 8.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                val accountLabel = if (account != null && account.username.isNotBlank()) "🔑 ${account.username} (Đổi)" else "🔑 TK (Đổi)"
                Surface(
                    modifier = Modifier
                        .clickable { onEditAccount() }
                        .testTag("edit_account_tab_$tabIndex"),
                    shape = RoundedCornerShape(3.dp),
                    color = Color(0xFF334155)
                ) {
                    Text(
                        text = accountLabel,
                        color = Color(0xFFF1F5F9),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            fontSize = 10.sp,
            maxLines = 1
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}