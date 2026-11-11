# Security review — v5.0.0

Đánh giá tĩnh cho mã nguồn tại tag `v5.0.0`, có kiểm chứng runtime trên một APK release thật.
Đây **không** phải pentest, không có kiểm thử trên máy chủ game.

> Tài liệu này thay thế bản đánh giá cũ cho `v3.0.1`. Bản cũ mô tả trạng thái mà mã hiện tại
> **không còn** — xem mục "Thay đổi bị revert" bên dưới.
>
> `v5.0.0` so với `v4.1.0`: ký bằng khoá release thật, build release không debuggable và đã
> rút gọn, `versionCode` theo công thức semver, nhánh `main` là nhánh phát hành duy nhất.
> Ba thay đổi đầu **đóng** một lỗi mức Cao; phần còn lại của bề mặt WebView không đổi.

## Trạng thái: CHƯA hardened

Ứng dụng dùng cá nhân. Không phân phối cho người khác ở trạng thái hiện tại.

## Findings

| Mức | Vấn đề | Vị trí | Hệ quả |
|---|---|---|---|
| Nghiêm trọng | `onReceivedSslError` gọi `handler?.proceed()` vô điều kiện | `GameTabManager.kt` | Chấp nhận chứng chỉ hết hạn, sai host hoặc tự ký. Người đứng giữa đường truyền đọc được mật khẩu và cookie phiên |
| Nghiêm trọng | `onPageFinished` bơm script điền mật khẩu vào bất kỳ trang nào tải xong khi hệ thống đang chạy và tab active | `GameTabManager.kt` | Một redirect, XSS trên origin tin cậy, hoặc URL tùy chỉnh do người khác đưa, sẽ nhận được mật khẩu đã điền |
| Cao | `addJavascriptInterface(..., "AndroidBridge")` không giới hạn origin, không bao giờ gỡ | `GameTabManager.kt` | JavaScript trong mọi frame gọi được callback: đổi trạng thái, ghi log, đăng nhập lại |
| Cao | Mật khẩu lưu plaintext trong `SharedPreferences` | `data/PreferencesManager.kt` | Root hoặc forensics đọc được toàn bộ. **Đã giảm nhẹ ở 5.0.0**: APK release không debuggable nên `adb run-as` không còn dùng được — nhưng thiết bị root vẫn đọc được |
| Trung bình | `mixedContentMode = MIXED_CONTENT_ALWAYS_ALLOW` | `GameTabManager.kt` | Tài nguyên HTTP bị chèn vào trang HTTPS |
| Trung bình | Dependency khai báo nhưng không dùng (Firebase AI, AppCheck, Room, Retrofit, Moshi, logging-interceptor) | `app/build.gradle.kts` | Bề mặt tấn công rộng hơn cần thiết. R8 đã loại khỏi APK (22,7 MB → 2,88 MB) nhưng vẫn nằm trong classpath lúc build |
| Thấp | Không certificate pinning | — | Phụ thuộc hoàn toàn vào CA tin cậy của hệ thống |
| Thấp | `POST_NOTIFICATIONS` khai báo nhưng không request runtime | `AndroidManifest.xml` | Trên API 33+, notification của foreground service bị ẩn — mất chỉ báo service đang chạy |
| Thấp | `REQUEST_INSTALL_PACKAGES` khai báo cho kênh tự cập nhật | `AndroidManifest.xml` | Không cho phép cài ngầm: mỗi lần vẫn cần người dùng chạm + hộp thoại hệ thống. Nhưng APK được cài là APK mà kênh tải về, và kênh đó tin hoàn toàn vào GitHub + chữ ký của chính APK |

### Đã đóng ở 5.0.0

| Vấn đề cũ | Cách đóng | Kiểm chứng |
|---|---|---|
| APK lưu hành là bản debug, ký `debug.keystore`, `debuggable=true` | Ký bằng khoá release thật; `isDebuggable = false` | `apksigner verify --print-certs` → `CN=Vo Lam Idle Manager`; `dumpsys package` → `flags=0x0`; manifest binary không còn attribute `debuggable` |
| `isMinifyEnabled = false` | Bật R8 + `shrinkResources` | APK 22.713.334 → 2.878.060 byte; app vẫn chạy đủ 4 tab trên máy thật |
| Khoá ký nằm rải rác, quyền đọc cho mọi tài khoản máy | ACL chỉ chủ sở hữu; bản sao khác ổ; mật khẩu tách khỏi tệp khoá | `icacls` trên cả ba tệp: chỉ `chu so huu`, không còn `BUILTIN\Users`/`Authenticated Users` |

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
7. ~~Ký release bằng upload key thật, `isDebuggable = false`, `isMinifyEnabled = true`, `shrinkResources = true`.~~ **Đã làm ở 5.0.0.**
8. Xóa dependency không dùng.
9. Thêm `network_security_config` nếu cần giới hạn CA.
10. Không dùng trên thiết bị dùng chung hoặc đã root.
11. Giữ khoá ký tách khỏi máy build khi có thể: một bản offline (USB mã hoá) và mật khẩu nằm ở nơi khác với tệp `.p12`.

Mục 1 và 2 là thay đổi nhỏ nhất mà loại bỏ được hai lỗi Nghiêm trọng.

## Phạm vi & giới hạn

- Đánh giá **tĩnh** trên mã nguồn, cộng kiểm chứng runtime trên LDPlayer 14 (Android 14 x86_64).
- Không đánh giá máy chủ game, không đánh giá rủi ro phía dịch vụ.
- Không đánh giá bản PC (`VOLAMIDLE-Manager`), vốn là dự án riêng.
- `assembleRelease` chạy được từ 5.0.0 (có `keystore.properties`). APK lưu hành là bản release đã ký.
- Chưa kiểm preflight của bộ cập nhật trên Android 7.0–8.1 thật; đã bật ký v1 để phòng.
- Báo cáo này đúng cho tag `v5.0.0`. Sửa mã thì phải đánh giá lại.

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

### Khóa ký — trạng thái từ 5.0.0

Từ `v5.0.0`, APK lưu hành được ký bằng khoá release thật:

| Trường | Giá trị |
|---|---|
| Tệp | `volamidle-release.p12` (PKCS12) — gitignored, ACL chỉ chủ sở hữu |
| Alias | `upload` |
| Khoá | RSA 4096, `SHA384withRSA` |
| Hiệu lực | 2026-11-11 → 2054-03-29 |
| DN | `CN=Vo Lam Idle Manager, OU=Release Signing, O=Thien Co Labs, C=VN` |
| SHA-256 cert | `3A:44:D8:D1:CA:33:4B:6E:49:7E:29:95:B9:70:5A:59:BE:B5:A2:AC:AE:25:BB:C3:1E:9F:72:16:BF:86:90:A7` |

Fingerprint trên là thông tin **công khai** — nó nằm trong mọi bản cài và `apksigner` in ra từ
dữ liệu công khai. Công bố nó là an toàn; biến nó thành pin cứng trong app thì **không**, vì
`ApkInspector` cố ý so với signer đang cài để còn xoay khoá theo lineage.

Bố trí hiện tại:

- Bản đang dùng: trong repo, gitignored, ACL chỉ chủ sở hữu.
- Bản sao: `o khac\` — **khác ổ vật lý** với bản đang dùng.
- Mật khẩu: `tep mat khau cuc bo` — **tách khỏi cả hai tệp khoá**.
- **Còn thiếu:** một bản offline (USB/ổ ngoài mã hoá). Chưa có thì rủi ro mất trộm/hỏng máy
  vẫn chưa được che.

- **Mất file ⇒ không bản nào cập nhật được nữa.** Người dùng phải gỡ (mất toàn bộ tài khoản
  đã lưu) và sideload tay.
- **Lộ file ⇒ kẻ tấn công ký được APK mà Android SẼ chấp nhận** làm bản cập nhật. Repo public
  hay private lúc đó không còn ý nghĩa; file là toàn bộ bí mật.
- Bản ký release key đầu tiên **không thể** cài đè lên bản ký debug — đó là migration một lần
  có chủ đích (xem README). Sau đó không bao giờ đổi khoá.

### Canh commit keystore

`*.jks` / `*.p12` / `keystore.properties` / `KEYSTORE-INFO.txt` nằm trong `.gitignore`.
Nếu ai đó commit chúng, cả kênh thành giả mạo được và kiểm tra chữ ký lặng lẽ mất giá trị.
Từ 5.0.0, CI có bước `git ls-files | grep -Ei '(\.jks$|\.p12$|\.pfx$|\.keystore$|keystore\.properties|KEYSTORE-INFO|MAT-KHAU)'`
và fail nếu khớp (`.github/workflows/ci.yml`).
