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
| Chọn cụm | Dropdown Cụm 1/2/3 — cả ba trỏ về cùng một máy chủ |
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
| `v3` | v1 → v3 | Bản dev gửi: gỡ VPN, **giữ** chọn cụm, thêm tính năng tự động trong game |

v3 **không** bao gồm các bản sửa của nhánh `main`:

- Không có `validateCustomUrl` — ô URL tùy chỉnh nhận mọi chuỗi
- Không có giới hạn thử lại khi lỗi máy chủ — tải lại mỗi 2.5 giây vô hạn
- Không có bản sửa migration — mật khẩu nhân bản 5–11 lần mỗi tab, có thể hồi sinh tài khoản đã xóa
- Không có bộ test migration

## Cấu trúc mã nguồn

```
app/src/main/java/com/example/
├── MainActivity.kt              # Activity chính, dựng layout theo hướng màn hình
├── KeepAliveService.kt          # Foreground service + wakelock + wifi lock
├── data/PreferencesManager.kt   # Lưu tài khoản theo cụm, cấu hình
├── model/GameState.kt           # Data class và enum
├── network/TrafficMonitor.kt    # Đọc thống kê lưu lượng qua TrafficStats
├── ui/
│   ├── MainViewModel.kt         # State của toàn app
│   └── components/              # HeaderBar, SidebarView, GameWebViewContainer, ...
└── webview/
    ├── GameTabManager.kt        # Quản lý 4 WebView, đăng nhập, giám sát, phục hồi
    └── GameScriptInjector.kt    # Sinh mã JS bơm vào trang game
```

## Bảo mật — đã biết, CHƯA khắc phục

Bản này **chưa được hardened**. Các vấn đề dưới đây đã được audit và ghi nhận:

| Mức | Vấn đề |
|---|---|
| Critical | `onReceivedSslError` gọi `proceed()` vô điều kiện — chấp nhận mọi chứng chỉ TLS |
| Critical | Tự động điền mật khẩu vào **bất kỳ** trang nào load xong, không kiểm tra tên miền (8 điểm inject) |
| High | `AndroidBridge` mở cho mọi frame, không bao giờ gỡ; `onGameKickedDetected` cho phép trang web tự ra lệnh bơm mật khẩu |
| High | Mật khẩu lưu plaintext, nhân bản 5–11 lần mỗi tab; `allowBackup=true` → được backup lên cloud |
| High | APK `debuggable=true` → `adb run-as` đọc được toàn bộ mật khẩu |
| Medium | `usesCleartextTraffic=true` + `MIXED_CONTENT_ALWAYS_ALLOW` |
| Medium | Ô URL tùy chỉnh không validate scheme/host |
| Medium | `versionCode` vẫn là 1 — không cập nhật đè được bản cũ |
| Low | `isMinifyEnabled=false`, ~13MB dependency không dùng (firebase-ai, okhttp, room, retrofit) |

**Chỉ dùng cho mục đích cá nhân.** Không phân phối cho người khác ở trạng thái hiện tại.

## Không commit

`local.properties`, `debug.keystore`, `*.jks`, `*.keystore`, `.env`, `build/`,
`.aistudio/`, `public/` — tất cả đã nằm trong `.gitignore`.
