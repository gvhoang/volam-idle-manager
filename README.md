# Võ Lâm Idle Manager

Chạy **1–8 tài khoản** webgame [Võ Lâm Idle](https://volamidle.pages.dev) song song trong một ứng dụng Android: mỗi tab là một profile Chromium cách ly, tự đăng nhập, tự phục hồi khi văng phiên, giữ kết nối 24/7, tự làm việc trong game và tự cập nhật từ GitHub Releases.

[![Release](https://img.shields.io/badge/release-v5.0.0-2ea44f?style=flat-square)](https://github.com/gvhoang/volam-idle-manager/releases/tag/v5.0.0)
[![versionCode](https://img.shields.io/badge/versionCode-50000-2ea44f?style=flat-square)](CHANGELOG.md)
[![minSdk 24 (Android 7.0)](https://img.shields.io/badge/minSdk-24-3ddc84?style=flat-square)](#yêu-cầu-hệ-thống)
[![Platform](https://img.shields.io/badge/platform-Android-3ddc84?style=flat-square)](#yêu-cầu-hệ-thống)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7f52ff?style=flat-square)](#kiến-trúc)
[![License](https://img.shields.io/badge/license-All%20rights%20reserved-lightgrey?style=flat-square)](#giấy-phép)

> [!WARNING]
> ## Đọc trước khi nâng cấp từ 4.x
>
> **5.0.0 là bản đầu tiên được ký bằng khóa release thật.** Các bản 4.0.0 và 4.1.0 ký bằng Android debug key. Android không cho cài một APK khác chữ ký lên bản đang cài — nó dừng với `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Vì vậy **nâng cấp từ 4.x bắt buộc phải gỡ ứng dụng trước, và gỡ ứng dụng sẽ xoá toàn bộ tài khoản đã lưu** (`SharedPreferences`).
>
> Làm theo thứ tự này:
>
> 1. Mở từng tab trong bản 4.x, ghi lại **tài khoản, mật khẩu, URL tùy chỉnh** (nếu có) và cấu hình `🤖 Tự động` ra ngoài máy.
> 2. Gỡ `Võ Lâm Idle` (Cài đặt → Ứng dụng → Gỡ cài đặt).
> 3. Cài `volamidle-manager.apk` từ [Releases](https://github.com/gvhoang/volam-idle-manager/releases/tag/v5.0.0).
> 4. Nhập lại tài khoản cho từng tab.
>
> Nút **Cập nhật** bên trong bản 4.1.0 sẽ báo *“Không cài được: bản cập nhật được ký bằng khóa khác với bản đang cài”*. Đó là hành vi đúng, không phải lỗi tải.
>
> Từ 5.0.0 trở đi, mọi bản phát hành dùng **cùng một khóa**, nên cài đè bình thường và tài khoản được giữ nguyên. Đừng đổi khóa ký nữa.

## Mục lục

| Nhóm | Mục |
|---|---|
| Bắt đầu | [Giới thiệu](#giới-thiệu) · [Tính năng](#tính-năng) · [Cài đặt và lần chạy đầu](#cài-đặt-và-lần-chạy-đầu) |
| Dùng hằng ngày | [Sử dụng](#sử-dụng) · [Tham chiếu cấu hình tự động](#tham-chiếu-cấu-hình-tự-động) · [Xử lý sự cố](#xử-lý-sự-cố) |
| Kỹ thuật | [Kiến trúc](#kiến-trúc) · [Build từ mã nguồn](#build-từ-mã-nguồn) · [Quy trình phát hành](#quy-trình-phát-hành) · [Kiểm thử](#kiểm-thử) |
| Tham khảo | [Hiệu năng](#hiệu-năng) · [Bảo mật](#bảo-mật) · [Lịch sử phiên bản](#lịch-sử-phiên-bản) · [Tài liệu](#tài-liệu) |
| Dự án | [About](#about) · [Giấy phép](#giấy-phép) |

## Giới thiệu

Võ Lâm Idle là webgame idle chạy hoàn toàn trong trình duyệt. Chơi nhiều tài khoản theo cách thông thường nghĩa là mở nhiều tab, mỗi tab giữ một phiên WebSocket riêng — và mọi tab đều bị trình duyệt hoặc Android tạm dừng khi chuyển ứng dụng hay tắt màn hình. Nhân vật đứng yên, gói tin dồn lại, và khi quay lại thì game tua nhanh hoặc nhảy vị trí.

Ứng dụng này nhúng 1–8 WebView ngay trong một app Android. Mỗi WebView có profile Chromium cách ly (cookie, `localStorage`, phiên riêng), được giữ ở trạng thái hiển thị kể cả khi app ra nền, và được đăng nhập lại tự động khi mất phiên. Một foreground service giữ CPU và Wi-Fi thức; một lớp JavaScript bơm vào trang lo phần đăng nhập, quét trạng thái, điều khiển game và tự động hóa.

Phạm vi có chủ ý: **một máy chủ game duy nhất**, số tab cấu hình trong khoảng **1–8**, không có cơ chế cấu hình đa game. Đây là công cụ cá nhân — xem [Bảo mật](#bảo-mật) trước khi dùng trên máy có dữ liệu thật.

**Thông tin bản dựng**

| Trường | Giá trị |
|---|---|
| Tên hiển thị (launcher) | `Võ Lâm Idle` |
| Phiên bản | `5.0.0` |
| `versionCode` | `50000` |
| `applicationId` | `com.aistudio.volamidle.qkrvtp` |
| `minSdk` / `targetSdk` / `compileSdk` | 24 / 36 / 36 |
| Ngôn ngữ và UI | Kotlin 2.2.10, Jetpack Compose |
| Build | AGP 9.1.1, Gradle 9.3.1, KSP |
| WebView | `androidx.webkit`, yêu cầu `WebViewFeature.MULTI_PROFILE` |
| Máy chủ game | `https://volamidle.pages.dev` (mặc định) |
| Nhánh chính | `main` (từ 5.0.0) |
| Giấy phép | Không có tệp `LICENSE` — xem [Giấy phép](#giấy-phép) |

## Tính năng

### Nhiều tài khoản, nhiều profile

| Tính năng | Chi tiết trong mã nguồn |
|---|---|
| 1–8 tab | `TabLimits`: `MIN = 1`, `DEFAULT = 4`, `RECOMMENDED_MAX = 4`, `HARD_MAX = 8`; máy `isLowRamDevice` bị chặn ở 4 |
| Cấp phát một lần | `GameTabManager` cấp mảng `SLOT_COUNT = 8` phần tử và **không bao giờ cấp lại**; đổi số tab chỉ bật/tắt ô, nên `onRenderProcessGone`, `Runnable` retry và watchdog giữ index hợp lệ |
| Cách ly phiên | `WebViewCompat.setProfile(webView, "Isolated_Cluster_<id>_Tab_<n>")` khi thiết bị hỗ trợ `MULTI_PROFILE` |
| Cấu hình theo tab | Tài khoản, mật khẩu (che), URL tùy chỉnh, cờ tự động đăng nhập |
| Kiểm tra URL | Chỉ nhận `https` trên `volamidle.pages.dev` hoặc subdomain; chuỗi rỗng dùng mặc định |
| Mở, tải lại, dừng từng tab | `▶ Mở`, `↻`, `⏹` trong sheet nhanh, áp cho tab đang chọn |
| Giả lập UA desktop | Nút `PC` đổi User-Agent và tải lại tab đó |
| Di trú khóa cũ | Đọc lần lượt khóa theo cụm, `pref_*_tab_N` và `*_tab_N` trước khi bỏ trống |
| Lưu đồng bộ | Ghi bằng `SharedPreferences.commit()` để không mất khi tiến trình bị thu hồi |

Ghi chú trung thực: checkbox tự động đăng nhập theo tab được lưu vào prefs nhưng **luồng đăng nhập không đọc cờ này**. Muốn một tab không tự đăng nhập, để trống tài khoản hoặc mật khẩu, hoặc bấm `⏹` cho tab đó.

### Giao diện và bố cục

Toàn bộ chrome quản trị nằm trong **một dải trạng thái 32dp** ở đáy và **một sheet điều khiển nhanh** mở bằng `⋯`. Vùng game chiếm phần còn lại của màn hình.

| Tính năng | Chi tiết |
|---|---|
| Dải trạng thái 32dp | `☰` mở bảng điều khiển đầy đủ · chip tab đang chọn · `T2`, `T3` … · `⋯` mở sheet nhanh. Là **sibling** của `AndroidView`, không phải lớp phủ, nên không ăn chạm vào game |
| Chip tab cuộn ngang | Từ 5 tab, dải chuyển sang `LazyRow`: `☰` và `⋯` ghim hai đầu, chip đang chọn rộng 76dp, các chip còn lại cuộn. Chiều cao dải không đổi |
| Bố cục **4 Dọc** | `cols = min(N, 4)`; quá 4 tab tự xuống hàng, mỗi ô giữ 1/4 chiều ngang, hàng cuối căn trái. Với N ≤ 4 dùng đúng nhánh bố cục cũ |
| Bố cục **Lưới 2x2** | `cols = min(N, 2)`, `rows = ceil(N / 2)` |
| Bố cục **1 Tab** | Một tab toàn màn hình, chuyển bằng chip trên dải trạng thái |
| Quy hoạch bố cục có test | `TabLayoutPlan.planFor(mode, n)` là hàm thuần, có unit test riêng |
| Treo máy | Overlay đen OLED, hạ độ sáng cửa sổ xuống `0.01`, chạm đúp để mở khóa |
| Giữ màn hình sáng | `FLAG_KEEP_SCREEN_ON` bật trong `MainActivity` |
| Viewport cho cột hẹp | Chèn CSS: tắt `backdrop-filter`, đặt `min-width` cho input, `width:100%` dưới 280px |
| Không nháy khi đổi bố cục | Chỉ tháo/gắn lại view khi layout hoặc tab đang chọn thay đổi |
| Chạm chrome không phá game | Mỗi lần chạm gọi `markUiInteraction()`, đẩy mốc chạm của **mọi** tab lên hiện tại; vòng bơm script 850ms bỏ qua tab vừa có chạm trong 5 giây |

### Tự động đăng nhập

| Tính năng | Chi tiết |
|---|---|
| Điền tài khoản và mật khẩu | Đặt giá trị qua native setter của `HTMLInputElement`, bắn `focus`, `keydown`, `input`, `change`, `keyup`, `blur` để React/Vue nhận |
| Tích “Nhớ tài khoản” | Tìm checkbox theo nhãn đã bỏ dấu, bắn `change`, không đụng checkbox hiện mật khẩu hay mật mã |
| Bấm “Xác Định” | Tìm nút theo điểm ưu tiên; dự phòng `Enter` và `form.submit()` |
| Bấm “Bắt Đầu Trò Chơi” | Mở khung đăng nhập rồi thử điền lại ở các mốc 180/380/650/950ms |
| Đồng ý điều khoản | Tự bấm “Tôi Đồng Tình” / “Tôi Đã Hiểu” |
| Xử lý tranh chấp phiên | Tự bấm “Chơi ở đây”, “Chơi ở tab này”, “Tiếp tục ở đây”, “Kết nối lại”, “Thử lại” |
| Chọn nhân vật | Bấm thẻ nhân vật rồi bấm “Xác Định” / “Vào Game” / “Bắt Đầu” |
| Bơm lại sau khi trang xong | Chạy lại script ở 350ms và 800ms nếu tab vẫn `LOGGING_IN` |
| Vòng lặp khi kẹt đăng nhập | Mỗi 850ms bơm lại script cho tab `LOGGING_IN`; tạm dừng 5 giây sau khi người dùng chạm tab |
| Phát hiện bị văng | Watchdog nhận diện màn hình đăng nhập hoặc chọn nhân vật và gọi `onGameKickedDetected` để đăng nhập lại |
| Nhận diện đã vào game | Ưu tiên HUD trận đấu (`Hành trang` + `Kỹ năng`/`Võ công`/`Hệ thống`, hoặc `Chế độ:`); ô chat bị loại khỏi danh sách ô nhập của form đăng nhập |

### Tự động trong game

Điều khiển game qua **kênh lệnh ẩn `#vlmAuto`** của chính game (bản game ≥ 990): tạo một `<button id="vlmAuto">` ẩn, đặt `dataset.act` / `dataset.arg`, gọi `click()`, rồi chờ game ghi `dataset.xong` / `dataset.kq`. Không mô phỏng chuột, không mô phỏng bàn phím.

| Nhóm | Hành động |
|---|---|
| Trợ thủ | Bật Dã Tẩu tự trả/nhận, tự dùng Lệnh Bài Dã Tẩu, tự gọi Sát Thủ |
| Dã Tẩu | Mua Lệnh Bài bằng KNB khóa; đóng Bình Phúc Duyên Lộ và mở Rương Phúc Duyên để lấy Lệnh Bài |
| Tiên Thảo | Dùng Tiên Thảo Lộ / Tiên Thảo Lộ Đặc Biệt trong túi; hết túi thì mua bằng KNB khóa |
| Quà tặng | Nhận quà Điểm danh và Mốc cấp; Khấn tượng +20% kinh nghiệm trong 8 giờ |
| Sự kiện | Đổi Đại Hồng Bao (tối đa 1/ngày) và Hồng Bao (tối đa 3/ngày) |
| Phó bản | Chuyển Chế độ Phó Bản tới khi hết lượt, rồi Dã Tẩu, rồi trả về chế độ cũ |
| Kinh tế | Đổi KNB → KNB khóa; mua Lệnh Bài Sát Thủ; mua Kim Nguyên Bảo ở Kỳ Trân Các |
| Chế độ | Tự chuyển Nhiệm Vụ / Phó Bản theo lượt còn lại và trạng thái nhân vật |

Cấu hình chi tiết ở [Tham chiếu cấu hình tự động](#tham-chiếu-cấu-hình-tự-động). Cổng an toàn: mặc định tổng **TẮT**; các khoản mua bằng KNB mặc định `0`/tắt (riêng `ldtMua` mặc định bật); chỉ đổi chế độ, đi khấn hoặc mở bảng khi tab không bị chạm 60 giây; bản game < 990 thì không làm gì.

### Tối ưu tài nguyên và giữ kết nối

| Cơ chế | Chi tiết |
|---|---|
| Watchdog luân phiên | Mỗi 1200ms quét **một** tab; mỗi tab được quét lại sau ~4,8 giây với 4 tab |
| Dọn DOM luân phiên | Mỗi 90 giây dọn **một** tab; mỗi tab dọn lại sau ~6 phút với 4 tab |
| Dọn theo id thật | Chỉ `#chatLines`, `#u20lines`, `.floating-text`, `.damage-text`, `.damage-number`; giữ 30 dòng chat và 10 hiệu ứng gần nhất |
| Không dọn khi đang gõ | Bỏ qua nếu `activeElement` là `INPUT`, `TEXTAREA`, `SELECT` hoặc `contentEditable` |
| Cập nhật UI có điều kiện | Chỉ phát `StateFlow` khi một trong các trường hiển thị thực sự đổi |
| Giới hạn FPS | 30 hoặc 60 FPS bằng cách override `requestAnimationFrame` (chu kỳ 33ms hoặc 16ms); nhịp nền 20 FPS; kẹp delta ≤ 50ms |
| Không ép lớp cứng | Không dùng `LAYER_TYPE_HARDWARE`; để Chromium tự composite |
| Quét tên có giới hạn | Tối đa 400 node mỗi lượt tìm tên nhân vật |
| Foreground service | `KeepAliveService`, `foregroundServiceType="specialUse"`, subtype “24/7 background game idle manager and partial wakelock” |
| WakeLock | `PARTIAL_WAKE_LOCK` 24 giờ, acquire lại ở mỗi `onStartCommand` |
| WifiLock | `WIFI_MODE_FULL_LOW_LATENCY` trên Android 10+, `WIFI_MODE_FULL_HIGH_PERF` bên dưới |
| Giữ WebView “hiển thị” | `KeepAliveWebView` ép `VISIBLE` và có focus xuống Chromium khi app ra nền |
| Giả lập tab hiển thị | JS ghi đè `document.hidden`, `visibilityState`, `hasFocus`; chặn `visibilitychange` và `pagehide` |
| Audio giữ renderer | Tạo `AudioContext` với gain `0.00001` sau lần chạm đầu tiên |

Android 13+ khai báo `POST_NOTIFICATIONS` nhưng app **không xin quyền runtime**. Không thấy thông báo service thì bật thủ công trong Cài đặt ứng dụng.

### Giám sát, phục hồi và cập nhật

| Tính năng | Chi tiết |
|---|---|
| Trạng thái tab | `IDLE` (⚪ Chờ mở), `LOGGING_IN` (🟡 Đang vào), `IN_GAME` (🟢 Online), `OFFLINE` (🔴 Đã dừng) |
| Thông số nhân vật | Tên, phái, cấp, % EXP, Đại Hồng Bao, Hồng Bao, kỷ lục tháp, Boss Hoàng Kim, nhiệm vụ Dã Tẩu |
| Dòng trạng thái | Mỗi thẻ tab hiển thị `lastStatus` do script hoặc bridge ghi về |
| Lưu lượng | Tổng byte (Rx + Tx) và tốc độ tải xuống, cập nhật mỗi 2 giây; có nút reset mốc |
| Renderer bị thu hồi | `onRenderProcessGone` tháo WebView khỏi cây view, `destroy()`, tạo WebView mới và gắn lại đúng ô qua `registerSlot` |
| Lỗi tải trang | Tối đa 3 lần, giãn cách 2,5s → 5s → 7,5s; `ERROR_HOST_LOOKUP` dừng ngay |
| Không báo sai thành công | `onPageFinished` không ghi “Trang đã sẵn sàng” khi đang ở trang lỗi của Chromium |
| Tự kiểm tra cập nhật | Mỗi 12 giờ ± 30 phút jitter, dùng `ETag` / `If-None-Match`; `304` không tính là lỗi |
| Tải và cài | Tải **thủ công**, cài **không bao giờ tự động** — mỗi lần cần một cú chạm mở `PackageInstaller` và hộp thoại xác nhận của hệ thống |
| Kiểm tra trước khi cài | `getPackageArchiveInfo`: đúng `applicationId`, `versionCode` lớn hơn bản đang chạy, signer trùng app đang cài |
| Toàn vẹn tệp | So `assets[].digest` (SHA-256) của GitHub với file tải về — kiểm tra **toàn vẹn**, không phải ranh giới bảo mật |
| Giới hạn redirect | `followRedirects(false)`; mỗi hop phải HTTPS, host khớp chính xác `api.github.com`, `github.com`, `release-assets.githubusercontent.com`, `objects.githubusercontent.com`, port 443, tối đa 3 hop |

## Cài đặt và lần chạy đầu

### Từ APK phát hành

1. Vào [Releases](https://github.com/gvhoang/volam-idle-manager/releases/tag/v5.0.0), tải `volamidle-manager.apk`.
2. Kiểm tra toàn vẹn trước khi cài:

```bash
sha256sum volamidle-manager.apk
# Windows
certutil -hashfile volamidle-manager.apk SHA256
```

Đối chiếu với digest công bố trong phần mô tả release. README không nhúng checksum vì build lại sẽ ra file khác.

3. Cài:

```bash
adb install -r volamidle-manager.apk
# hoặc mở trực tiếp file APK trên máy và cho phép "Cài từ nguồn không xác định"
```

APK ký bằng khóa release. Nếu `adb` báo `INSTALL_FAILED_UPDATE_INCOMPATIBLE` thì trên máy vẫn còn bản 4.x — xem cảnh báo ở đầu tài liệu.

4. Mở app. Các tab đang bật ở trạng thái `⚪ Chờ mở`.

### Lần chạy đầu

1. Android 13+: bật quyền thông báo cho `Võ Lâm Idle` nếu muốn thấy thông báo của service.
2. Tắt tối ưu pin cho ứng dụng (Cài đặt → Pin → Không giới hạn). OEM sẽ giết foreground service nếu bỏ qua bước này.
3. Mở bảng điều khiển bằng `☰`, bấm `🔑 TK (Đổi)` ở từng tab và nhập tài khoản.
4. Bấm `ĐĂNG NHẬP HẾT`. Theo dõi `🟡 Đang vào` → `🟢 Online`.

### Yêu cầu hệ thống

| Hạng mục | Yêu cầu |
|---|---|
| Android | 7.0 (API 24) trở lên |
| WebView | Bản Chromium hỗ trợ `WebViewFeature.MULTI_PROFILE`. Không hỗ trợ thì app vẫn chạy nhưng mọi tab dùng chung profile mặc định — mất cách ly cookie và phiên |
| RAM | 4 tab cần khoảng 1,0–1,3 GB cho app và renderer; 8 tab đo được ~1,9 GB. Khuyến nghị máy 6–8 GB |
| Mạng | Wi-Fi hoặc 4G ổn định; app giữ WifiLock nhưng không đảm bảo mạng của nhà mạng |
| Quyền khai báo | 8 quyền: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `REQUEST_INSTALL_PACKAGES`. APK merge thêm `READ_GSERVICES` và `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` từ thư viện |
| Tối ưu pin | Bắt buộc tắt tối ưu cho app, nếu không hệ điều hành sẽ giết service |

App bật `android:largeHeap="true"`, nhưng đó chỉ là gợi ý cho heap Java/Kotlin, không tăng RAM cho các tiến trình renderer.

## Sử dụng

### Bảng điều khiển nhanh

Mở bằng `⋯` ở góc phải dải trạng thái 32dp. Sheet là overlay, mở/đóng không đổi kích thước WebView nên game không reflow HUD và không đóng `<select>` đang mở.

| Hàng | Nội dung |
|---|---|
| 1 | `▶ Mở` / `PC` / `↻` / `⏹` cho tab đang chọn |
| 2 | Ba chế độ xem: `4 Dọc`, `2x2`, `1 Tab` |
| 3 | `SỐ TAB` với `−` và `+`, giới hạn 1–8, có xác nhận khi vượt 4 hoặc khi giảm số tab đang chạy |
| 4 | `⚡ 30FPS` / `⚡ 60FPS` và `🌙 Treo máy` |
| 5 | Lưu lượng: tổng MB, tốc độ tải xuống, nút reset |
| 6 | `ĐỒNG BỘ AUTO CẢ <N> TAB` (N là số tab đang bật): `📜 Nhiệm Vụ`, `⚔️ Phó Bản`, `🔥 Luyện Công` |
| 7 | `🤖 Tự động` (mở hộp thoại cấu hình auto-pilot) |
| 8 | `☰ Bảng điều khiển` |
| 9 | `📦 Phiên bản: x.y.z` và các nút kiểm tra, tải, cài cập nhật |

`☰` mở drawer đầy đủ: tài khoản từng tab, `ĐĂNG NHẬP HẾT`, `DỪNG TẤT CẢ`, đồng bộ “Màn hình đen”.

### Số cửa sổ game

1. Mở sheet nhanh, hàng `SỐ TAB`.
2. Giá trị lưu ở khóa `pref_tab_count` và áp dụng ngay, không cần khởi động lại.
3. Tăng vượt 4 lần đầu hiện hộp xác nhận về RAM/CPU. Giảm xuống dưới một tab đang chạy cũng hỏi lại: tab đó mất phiên và phải đăng nhập lại (tài khoản vẫn được lưu).
4. Trên máy `isLowRamDevice`, trần bị hạ xuống 4 và giá trị cao hơn do máy khác để lại cũng bị kẹp về 4 khi đọc.
5. Vượt 4 tab, app tự hạ 60 → 30 FPS một lần (có toast) vì chu kỳ 33ms giảm khoảng một nửa công JS mỗi tab.
6. Nếu hệ thống đang chạy, các tab vừa tăng được mở và đăng nhập tự động, giãn cách 800ms.

### Cấu hình tài khoản cho từng tab

1. Mở bảng điều khiển bằng `☰`.
2. Bấm `🔑 TK (Đổi)` ở tab cần cấu hình.
3. Nhập **Tài khoản**, **Mật khẩu**. Để trống **URL Tùy chỉnh** nếu dùng máy chủ mặc định.
4. URL chỉ được chấp nhận khi là `https://` trên `volamidle.pages.dev` hoặc subdomain; sai thì hiện dòng đỏ “Chỉ chấp nhận HTTPS trên volamidle.pages.dev”.
5. Bấm **Lưu cấu hình**. Nhãn nút đổi thành `🔑 <tên tài khoản> (Đổi)`.

### Đăng nhập và dừng

- `ĐĂNG NHẬP HẾT` trong drawer mở và đăng nhập **mọi** tab đang bật.
- `▶ Mở` trong sheet nhanh chỉ áp cho tab đang chọn.
- `DỪNG TẤT CẢ` dừng toàn bộ; `⏹` dừng tab đang chọn.
- Dòng trạng thái dưới mỗi thẻ cho biết app đang làm gì: “Đang tải trang…”, “Đã ấn [Xác Định]…”, “⚡ Đã tự bấm [Chơi ở đây]…”, “🟢 Đang chạy ổn định”.

### Đồng bộ chế độ

1. Trong sheet nhanh, mục `ĐỒNG BỘ AUTO CẢ <N> TAB` — nhãn tự đổi theo số tab đang bật.
2. Chọn `📜 Nhiệm Vụ`, `⚔️ Phó Bản` hoặc `🔥 Luyện Công`.
3. Script tìm `<select>` của game, đặt `selectedIndex`, bắn `input`/`change`; nếu game dùng nút thay vì select, script bấm nút tương ứng và tránh thanh menu đáy.
4. Kết quả hiện ở dòng trạng thái: `⚡ Chế độ: Phó Bản`.

Checkbox **Màn hình đen** trong drawer bật/tắt cài đặt `Hệ thống → Hiển Thị → Màn hình đen` của game trên mọi tab; dòng phụ hiện `🟢 Đang bật (N Tab)`.

### Bật tự động trong game

1. Bấm `🤖 Tự động` trong sheet nhanh. Nút hiện `⚪ Đang tắt` hoặc `🟢 ĐANG BẬT`.
2. Bật công tắc tổng ở đầu hộp thoại (`🟢 Đang bật cho N tab`).
3. Chọn các nhóm muốn dùng. Mặc định bật các mục miễn phí; các khoản mua bằng KNB mặc định tắt hoặc `0`, riêng `ldtMua` mặc định bật.
4. Bấm **Lưu**. Cấu hình được đẩy xuống các tab đang mở ngay, không chờ nhịp 30 giây.

**Khuyến nghị lần đầu:** chỉ bật auto cho **một tài khoản**, đặt `knbMua = 0`, `lbMua = 0`, `k2k = 0`, `ttlMua = false`, `tt2Mua = false`, `evHb = false`, `evDhb = false`, và **tắt `ldtMua`** nếu muốn tuyệt đối không tiêu KNB khóa. Theo dõi dòng trạng thái vài nhịp để xác nhận app chỉ làm các việc miễn phí (log `🤖` bật Trợ thủ, nhận quà điểm danh) trước khi cho phép tiêu KNB.

**Đặt lại bộ đếm** xoá hạn mức ngày trong `localStorage` của các tab đang mở. Dùng khi bộ đếm cục bộ lệch với bộ đếm của game.

### Treo máy và FPS

- `🌙 Treo máy`: overlay đen, độ sáng cửa sổ `0.01`, đồng hồ vẫn chạy, chạm đúp (hoặc Back) để mở khóa. `FLAG_KEEP_SCREEN_ON` và service vẫn hoạt động.
- `⚡ 30FPS` / `⚡ 60FPS`: lưu vào `pref_fps`, áp ngay cho mọi tab. 30 FPS giảm tải GPU/CPU đáng kể khi chạy nhiều renderer.

### Cập nhật ứng dụng

1. Hàng cuối sheet hiển thị `📦 Phiên bản: 5.0.0` kèm trạng thái cập nhật.
2. `Kiểm tra cập nhật` gọi `api.github.com` và báo `Đã là bản mới nhất` hoặc số phiên bản mới.
3. Có bản mới → `Tải về` → thanh tiến trình → `Cài đặt`.
4. Android hiện hộp thoại xác nhận. Chấp nhận để cài đè; tài khoản đã lưu **giữ nguyên** vì dữ liệu nằm trong `SharedPreferences` và cùng khóa ký.
5. Từ chối quyền “Cài ứng dụng không rõ nguồn gốc” khiến bước cài thất bại — app có nút mở thẳng màn hình cài đặt đó.

Số phiên bản được so bằng `versionCode`, không so chuỗi. Từ 5.0.0 quy ước là:

```text
versionCode = major * 10000 + minor * 100 + patch
5.0.0 → 50000     5.1.0 → 50100     5.1.2 → 50102
```

App đọc `versionCode: N` trong **body release** trước, chỉ suy từ tag khi body không khai báo.

## Tham chiếu cấu hình tự động

Toàn bộ cấu hình lưu thành **một chuỗi JSON** dưới khóa `pref_auto_cfg` (`AutoConfig` trong `webview/GameAutoPilot.kt`).

### 18 trường

| Trường | Nhãn trong hộp thoại | Mặc định | Ghi chú |
|---|---|---|---|
| `on` | công tắc tổng ở đầu hộp thoại | `false` | Không bao giờ tự bật |
| `dt` | Dã Tẩu tối đa: bật Trợ thủ, Chế độ Nhiệm Vụ khi còn lượt | `true` | Yêu cầu cấp ≥ 20 khi vào nhánh Dã Tẩu |
| `ldtMua` | Mua Lệnh Bài Dã Tẩu bằng KNB khóa (20 / cái) | `true` | Chỉ mua khi còn thiếu và chưa đạt trần ngày |
| `pd` | Bình Phúc Duyên Lộ + Rương Phúc Duyên (lấy Lệnh Bài Dã Tẩu) | `true` | Đóng bình khi có bình hoặc ≥ 10 Phúc Duyên; mở rương theo bội số 10 |
| `st` | Sát Thủ: tự gọi khi có Lệnh Bài | `true` | Chỉ chạy khi `I.lb <= 0` và cấp ≥ 20 |
| `pb` | Tự chạy phó bản: Chế độ Phó Bản tới khi hết lượt, rồi Dã Tẩu, rồi về chế độ cũ | `true` | Phát hiện hết lượt sau 2 phút không vào phòng |
| `qua` | Nhận quà Điểm danh + Mốc cấp | `true` | Cách nhau 60 giây |
| `khan` | Khấn tượng mỗi ngày (+20% kinh nghiệm 8 giờ) | `true` | Chỉ khi không ở phó bản/tháp/truy sát và tab rảnh 60 giây |
| `evDhb` | Sự kiện: đổi Đại Hồng Bao (100 mỗi loại, tối đa 1/ngày) | `false` | Trần ngày cứng = 1 |
| `evHb` | Sự kiện: đổi Hồng Bao (20 mỗi loại, tối đa 3/ngày) | `false` | Trần ngày cứng = 3 |
| `ttl` | Tiên Thảo Lộ hết thì dùng trong túi | `true` | Bỏ qua khi game báo giá trị hiệu lực > 60000 |
| `ttlMua` | … túi hết thì mua (20 KNB khóa) | `false` | Mua 1 cái mỗi lần |
| `tt2` | Tiên Thảo Lộ Đặc Biệt hết thì dùng trong túi | `true` | Bỏ qua khi game báo giá trị hiệu lực > 60000 |
| `tt2Mua` | … túi hết thì mua (300 KNB khóa) | `false` | Mua 1 cái mỗi lần |
| `k2k` | Đổi KNB → KNB khóa tối đa | `0` | `0..100000` KNB/ngày; mỗi lệnh tối thiểu 10 KNB; `0` = không đổi |
| `lbMua` | Mua Lệnh Bài Sát Thủ | `0` | `0..100` cái/ngày, 100 KNB/cái; mỗi lệnh tối đa 10 cái |
| `knbMua` | Mua Kim Nguyên Bảo ở Kỳ Trân Các | `0` | `0..1000` KNB/ngày (1 vạn lượng/cái); trần game 1000/ngày |
| `knbGiu` | Giữ lại ít nhất | `0` | `0..10000000` lượng; là **sàn** ngân lượng, không phải số được tiêu |

### Thứ tự và cooldown

Thứ tự chạy cố định trong mỗi nhịp:

```text
troThu → khan → tienThao → qua → daTau → muaLb → doiHongBao → muaKnb → cheDo
```

| Hành động | Cooldown |
|---|---|
| `troThu` (Trợ thủ) | 30 phút |
| `khan` (Khấn tượng) | 10 phút |
| `tienThao` (mỗi loại Tiên Thảo) | 3 phút |
| `qua` (Điểm danh / mốc cấp) | 60 giây |
| `daTau` / mua Lệnh Bài Dã Tẩu | 5 phút |
| `muaLb` (Lệnh Bài Sát Thủ) | 5 phút |
| `doiHongBao` (sự kiện) | 60 giây; lỗi xác nhận thì nghỉ 30 phút |
| `muaKnb` (Kỳ Trân Các) | 60 giây |
| `cheDo` (đổi chế độ) | 60 giây |

### Bộ đếm ngày và cổng an toàn

| Cơ chế | Chi tiết |
|---|---|
| Lưu bộ đếm | `localStorage`, khóa `vlm_auto_<k>`, giá trị `{d:'YYYY-M-D', n:N}` |
| Múi giờ | UTC+7 |
| Trần cứng | `knb` 1000, `evDhb` 1, `evHb` 3 |
| Khóa phụ | `vlm_auto_prev` lưu chế độ cũ để quay về; `vlm_auto_pbx` đánh dấu đã xong phó bản trong ngày |
| Cổng “người dùng rảnh” | Chỉ đổi chế độ, đi khấn, mở bảng Kỳ Trân Các hoặc sự kiện sau 60 giây không chạm tab |
| Cổng phiên bản | Bản game < 990: ghi log một lần rồi không chạy |
| Yêu cầu trạng thái tươi | Chỉ chạy khi trạng thái game cập nhật trong 10 giây gần nhất |
| Chống tiêm hai lần | Đầu script kiểm `window.__vlmAuto.ver`; đã đúng bản thì chỉ cập nhật cấu hình |
| Chống mất đồng bộ | Không đọc được bộ đếm của game thì **không** cho phép mua ở Kỳ Trân Các hay sự kiện |
| Đặt lại | Nút **Đặt lại bộ đếm** xóa các khóa `vlm_auto_*` (giữ `vlm_auto_prev`) trên tab đang mở |

### Khóa lưu trữ

| Khóa | Nội dung |
|---|---|
| `pref_auto_cfg` | Toàn bộ 18 trường dưới dạng một chuỗi JSON |
| `pref_tab_count` | Số tab 1–8 |
| `pref_fps` | `30` hoặc `60` |
| `pref_cluster` | Tên cụm cũ; vẫn được đọc nhưng không còn bộ chọn trên UI |
| `pref_rx_offset` / `pref_tx_offset` | Mốc reset lưu lượng |
| `user_cluster_*` / `pass_cluster_*` / `url_cluster_*` / `auto_cluster_*` | Tài khoản theo tab, ghi đồng thời nhiều định dạng khóa để tương thích bản cũ |
| `pref_user_tab_N` / `pref_pass_tab_N` / `pref_url_tab_N` / `pref_auto_tab_N` | Khóa legacy |
| `user_tab_N` / `pass_tab_N` | Khóa legacy dạng ngắn |

Không có mã hóa: mật khẩu nằm dạng plaintext. Xem [Bảo mật](#bảo-mật).

## Kiến trúc

```text
MainActivity (Jetpack Compose)
  ├─ GameStatusStrip ......... dải 32dp: ☰, chip tab, ⋯ (LazyRow khi > 4 tab)
  ├─ GameWebViewContainer .... N ô game theo TabLayoutPlan, idle overlay, registerSlot
  ├─ ControlSheet ............ sheet 9 hàng: tab, layout, số tab, FPS, mạng, đồng bộ, 🤖, drawer, cập nhật
  ├─ SidebarView ............. drawer: tài khoản từng tab, ĐĂNG NHẬP HẾT / DỪNG TẤT CẢ, Màn hình đen
  ├─ AutoConfigDialog ........ 1 công tắc tổng, 13 ô tích, 4 ô số, nút đặt lại bộ đếm
  └─ OledBlackScreen ......... overlay treo máy
        │
        │ StateFlow + callback
        ▼
MainViewModel (AndroidViewModel)
  ├─ TrafficMonitor .......... TrafficStats theo UID, cập nhật mỗi 2 giây
  ├─ PreferencesManager ...... SharedPreferences: tài khoản, auto, FPS, số tab
  └─ GameTabManager
       ├─ 8 × KeepAliveWebView (profile riêng) ──evaluateJavascript──┐
       ├─ GameAutoPilot ...... giữ script #vlmAuto, đẩy cấu hình     │
       ├─ KeepAliveService ... FGS specialUse + WakeLock + WifiLock  │
       └─ GameScriptInjector . sinh JS: login, quét, chọn, FPS, GC   │
                                                                     ▼
                                                    Trang game (DOM + #vlmAuto)
                                                                     │
                                                                     ▼
                                                    https://volamidle.pages.dev
```

**Luồng đăng nhập.** `loginAll()` bật `isSystemRunning`, start `KeepAliveService`, rồi đi qua `0 until tabCount`: đặt `LOGGING_IN`, chọn URL, `loadUrl`, nghỉ 800ms giữa các tab. `onPageFinished` bơm script giới hạn FPS, script viewport và script đăng nhập nếu tab đang active. Script xử lý theo thứ tự: tranh chấp phiên → form đăng nhập → chọn nhân vật → “Bắt Đầu Trò Chơi” → HUD trận đấu. Thấy HUD thì gọi `AndroidBridge.onInGameDetected(tabIdx)`, tab sang `IN_GAME`. Tab còn `LOGGING_IN` được bơm lại mỗi 850ms, trừ khi vừa bị chạm trong 5 giây.

**Luồng watchdog.** Mỗi 1200ms chọn một tab theo vòng tròn; chỉ quét khi tab active, `IN_GAME`, URL không phải `about:blank` và không bị chạm trong 5 giây. Script ưu tiên bấm nút tranh chấp phiên, rồi kiểm tra dấu hiệu màn hình đăng nhập. Phát hiện màn hình đăng nhập → `onGameKickedDetected` → đăng nhập lại. Vẫn trong game → quét 9 thông số và chỉ phát `StateFlow` khi dữ liệu hiển thị đổi. Song song, mỗi 90 giây dọn DOM một tab.

**Luồng auto-pilot.** `GameAutoPilot` chạy nhịp Kotlin 30 giây: mỗi 4 nhịp bơm lại toàn bộ script, các nhịp còn lại chỉ đẩy cấu hình. Script được cài lại khi URL tab đổi; cổng `window.__vlmAuto.ver` chặn cài trùng. Trong trang, `setInterval` 20 giây gọi `tick()`; `tick()` chỉ chạy khi `on = true`, trạng thái tươi dưới 10 giây và bản game ≥ 990. Sau 30 giây kể từ lúc vào game, `tick()` chạy lần lượt 9 hành động, mỗi hành động tự kiểm cooldown và trần ngày; kết quả ghi qua `AndroidBridge.onGameLog`.

**Luồng chạy nền.** `onPause()` / `onStop()` gọi `onAppBackgrounded()`: bật cờ `keepAliveInBackground`, `resumeTimers()`, đặt renderer priority `IMPORTANT`, đặt `window.__isAppInBackground = true`. `KeepAliveWebView` ép các callback visibility luôn là hiển thị/có focus. Script FPS giả lập tab đang hiển thị và chuyển `requestAnimationFrame` sang `setTimeout` 50ms. `onResume()` đảo lại và chạy GC một lần.

Cấu trúc thư mục đầy đủ và các quyết định thiết kế nằm trong [Tài liệu](#tài-liệu).

## Build từ mã nguồn

### Yêu cầu

| Thành phần | Ghi chú |
|---|---|
| JDK | `gradle/gradle-daemon-jvm.properties` ghim `toolchainVersion=25`; Foojay resolver trong `settings.gradle.kts` tự tải JDK 25 nếu máy chưa có. Mã nguồn biên dịch với Java 11 |
| Android SDK | `platforms;android-36`, `build-tools;36.0.0`, `platform-tools` |
| Gradle | Dùng wrapper trong repo (`gradlew` / `gradlew.bat`), phân phối 9.3.1 |
| Đường dẫn project | **Chỉ ký tự ASCII** — xem ghi chú Windows bên dưới |

```bash
sdkmanager --licenses
sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
```

Các tag cũ `v2.0.0` và `v3.0.0` dùng `compileSdk { version = release(36) { minorApiLevel = 1 } }` nên cần thêm `platforms;android-36.1`. Mã hiện tại dùng `compileSdk = 36`.

### `local.properties`

Tạo ở gốc project, **không commit**:

```properties
# Windows
sdk.dir=C\:\\Users\\<ban>\\AppData\\Local\\Android\\Sdk
# Linux/macOS
# sdk.dir=/home/<ban>/Android/Sdk
```

### Ký bản release

Khóa release là file PKCS12 ở gốc repo, thông tin truy cập nằm trong `keystore.properties` (cả hai đều đã nằm trong `.gitignore` — đừng commit):

```properties
storeFile=volamidle-release.p12
storePassword=<mật khẩu — không ghi vào tài liệu>
keyAlias=upload
keyPassword=<mật khẩu — không ghi vào tài liệu>
```

Thông tin chứng chỉ của khóa đang dùng:

| Thuộc tính | Giá trị |
|---|---|
| Định dạng | PKCS12 (`.p12`) |
| Alias | `upload` |
| Khóa | RSA 4096 |
| Thuật toán chữ ký | SHA384withRSA |
| Hiệu lực đến | 2054-03-29 |
| DN | `CN=Vo Lam Idle Manager, OU=Release Signing, O=Thien Co Labs, L=Ho Chi Minh City, ST=Ho Chi Minh, C=VN` |
| SHA-256 fingerprint | `3A:44:D8:D1:CA:33:4B:6E:49:7E:29:95:B9:70:5A:59:BE:B5:A2:AC:AE:25:BB:C3:1E:9F:72:16:BF:86:90:A7` |

Đối chiếu bằng `apksigner verify --print-certs app-release.apk`. Nếu build không tìm thấy khóa, kiểm tra `keystore.properties` và sự tồn tại của file `.p12`; bản build cũ hơn đọc `KEYSTORE_PATH` / `STORE_PASSWORD` / `KEY_PASSWORD` từ biến môi trường.

> **Sao lưu khóa ở hai nơi offline.** Mất file ⇒ không bản nào tự cập nhật được nữa và người dùng phải gỡ rồi cài tay. Lộ file ⇒ người khác ký được APK mà Android **sẽ** chấp nhận làm bản cập nhật. Từ 5.0.0, khóa này không được đổi nữa.

### Build và kiểm thử

```bash
# Windows dùng gradlew.bat
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

| Đầu ra | Đường dẫn |
|---|---|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Release APK | `app/build/outputs/apk/release/app-release.apk` |

Release dùng signing config `release`; thiếu khóa thì `assembleRelease` dừng ở bước đóng gói. Không phát hành APK debug.

### Cảnh báo Windows: đường dẫn có ký tự ngoài ASCII

Nếu đường dẫn project chứa ký tự có dấu (ví dụ `C:\Code vs AI\võ-lâm-idle`), Gradle ghi classpath vào `@argfile` bằng UTF-8 nhưng JVM đọc file này bằng codepage ANSI của Windows. Kết quả: test báo `ClassNotFoundException` cho **mọi** class dù code biên dịch đúng.

Cách xử lý: clone và build trong thư mục ASCII tuyệt đối, ví dụ `C:\v4rel`. `android.overridePathCheck=true` trong `gradle.properties` chỉ tắt kiểm tra đường dẫn của AGP, **không** sửa lỗi encoding của argfile.

## Quy trình phát hành

Mỗi bản phát hành là một tag `vX.Y.Z` trên nhánh `main`, kèm asset `volamidle-manager.apk` và body release khai báo `versionCode: N` tường minh — app đọc con số này trước khi suy từ tag. Từ 5.0.0, `versionCode` theo công thức `major*10000 + minor*100 + patch`, nên `5.0.0` là `50000` và mọi bản sau phải lớn hơn bản trước. Bản phát hành phải được ký bằng đúng khóa `upload`; ký bằng khóa khác sẽ khiến mọi người dùng hiện tại không cài đè được.

Quy trình đầy đủ, gồm checklist trước khi tag, kiểm tra signer và phần body release: [`docs/RELEASE.md`](docs/RELEASE.md).

## Kiểm thử

23 unit test trong `app/src/test/`, hai nhóm: **kiểm tra sự hiện diện** (soi chuỗi JavaScript do Kotlin sinh ra — không chạy JS thật) và **kiểm tra hành vi** (gọi hàm thuần của Kotlin).

| Tệp | Số test | Nội dung |
|---|---|---|
| `ExampleUnitTest` | 7 | Sanity check JUnit; 3 bố cục `FOUR_COLUMNS` / `TWO_BY_TWO` / `SINGLE_TAB`; script login và watchdog có “Chơi ở đây”, “Chơi ở tab này”; script đồng bộ chế độ; script màn hình đen; script keepalive chống jitter (`visibilityState`, `__isAppInBackground`, `safeDelta`, `initSilentKeepAliveAudio`); script login mới (`isVisible`, “bat dau tro choi”, “nho tai khoan”, `findLoginSubmitButton`) |
| `ExampleRobolectricTest` | 1 | `R.string.app_name == "Võ Lâm Idle"` với Robolectric `sdk = 36` |
| `TabLayoutPlanTest` | 7 | `n ≤ 4` dùng nhánh legacy; `n = 5..8` ra `cols = 4`, `rows = 2`; lưới 2x2 ra `cols = 2`; 1 Tab luôn `cols = 1, rows = 1`; biên `MIN`/`HARD_MAX` |
| `UpdateRulesTest` | 8 | `versionCode` theo công thức từ tag; body `versionCode` thắng tag; body rác hoặc tag lạ trả `null`; allowlist chấp nhận đúng 4 host GitHub và từ chối host lạ, HTTP, port khác |

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Có thêm 1 instrumented test (`ExampleInstrumentedTest`) kiểm tra `applicationId`, chạy bằng `./gradlew :app:connectedDebugAndroidTest` với thiết bị hoặc emulator.

## Xử lý sự cố

| Triệu chứng | Nguyên nhân | Cách xử lý |
|---|---|---|
| Cài 5.0.0 lên bản 4.x báo `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 5.0.0 ký bằng khóa release, 4.x ký bằng debug key | Gỡ bản cũ rồi cài lại — xem cảnh báo ở đầu tài liệu. Tài khoản đã lưu sẽ mất |
| Chỉ 1 tab Online, các tab khác kẹt “Đang vào” | Lỗi lịch sử: nhận diện “đã vào game” dựa trên việc không còn ô nhập, nhưng màn hình game có ô chat | Đã sửa từ 4.0: ưu tiên HUD trận đấu, loại ô chat khỏi danh sách ô nhập. Kiểm tra đang chạy đúng bản; xem dòng trạng thái từng tab; thử `↻` hoặc `▶ Mở` |
| Không mở hoặc chọn được dropdown trong game | Vòng lặp đăng nhập 850ms bắn click ra ngoài làm đóng `<select>` đang mở | Đã sửa: vòng lặp có touch-guard 5 giây và chỉ chạy khi tab `LOGGING_IN`. Nếu vẫn gặp, chờ 5 giây không chạm |
| Tab hiện “Đang kết nối lại máy chủ…” mãi | Bản cũ thử lại vô hạn khi lỗi main-frame | Tối đa 3 lần (2,5s → 5s → 7,5s), sau đó báo “❌ Máy chủ không phản hồi…”. Kiểm tra mạng, DNS và URL; `ERROR_HOST_LOOKUP` dừng ngay |
| Test báo `ClassNotFoundException` cho mọi class | Đường dẫn project có ký tự có dấu; JVM đọc `@argfile` bằng ANSI | Build tại đường dẫn ASCII, ví dụ `C:\v4rel` |
| Build lỗi giấy phép SDK hoặc không thấy platform 36 | Chưa cài platform hoặc chưa chấp nhận giấy phép | `sdkmanager --licenses`, cài `platforms;android-36` và `build-tools;36.0.0`; tag cũ cần `platforms;android-36.1` |
| `Keystore file not found` | Thiếu `debug.keystore` ở gốc repo, hoặc thiếu khóa release khi build `assembleRelease` | Tạo `debug.keystore` cho bản debug; release cần `keystore.properties` trỏ tới file `.p12` hợp lệ |
| Bỏ tick tự động đăng nhập nhưng app vẫn đăng nhập | Cờ `autoLoginEnabled` được lưu nhưng luồng login không đọc | Để trống tài khoản hoặc mật khẩu của tab đó, hoặc bấm `⏹` |
| Không thấy thông báo service | Android 13+ chưa cấp `POST_NOTIFICATIONS`; app không xin runtime | Bật thông báo trong Cài đặt ứng dụng |
| Mất kết nối khi khóa màn hình hoặc để lâu | OEM tối ưu pin giết foreground service | Tắt tối ưu pin, đặt “Không giới hạn”, khóa app trong recent apps nếu OEM yêu cầu |
| Treo máy không mở khóa được | Overlay chỉ nhận chạm đúp | Chạm đúp hai lần liên tiếp, hoặc bấm Back |
| Tự động không làm gì | Công tắc tổng tắt; game < 990; chưa vào game đủ 30 giây; đang trong phó bản/tháp/truy sát/đã chết; tab chưa `IN_GAME` | Bật công tắc tổng, xem dòng trạng thái có log `🤖`, chờ ngoài phó bản, kiểm tra phiên bản game |
| Mua KNB nhưng không nhận được | Không đọc được bộ đếm hoặc trạng thái ví của game; giao dịch chưa xác nhận | Xem log “Kỳ Trân Các: đã bấm mua nhưng không nhận được KNB”; đặt `knbMua = 0` và kiểm tra thủ công |
| Máy quá nóng hoặc tụt pin | Nhiều renderer cùng chạy | Giảm số tab về 4 hoặc ít hơn, hạ FPS xuống 30, dừng bớt tab bằng `⏹` |
| Tăng lên 6–8 tab rồi vài tab tự đăng nhập lại | Android thu hồi renderer vì thiếu RAM; app bắt `onRenderProcessGone` và tạo lại WebView cho đúng ô | Giảm số tab, đóng app khác, tăng RAM máy. Đây là hành vi đúng, không phải crash |
| Bấm cập nhật nhưng không cài được | Chưa cấp quyền “Cài ứng dụng không rõ nguồn gốc”, hoặc APK tải về có signer khác | Cấp quyền trong Cài đặt. Nếu signer khác, phải gỡ bản cũ rồi cài tay một lần |
| Báo “Đã là bản mới nhất” dù GitHub có bản mới | Body release không khai báo `versionCode`, và tag không suy được số | Thêm dòng `versionCode: N` vào body release; `N` phải lớn hơn `versionCode` đang chạy (5.0.0 là `50000`) |

## Hiệu năng

Đo trên LDPlayer 14, Android 14 x86_64, VM 4 nhân / 6 GB, bằng jiffies `/proc` cho tiến trình app và từng renderer.

**4 tab — so sánh trước và sau tối ưu (4 tài khoản thật):**

| Chỉ số | Bản trước tối ưu | Từ bản 4.0.0 |
|---|---|---|
| CPU tiến trình app | 81% | 80% |
| Tab Online | 1–2 / 4 | 4 / 4 |
| RAM PSS của app | — | ~222 MB, ổn định sau 3 phút |

Mức CPU gần như không đổi, nhưng cùng chi phí đó nuôi được 4 tab thay vì 1–2. Phần lớn tải đến từ các tiến trình renderer của Chromium đang vẽ game, không phải Kotlin/Compose.

**8 tab — 8 tài khoản đã vào game, đo trong 30 giây:**

| Chỉ số | Giá trị |
|---|---|
| Tiến trình app | 100,3% một nhân |
| 8 tiến trình renderer | 12,4 – 30,3% một nhân, tổng ~119% |
| Tổng | ~220% một nhân (~55% của VM 4 nhân) |
| RSS tiến trình app | 271 MB |
| RSS mỗi renderer | 166 – 291 MB |
| Tổng RSS | ~1,9 GB |
| Số tiến trình renderer | 8 — đúng bằng số tab |

**Soak 30 phút ở N = 8:** 0 crash, 0 lần `onRenderProcessGone`.

Chi phí gần như tuyến tính theo số tab: mỗi renderer đã vào game giữ 170–290 MB. Đó là lý do `RECOMMENDED_MAX = 4` và vì sao hộp xác nhận hiện ra khi vượt mốc. Cảnh báo trong app dùng ước lượng thận trọng hơn (280–320 MB/tab, ~2,4 GB cho 8 tab).

Các thay đổi tạo ra kết quả này:

| Thay đổi | Tác động |
|---|---|
| Watchdog 4 tab/2,5s → 1 tab/1,2s | Giảm khoảng 2 lần số lượt quét DOM mỗi giây |
| GC 45s → 90s và luân phiên | Mỗi tab dọn lại sau ~6 phút thay vì mỗi 45 giây |
| So sánh các trường trước khi phát `StateFlow` | Không recompose UI mỗi nhịp quét |
| Bỏ `LAYER_TYPE_HARDWARE` | Bỏ texture lớp phủ, giảm RAM và compositing |
| Giới hạn 400 node khi quét tên | Tránh duyệt toàn bộ DOM mỗi lượt |
| Shim `cancelAnimationFrame` | Không rò rỉ `setTimeout` mỗi frame |

Muốn giảm tải thêm một cách thực chất chỉ có ba cách: giảm số tab, hạ FPS xuống 30, hoặc giảm tần suất watchdog và GC.

## Bảo mật

**Trạng thái 5.0.0: đã sửa phần ký và đóng gói, chưa hardened phần WebView và lưu trữ.** Danh sách dưới đây là các vấn đề còn tồn tại trong mã hiện tại, không phải giả định.

| Mức | Vấn đề | Hệ quả |
|---|---|---|
| Nghiêm trọng | `onReceivedSslError` gọi `handler?.proceed()` vô điều kiện | Chấp nhận chứng chỉ hết hạn, sai host hoặc tự ký; người đứng giữa đọc được mật khẩu và phiên |
| Nghiêm trọng | `onPageFinished` bơm script điền mật khẩu vào **bất kỳ** trang nào tải xong khi hệ thống đang chạy và tab active; không kiểm tra host tại thời điểm inject | Một redirect hoặc XSS trên origin tin cậy có thể nhận được mật khẩu đã điền |
| Cao | `addJavascriptInterface(..., "AndroidBridge")` không giới hạn origin, không bao giờ gỡ | JavaScript trong mọi frame gọi được callback đổi trạng thái, ghi log, đăng nhập lại |
| Cao | Mật khẩu lưu plaintext trong `SharedPreferences`, ghi lặp qua nhiều định dạng khóa | Root, `adb run-as` hoặc forensics đọc được toàn bộ |
| Trung bình | `mixedContentMode = MIXED_CONTENT_ALWAYS_ALLOW` | Tài nguyên HTTP có thể bị chèn vào trang HTTPS |
| Thấp | Không certificate pinning | Phụ thuộc hoàn toàn vào CA tin cậy của hệ thống |

**5.0.0 đã sửa:**

| Thay đổi | Ý nghĩa |
|---|---|
| Ký bằng khóa release thật, alias `upload` | Bản cập nhật phải cùng khóa mới cài đè được; chữ ký trở thành ranh giới thật của kênh cập nhật |
| Release `isDebuggable = false` | `adb run-as` không còn đọc được `SharedPreferences` của bản phát hành |
| Release minify và shrink | Giảm kích thước APK và bớt mã không dùng trong bản phát hành |

Đã tắt đúng và không cần xử lý thêm: `android:allowBackup="false"`, `android:usesCleartextTraffic="false"`, `allowFileAccess = false`, `allowContentAccess = false`. Lưu ý: `backup_rules.xml` và `data_extraction_rules.xml` vẫn là mẫu rỗng, nên nếu ai đó bật lại `allowBackup` thì toàn bộ prefs (gồm mật khẩu) sẽ vào backup.

**Về kênh tự cập nhật.** Ranh giới thật là **chữ ký APK**, do Android so signer set với app đang cài. `assets[].digest` (SHA-256) cùng nguồn với file APK nên chỉ bắt được hỏng hóc khi tải, **không** bắt được kẻ sửa release — đó là kiểm tra toàn vẹn, không phải ranh giới bảo mật. Muốn thành ranh giới thật thì phải ký digest ngoài luồng (minisign/GPG) với public key nhúng trong app.

Bảng đầy đủ, phần thay đổi bị revert và **thứ tự hardening 11 bước**: [`SECURITY.md`](SECURITY.md). Hai bước đầu — `handler.cancel()` và `MIXED_CONTENT_NEVER_ALLOW` — là thay đổi nhỏ nhất loại bỏ được cả hai lỗi Nghiêm trọng.

## Lịch sử phiên bản

| Phiên bản | Nội dung chính |
|---|---|
| **5.0.0** | Ký bằng khóa release thật (**breaking**: phải gỡ bản 4.x, mất tài khoản đã lưu); quy ước `versionCode` mới; `main` là nhánh chính thức duy nhất; viết lại README và thêm `CHANGELOG.md` |
| **4.1.0** | Chrome 108dp → dải trạng thái 32dp + sheet điều khiển nhanh; số tab 1–8; tự cập nhật từ GitHub Releases |
| **4.0.0** | Port bộ tự động `#vlmAuto` (18 trường); sửa treo khung sau khi chat; DOM GC theo id thật; watchdog luân phiên 1,2s |
| **3.0.1** | Gỡ bộ chọn Cụm; hardening WebView (sau bị revert ở 4.0); retry có giới hạn |
| **3.0.0** | Nhánh `v3`; giữ bộ chọn Cụm; bước đầu tự động hóa trong game |
| **2.0.0** | Nhánh `main`; gỡ VPN và chọn Cụm; giữ tài khoản khi nâng cấp; thêm Gradle wrapper cho Windows |
| **1.0** | Bản đầu: 4 tab WebView, tự đăng nhập, có VPN và chọn Cụm |

Chi tiết thay đổi, lý do và các mục đã bị gỡ: [`CHANGELOG.md`](CHANGELOG.md).

## About

Dự án bắt đầu như một công cụ cá nhân để giữ vài tài khoản Võ Lâm Idle chạy ổn định qua đêm trên một chiếc điện thoại Android, rồi dần được cộng đồng dùng thật đẩy thành thứ phải chạy đúng 24/7: nhiều profile cách ly, tự đăng nhập lại, tự làm việc trong game và tự cập nhật.

Dự án tồn tại và tiếp tục được cải thiện nhờ những người đã bỏ thời gian thử, báo lỗi và chỉ ra chỗ sai — cảm ơn

- **@vnlengkeng**
- **@Thích Kim Kim**
- **Admin @NOV**
- **Phát Ngôn Viên: @Mr Hoàng**

và toàn bộ cộng đồng **Võ Lâm Idle** trên Discord. Những vấn đề tưởng nhỏ — treo khung sau khi chat, tab kẹt “Đang vào”, dropdown không mở được, hay việc cần chạy quá 4 tab — đều được phát hiện từ việc dùng thật và thúc đẩy thành thay đổi trong các bản 4.x và 5.0.0.

Tham gia trao đổi, báo lỗi và theo dõi bản phát hành tại **https://discord.gg/volamidle**.

## Giấy phép

Repo **không có tệp `LICENSE`**. Theo mặc định của luật bản quyền, không có giấy phép nghĩa là **không** cấp quyền tái sử dụng, phân phối lại hoặc tạo tác phẩm phái sinh. Toàn bộ quyền được bảo lưu.

Không có quy trình đóng góp chính thức và không có issue template. CI (`.github/workflows/ci.yml`) chỉ chạy unit test + lint trên `main`, không ký APK và không cần khoá. Nếu bạn đọc mã để học, hãy giữ nguyên các ghi chú về giới hạn bảo mật và tự đánh giá rủi ro trên thiết bị của mình.

## Tài liệu

| Tài liệu | Nội dung |
|---|---|
| [`CHANGELOG.md`](CHANGELOG.md) | Lịch sử thay đổi theo định dạng Keep a Changelog |
| [`SECURITY.md`](SECURITY.md) | Đánh giá bảo mật tĩnh, bảng finding đầy đủ và thứ tự hardening 11 bước |
| [`docs/RELEASE.md`](docs/RELEASE.md) | Quy trình phát hành: checklist, ký, tag, body release, kiểm tra sau khi phát hành |
| [`docs/DESIGN-LAYOUT.md`](docs/DESIGN-LAYOUT.md) | Đặc tả bố cục 32dp: ngân sách chiều cao, vị trí từng nút, vì sao dải đáy phải là sibling chứ không phải overlay |
| [`docs/DESIGN-TAB-COUNT.md`](docs/DESIGN-TAB-COUNT.md) | Đặc tả 1–8 tab: `TabLimits`, quy tắc `cols`/`rows`, cách giữ nguyên pixel ở N ≤ 4, kế hoạch đo và cổng ship N = 8 |
| [`docs/DESIGN-AUTOUPDATE.md`](docs/DESIGN-AUTOUPDATE.md) | Đặc tả tự cập nhật: state machine, allowlist host, kiểm tra signer, vì sao SHA-256 không phải ranh giới bảo mật |
| [`docs/PORT-DISCORD.md`](docs/PORT-DISCORD.md) | Hướng dẫn port cảnh báo Discord webhook từ bản PC. **Tài liệu thiết kế — app chưa có tính năng Discord** |
