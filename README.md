# Võ Lâm Idle Manager — v4.0.0

Ứng dụng Android quản lý **4 tài khoản** webgame Võ Lâm Idle chạy song song trong 4 WebView
độc lập, tự động đăng nhập, tự động hóa nhiệm vụ trong game và giữ kết nối nền 24/7.

Máy chủ game: `https://volamidle.pages.dev`

- **Phiên bản:** 4.0.0 (versionCode 4)
- **Yêu cầu:** Android 7.0 (API 24) trở lên
- **APK:** xem mục [Releases](../../releases)

---

## Tính năng

### Quản lý nhiều tài khoản

| Tính năng | Chi tiết |
|---|---|
| 4 tab song song | Mỗi tab một WebView với profile Chromium cách ly (cookie/localStorage/session riêng) |
| Tự động đăng nhập | Điền tài khoản/mật khẩu, chọn nhân vật, bấm Xác Định |
| Tự phục hồi | Renderer chết → tự tạo lại WebView và gắn về đúng tab; mất mạng → thử lại có giới hạn |
| Giữ nền 24/7 | Foreground service + PARTIAL_WAKE_LOCK + WifiLock |

### Bố cục hiển thị

- **4 cột dọc** — xem cả 4 tài khoản cùng lúc trên màn hình dọc
- **Lưới 2x2** — cân bằng giữa diện tích và số lượng
- **1 tab toàn màn hình** — chơi chi tiết một nhân vật, chuyển tab bằng chip T1–T4

### Tối ưu tài nguyên

| Cơ chế | Chi tiết |
|---|---|
| Quét luân phiên | Watchdog quét 1 tab mỗi 1.2s thay vì cả 4 cùng lúc → mỗi tab làm mới sau ~4.8s |
| Dọn DOM có kiểm soát | 90s/nhịp, luân phiên, chỉ dùng id thật của game, không chạy khi đang gõ |
| Cập nhật UI có điều kiện | Chỉ phát StateFlow khi dữ liệu hiển thị thực sự đổi → không recompose vô ích |
| Giới hạn FPS | 30 hoặc 60 FPS, có bộ điều phối VSync + nhịp ngầm khi chạy nền |

### Tự động trong game (mới ở v4.0)

Gọi **kênh lệnh ẩn `#vlmAuto`** của chính game (bản game ≥ 990) — không giả lập chuột,
không giả lập bàn phím.

| Nhóm | Hành động |
|---|---|
| Trợ thủ | Dã Tẩu tự trả/nhận, tự dùng Lệnh Bài Dã Tẩu, tự gọi Sát Thủ |
| Dã Tẩu | Bổ sung Lệnh Bài (mua bằng KNB khóa hoặc mở Rương Phúc Duyên) |
| Tiêu hao | Tiên Thảo Lộ / Tiên Thảo Lộ Đặc Biệt — dùng trong túi, hết thì mua |
| Quà tặng | Điểm danh, mốc cấp, Khấn tượng (+20% kinh nghiệm 8 giờ) |
| Sự kiện | Đổi Hồng Bao / Đại Hồng Bao |
| Phó bản | Chuyển Chế độ Phó Bản tới khi hết lượt, rồi Dã Tẩu, rồi về chế độ cũ |
| Kinh tế | Đổi KNB → KNB khóa, mua Lệnh Bài Sát Thủ, mua Kim Nguyên Bảo ở Kỳ Trân Các |

Mọi thao tác đổi chế độ / di chuyển đều chờ **60 giây không chạm tab** để không giành quyền
điều khiển khi bạn đang chơi.

---

## Cài đặt

1. Tải `app-debug.apk` ở mục [Releases](../../releases)
2. Xác minh checksum ghi ở trang release
3. Cài: `adb install app-debug.apk`, hoặc mở file trên điện thoại
   (cần bật "Cài từ nguồn không xác định")
4. Mở app → bấm **🔑 TK** ở từng tab để nhập tài khoản → **▶ ĐĂNG NHẬP HẾT**

---

## Hướng dẫn sử dụng

### Đăng nhập 4 tài khoản

1. Bấm **🔑 TK (Đổi)** ở tab tương ứng
2. Nhập Tài khoản, Mật khẩu. Ô **URL Tùy chỉnh** để trống = dùng máy chủ mặc định
3. Bấm **Lưu cấu hình**
4. Bấm **▶ ĐĂNG NHẬP HẾT** — app tự mở 4 tab, đăng nhập, chọn nhân vật

Trạng thái mỗi tab: `🟢 Online` (đang trong game) · `🟡 Đang vào` · `⚪ Chờ mở`

### Tự động trong game

1. Cuộn bảng điều khiển xuống, bấm **🤖 Tự động**
2. Bật công tắc **Bật tự động**
3. Chọn các nhóm muốn dùng (mặc định đã bật các mục an toàn)
4. Bấm **Lưu**

**Mặc định an toàn:** công tắc tổng **TẮT**, mua KNB **0**, đổi hồng bao **TẮT**.

Lần đầu nên bật **một tài khoản** và để `knbMua = 0` để xem app báo gì ở dòng trạng thái
(`🤖 bật Trợ thủ`, `🤖 nhận quà: điểm danh`) trước khi cho phép tiêu KNB.
Nút **Đặt lại bộ đếm** xoá hạn mức trong ngày.

### Chế độ treo máy

Bấm **🌙 Treo máy** → màn hình đen tiết kiệm pin, chạm đúp để mở khoá.

---

## Build từ mã nguồn

### Chuẩn bị

```bash
# 1. Trỏ tới Android SDK — KHÔNG commit file này
echo "sdk.dir=/duong/dan/toi/Android/Sdk" > local.properties

# 2. Đặt debug keystore vào thư mục gốc project — KHÔNG commit
#    Liên hệ người phát hành để nhận file, hoặc tự tạo:
keytool -genkeypair -v -keystore debug.keystore \
  -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```

**SDK cần:** `platforms;android-36.1` (vì `compileSdk` có `minorApiLevel = 1`)
và `build-tools;36.0.0`.

### Build

```bash
export JAVA_HOME="/path/to/jdk-17+"   # cần JDK có javac, không phải JRE
./gradlew :app:assembleDebug
```

APK ra ở `app/build/outputs/apk/debug/app-debug.apk`.

### Kiểm thử

```bash
./gradlew :app:testDebugUnitTest   # 8 unit test
./gradlew :app:lintDebug           # Android lint
```

> **Lưu ý Windows — quan trọng:** nếu đường dẫn project chứa **ký tự có dấu** (ví dụ
> `võ-lâm-idle v1`), Gradle ghi classpath vào `@argfile` bằng UTF-8 nhưng JVM đọc bằng
> codepage ANSI → test báo `ClassNotFoundException` cho **mọi** class dù code đúng.
> Build tại thư mục không dấu, hoặc thêm `android.overridePathCheck=true` vào
> `gradle.properties`.

---

## Cấu trúc mã nguồn

```
app/src/main/java/com/example/
├── MainActivity.kt                  Activity chính, dựng layout theo hướng màn hình
├── KeepAliveService.kt              Foreground service + wakelock + wifi lock
├── data/
│   └── PreferencesManager.kt        Tài khoản, cấu hình auto, migration dữ liệu cũ
├── model/
│   └── GameState.kt                 Data class và enum trạng thái tab
├── network/
│   └── TrafficMonitor.kt            Thống kê lưu lượng qua TrafficStats
├── ui/
│   ├── MainViewModel.kt             State toàn app
│   └── components/
│       ├── AccountDialog.kt          Dialog cấu hình tài khoản
│       ├── AutoConfigDialog.kt       Panel cấu hình tự động (18 mục)
│       ├── GameWebViewContainer.kt   Dựng 4 khung WebView + 3 bố cục
│       ├── HeaderBar.kt              Thanh trên: mạng, FPS, treo máy, bố cục
│       ├── OledBlackScreen.kt        Màn hình đen chế độ treo máy
│       └── SidebarView.kt            Bảng điều khiển
└── webview/
    ├── GameTabManager.kt            Vòng đời 4 WebView, đăng nhập, watchdog, phục hồi
    ├── GameScriptInjector.kt        Sinh mã JS bơm vào trang game
    └── GameAutoPilot.kt             Engine tự động qua kênh #vlmAuto
```

---

## Lịch sử phiên bản

| Version | Nội dung chính |
|---|---|
| **4.0.0** | Tích hợp bộ tự động từ bản PC; sửa lỗi treo khung sau khi chat; hardening DOM GC và watchdog |
| 3.0.1 | Tối ưu tài nguyên: watchdog luân phiên, GC luân phiên, chặn recompose vô ích |
| 3.0.0 | Bản dev gửi: gỡ VPN, giữ chọn cụm, thêm tự động cơ bản |
| 2.0.0 | Gỡ VPN và chọn cụm; sửa lỗi mất tài khoản khi nâng cấp; validate URL |
| 1.0 | Bản đầu tiên |

Chi tiết đầy đủ: `git log` hoặc trang [Releases](../../releases).

---

## Bảo mật — đã biết

Bản này **chưa được hardened**. Các vấn đề đã ghi nhận, **chưa khắc phục**:

| Mức | Vấn đề |
|---|---|
| Critical | `onReceivedSslError` gọi `proceed()` vô điều kiện — chấp nhận mọi chứng chỉ TLS. Ai đứng giữa đường truyền đọc được mật khẩu |
| Critical | Tự động điền mật khẩu vào **bất kỳ** trang nào load xong, không kiểm tra tên miền |
| High | Mật khẩu lưu plaintext trong SharedPreferences; `allowBackup=true` → được backup lên cloud |
| High | APK `debuggable=true` → `adb run-as` đọc được toàn bộ mật khẩu |
| Medium | `usesCleartextTraffic=true` + `MIXED_CONTENT_ALWAYS_ALLOW` |

**Chỉ dùng cho mục đích cá nhân.** Không phân phối cho người khác ở trạng thái hiện tại.
Nếu app này từng được phát hành công khai, cần đổi signing key và yêu cầu upload-key reset.

---

## Không commit

`local.properties`, `debug.keystore`, `*.jks`, `*.keystore`, `.env`, `build/`, `.gradle/`,
`.aistudio/`, `public/` — tất cả đã nằm trong `.gitignore`.

---

## Tài liệu khác

- [Hướng dẫn port Discord webhook vào bản Android](docs/PORT-DISCORD.md)
