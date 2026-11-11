# Thiết kế: tự động cập nhật từ GitHub Releases

Spec triển khai. Đọc mục 8 (rủi ro tồn dư) trước khi code.

## 0. Trạng thái đã xác minh

| Sự kiện | Bằng chứng |
|---|---|
| Repo **PUBLIC** | `gh repo view` → `"visibility":"PUBLIC"` |
| Release mới nhất `v4.0.0`, asset `v4-app-debug.apk`, 22.543.887 byte, digest `sha256:7d9d77ad…` | `gh release view --json assets` |
| APK ký bằng **debug key** `CN=Android Debug` | `apksigner verify --print-certs` |
| `debug.keystore` **không** nằm trong repo (untracked) | `git ls-files \| grep -i keystore` → rỗng; `.gitignore:5-7` |
| Chưa có `REQUEST_INSTALL_PACKAGES`, chưa có `<provider>`, chưa có `PackageInstaller` | grep `app/src/main` → 0 hit |
| `usesCleartextTraffic="false"` | `AndroidManifest.xml:23` |
| OkHttp 4.10.0 + `androidx.core:core-ktx:1.18.0` có sẵn | `app/build.gradle.kts:127`, `libs.versions.toml` |
| Asset URL 302 → **`release-assets.githubusercontent.com`** (không phải `objects.githubusercontent.com`) | trace trực tiếp |
| Giới hạn API chưa xác thực: **60 request/giờ/IP** | response `X-RateLimit-Limit: 60` |

## 1. Mô hình mối đe dọa

Tài sản theo thứ tự giá trị: (a) code sẽ chạy với mật khẩu game của người dùng, (b) prefs chứa mật
khẩu plaintext, (c) tính liên tục phiên 24/7.

| # | Tấn công | Kiểm soát bắt buộc |
|---|---|---|
| T1 | MITM TLS trên `api.github.com`/`github.com` | OkHttp dùng system trust store; **mọi hop** phải `https:` + allowlist host **chính xác**; `followRedirects(false)` + tự xử lý hop có kiểm; không resume qua phiên. **Đường này độc lập với lỗi WebView** — `handler?.proceed()` chỉ ảnh hưởng điều hướng WebView, OkHttp tự verify. Điểm chung duy nhất: cùng system CA store. |
| T2 | Asset bị thay trên repo | Android kiểm chữ ký: khác khóa → `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Cùng khóa → cài được. Thêm pre-flight đọc `getPackageArchiveInfo` để báo lỗi tiếng Việt thay vì process bị kill. |
| T3 | Repo/account bị chiếm | Không có gì trong app chặn được. Kiểm soát khả dụng: kiểm chữ ký (T2); `USER_ACTION_REQUIRED` để người dùng luôn thấy hộp thoại hệ thống; **một tap mỗi lần cập nhật, không bao giờ im lặng**; theo dõi release feed; bật 2FA + hardware key cho GitHub. |
| T4 | Downgrade | Android từ chối `versionCode` thấp hơn (`INSTALL_FAILED_VERSION_DOWNGRADE`). **Nhưng `versionCode` BẰNG thì Android chấp nhận** (như `adb install -r`) ⇒ luật "phải lớn hơn" phải nằm trong code ta, không ở platform. |
| T5 | App độc trên máy kích hoạt installer | Không có bề mặt export: chỉ `MainActivity` exported và **không đọc tham số cài đặt từ `Intent`**; receiver `exported=false`; `PendingIntent` **explicit + `setPackage(packageName)`**; `setAppPackageName()` giới hạn session vào đúng package ta. |
| T6 | Path traversal qua tên file từ server | **Không dùng tên từ server làm đường dẫn.** Tên cố định `update.apk` / `update.apk.part` trong thư mục riêng; assert `canonicalPath.startsWith(dir.canonicalPath + sep)`; từ chối ghi đè symlink. |
| T7 | Cạn dung lượng (tải lặp, session rò) | Single-flight `Mutex`; trần 200 MB và ≤105% `assets[].size`; pre-check `StatFs`; xoá `.part` mọi nhánh lỗi; luôn `abandonSession` khi không commit; quét `cacheDir/updates/` lúc khởi động, xoá file > 24h; throttle kiểm tra tay 1/60s; không auto-retry vô hạn. |
| T8 | Nâng lên APK **khác khóa** (khi chuyển sang release key) | Không tránh được bằng code. Phải là **migration sản phẩm**: phát hiện mismatch pre-flight, hiện hướng dẫn gỡ + cài lại. |
| T9 | TOCTOU: file bị thay giữa verify và install | Giữ trong `cacheDir` (app-private), **không** `DownloadManager`/shared storage; verify SHA-256 rồi copy vào session ngay trong cùng coroutine, xoá file sau. |
| T10 | App độc chặn `PendingIntent` kết quả | Explicit component + `setPackage()` + receiver `exported=false`; chỉ đọc status từ `PackageInstaller.EXTRA_STATUS`. |
| T11 | MITM qua CA người dùng cài | `targetSdk 24+`: CA người dùng **không** được tin cho traffic app. Không thêm `network_security_config` nới trust, không tự viết `X509TrustManager`, **không pin** (pin cert GitHub xoay vòng = kênh chết vĩnh viễn). Trên máy root/userdebug, kiểm soát này mất — nói thẳng. |
| T12 | Quyền riêng tư: bản thân lần kiểm tra | GET không xác thực tiết lộ IP + việc app poll repo. Giảm bằng tần suất ≤1/ngày; không nhúng định danh thiết bị vào `User-Agent`. |

## 2. Android đã làm gì sẵn vs ta phải thêm

### 2.1 Chữ ký — có, và đây là **ranh giới thật**
Android so signer set của APK mới với app đang cài; lệch → `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
Không app nào vượt được bằng quyền `REQUEST_INSTALL_PACKAGES`.

Nhưng nó chỉ chứng minh "cùng khóa với bản đang cài". Bản đang cài ký bằng **debug key** mà keystore
chỉ nằm trên một máy ⇒ ai có file đó ký được APK mà Android chấp nhận. Đây là **sự thật trung tâm**
của thiết kế này (xem §8).

Ta vẫn thêm pre-flight (package/versionCode/signer) — chỉ để báo lỗi đúng, **không phải** ranh giới bảo mật.

### 2.2 Cài im lặng — bất khả thi với app thường
`INSTALL_PACKAGES` là `signature`-level, không cấp được. Chỉ có `PackageInstaller` + xác nhận của
người dùng trong UI hệ thống. Ta kiểm soát việc **buộc** phải xác nhận:
`SessionParams(MODE_FULL_INSTALL)` + `setAppPackageName(packageName)` +
`setRequireUserAction(USER_ACTION_REQUIRED)` (khai báo tường minh, để sau này không ai lật thành NOT_REQUIRED).

Luồng API 26→36 như nhau:
1. `createSession(params)` → `sessionId`
2. `openSession(sessionId).use { session.openWrite("base.apk",0,size).use { copy; session.fsync(out) } }`
3. `PendingIntent` **explicit** broadcast tới receiver nội bộ
4. `session.commit(pi.intentSender)`
5. `session.close()`
6. Receiver nhận `STATUS_SUCCESS`/`STATUS_FAILURE_*`

**`FLAG_MUTABLE` bắt buộc** (`targetSdk 36`): hệ thống ghi `EXTRA_STATUS`/`EXTRA_STATUS_MESSAGE` vào
base Intent; immutable ⇒ kết quả không tới được. An toàn vì base intent explicit + `setPackage()`.

Xử lý cả `STATUS_PENDING_USER_ACTION` (Intent nằm trong `PackageInstaller.EXTRA_INTENT`, chỉ
`startActivity` khi app đang visible).

### 2.3 `REQUEST_INSTALL_PACKAGES` và toggle runtime
- Quyền manifest là `normal`; cổng thật là AppOp `OP_REQUEST_INSTALL_PACKAGES` (mục "Cài đặt ứng dụng không rõ nguồn gốc").
- Kiểm: API ≥26 `packageManager.canRequestPackageInstalls()`; API 24–25 (`minSdk` = 24 nên **bắt buộc** có nhánh) `Settings.Secure.getInt(INSTALL_NON_MARKET_APPS) == 1`.
- Điều hướng: ≥26 `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` + `package:` URI; 24–25 `ACTION_SECURITY_SETTINGS`; bọc `try/catch ActivityNotFoundException`, fallback `ACTION_APPLICATION_DETAILS_SETTINGS` (MIUI/EMUI hay đổi tên màn hình).
- **Không có callback kết quả.** Kiểm lại trong `onResume`, tự tiếp tục hành động đang dở (`pending_action` lưu prefs). Không tin là người dùng đã bật.

### 2.4 `versionCode`
- Thấp hơn → từ chối. Bằng → **chấp nhận**. Cao hơn → cập nhật.
- Hệ quả: `versionCode` phải đơn điệu tăng **mãi mãi**. Release quên bump trông như "đã mới nhất".

### 2.5 Chi tiết phiên bản dễ vấp

| Hành vi | Từ | Ảnh hưởng | Xử lý |
|---|---|---|---|
| `PendingIntent` phải khai mutability | targetSdk ≥31 | Immutable → mất kết quả | `FLAG_MUTABLE or FLAG_UPDATE_CURRENT` |
| Dynamic receiver cần `RECEIVER_EXPORTED/NOT_EXPORTED` | ≥34 | `registerReceiver` ném | Ưu tiên `<receiver manifest exported=false>` |
| Implicit intent tới component non-exported bị chặn | ≥34 | mọi intent phải explicit + `setPackage` | đã bắt buộc |
| Hạn chế start Activity từ background | Android 10+ | `commit()` khi app không visible → im lặng không làm gì | **chỉ** start từ tap trong `MainActivity` đang STARTED; **không bao giờ** từ `KeepAliveService` |
| FGS start restriction | 12+/chặt hơn ở 15 | service không tự khởi động lại sau update | đừng dựa vào; dùng `ACTION_MY_PACKAGE_REPLACED` + notification "chạm để mở" |
| `QUERY_ALL_PACKAGES` | 11+/14+ | **Không cần** — `getPackageArchiveInfo` đọc file, đọc signer của chính mình không cần visibility | không thêm `<queries>` |
| Play Protect quét APK sideload/debuggable | mọi bản | hộp thoại "Ứng dụng không an toàn" | không bypass được; ghi README |

**`FileProvider` KHÔNG cần** nếu dùng `PackageInstaller`. Đừng thêm provider, đừng thêm `file_paths.xml`.
Mỗi provider là bề mặt phải cấp quyền; session API ghi bytes vào staging của installer, không đưa URI ra ngoài.

## 3. Giao thức kiểm tra

### 3.1 Endpoint
```
GET https://api.github.com/repos/gvhoang/volam-idle-manager/releases/latest
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2022-11-28
User-Agent: VOLAMIdleManager/<versionName> (Android; +https://github.com/gvhoang/volam-idle-manager)
```
Field cần: `tag_name`, `body`, `published_at`, `html_url`, `assets[].name`,
`assets[].browser_download_url`, `assets[].size`, `assets[].digest` (`"sha256:<hex>"` — đã có).
Dùng `digest` cho checksum, chỉ regex `body` nếu `digest` null.

`/releases/latest` xếp theo **thời gian tạo, không theo semver** ⇒ publish `v5.0.0` rồi `v4.1.0` thì
"latest" là bản **cũ**. Cổng `remoteVersionCode > BuildConfig.VERSION_CODE` biến việc đó thành no-op.

Chọn asset: đúng một asset tên cố định (`volamidle-manager.apk` từ nay); nếu không có, chỉ nhận khi
**đúng một** asset kết thúc `.apk`; từ hai trở lên → fail `"Không tìm thấy tệp APK trong bản phát hành"`,
không đoán.

### 3.2 Rate limit (60/giờ/IP — dùng chung sau CGNAT)
| Quy tắc | Giá trị |
|---|---|
| Sàn kiểm tra tự động | 1 lần / 12 giờ, jitter ±30 phút, lưu `update_last_check_attempt` |
| Conditional request | Lưu `ETag`, gửi `If-None-Match`; `304` → dùng cache |
| Kiểm tra tay | 1 lần / 60 giây, từ chối kèm thông báo nếu `X-RateLimit-Remaining == 0` |
| Backoff | tối đa 3 lần, 2s → 8s → 30s, **chỉ** với lỗi mạng/5xx |
| 403/429 | **Không retry.** Đọc `Retry-After` hoặc `X-RateLimit-Reset`, lưu `update_blocked_until` |
| Trần thực tế | 2 request/ngày ≈ 0,3% hạn mức. Đây mới là kiểm soát thật. |

### 3.3 Phiên bản — luật sống sót khi đổi scheme tag
```
1. codeHint  = body khớp /version(?:\s|-)?code[^0-9]{0,16}(\d+)/i
2. tagTriple = tag khớp /^v?(\d+)\.(\d+)(?:\.(\d+))?(?:[-+].*)?$/i → major*10000+minor*100+patch
3. chọn: codeHint nếu có và nhất quán; codeHint nếu tag không đọc được; candidate nếu body không có hint; ngược lại UNPARSED
```
- **Template release phải ghi `versionCode` tường minh** (body v4.0.0 đã ghi). Nhờ đó đổi scheme tag không phá gì.
- Từ v5: `versionCode` **phải** = `major*10000+minor*100+patch`. Thêm unit test lúc release:
  `BuildConfig.VERSION_CODE == parse(BuildConfig.VERSION_NAME)` → biến lỗi đánh số thành build đỏ.
  Migration một bước: `4 → 5.0.0 → 50000` vẫn đơn điệu.
- Tag không đọc được **và** không có hint → `UNPARSED`: hiện `"Không đọc được số phiên bản của bản phát hành mới."` + nút mở `html_url`. **Không tự tải.**
- So sánh tuple, không so chuỗi. `remoteVersionCode <= BuildConfig.VERSION_CODE` → `UpToDate`.
- Giá trị suy từ tag **không bao giờ** dùng để quyết định cài: sau khi tải, `versionCode` đọc từ chính APK phải lớn hơn, nếu không huỷ.

### 3.4 Chạy ở đâu, khi nào
**Khuyến nghị: tự kiểm tra lúc mở app + nút thủ công. KHÔNG BAO GIỜ tự tải.**
- App 24/7 ít khi khởi động lại ⇒ kiểm lúc cold start (sau frame đầu) **và** mỗi 12 giờ từ coroutine
  cấp process. Không dùng WorkManager (thêm dependency + IPC surface cho một file 22 MB người dùng vẫn phải duyệt).
- `UpdateChecker` là `suspend fun`, OkHttp blocking `execute()` trong `withContext(Dispatchers.IO)`,
  `callTimeout(8s)`.
- **Không** trong `onCreate`, không trên main thread. Khởi từ `LaunchedEffect(Unit) { delay(3_000); … }`
  để WebView được CPU trước.
- Không chạy khi đang tải (single `Mutex`).
- Lỗi nền im lặng trong UI (chỉ log) — lỗi mạng nền không được bung dialog đè game.

## 4. Tải về

### 4.1 Host và redirect — cái bẫy
Trace thực tế:
```
GET https://github.com/…/releases/download/v4.0.0/v4-app-debug.apk
302 → https://release-assets.githubusercontent.com/github-production-release-asset/… (ký, hết hạn)
200  Content-Length: 22543887
```
⇒ `assert(host == "github.com")` **fail**, còn `followRedirects(true)` thì tin bất cứ đâu GitHub trỏ tới.

- `OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)`
- Vòng hop tay, **tối đa 3 hop**, mỗi hop phải:
  - `scheme == "https"`
  - `host` ∈ đúng `{"api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"}` — **khớp chính xác, không `endsWith`, không wildcard**
  - port mặc định 443 hoặc vắng
- Response cuối phải `200`; `Content-Type` nên là `application/vnd.android.package-archive` (log nếu lệch, đừng fail cứng — CDN hay viết lại).
- Verify **URL cuối**, log cả host đầu và cuối (hữu ích khi GitHub đổi CDN).

**Không pin cert** — xoay vòng sẽ giết kênh vĩnh viễn. Ranh giới thật là chữ ký APK.

### 4.2 Đích, tên, trần
- `File(context.cacheDir, "updates")`, `mkdirs()`, assert `canonicalPath`. **`cacheDir`** vì file chỉ là
  trung gian; app-private; `allowBackup=false` nên không vào backup. Rủi ro chấp nhận: hệ thống có thể
  xoá `cacheDir` khi thiếu chỗ — cửa sổ chỉ vài giây (tải → verify → ghi session → xoá).
- Tên: hằng `update.apk`, partial `update.apk.part`. Tên từ server chỉ để hiển thị.
- Trần: `MAX_DOWNLOAD_BYTES = 200 MB`; huỷ nếu streamed > `1.05 × assets[].size`;
  pre-check `StatFs(...).availableBytes >= max(3×size, 100 MiB)`; **đếm byte khi stream**, không tin `Content-Length`.
- HTTPS-only: manifest đã `usesCleartextTraffic="false"` nhưng **đừng dựa vào** — kiểm `scheme` từng hop là kiểm soát thật. Không thêm `network_security_config` (WebView cố ý cho mixed content; config toàn cục rủi ro regression).
- **Không** `DownloadManager` (ghi shared storage, app khác thay được file, không ép được allowlist).

### 4.3 Checksum — làm, nhưng dán nhãn đúng
Verify SHA-256 (từ `assets[].digest`, fallback `body`), tính **trong cùng vòng đọc** với lúc ghi.

**Nói thẳng trong comment code và `SECURITY.md`:** digest lấy từ **cùng nguồn** với APK ⇒ bắt được hỏng
hóc (CDN cắt cụt, proxy lỗi, lỗi lưu trữ) và **không** bắt được kẻ sửa release (hắn sửa cả hai).
Đây là **kiểm tra toàn vẹn, không phải ranh giới bảo mật**. Ranh giới là chữ ký APK.
Giữ vì hỏng file cho trải nghiệm tệ nhất (process bị kill + mã khó hiểu) và chỉ ~15 dòng.

Muốn thành ranh giới thật phải ký digest ngoài luồng (minisign/GPG, public key nhúng trong app) —
nâng cấp thật, chi phí thật; chưa đáng khi APK còn ký debug key.

### 4.4 Ngắt / resume / dọn
- **Không resume.** URL redirect là signed, hết hạn ~1 giờ. Mọi ngắt → xoá `.part`, tải lại từ 0 (tối đa 2 lần/lần bấm).
- Ghi `update.apk.part` → `fsync()` → verify SHA-256 → `renameTo(update.apk)`. Tên cuối chỉ chứa file đã verify.
- `finally`: đóng cả hai stream; mọi exception đều xoá `.part`.
- Quét lúc khởi động: xoá file trong `cacheDir/updates/` có `lastModified` > 24h và mọi `.part`.
- Sau `commit()` thành công: xoá `update.apk` ngay (installer đã copy sang staging).

## 5. Cài đặt

### 5.1 Luồng chính xác
```kotlin
// 0. Điều kiện, theo thứ tự
//    BuildConfig.VERSION_CODE < remote.versionCode
//    canRequestPackageInstalls() (≥26) / INSTALL_NON_MARKET_APPS (24-25)
//    dung lượng trống, kiểm tra archive (§5.2)

val installer = context.packageManager.packageInstaller
val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
params.setAppPackageName(context.packageName)
params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
params.setSize(apkFile.length())
val sessionId = installer.createSession(params)

installer.openSession(sessionId).use { session ->
    session.openWrite("base.apk", 0, apkFile.length()).use { out ->
        apkFile.inputStream().use { it.copyTo(out, 64 * 1024) }
        session.fsync(out)
    }
    session.commit(pendingIntent.intentSender)
}

val base = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
val pendingIntent = PendingIntent.getBroadcast(
    context, 0, base,
    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
)
```
Nghĩa vụ nhánh lỗi: `createSession` xong mà không `commit` → `abandonSession(sessionId)`.
`openSession`/`commit` ném `SecurityException` khi AppOp bị từ chối → điều hướng sang §2.3.

Manifest thêm:
```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
<receiver android:name=".update.InstallResultReceiver" android:exported="false" />
<receiver android:name=".update.PackageReplacedReceiver" android:exported="false">
    <intent-filter><action android:name="android.intent.action.MY_PACKAGE_REPLACED"/></intent-filter>
</receiver>
```
Không `<provider>`, không `<queries>`.

### 5.2 Pre-flight archive (UX, không phải ranh giới)
```kotlin
val archive = packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags) ?: fail("APK_INVALID")
require(archive.packageName == context.packageName)
require(versionCodeOf(archive) > BuildConfig.VERSION_CODE)
require(signerSha256(archive) == signerSha256(installedSelf()))
```
API <28 dùng `PackageInfo.signatures` (deprecated), ≥28 `signingInfo.apkContentsSigners`.
So với **app đang cài lúc chạy**, không hardcode fingerprint: pin cứng sẽ phải được cập nhật bởi chính app
không cập nhật được khi đổi khóa; so runtime thì vẫn đúng qua migration và **fail to** (báo rõ) khi lệch.

### 5.3 Vì sao không dùng ACTION_VIEW / file:// / content URI
| Mẫu | Lý do loại |
|---|---|
| `ACTION_VIEW` + `file://` | `FileUriExposedException` từ targetSdk 24 |
| `ACTION_INSTALL_PACKAGE` | Deprecated từ API 29; không `setAppPackageName`; hành vi khác theo OEM |
| `ACTION_VIEW` + `content://` + `FileProvider` | Grant tới **mọi** activity khớp intent — kể cả app độc đăng ký `application/vnd.android.package-archive`, nó giữ handle đọc APK và tự dựng UI; lại cần provider exported-when-granted ⇒ thêm bề mặt vĩnh viễn |

Chỉ dùng fallback này nếu OEM nào đó hỏng session API; khi đó provider phải là
`${applicationId}.updates`, `exported="false"`, `grantUriPermissions="true"`, `<cache-path name="updates" path="updates/"/>`.
`root-path`/`external-path` là bug (lộ cả `cacheDir` cha hoặc shared storage).

### 5.4 Điều gì xảy ra với app khi cài
Process **bị kill**. WebView, FGS, notification chết theo. `START_STICKY` không đáng tin khi package bị replace.
⇒ Chỉ cài từ tap trong `ControlSheet` đang mở — không từ service, không từ timer nền, không tự động.
Cảnh báo ngay trước `commit()`: `"Cài đặt sẽ đóng ứng dụng. Các tab game sẽ tải lại."`
(tài khoản nằm trong prefs và injector điền lại khi reload ⇒ đây là reload, không phải mất dữ liệu).
`MY_PACKAGE_REPLACED` → notification `"Đã cập nhật lên X.Y.Z — chạm để mở"`. Không auto-start Activity (BAL chặn).

### 5.5 Hiện lý do thất bại
Receiver đọc `EXTRA_STATUS`, `EXTRA_STATUS_MESSAGE`, `EXTRA_SESSION_ID`.
**Lưu `status|message|versionName|timestamp` vào prefs và đọc lại lần mở sau** — kết quả thường tới ở process mới.

| Điều kiện | Tiếng Việt |
|---|---|
| `STATUS_SUCCESS` | `Đã cập nhật lên {version}.` |
| `STATUS_PENDING_USER_ACTION` | mở `EXTRA_INTENT` (chỉ khi foreground) |
| `STATUS_FAILURE_ABORTED` | `Bạn đã huỷ cài đặt.` |
| `STATUS_FAILURE_BLOCKED` | `Hệ thống hoặc Play Protect đã chặn cài đặt.` |
| `STATUS_FAILURE_CONFLICT` | `Xung đột gói: đã có ứng dụng khác cùng tên.` |
| `STATUS_FAILURE_INCOMPATIBLE` hoặc message chứa `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | `Không cài được: bản cập nhật được ký bằng khóa khác với bản đang cài. Phải gỡ ứng dụng rồi cài lại — toàn bộ tài khoản đã lưu sẽ mất.` |
| message chứa `INSTALL_FAILED_VERSION_DOWNGRADE` | `Không cài được: bản cập nhật không mới hơn bản đang dùng.` |
| `STATUS_FAILURE_INVALID` | `Tệp APK không hợp lệ hoặc bị hỏng.` |
| `STATUS_FAILURE_STORAGE` | `Không đủ dung lượng để cài đặt.` |
| `STATUS_FAILURE_TIMEOUT` | `Hết thời gian chờ cài đặt.` |
| khác | `Cài đặt thất bại ({status}).` + raw message trong dòng chi tiết |

Kiểm chuỗi trên `EXTRA_STATUS_MESSAGE` là nhánh tin cậy (cùng một `INSTALL_FAILED_*` có thể hiện thành
`INVALID` hay `INCOMPATIBLE` tuỳ phiên bản); enum status là fallback.

## 6. UX

### 6.1 Vị trí
`ControlSheet`, chèn hàng mới **sau nút mở drawer**, trước dấu đóng block — đó là vùng cấp app
(không phải per-tab), đúng chỗ cho hàng phiên bản.

Tham số mới: `appVersionName`, `updateState`, `onCheckUpdate`, `onStartDownload`, `onInstallUpdate`, `onOpenReleasesPage`.

`UpdateUiState`: `Idle | Checking | UpToDate(version) | Available(version, size, notes) | Downloading(bytes,total) | Verifying | ReadyToInstall(version) | NeedsPermission | Installing | Failed(reason) | NoNetwork | RateLimited(retryAt)`.

Hàng cao **40dp**, dùng `SheetButton` hiện có (**không** dùng Material `Button` — nó ép 48dp).
Trái: `📦 Phiên bản: {ver}`. Phải: một target 40dp, nhãn/action theo state.

### 6.2 Chuỗi tiếng Việt (chính xác)

| Key | Chuỗi |
|---|---|
| `update_version_row` | `📦 Phiên bản: %1$s` |
| `update_btn_check` | `Kiểm tra cập nhật` |
| `update_state_checking` | `Đang kiểm tra…` |
| `update_state_uptodate` | `Đã là bản mới nhất` |
| `update_state_available` | `Có bản mới %1$s • %2$s` |
| `update_btn_download` | `Tải về` |
| `update_state_downloading` | `Đang tải… %1$d%%` |
| `update_state_verifying` | `Đang kiểm tra tệp…` |
| `update_btn_install` | `Cài đặt` |
| `update_state_needs_permission` | `Cần bật "Cài đặt ứng dụng không rõ nguồn gốc" cho ứng dụng này` |
| `update_btn_open_settings` | `Mở cài đặt` |
| `update_state_installing` | `Đang mở trình cài đặt…` |
| `update_state_nonetwork` | `Không có mạng. Thử lại sau.` |
| `update_state_ratelimited` | `GitHub đang giới hạn truy cập. Thử lại sau ít phút.` |
| `update_state_unparsable` | `Không đọc được số phiên bản của bản phát hành mới.` |
| `update_btn_open_releases` | `Mở trang phát hành` |
| `update_banner_available` | `Bản %1$s đã có • Cập nhật` |
| `update_confirm_title` | `Cài bản %1$s?` |
| `update_confirm_body` | `Dung lượng %1$s. Cài đặt sẽ đóng ứng dụng, các tab game sẽ tải lại.` |
| `update_confirm_ok` | `Cài đặt` |
| `update_confirm_cancel` | `Để sau` |
| `update_error_sha` | `Tệp tải về bị lỗi (sai SHA-256). Đã xoá, thử tải lại.` |
| `update_error_storage` | `Không đủ dung lượng trống (cần khoảng %1$s).` |
| `update_error_host` | `Máy chủ tải về không hợp lệ.` |
| `update_error_no_asset` | `Bản phát hành không kèm tệp APK.` |
| `update_error_signature` | `Không cài được: bản cập nhật được ký bằng khóa khác với bản đang cài. Phải gỡ ứng dụng rồi cài lại — toàn bộ tài khoản đã lưu sẽ mất.` |
| `update_error_downgrade` | `Không cài được: bản cập nhật không mới hơn bản đang dùng.` |
| `update_error_invalid_apk` | `Tệp APK không hợp lệ hoặc bị hỏng.` |
| `update_error_blocked` | `Hệ thống hoặc Play Protect đã chặn cài đặt.` |
| `update_error_aborted` | `Bạn đã huỷ cài đặt.` |
| `update_error_generic` | `Cài đặt thất bại (%1$s).` |
| `update_banner_updated` | `Đã cập nhật lên %1$s — chạm để mở` |
| `cd_update_check` | `Kiểm tra cập nhật` |
| `cd_update_download` | `Tải bản cập nhật` |
| `cd_update_install` | `Cài bản cập nhật` |

### 6.3 Nhắc không chặn
- Cold start, sau khi check trả `Available`: banner **32dp** (cùng trọng lượng thị giác với dải trạng thái),
  `Bản 5.0.0 đã có • Cập nhật`, tap → mở `ControlSheet`; tự ẩn sau 8s. Không dialog, không cướp focus.
- **Không** modal đè game. Modal duy nhất là xác nhận cài, do người dùng bấm.
- Ẩn banner khi `isSleepMode` (màn hình cố ý đen 0.01).
- Banner tối đa 1 lần / `versionCode` (lưu `update_banner_shown_for`).
- Lỗi check nền im lặng; chỉ check tay mới hiện thông báo.

## 7. Bảng lỗi

| # | Triệu chứng | Nguyên nhân | Người dùng thấy | Xử lý |
|---|---|---|---|---|
| 1 | 403/429 | hết 60/giờ, NAT chung | `GitHub đang giới hạn truy cập…` | lưu `retryAt`; không retry tới hạn; check tay vẫn throttle |
| 2 | fail ngay | không mạng/captive portal | `Không có mạng. Thử lại sau.` | im lặng nếu tự động |
| 3 | 404 | chưa có release / repo đổi tên | `Không tìm thấy bản phát hành` | kiểm hằng repo; log body |
| 4 | 200 nhưng không có `.apk` | release chỉ có notes / asset tách | `Bản phát hành không kèm tệp APK.` | không đoán |
| 5 | tag không đọc được | đổi scheme tag | `Không đọc được số phiên bản…` | mở browser; không tự tải |
| 6 | redirect host lạ | CDN xoay/proxy/tấn công | `Máy chủ tải về không hợp lệ.` | fail closed; log host đầu+cuối |
| 7 | tải dừng giữa | đổi mạng/CDN timeout | `Đang tải…` reset | xoá `.part`, tối đa 2 lần/lần bấm |
| 8 | SHA lệch | response hỏng | `Tệp tải về bị lỗi (sai SHA-256)…` | xoá, retry 1 lần, rồi link release |
| 9 | "Ứng dụng không an toàn" | Play Protect quét APK debug sideload | dialog hệ thống | người dùng bấm "Vẫn cài"; ghi README |
| 10 | Bấm Cài đặt không gì xảy ra | start khi app không visible (BAL) | `Đang mở trình cài đặt…` mãi | chỉ cho từ `MainActivity` STARTED; timeout state sau 20s → `Failed(generic)` |
| 11 | `SecurityException` khi `createSession` | AppOp bị từ chối | `Cần bật "Cài đặt ứng dụng không rõ nguồn gốc"…` | điều hướng §2.3, lưu `pending_action`, resume `onResume` |
| 12 | `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | khác khóa ký | chuỗi signature (§5.5) | giải thích rõ mất tài khoản; link release; không retry |
| 13 | `INSTALL_FAILED_VERSION_DOWNGRADE` | release quên bump | `Không cài được: … không mới hơn…` | cổng của ta phải bắt trước — log là lỗi release |
| 14 | `STATUS_FAILURE_STORAGE` | hết chỗ / session rò | `Không đủ dung lượng để cài đặt.` | quét cache, abandon session mồ côi |
| 15 | `cacheDir` bị xoá giữa luồng | áp lực lưu trữ | `Tệp APK không hợp lệ hoặc bị hỏng.` | tải lại (file là phù du) |
| 16 | `cacheDir` đầy APK cũ | lần chạy bị ngắt | (không có UI) | quét 24h + xoá sau commit |
| 17 | Bấm Cài đặt nhiều lần → session mồ côi | double-tap | staging đầy dần | `Mutex` single-flight + disable nút khi `Installing` |
| 18 | Dialog installer khác trên MIUI/EMUI | skin hãng | thêm bước tiếng Trung/Anh, MIUI có thể từ chối | ghi tài liệu; giữ `setRequireUserAction`; fallback §5.3 chỉ khi session fail lặp lại |
| 19 | Cài xong app không chạy | process bị kill, FGS không restart | không gì tới khi mở lại | notification `MY_PACKAGE_REPLACED` |
| 20 | `versionCode` bằng sau khi tải | "latest" cũ / tag sai | `Không cài được: … không mới hơn…` | huỷ trước khi tạo session (kiểm archive) |

## 8. Rủi ro tồn dư — nói thẳng

1. **Cả kênh dựa vào một file.** Bảo đảm duy nhất của self-update là "APK mới cùng khóa với bản đang cài".
   Bản đang cài ký bằng `CN=Android Debug` từ `debug.keystore`, file **untracked** và chỉ có trên một máy.
   - Mất file ⇒ **không bản nào cập nhật được nữa**. Mọi người dùng phải gỡ (mất toàn bộ tài khoản đã lưu) và sideload tay.
   - Lộ file ⇒ kẻ tấn công ký được APK mà Android **sẽ** chấp nhận làm bản cập nhật. Repo public hay private lúc đó không còn ý nghĩa; file là toàn bộ bí mật.
   - Debug key cũng ⇒ `debuggable=true` ⇒ `adb run-as` đọc được mật khẩu plaintext.

   **Việc dự án BẮT BUỘC làm trước khi ship tính năng này:** tạo release keystore, backup hai nơi offline,
   ghi vào `SECURITY.md`, và cắt bản release kế tiếp bằng khóa đó. Chấp nhận: bản ký release key đầu tiên
   **không thể** self-update đè lên bản ký debug (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Biến nó thành
   migration một lần có chủ đích: ship một bản ký debug cuối cùng mà tính năng duy nhất là **xuất tài khoản
   ra file**, rồi hướng dẫn gỡ → cài bản ký key → nhập lại. Sau đó **không bao giờ đổi khóa**. Đừng "để sau";
   mỗi release làm bán kính ảnh hưởng rộng thêm.

2. **Repo public + account GitHub bị chiếm = RCE trên mọi máy đã cài.** Kiểm soát thật theo thứ tự giá trị:
   (a) khóa ký riêng mà kẻ tấn công không có — **kiểm soát duy nhất** chặn được asset độc cùng repo, và hiện **chưa có**;
   (b) không bao giờ cài mà không có tap, để người dùng ít nhất thấy hộp thoại hệ thống;
   (c) theo dõi release feed (email GitHub) để phát hiện release lạ;
   (d) bật 2FA + hardware key cho GitHub — rẻ, hiệu quả, và là gốc tin cậy thật của kênh phân phối.

3. **Auto-update phải TẮT mặc định. Khuyến nghị: tự kiểm tra BẬT (có rate limit, im lặng), tự tải TẮT, tự cài KHÔNG BAO GIỜ.**
   App giữ mật khẩu game plaintext và tự điền vào trang. Một lỗi "cập nhật im lặng" không phải "app khởi động lại"
   mà là "ai kiểm soát kênh cập nhật lấy được mọi tài khoản". Khi APK còn ký debug key, ranh giới chữ ký
   chưa thật ⇒ chưa đủ tư cách cho tự động nhiều hơn. Người dùng muốn ít ma sát hơn có thể tắt kiểm tra trong prefs;
   xác nhận cài là không thương lượng.

4. **Pin TLS không phải kiểm soát ở đây.** Kênh tin system CA store như mọi app. Trên máy root/userdebug có CA
   của kẻ tấn công, transport bị xâm phạm và chỉ còn chữ ký APK — vòng lại về §8.1.

5. **Checksum là toàn vẹn, không phải xác thực.** `assets[].digest` cùng nguồn với APK. Muốn chống giả mạo
   thật cần chữ ký tách rời trên digest, verify bằng public key nhúng trong app. Đáng làm khi đã có release key
   và CI; chưa đáng trước đó.

6. **Chuỗi cung ứng ngoài phần update code.** `isMinifyEnabled=false`, `debuggable=true`, không CI, tag
   `v4.0.0` nằm trên nhánh `v3` trong khi default là `main`, khóa ký máy cục bộ. Cơ chế update không sửa
   được gì trong số này; nó chỉ làm hậu quả lan nhanh hơn.

7. **Cần canh chuyện commit keystore.** `debug.keystore` trong `.gitignore` là đúng. Nếu ai đó commit nó
   (hoặc `my-upload-key.jks`), cả kênh thành giả mạo được và kiểm tra chữ ký lặng lẽ mất giá trị.
   Đề xuất: pre-commit/CI fail nếu có `*.jks`/`*.keystore` được track, + một dòng trong `SECURITY.md`.

## 9. Danh sách file

| File | Thay đổi |
|---|---|
| `app/src/main/AndroidManifest.xml` | `REQUEST_INSTALL_PACKAGES`; `.update.InstallResultReceiver` (`exported=false`); `.update.PackageReplacedReceiver` + `MY_PACKAGE_REPLACED`; `android:name=".VolamIdleApp"` |
| `…/update/UpdateRepository.kt` | `checkForUpdate()`, `downloadApk()`, `installApk()`, `Mutex`, persist prefs |
| `…/update/UpdateChecker.kt` | OkHttp `followRedirects(false)`, validate hop, map `UpdateRelease`, luật version (§3.3) |
| `…/update/UpdateDownloader.kt` | `.part` → fsync → SHA-256 → rename; trần size/free-space; quét khởi động |
| `…/update/ApkInstaller.kt` | session, `setAppPackageName`, `USER_ACTION_REQUIRED`, `FLAG_MUTABLE`, `abandonSession` |
| `…/update/ApkInspector.kt` | `getPackageArchiveInfo` → package/versionCode/signer, nhánh API 24/28 |
| `…/update/InstallResultReceiver.kt` | status + message → persisted result (§5.5) |
| `…/update/UpdateState.kt` | sealed `UpdateUiState` + map chuỗi |
| `…/VolamIdleApp.kt` | `processScope`, singleton repository, vòng 12h |
| `…/ui/MainViewModel.kt` | `updateState: StateFlow<UpdateUiState>` + handlers |
| `…/ui/components/ControlSheet.kt` | tham số mới; hàng chèn sau nút drawer |
| `…/MainActivity.kt` | wire tham số; `onResume` kiểm lại quyền + resume `pending_action` |
| `app/src/main/res/values/strings.xml` | chuỗi §6.2 |
| `SECURITY.md` | chính sách khóa ký, kế hoạch migration, "checksum chỉ là toàn vẹn", canh commit keystore |

**Test nên viết** (mỗi cái là verification cho một kiểm soát): allowlist từ chối redirect ngoài GitHub;
so sánh version theo tuple và `4.10.0 > 4.9.0`; `parse(tag)` khớp `BuildConfig.VERSION_CODE` lúc release;
downloader dừng ở trần và xoá `.part`; SHA lệch xoá file; installer từ chối `versionCode <=` trước khi tạo session;
`InstallResultReceiver` map `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; không component nào ngoài `MainActivity` exported.
