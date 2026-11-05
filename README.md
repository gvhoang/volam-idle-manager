# Võ Lâm Idle Manager (Android)

Ứng dụng Android quản lý 4 tài khoản webgame **Võ Lâm Idle** chạy song song trong
4 WebView độc lập, có tự động đăng nhập và giữ kết nối nền 24/7.

Máy chủ game: `https://volamidle.pages.dev`

---

## Tính năng

| Nhóm | Chi tiết |
|---|---|
| Đa tab | 4 WebView chạy song song, mỗi tab một profile cách ly (cookie/session riêng) |
| Tự động đăng nhập | Điền tài khoản/mật khẩu vào form đăng nhập của game, bấm xác định |
| Bố cục | 4 cột dọc / lưới 2x2 / 1 tab toàn màn hình |
| Giữ kết nối | Foreground service + PARTIAL_WAKE_LOCK + WifiLock, tự phục hồi khi bị thu hồi RAM |
| Khóa FPS | Giới hạn 30 hoặc 60 FPS để giảm tải GPU |
| Chế độ treo máy | Màn hình đen OLED, chạm đúp để mở khóa |
| Giám sát | Thống kê lưu lượng mạng, quét thông số nhân vật (cấp, phái, nhiệm vụ) |

## Yêu cầu

- **Android 7.0** (API 24) trở lên
- Android System WebView hỗ trợ `MULTI_PROFILE` để cách ly profile giữa các tab

## Build

```bash
# 1. Trỏ tới Android SDK (tạo file, KHÔNG commit)
echo "sdk.dir=/duong/dan/toi/Android/Sdk" > local.properties

# 2. Tạo debug keystore (KHÔNG commit)
keytool -genkeypair -v -keystore debug.keystore -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"

# 3. Build
./gradlew :app:assembleDebug
```

APK ra ở `app/build/outputs/apk/debug/app-debug.apk`.

### Kiểm thử

```bash
./gradlew :app:testDebugUnitTest   # 17 unit test, gồm 9 test migration tài khoản
./gradlew :app:lintDebug           # Android lint
```

> **Lưu ý Windows:** nếu đường dẫn project chứa ký tự có dấu, Gradle ghi classpath
> vào `@argfile` bằng UTF-8 nhưng JVM đọc bằng codepage ANSI, khiến test không tìm
> thấy class. Build tại thư mục không dấu để tránh lỗi này.

## Lịch sử thay đổi

Xem [Releases](../../releases) và `git log`.

Bản `2.0.0` là bản đầu tiên được phát hành qua GitHub Releases, kèm SHA-256 của APK.

## Cấu trúc mã nguồn

```
app/src/main/java/com/example/
├── MainActivity.kt              # Activity chính, dựng layout theo hướng màn hình
├── KeepAliveService.kt          # Foreground service + wakelock + wifi lock
├── data/PreferencesManager.kt   # Lưu tài khoản, cấu hình, migration dữ liệu cũ
├── model/GameState.kt           # Data class và enum
├── network/TrafficMonitor.kt    # Đọc thống kê lưu lượng qua TrafficStats
├── ui/
│   ├── MainViewModel.kt         # State của toàn app
│   └── components/              # HeaderBar, SidebarView, GameWebViewContainer, ...
└── webview/
    ├── GameTabManager.kt        # Quản lý 4 WebView, đăng nhập, giám sát, phục hồi
    └── GameScriptInjector.kt    # Sinh mã JS bơm vào trang game
```

## Bảo mật

Ứng dụng lưu mật khẩu game trong `SharedPreferences` dạng **plaintext** và tự động
điền vào form đăng nhập. Xem mục **Known issues** trong phần Releases để biết các
rủi ro đã ghi nhận và trạng thái khắc phục.

Không commit `local.properties`, `*.keystore`, `*.jks`, hay `.env`.
