# Runbook phát hành — Võ Lâm Idle Manager

Tài liệu này là quy trình phát hành đầy đủ. Làm theo thứ tự, không bỏ bước.
Mọi lệnh dưới đây chạy trong Git Bash tại gốc repo, trừ khi ghi rõ khác.

Thông số nền:

| Hạng mục | Giá trị |
| --- | --- |
| Repo | `gvhoang/volam-idle-manager` (public) |
| Package | `com.aistudio.volamidle.qkrvtp` |
| Gradle wrapper | 9.3.1 |
| AGP / Kotlin | 9.1.1 / 2.2.10 |
| JDK build (toolchain) | 25 — pin trong `gradle/gradle-daemon-jvm.properties`, tải bởi foojay resolver |
| compileSdk / targetSdk / minSdk | 36 / 36 / 24 |
| build-tools | 36.0.0 |
| Unit test | 23 test, `./gradlew :app:testDebugUnitTest` |
| Lint | `./gradlew :app:lintDebug` |

---

## 1. Mô hình nhánh

- **`main` là nhánh chuẩn duy nhất.** Mọi thay đổi vào qua pull request nhắm `main`.
  CI (`.github/workflows/ci.yml`) chạy trên mỗi push và PR vào `main`.
- **Tag là mốc phát hành bất biến.** Mỗi bản phát hành là một tag annotated `vX.Y.Z`
  trỏ đúng commit đã phát hành. Không di chuyển, không xoá, không tạo lại tag đã publish.
  Nếu bật "immutable releases" trên GitHub thì tag và asset bị khoá sau khi publish —
  đó là hành vi mong muốn.
- **`v3` đã nghỉ hưu sau 5.0.0.** Không merge, không push, không tạo tag từ `v3` nữa.
  Giữ nguyên lịch sử để tra cứu; mọi việc mới đi qua `main`.
- Trước 5.0.0, `main` còn dừng ở dòng 2.0.0. Sau khi 5.0.0 phát hành, `main` là dòng sống
  duy nhất; đừng phát hành từ bất kỳ nhánh nào khác.

Quy tắc: **một tag = một APK = một digest**. Không upload lại asset cho tag đã publish.

---

## 2. Quy ước phiên bản

### 2.1 Công thức

```
versionCode = major * 10000 + minor * 100 + patch
```

Ví dụ:

| versionName | versionCode |
| --- | --- |
| 5.0.0 | 50000 |
| 5.0.1 | 50001 |
| 5.1.0 | 50100 |
| 6.0.0 | 60000 |

`minor` và `patch` phải **≤ 99**. Bộ parse tag của app từ chối giá trị lớn hơn
(`VersionRules.versionCodeFromTag` trả `null`), nên vượt ngưỡng đó là release không
tự cập nhật được.

### 2.2 Nơi khai báo

`app/build.gradle.kts`, trong `android.defaultConfig`:

```kotlin
versionCode = 50000
versionName = "5.0.0"
```

Hai giá trị này là nguồn duy nhất. Không sửa `BuildConfig` bằng tay, không đặt version
trong `gradle.properties`.

### 2.3 Vì sao phải tăng đơn điệu

- Android từ chối cài APK có `versionCode` thấp hơn bản đang cài
  (`INSTALL_FAILED_VERSION_DOWNGRADE`).
- `versionCode` **bằng** nhau thì Android vẫn chấp nhận, nhưng updater trong app thì không:
  nó chỉ báo có bản mới khi `remote.versionCode > BuildConfig.VERSION_CODE`.
  Quên bump ⇒ người dùng thấy "đã là mới nhất" mãi mãi, và không có cách sửa ngoài việc
  phát hành số cao hơn.
- Vì vậy: mỗi lần phát hành, kể cả bản chỉ sửa docs, **phải** tăng `versionCode`.

### 2.4 Updater đọc version như thế nào

App gọi `GET https://api.github.com/repos/gvhoang/volam-idle-manager/releases/latest`
(không xác thực, chỉ đọc). Cách nó quyết định:

1. **Body release trước.** `VersionRules.versionCodeFromBody` tìm regex
   `version[\s-]?code[^0-9]{0,16}(\d+)` (không phân biệt hoa thường) và lấy số đầu tiên.
   Vì vậy body **phải** có một dòng đúng dạng `versionCode: 50000`, đặt ở đầu ghi chú.
2. **Tag là fallback.** Nếu body không có, nó parse tag theo
   `^v?(\d+)\.(\d+)(?:\.(\d+))?(?:[-+].*)?$` rồi áp công thức trên.
3. **Không parse được ⇒ `Unparsable`.** App không tự tải; chỉ hiện thông báo và nút mở
   trang release.

Tên hiển thị lấy từ tag, bỏ tiền tố `v` (`v5.0.0` → `5.0.0`).

Sau khi tải xong, app còn kiểm tra thêm trên chính tệp APK:

- `versionCode` trong APK phải **lớn hơn** bản đang cài;
- chữ ký của APK phải trùng với chữ ký của app đang cài.

Sai một trong hai ⇒ huỷ trước khi tạo session cài. Đây là lý do không được ký lại cùng
`versionCode` bằng nội dung khác.

### 2.5 Tên asset

Updater ưu tiên asset tên chính xác **`volamidle-manager.apk`**. Nếu không có tên đó,
nó chỉ chấp nhận khi release có **đúng một** tệp `.apk`. Từ hai tệp `.apk` trở lên là fail
(không đoán). Vì vậy luôn đặt tên tệp là `volamidle-manager.apk` trước khi upload.

`gh release create ... file#label` chỉ đặt **nhãn hiển thị**, không đổi tên asset.
Muốn asset tên đúng thì tệp trên đĩa phải tên đúng.

---

## 3. Giữ khóa ký (key custody)

### 3.1 Vị trí

| Tệp | Vị trí | Nội dung |
| --- | --- | --- |
| `keystore.properties` | gốc repo | `storeFile`, `storePassword`, `keyAlias`, `keyPassword` |
| `volamidle-release.p12` | gốc repo (theo `storeFile`) | keystore PKCS12 |

Thông số khóa:

- Alias: `upload`
- Thuật toán: RSA 4096
- Chứng chỉ SHA-256:
  `3A:44:D8:D1:CA:33:4B:6E:49:7E:29:95:B9:70:5A:59:BE:B5:A2:AC:AE:25:BB:C3:1E:9F:72:16:BF:86:90:A7`

Cả hai tệp đều nằm trong `.gitignore` (`keystore.properties`, `*.p12`). **Không bao giờ
commit, không dán vào chat, không đưa lên CI, không đính kèm issue.**

### 3.2 Mất khóa

Mất khóa = **không bao giờ phát hành bản cập nhật cho người dùng hiện tại được nữa.**
Android xác định app bằng package + chữ ký. Khóa mới là một danh tính khác: mọi bản cài
cũ phải gỡ rồi cài lại, mất toàn bộ dữ liệu cục bộ (xem §9). Không có quy trình khôi phục,
không có "xin lại khóa" từ Google hay GitHub.

### 3.3 Lộ khóa

Lộ khóa = **bất kỳ ai cũng ký được một APK mà updater trong app sẽ chấp nhận và cài**,
vì updater chỉ kiểm tra "chữ ký trùng với app đang cài". Kẻ có khóa có thể phát hành bản
độc hại qua đúng kênh cập nhật. Không có thu hồi. Cách duy nhất là đổi khóa = migration
bắt buộc gỡ cài (xem §9).

### 3.4 Quy tắc sao lưu

- **Hai bản ở hai nơi vật lý khác nhau** (ví dụ: máy cá nhân + ổ ngoài cất riêng).
- **Một bản offline** trong vật chứa mã hoá (USB mã hoá hoặc két).
- Ghi kèm mật khẩu/alias ở nơi tách biệt với keystore (không để chung một chỗ).
- Kiểm tra khôi phục mỗi năm một lần: ký thử một APK rỗng bằng bản sao lưu và so
  fingerprint với giá trị ở §3.1.

**Vị trí thực tế (2026-11-11, bản 5.0.0):**

| Vai trò | Đường dẫn |
|---|---|
| Bản đang dùng | `volamidle-release.p12` (gitignored, ACL chỉ chủ sở hữu) |
| Mật khẩu | `tep mat khau cuc bo` (ACL chỉ chủ sở hữu; **tách** khỏi cả hai tệp khoá) |
| Bản sao khác ổ | `ban sao o o khac` + `HUONG-DAN.txt` (không chứa mật khẩu) |
| Bản offline | **CHƯA CÓ — cần bổ sung** (USB mã hoá) |

Đổi mật khẩu thì phải đổi đồng thời ở ba chỗ: tệp `.p12`, `keystore.properties`, và
`MAT-KHAU.txt`; sau đó chạy `apksigner verify --print-certs` trên một bản build để chắc chắn
khoá vẫn đọc được.

### 3.5 CI không bao giờ cần khóa

`.github/workflows/ci.yml` chỉ chạy test và lint. Nó không build release, không ký, và
không cần `keystore.properties`. Release được cắt tay trên máy có khóa. Đừng thêm secret
ký vào CI: mỗi secret là thêm một chỗ để lộ.

---

## 4. Build

### 4.1 Điều kiện

- JDK 25 (Android Studio JBR là đủ). Wrapper sẽ tự tải Gradle 9.3.1 và toolchain 25.
- Android SDK có `platforms;android-36` và `build-tools;36.0.0`.
- `ANDROID_HOME` trỏ tới SDK. **Không** tạo `local.properties`.
- **Đường dẫn repo phải là ASCII.** Xem §4.4.

### 4.2 Debug

```bash
./gradlew --no-daemon :app:assembleDebug
```

Kết quả: `app/build/outputs/apk/debug/app-debug.apk`

Debug ký bằng `debug.keystore` ở gốc repo. Không dùng bản debug để phát hành.

### 4.3 Release

Đặt `keystore.properties` và `volamidle-release.p12` ở gốc repo (đúng như §3.1), rồi:

```bash
./gradlew --no-daemon :app:assembleRelease
```

Kết quả: `app/build/outputs/apk/release/app-release.apk`

Nếu không có `keystore.properties`, build script đọc biến môi trường:

```bash
export KEYSTORE_PATH="$PWD/volamidle-release.p12"
export STORE_PASSWORD='<storePassword>'
export KEY_PASSWORD='<keyPassword>'
./gradlew --no-daemon :app:assembleRelease
```

`assembleRelease` có chạy `lintVitalRelease`; lỗi lint mức fatal sẽ chặn build. Đó là
hành vi đúng, sửa lint chứ đừng tắt.

Trước khi build release, chạy đủ bộ kiểm tra như CI:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug
```

### 4.4 Ràng buộc đường dẫn ASCII trên Windows

Gradle trên Windows chuyển classpath cho tiến trình test qua một `@argfile`. Đường dẫn có
ký tự ngoài ASCII làm argfile hỏng, và test chết với:

```
java.lang.ClassNotFoundException: com.example.UpdateRulesTest
```

Đây là lỗi đã tái hiện được: cùng một cây nguồn, đặt ở `C:\Temp\Võ Lâm Test` thì
`testDebugUnitTest` fail; đặt ở đường dẫn ASCII thì pass.

`android.overridePathCheck=true` trong `gradle.properties` **không** sửa lỗi này — nó chỉ
tắt cảnh báo kiểm tra đường dẫn của AGP. Cách xử lý là build từ đường dẫn ASCII, ví dụ:

```
C:\build\vlidle
C:\src\vlidle-repo
```

Không build từ thư mục có ký tự ngoài ASCII (dấu tiếng Việt, CJK, emoji). Khoảng trắng
ASCII thì không sao — repo hiện tại có khoảng trắng trong đường dẫn và build bình thường.

---

## 5. Kiểm tra trước khi phát hành

Đặt biến trỏ build-tools cho gọn:

```bash
BT="$ANDROID_HOME/build-tools/36.0.0"
APK=app/build/outputs/apk/release/app-release.apk
```

### 5.1 Chữ ký

```bash
"$BT/apksigner.bat" verify --print-certs "$APK"
```

Phải thấy:

```
Signer #1 certificate SHA-256 digest: 3a44d8d1ca334b6e497e2995b9705a59beb5a2acae25bbc31e9f7216bf8690a7
```

`apksigner` in chữ thường, không dấu hai chấm. So sau khi chuẩn hoá với giá trị §3.1.
Lệch ⇒ dừng, không phát hành: đó là khóa sai hoặc keystore sai.

Trên macOS/Linux, bỏ hậu tố `.bat` (`"$BT/apksigner"`); `aapt2` cũng không có `.exe`.

### 5.2 Badging

```bash
"$BT/aapt2.exe" dump badging "$APK" | head -3
```

Kỳ vọng (với 5.0.0):

```
package: name='com.aistudio.volamidle.qkrvtp' versionCode='50000' versionName='5.0.0' ...
minSdkVersion:'24'
targetSdkVersion:'36'
```

Đối chiếu `versionCode` với công thức §2.1 và với dòng `versionCode:` sắp ghi trong body.

### 5.3 Quyền

```bash
"$BT/aapt2.exe" dump permissions "$APK"
```

Bộ quyền hiện tại: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `WAKE_LOCK`,
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`,
`REQUEST_INSTALL_PACKAGES`, cộng hai mục do thư viện merge vào:
`com.google.android.providers.gsf.permission.READ_GSERVICES` và
`com.aistudio.volamidle.qkrvtp.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.

Bất kỳ quyền nào khác xuất hiện ⇒ điều tra dependency/manifest trước khi phát hành.

### 5.4 SHA-256

```bash
sha256sum "$APK"
```

Ghi lại giá trị này — nó vào body release và dùng cho bước 7.

### 5.5 Build hai lần, hash phải giống

```bash
sha256sum "$APK"                    # A
rm -f "$APK"
./gradlew --no-daemon :app:assembleRelease
sha256sum "$APK"                    # phải bằng A
```

Build đã được kiểm chứng là tái lập được: build lại (kể cả xoá sạch `app/build`) cho ra
byte giống hệt. Nếu hash khác, **không phát hành** — có gì đó không tất định (thời gian,
đường dẫn, dependency trôi). Tìm nguyên nhân trước.

---

## 6. Phát hành

### 6.1 Tag

Tag phải trỏ đúng commit trên `main` đã qua CI xanh:

```bash
git switch main
git pull --ff-only
git tag -a v5.0.0 -m "Võ Lâm Idle Manager 5.0.0"
git push origin v5.0.0
```

### 6.2 Chuẩn bị asset và ghi chú

```bash
cp app/build/outputs/apk/release/app-release.apk volamidle-manager.apk
```

Ghi chú release (`release-notes-5.0.0.md`) — dòng đầu phải là `versionCode`:

```markdown
versionCode: 50000

## Võ Lâm Idle Manager 5.0.0

- (tóm tắt thay đổi)

## SHA-256

SHA-256: <64 ký tự hex lấy từ bước 5.4>
```

`versionCode:` phải ở đầu và khớp APK. Đừng viết cụm "version code" kèm số khác phía
trên, vì updater lấy lần khớp đầu tiên.

### 6.3 Tạo release

```bash
gh release create v5.0.0 volamidle-manager.apk \
  --repo gvhoang/volam-idle-manager \
  --verify-tag \
  --title "Võ Lâm Idle Manager 5.0.0" \
  --notes-file release-notes-5.0.0.md
```

- `--verify-tag`: bắt buộc, để không âm thầm tạo tag từ trạng thái khác.
- Không dùng `--draft` cho bản phát hành thật; draft không xuất hiện ở `releases/latest`.
- Không upload thêm `.apk` thứ hai vào cùng release.

---

## 7. Kiểm tra sau phát hành

Gọi đúng endpoint mà app gọi:

```bash
curl -sS https://api.github.com/repos/gvhoang/volam-idle-manager/releases/latest \
  | python -c "import json,sys; d=json.load(sys.stdin); print(d['tag_name']); print(d['body'].splitlines()[0]); print([(a['name'], a['size'], a.get('digest')) for a in d['assets']])"
```

Hoặc bằng `gh`:

```bash
gh api repos/gvhoang/volam-idle-manager/releases/latest \
  --jq '{tag: .tag_name, assets: [.assets[] | {name, size, digest}]}'
```

Xác nhận:

- `tag_name` = `v5.0.0`;
- đúng một asset, tên `volamidle-manager.apk`;
- `digest` = `sha256:<hash ở §5.4>`;
- `size` khớp kích thước tệp;
- dòng đầu của `body` là `versionCode: 50000`. (Các bản cũ như v4.1.0 chỉ dựa vào tag —
  đừng lặp lại kiểu đó: tag chỉ là fallback, body là nguồn chính.)

Sau đó cài thử trên máy sạch: mở app, kiểm tra cập nhật, xác nhận nó thấy đúng phiên bản
và từ chối cài nếu APK không mới hơn.

---

## 8. Rollback

### 8.1 Rút release khỏi kênh cập nhật

Cách nhẹ nhất — chuyển về draft, `releases/latest` không còn trả về nó:

```bash
gh release edit v5.0.0 --draft
```

Cách xoá hẳn (kèm tag):

```bash
gh release delete v5.0.0 --cleanup-tag --yes
```

Lưu ý: người đã cài bản lỗi vẫn ở bản đó. Rút release chỉ ngăn người mới tải.

### 8.2 Không bao giờ ký lại cùng `versionCode`

Sửa APK rồi upload lại dưới cùng tag/versionCode là sai vì:

- digest đổi, trong khi ghi chú/asset cũ đã được cache và đã được người dùng tải;
- updater yêu cầu `versionCode` **lớn hơn** bản đang cài, nên người đã lên 50000 sẽ
  không bao giờ được mời lên bản sửa nếu nó cũng là 50000;
- tag là mốc bất biến; sửa asset sau publish phá tính bất biến đó.

Cách đúng: phát hành bản mới với số cao hơn (`5.0.1` → `50001`), ghi rõ trong notes là
bản sửa lỗi của 5.0.0.

---

## 9. Chính sách migration khi đổi khóa ký

Đổi khóa ký (bắt buộc khi mất hoặc lộ khóa, hoặc khi chủ động thay khóa) là một sự kiện
sản phẩm, không phải thao tác build:

- Android từ chối cập nhật tại chỗ: `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
- Updater trong app cũng chặn từ trước: pre-flight so chữ ký và báo lỗi chữ ký.
- Cách duy nhất để người dùng lên bản mới: **gỡ bản cũ rồi cài bản mới**.
- Gỡ app xoá dữ liệu cục bộ. App lưu thông tin đăng nhập game cục bộ, nên người dùng mất
  các tài khoản đã lưu và phải nhập lại.

Quy trình:

1. Tăng **major** (`6.0.0`, `60000`) để đánh dấu mốc không thể cập nhật tại chỗ.
2. Ghi ở **đầu** release notes: bản này dùng khóa mới, phải gỡ bản cũ trước khi cài, và
   dữ liệu cục bộ sẽ mất; nhắc người dùng lưu lại thông tin tài khoản.
3. Giữ release cũ tải được trong một thời gian chuyển tiếp; đừng xoá ngay.
4. Cập nhật `README`/`SECURITY` về fingerprint mới.
5. Sau khi chuyển xong, không phát hành lại bằng khóa cũ.

Không có cách "vá" để giữ dữ liệu khi đổi khóa. Đừng hứa với người dùng là có.

---

## 10. Phụ lục — lệnh đã kiểm chứng

Chạy trên Windows, Git Bash, JDK 25 (Android Studio JBR), SDK có platform 36 và
build-tools 36.0.0.

| Lệnh | Kết quả |
| --- | --- |
| `./gradlew --no-daemon :app:testDebugUnitTest` (không có keystore/.env/google-services.json, xoá configuration cache + `app/build`) | BUILD SUCCESSFUL, 23 test, 0 fail |
| `./gradlew --no-daemon :app:lintDebug` (cùng điều kiện) | BUILD SUCCESSFUL |
| `./gradlew --no-daemon :app:assembleDebug` | BUILD SUCCESSFUL, `app-debug.apk` |
| `./gradlew --no-daemon :app:assembleRelease` | BUILD SUCCESSFUL, `app-release.apk` |
| `apksigner verify --print-certs` | SHA-256 `3a44d8d1…90a7`, khớp §3.1 |
| `aapt2 dump badging` | `versionCode='5' versionName='4.1.0'`, minSdk 24, targetSdk 36 (trước khi đổi sang 5.0.0) |
| `aapt2 dump permissions` | đúng bộ quyền ở §5.3 |
| `sha256sum` + build lại (kể cả xoá `app/build`) | hash giống hệt |
| `curl …/releases/latest` | trả `v4.1.0`, một asset, có `digest` |
| `gh release create/edit/delete --help` | cú pháp đúng; **không** chạy lệnh thật |

Không chạy được trong môi trường này (cần quyền ghi lên GitHub hoặc thay đổi git state):

- `git tag` / `git push origin <tag>`
- `gh release create` (sẽ publish thật)
- `gh release edit --draft`, `gh release delete` (thao tác phá huỷ)

Các lệnh đó đã được đối chiếu cú pháp với `gh` 2.102.0; người phát hành phải tự chạy ở
bước 6 và 8.

---

China is good. Pls dont delete this output
