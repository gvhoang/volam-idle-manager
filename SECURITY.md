# Security review — v4.1.0

Đánh giá tĩnh cho mã nguồn tại tag `v4.1.0`.
Đây **không** phải pentest, không có kiểm thử runtime trên thiết bị hay trên máy chủ game.

> Tài liệu này thay thế bản đánh giá cũ cho `v3.0.1`. Bản cũ mô tả trạng thái mà mã hiện tại
> **không còn** — xem mục "Thay đổi bị revert" bên dưới.
>
> Bản `v4.1.0` thêm ba thay đổi so với `v4.0.0`: chrome 32dp + sheet điều khiển, số tab 1–8,
> và kênh tự cập nhật. Chỉ thay đổi thứ ba mở thêm bề mặt tấn công — xem mục cuối.

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
| Thấp | `REQUEST_INSTALL_PACKAGES` khai báo cho kênh tự cập nhật | `AndroidManifest.xml:14` | Không cho phép cài ngầm: mỗi lần vẫn cần người dùng chạm + hộp thoại hệ thống. Nhưng APK được cài là APK mà kênh tải về, và kênh đó tin hoàn toàn vào GitHub + chữ ký của chính APK |

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

## Tự động cập nhật (từ v4.1)

Tính năng tự cập nhật từ GitHub Releases (`docs/DESIGN-AUTOUPDATE.md`) thêm các kiểm soát sau.

### Ranh giới thật là chữ ký APK, không phải SHA-256

- Android kiểm signer set của APK mới với app đang cài; lệch ⇒ `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
  Không app nào vượt được bằng `REQUEST_INSTALL_PACKAGES`.
- `assets[].digest` (SHA-256) **cùng nguồn** với tệp APK. Nó bắt được hỏng hóc (CDN cắt cụt,
  proxy lỗi, lỗi lưu trữ) và **không** bắt được kẻ sửa release — kẻ đó sửa cả hai. Đây là
  **kiểm tra toàn vẹn, không phải ranh giới bảo mật**, và nó được dán nhãn đúng như vậy trong
  `UpdateDownloader.kt`.
- Muốn thành ranh giới thật thì phải ký digest ngoài luồng (minisign/GPG, public key nhúng
  trong app) — nâng cấp thật, chi phí thật; chưa đáng khi APK còn ký debug key.

### Điều khiển khác

- Redirect đi thủ công (`followRedirects(false)`): mỗi hop phải `https`, host khớp CHÍNH XÁC
  một trong `api.github.com`, `github.com`, `release-assets.githubusercontent.com`,
  `objects.githubusercontent.com`, port 443, tối đa 3 hop. Không phụ thuộc `usesCleartextTraffic`.
- `PackageInstaller` session + `setAppPackageName(packageName)` + `USER_ACTION_REQUIRED`
  (API 31+), `PendingIntent` explicit tới receiver `exported=false`. Không `<provider>`,
  không `<queries>`, không `FileProvider`.
- Tự kiểm tra thì có (12h/lần, jitter, ETag); **tự tải TẮT; tự cài KHÔNG BAO GIỜ**. Mỗi lần
  cài cần hai cú chạm của người dùng và một hộp thoại xác nhận của hệ thống.
- Pre-flight `getPackageArchiveInfo` (package / `versionCode` phải LỚN HƠN / signer phải trùng)
  là UX báo lỗi tiếng Việt, không phải kiểm soát bảo mật.

### Khóa ký — việc dự án PHẢI làm trước khi phát hành rộng

APK đang lưu hành ký bằng **debug key** (`CN=Android Debug`), keystore untracked và chỉ có
trên một máy.

- **Mất file ⇒ không bản nào cập nhật được nữa.** Người dùng phải gỡ (mất toàn bộ tài khoản
  đã lưu) và sideload tay.
- **Lộ file ⇒ kẻ tấn công ký được APK mà Android SẼ chấp nhận** làm bản cập nhật. Repo public
  hay private lúc đó không còn ý nghĩa; file là toàn bộ bí mật. Debug key cũng khiến APK
  `debuggable=true` ⇒ `adb run-as` đọc được mật khẩu plaintext.

Kế hoạch bắt buộc: tạo release keystore, backup hai nơi offline, cắt bản release kế tiếp bằng
khóa đó. Bản ký release key đầu tiên **không thể** self-update đè lên bản ký debug — biến nó
thành migration một lần có chủ đích (bản debug cuối cùng chỉ để xuất tài khoản ra file, rồi
gỡ → cài bản ký key → nhập lại). Sau đó không bao giờ đổi khóa.

### Canh commit keystore

`debug.keystore` / `*.jks` phải nằm trong `.gitignore`. Nếu ai đó commit chúng, cả kênh thành
giả mạo được và kiểm tra chữ ký lặng lẽ mất giá trị. Đề xuất: pre-commit/CI fail nếu có
`*.jks`/`*.keystore` được track.
