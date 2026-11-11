# Security review — v4.0.0

Đánh giá tĩnh cho mã nguồn tại tag `v4.0.0` (commit `bd53f09`).
Đây **không** phải pentest, không có kiểm thử runtime trên thiết bị hay trên máy chủ game.

> Tài liệu này thay thế bản đánh giá cũ cho `v3.0.1`. Bản cũ mô tả trạng thái mà mã hiện tại
> **không còn** — xem mục "Thay đổi bị revert" bên dưới.

## Trạng thái: CHƯA hardened

Ứng dụng dùng cá nhân. Không phân phối cho người khác ở trạng thái hiện tại.

## Findings

| Mức | Vấn đề | Vị trí | Hệ quả |
|---|---|---|---|
| Nghiêm trọng | `onReceivedSslError` gọi `handler?.proceed()` vô điều kiện | `GameTabManager.kt:295-302` | Chấp nhận chứng chỉ hết hạn, sai host hoặc tự ký. Người đứng giữa đường truyền đọc được mật khẩu và cookie phiên |
| Nghiêm trọng | `onPageFinished` bơm script điền mật khẩu vào bất kỳ trang nào tải xong khi hệ thống đang chạy và tab active | `GameTabManager.kt:254-278` (inject tại `:273` và `:278`) | Một redirect, XSS trên origin tin cậy, hoặc URL tùy chỉnh do người khác đưa, sẽ nhận được mật khẩu đã điền |
| Cao | `addJavascriptInterface(..., "AndroidBridge")` không giới hạn origin, không bao giờ gỡ | `GameTabManager.kt:242` | JavaScript trong mọi frame gọi được callback: đổi trạng thái, ghi log, đăng nhập lại |
| Cao | Mật khẩu lưu plaintext trong `SharedPreferences`, ghi lặp qua nhiều định dạng khóa | `data/PreferencesManager.kt` | Root, `adb run-as` (vì APK debug), hoặc forensics đọc được toàn bộ |
| Cao | APK đang lưu hành là bản debug: `debuggable=true`, ký bằng `debug.keystore` | manifest đã merge | `adb run-as` đọc được prefs; không thể phát hành công khai; không nâng cấp liên tục được bằng khóa khác |
| Trung bình | `mixedContentMode = MIXED_CONTENT_ALWAYS_ALLOW` | `GameTabManager.kt:233` | Tài nguyên HTTP bị chèn vào trang HTTPS |
| Trung bình | `isMinifyEnabled = false`; dependency khai báo nhưng không dùng (Firebase AI, AppCheck, Room, Retrofit, Moshi, OkHttp, logging-interceptor) | `app/build.gradle.kts` | APK lớn, bề mặt tấn công rộng hơn cần thiết |
| Thấp | Không certificate pinning | — | Phụ thuộc hoàn toàn vào CA tin cậy của hệ thống |
| Thấp | `POST_NOTIFICATIONS` khai báo nhưng không request runtime | `AndroidManifest.xml:11` | Trên API 33+, notification của foreground service bị ẩn — mất chỉ báo service đang chạy |

## Đã tắt đúng (không cần xử lý)

| Cấu hình | Giá trị | Ghi chú |
|---|---|---|
| `android:allowBackup` | `false` | `backup_rules.xml` và `data_extraction_rules.xml` vẫn là mẫu rỗng — nếu ai bật lại backup, toàn bộ prefs (gồm mật khẩu) sẽ vào backup |
| `android:usesCleartextTraffic` | `false` | Chặn tải trang qua HTTP |
| `allowFileAccess` / `allowContentAccess` | `false` / `false` | Chặn `file://` và `content://` |
| `customUrl` | chỉ nhận HTTPS đúng host `volamidle.pages.dev` | `PreferencesManager.isTrustedGameUrl()` — kiểm tra lúc nhập, **không** kiểm tra lúc inject |
| Retry khi lỗi main-frame | tối đa 3 lần, giãn cách 2.5s → 5s → 7.5s | `ERROR_HOST_LOOKUP` dừng hẳn |

## Thay đổi bị revert

`v3.0.1` từng: từ chối lỗi TLS, `MIXED_CONTENT_NEVER_ALLOW`, giới hạn điều hướng top-level,
chỉ inject trong origin tin cậy. Commit `c186416` đã revert các thay đổi đó trong quá trình
làm 4.0, nên:

- `handler?.proceed()` quay lại
- `mixedContentMode = MIXED_CONTENT_ALWAYS_ALLOW` quay lại
- không còn `shouldOverrideUrlLoading`
- inject không còn kiểm tra origin

Nếu bạn đang dựa vào bản đánh giá `v3.0.1` cũ, **đừng** — chuỗi đó không còn trong mã.

## Thứ tự hardening đề xuất

1. `onReceivedSslError` → `handler.cancel()`. Chỉ thêm ngoại lệ nếu thật sự cần CDN nội bộ.
2. `mixedContentMode = MIXED_CONTENT_NEVER_ALLOW`.
3. Khôi phục `shouldOverrideUrlLoading`, chỉ cho phép `https://volamidle.pages.dev` và subdomain.
4. Kiểm tra allowlist tại **thời điểm inject**, không chỉ lúc nhập URL.
5. Thay `addJavascriptInterface` bằng `WebViewCompat.addWebMessageListener` với `allowedOriginRules`.
6. Mã hóa mật khẩu bằng Android Keystore + AES-GCM, bỏ các khóa legacy sau migration.
7. Ký release bằng upload key thật, `isDebuggable = false`, `isMinifyEnabled = true`, `shrinkResources = true`.
8. Xóa dependency không dùng.
9. Thêm `network_security_config` nếu cần giới hạn CA.
10. Không dùng trên thiết bị dùng chung hoặc đã root.

Mục 1 và 2 là thay đổi nhỏ nhất mà loại bỏ được hai lỗi Nghiêm trọng.

## Phạm vi & giới hạn

- Đánh giá **tĩnh** trên mã nguồn. Không chạy app, không chạy thử exploit.
- Không đánh giá máy chủ game, không đánh giá rủi ro phía dịch vụ.
- Không đánh giá bản PC (`VOLAMIDLE-Manager`), vốn là dự án riêng.
- `assembleRelease` chưa chạy được vì thiếu `my-upload-key.jks`. APK lưu hành là bản debug.
- Báo cáo này đúng cho tag `v4.0.0`. Sửa mã thì phải đánh giá lại.
