package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.AccountConfig

@Composable
fun AccountDialog(
    accountConfig: AccountConfig,
    onSave: (AccountConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var username by remember { mutableStateOf(accountConfig.username) }
    var password by remember { mutableStateOf(accountConfig.password) }
    var autoLoginEnabled by remember { mutableStateOf(accountConfig.autoLoginEnabled) }
    var customUrl by remember { mutableStateOf(accountConfig.customUrl) }
    var urlError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("account_config_dialog"),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF1E293B),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "CẤU HÌNH TÀI KHOẢN TAB ${accountConfig.tabIndex + 1}",
                    color = Color(0xFF38BDF8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Tài khoản (Username)", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF38BDF8),
                        unfocusedLabelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_username_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mật khẩu (Password)", fontSize = 11.sp) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF38BDF8),
                        unfocusedLabelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_password_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customUrl,
                    onValueChange = {
                        customUrl = it
                        urlError = null
                    },
                    label = { Text("URL Tùy chỉnh (để trống dùng mặc định)", fontSize = 11.sp) },
                    singleLine = true,
                    isError = urlError != null,
                    placeholder = { Text("https://volamidle.pages.dev", color = Color(0xFF64748B), fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF38BDF8),
                        unfocusedLabelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                urlError?.let { message ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message,
                        color = Color(0xFFF87171),
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = autoLoginEnabled,
                        onCheckedChange = { autoLoginEnabled = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF10B981),
                            uncheckedColor = Color(0xFF64748B)
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Cho phép tự động đăng nhập khi bấm [Đăng nhập tất cả]",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, Color(0xFF475569)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1))
                    ) {
                        Text("Hủy", fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trimmedUrl = customUrl.trim()
                            val error = validateCustomUrl(trimmedUrl)
                            if (error != null) {
                                urlError = error
                                return@Button
                            }
                            urlError = null
                            onSave(
                                accountConfig.copy(
                                    username = username.trim(),
                                    password = password.trim(),
                                    autoLoginEnabled = autoLoginEnabled,
                                    customUrl = trimmedUrl
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier.testTag("save_account_button")
                    ) {
                        Text("Lưu cấu hình", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Kiểm tra URL tùy chỉnh trước khi lưu.
 * Trả về null nếu hợp lệ (hoặc để trống = dùng máy chủ mặc định), ngược lại là thông báo lỗi.
 *
 * Chỉ chấp nhận http/https tuyệt đối. Không kiểm tra tên miền có tồn tại thật hay không
 * (việc đó cần mạng) — nhưng chặn được các trường hợp gõ thiếu scheme, gõ sai định dạng,
 * hoặc dán nhầm một chuỗi không phải URL, vốn là nguyên nhân gây ERR_NAME_NOT_RESOLVED.
 */
private fun validateCustomUrl(url: String): String? {
    if (url.isBlank()) return null
    val uri = try {
        android.net.Uri.parse(url)
    } catch (_: Exception) {
        return "URL không hợp lệ"
    }
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") {
        return "URL phải bắt đầu bằng http:// hoặc https://"
    }
    if (uri.host.isNullOrBlank()) {
        return "URL thiếu tên miền (ví dụ: https://volamidle.pages.dev)"
    }
    return null
}
