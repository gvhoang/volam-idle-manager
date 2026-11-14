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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.data.PreferencesManager
import com.example.model.AccountConfig

@Composable
fun AccountDialog(
    accountConfig: AccountConfig,
    onSave: (AccountConfig) -> Unit,
    onDelete: (AccountConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var username by remember { mutableStateOf(accountConfig.username) }
    var password by remember { mutableStateOf(accountConfig.password) }
    var autoLoginEnabled by remember { mutableStateOf(accountConfig.autoLoginEnabled) }
    var customUrl by remember { mutableStateOf(accountConfig.customUrl) }
    var customUrlError by remember { mutableStateOf(false) }

    // Xoá tài khoản là thao tác không hoàn tác được (mất tài khoản ở mọi cụm + đăng xuất tab),
    // nên phải qua một lớp xác nhận. Khi đang xác nhận thì chỉ hiện hộp thoại xác nhận; "Hủy"
    // quay lại đúng bản nháp đang sửa vì composable này không rời composition.
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Color(0xFF1E293B),
            title = {
                Text(
                    text = "Xóa tài khoản tab ${accountConfig.tabIndex + 1}?",
                    color = Color(0xFFF87171),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Toàn bộ thông tin đăng nhập đã lưu sẽ bị xoá; phiên đăng nhập " +
                        "trong game cũng bị đăng xuất.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete(accountConfig)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444)),
                    modifier = Modifier.testTag("account_delete_confirm")
                ) {
                    Text("Xóa tài khoản", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = false },
                    modifier = Modifier.testTag("account_delete_cancel")
                ) {
                    Text("Hủy", fontSize = 11.sp)
                }
            }
        )
        return
    }

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
                        customUrlError = false
                    },
                    label = { Text("URL Tùy chỉnh (để trống dùng mặc định)", fontSize = 11.sp) },
                    singleLine = true,
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
                if (customUrlError) {
                    Text(
                        text = "Chỉ chấp nhận HTTPS trên volamidle.pages.dev",
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            // Đọc bản ĐÃ LƯU (accountConfig), không đọc ô nhập: người dùng có thể
                            // vừa xoá trắng ô nhưng chưa bấm Lưu — tài khoản vẫn còn trong prefs
                            // và vẫn cần được xoá. Chỉ khi cả ba trường đã trống thật thì không có
                            // gì để xoá: đóng hộp thoại, không chạy chuỗi xoá (sẽ dựng WebView và
                            // xoá cookie của một tab không có tài khoản).
                            if (accountConfig.username.isBlank() &&
                                accountConfig.password.isBlank() &&
                                accountConfig.customUrl.isBlank()
                            ) {
                                onDismiss()
                            } else {
                                confirmDelete = true
                            }
                        },
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        modifier = Modifier.testTag("account_delete_button")
                    ) {
                        Text("Xóa tài khoản", fontSize = 11.sp)
                    }

                    Row {
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
                                val cleanUrl = customUrl.trim()
                                if (cleanUrl.isNotEmpty() && !PreferencesManager.isTrustedGameUrl(cleanUrl)) {
                                    customUrlError = true
                                } else {
                                    onSave(
                                        accountConfig.copy(
                                            username = username.trim(),
                                            password = password.trim(),
                                            autoLoginEnabled = autoLoginEnabled,
                                            customUrl = cleanUrl
                                        )
                                    )
                                }
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
}
