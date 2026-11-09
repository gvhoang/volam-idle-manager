# Võ Lâm Idle Manager — v3

Ứng dụng Android quản lý 4 tài khoản webgame **Võ Lâm Idle** chạy song song trong
4 WebView độc lập, có tự động đăng nhập và giữ kết nối nền 24/7.

Máy chủ game: `https://volamidle.pages.dev`

> **Nhánh này (`v3`) phát triển từ baseline v1, KHÔNG phải từ nhánh `main` (v2.0.0).**
> Xem mục *Quan hệ với các nhánh khác* bên dưới.

---

## Tính năng

| Nhóm | Chi tiết |
|---|---|
| Đa tab | 4 WebView song song, mỗi tab một profile cách ly (cookie/session riêng) |
| Tự động đăng nhập | Điền tài khoản/mật khẩu vào form game, tự bấm xác định |
| Máy chủ game | Dùng `https://volamidle.pages.dev`; URL tùy chỉnh chỉ nhận HTTPS đúng máy chủ này |
| Bố cục | 4 cột dọc / lưới 2x2 / 1 tab toàn màn hình |
| Giữ kết nối | Foreground service + PARTIAL_WAKE_LOCK + WifiLock, tự phục hồi khi bị thu hồi RAM |
| Khóa FPS | Giới hạn 30 hoặc 60 FPS |
| Chế độ treo máy | Màn hình đen OLED, chạm đúp để mở khóa |
| Giám sát | Thống kê lưu lượng mạng, quét thông số nhân vật |
| Tự động trong game | Xử lý xung đột phiên, đồng bộ màn hình đen, dọn DOM định kỳ |

## Yêu cầu

- **Android 7.0** (API 24) trở lên
- Android System WebView hỗ trợ `MULTI_PROFILE` để cách ly profile giữa các tab

## Build

```bash
# 1. Trỏ tới Android SDK (KHÔNG commit)
echo "sdk.dir=/duong/dan/toi/Android/Sdk" > local.properties

# 2. Đặt debug keystore vào thư mục gốc project (KHÔNG commit)
#    Dev cung cấp file debug.keystore riêng — không có trong repo.

# 3. Build
./gradlew :app:assembleDebug
```

APK ra ở `app/build/outputs/apk/debug/app-debug.apk`.

### Kiểm thử

```bash
./gradlew :app:testDebugUnitTest   # 8 unit test
./gradlew :app:lintDebug           # Android lint
```

> **Lưu ý Windows:** nếu đường dẫn project chứa ký tự có dấu, Gradle ghi classpath
> vào `@argfile` bằng UTF-8 nhưng JVM đọc bằng codepage ANSI, khiến test không tìm
> thấy class. Build tại thư mục không dấu để tránh lỗi này.

## Quan hệ với các nhánh khác

| Nhánh | Nguồn gốc | Ghi chú |
|---|---|---|
| `main` | v1 → v2.0.0 | Bản do Thiên Cơ Labs refactor: gỡ VPN **và** chọn cụm, sửa lỗi mất tài khoản khi nâng cấp, thêm validate URL, thêm 9 test migration |
| `v3` | v1 → v3 | Bản dev gửi; bản phát hành hiện tại đã bỏ chọn cụm và giữ tính năng tự động trong game |

Bản phát hành này bổ sung validate URL, giới hạn thử lại và migration dữ liệu tài khoản khi bỏ chọn cụm.

## Cấu trúc mã nguồn

```
app/src/main/java/com/example/
├── MainActivity.kt              # Activity chính, dựng layout theo hướng màn hình
├── KeepAliveService.kt          # Foreground service + wakelock + wifi lock
├── data/PreferencesManager.kt   # Lưu tài khoản và cấu hình theo tab
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

Các thay đổi hardening trong bản 3.0.1 và các rủi ro còn lại được ghi tại [SECURITY.md](SECURITY.md).

**Chỉ dùng cho mục đích cá nhân.** Không phân phối cho người khác ở trạng thái hiện tại.

## Không commit

`local.properties`, `debug.keystore`, `*.jks`, `*.keystore`, `.env`, `build/`,
`.aistudio/`, `public/` — tất cả đã nằm trong `.gitignore`.
