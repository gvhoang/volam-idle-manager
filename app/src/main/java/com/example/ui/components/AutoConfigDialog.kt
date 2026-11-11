package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.webview.AutoConfig

/**
 * Hộp thoại 🤖 Tự động — bản Android của mục "Tự động" trong bảng tổng của Manager PC.
 *
 * Nội dung đúng thứ tự và đúng nhãn tiếng Việt của shell.html (TD_CK), để hai bên nói
 * cùng một ngôn ngữ khi đối chiếu cấu hình. Toàn bộ nằm trong vùng cuộn vì điện thoại
 * dọc không đủ chỗ cho 13 mục + 4 ô số.
 *
 * Hộp thoại chỉ sửa bản nháp trong bộ nhớ; quyền ghi xuống WebView nằm ở nút Lưu.
 */
@Composable
fun AutoConfigDialog(
    config: AutoConfig,
    tabCount: Int,
    onSave: (AutoConfig) -> Unit,
    onResetCounters: () -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(config) }

    // Ô số giữ dạng chuỗi để người dùng xoá trắng được khi đang gõ; đổi sang số lúc Lưu.
    var k2kText by remember { mutableStateOf(config.k2k.toString()) }
    var lbMuaText by remember { mutableStateOf(config.lbMua.toString()) }
    var knbMuaText by remember { mutableStateOf(config.knbMua.toString()) }
    var knbGiuText by remember { mutableStateOf(config.knbGiu.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("auto_config_dialog"),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF1E293B),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "🤖 TỰ ĐỘNG",
                    color = Color(0xFFFBBF24),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Máy ra lệnh qua kênh ẩn #vlmAuto của game — không bấm chuột, không gõ phím.",
                    color = Color(0xFF94A3B8),
                    fontSize = 9.5.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Công tắc tổng. Mặc định TẮT và không bao giờ tự bật.
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auto_master_switch"),
                    shape = RoundedCornerShape(6.dp),
                    color = if (draft.on) Color(0xFF064E3B) else Color(0xFF0F172A),
                    border = BorderStroke(
                        1.dp,
                        if (draft.on) Color(0xFF10B981) else Color(0xFF334155)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bật tự động",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (draft.on) "🟢 Đang bật cho $tabCount tab" else "⚪ Đang tắt",
                                color = if (draft.on) Color(0xFF6EE7B7) else Color(0xFF94A3B8),
                                fontSize = 9.sp
                            )
                        }
                        Switch(
                            checked = draft.on,
                            onCheckedChange = { draft = draft.copy(on = it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981),
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // ==== 13 ô tích, đúng thứ tự và nhãn của TD_CK trong shell.html ====
                    AutoCheckRow("Dã Tẩu tối đa: bật Trợ thủ, Chế độ Nhiệm Vụ khi còn lượt", draft.dt) {
                        draft = draft.copy(dt = it)
                    }
                    AutoCheckRow("Mua Lệnh Bài Dã Tẩu bằng KNB khóa (20 / cái)", draft.ldtMua) {
                        draft = draft.copy(ldtMua = it)
                    }
                    AutoCheckRow("Bình Phúc Duyên Lộ + Rương Phúc Duyên (lấy Lệnh Bài Dã Tẩu)", draft.pd) {
                        draft = draft.copy(pd = it)
                    }
                    AutoCheckRow("Sát Thủ: tự gọi khi có Lệnh Bài", draft.st) {
                        draft = draft.copy(st = it)
                    }
                    AutoCheckRow("Tự chạy phó bản: Chế độ Phó Bản tới khi hết lượt, rồi Dã Tẩu, rồi về chế độ cũ", draft.pb) {
                        draft = draft.copy(pb = it)
                    }
                    AutoCheckRow("Nhận quà Điểm danh + Mốc cấp", draft.qua) {
                        draft = draft.copy(qua = it)
                    }
                    AutoCheckRow("Khấn tượng mỗi ngày (+20% kinh nghiệm 8 giờ)", draft.khan) {
                        draft = draft.copy(khan = it)
                    }
                    AutoCheckRow("Sự kiện: đổi Đại Hồng Bao (100 mỗi loại, tối đa 1/ngày)", draft.evDhb) {
                        draft = draft.copy(evDhb = it)
                    }
                    AutoCheckRow("Sự kiện: đổi Hồng Bao (20 mỗi loại, tối đa 3/ngày)", draft.evHb) {
                        draft = draft.copy(evHb = it)
                    }
                    AutoCheckRow("Tiên Thảo Lộ hết thì dùng trong túi", draft.ttl) {
                        draft = draft.copy(ttl = it)
                    }
                    AutoCheckRow("… túi hết thì mua (20 KNB khóa)", draft.ttlMua) {
                        draft = draft.copy(ttlMua = it)
                    }
                    AutoCheckRow("Tiên Thảo Lộ Đặc Biệt hết thì dùng trong túi", draft.tt2) {
                        draft = draft.copy(tt2 = it)
                    }
                    AutoCheckRow("… túi hết thì mua (300 KNB khóa)", draft.tt2Mua) {
                        draft = draft.copy(tt2Mua = it)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = Color(0xFF334155))
                    Spacer(modifier = Modifier.height(6.dp))

                    // ==== Bốn ô số. 0 = không làm. ====
                    AutoNumberRow(
                        label = "Đổi KNB → KNB khóa tối đa",
                        value = k2kText,
                        suffix = "KNB / ngày (0 = không đổi)",
                        max = AutoConfig.K2K_MAX,
                        testTag = "auto_num_k2k"
                    ) { k2kText = it }

                    AutoNumberRow(
                        label = "Mua Lệnh Bài Sát Thủ",
                        value = lbMuaText,
                        suffix = "cái / ngày (100 KNB / cái, 0 = không mua)",
                        max = AutoConfig.LB_MUA_MAX,
                        testTag = "auto_num_lbMua"
                    ) { lbMuaText = it }

                    AutoNumberRow(
                        label = "Mua Kim Nguyên Bảo ở Kỳ Trân Các",
                        value = knbMuaText,
                        suffix = "KNB / ngày (1 vạn lượng / cái, 0 = không mua)",
                        max = AutoConfig.KNB_MUA_MAX,
                        testTag = "auto_num_knbMua"
                    ) { knbMuaText = it }

                    AutoNumberRow(
                        label = "Giữ lại ít nhất",
                        value = knbGiuText,
                        suffix = "lượng",
                        max = AutoConfig.KNB_GIU_MAX,
                        testTag = "auto_num_knbGiu"
                    ) { knbGiuText = it }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(5.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF7F1D1D))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Lưu ý tiền tệ",
                                color = Color(0xFFFCA5A5),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Kim Nguyên Bảo mua bằng ngân lượng: 1 KNB = 1 vạn (10.000) lượng, " +
                                    "game giới hạn 1.000 KNB / ngày mỗi tài khoản. \"Giữ lại ít nhất\" là SÀN " +
                                    "ngân lượng — máy không mua nếu ví tụt xuống dưới mức đó, nên đặt bằng số " +
                                    "bạn muốn còn lại, không phải số được tiêu.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                            Text(
                                text = "Mua / nhận / dùng làm ngay cả khi đang chơi. Đổi chế độ, đi khấn, " +
                                    "mua KNB và đổi Hồng Bao sự kiện chỉ khi không bấm vào tab đó 60 giây " +
                                    "(những việc này phải mở bảng nên sẽ che màn hình). Đang trong phó bản / " +
                                    "tháp / truy sát / đã chết thì chờ xong.",
                                color = Color(0xFF94A3B8),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onResetCounters,
                        border = BorderStroke(1.dp, Color(0xFF7F1D1D)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFCA5A5)),
                        modifier = Modifier.testTag("auto_reset_counters_button")
                    ) {
                        Text("Đặt lại bộ đếm", fontSize = 10.sp)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, Color(0xFF475569)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1))
                    ) {
                        Text("Hủy", fontSize = 10.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            // Chuỗi rỗng / rác -> 0; âm và vượt trần bị kẹp, để không có cách
                            // nào gửi xuống WebView một số ngoài khoảng của bảng PC.
                            onSave(
                                draft.copy(
                                    k2k = parseClamped(k2kText, AutoConfig.K2K_MAX),
                                    lbMua = parseClamped(lbMuaText, AutoConfig.LB_MUA_MAX),
                                    knbMua = parseClamped(knbMuaText, AutoConfig.KNB_MUA_MAX),
                                    knbGiu = parseClamped(knbGiuText, AutoConfig.KNB_GIU_MAX)
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier.testTag("save_auto_config_button")
                    ) {
                        Text("Lưu", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoCheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF10B981),
                uncheckedColor = Color(0xFF64748B)
            )
        )
        Text(
            text = label,
            color = Color(0xFFCBD5E1),
            fontSize = 10.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AutoNumberRow(
    label: String,
    value: String,
    suffix: String,
    max: Int,
    testTag: String,
    onChange: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(text = label, color = Color(0xFFCBD5E1), fontSize = 10.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = { raw ->
                    // Chỉ nhận chữ số: dấu trừ / dấu chấm sẽ bị loại ngay khi gõ.
                    val digits = raw.filter { it.isDigit() }
                    onChange(if (digits.isEmpty()) "" else digits.take(9))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF475569),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                modifier = Modifier
                    .width(96.dp)
                    .height(52.dp)
                    .testTag(testTag)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = suffix,
                color = Color(0xFF94A3B8),
                fontSize = 9.sp,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = "tối đa $max",
            color = Color(0xFF64748B),
            fontSize = 8.sp
        )
    }
}

private fun parseClamped(text: String, max: Int): Int {
    val v = text.toIntOrNull() ?: 0
    return if (v < 0) 0 else if (v > max) max else v
}
