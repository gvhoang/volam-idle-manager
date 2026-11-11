# Võ Lâm Idle Manager

Chạy 4 tài khoản webgame **Võ Lâm Idle** song song trên Android: 4 WebView cách ly, tự đăng nhập, tự phục hồi khi văng, giữ kết nối 24/7 và tự động hóa việc trong game.

| Trường | Giá trị |
|---|---|
| Tên dự án | Võ Lâm Idle Manager |
| Tên hiển thị (launcher) | `Võ Lâm Idle` |
| Phiên bản | 4.0.0 |
| `versionCode` | 4 |
| `applicationId` | `com.aistudio.volamidle.qkrvtp` |
| `minSdk` | 24 (Android 7.0) |
| `targetSdk` / `compileSdk` | 36 / 36 |
| Ngôn ngữ & UI | Kotlin 2.2.10, Jetpack Compose (BOM `2024.09.00`) |
| Build | AGP 9.1.1, Gradle 9.3.1, KSP 2.3.5 |
| WebView | `androidx.webkit:webkit:1.11.0`, yêu cầu `MULTI_PROFILE` |
| Máy chủ game | `https://volamidle.pages.dev` (hardcode) |
| Giấy phép | Không có tệp `LICENSE` trong repo |

**Trạng thái: dùng cá nhân — chưa hardened.** Bản APK đang lưu hành là bản debug, mật khẩu lưu plaintext và WebView chấp nhận mọi chứng chỉ TLS. Đọc [mục 15](#15-bảo-mật) trước khi cài lên máy có dữ liệu thật.

---

## 2. Mục lục

- [Giới thiệu](#3-giới-thiệu)
- [Tính năng](#4-tính-năng)
- [Bảng tham chiếu cấu hình Tự động](#5-bảng-tham-chiếu-cấu-hình-tự-động)
- [Kiến trúc](#6-kiến-trúc)
- [Cấu trúc mã nguồn](#7-cấu-trúc-mã-nguồn)
- [Yêu cầu hệ thống](#8-yêu-cầu-hệ-thống)
- [Cài đặt](#9-cài-đặt)
- [Hướng dẫn sử dụng](#10-hướng-dẫn-sử-dụng)
- [Build từ mã nguồn](#11-build-từ-mã-nguồn)
- [Kiểm thử](#12-kiểm-thử)
- [Xử lý sự cố](#13-xử-lý-sự-cố)
- [Hiệu năng](#14-hiệu-năng)
- [Bảo mật](#15-bảo-mật)
- [Lịch sử phiên bản](#16-lịch-sử-phiên-bản)
- [Đóng góp & giấy phép](#17-đóng-góp--giấy-phép)
- [Tài liệu khác](#18-tài-liệu-khác)

---

## 3. Giới thiệu

Võ Lâm Idle là một webgame idle chạy hoàn toàn trong trình duyệt. Chơi nhiều tài khoản cùng lúc theo cách thông thường nghĩa là mở nhiều tab, mỗi tab giữ một phiên WebSocket riêng, và mọi tab đều bị trình duyệt/Android tạm dừng khi chuyển ứng dụng hoặc tắt màn hình. Kết quả là nhân vật đứng yên, gói tin dồn lại, và khi quay lại thì game tua nhanh hoặc nhảy vị trí.

Ứng dụng này nhúng 4 WebView ngay trong một app Android. Mỗi WebView có profile Chromium cách ly (cookie, `localStorage`, session riêng), được giữ ở trạng thái hiển thị kể cả khi app ra nền, và được tự động đăng nhập/đăng nhập lại khi bị văng phiên. Một foreground service giữ CPU và Wi-Fi thức; một lớp script bơm vào trang lo phần đăng nhập, quét trạng thái và điều khiển game.

Phạm vi rõ ràng: một máy chủ game duy nhất, bốn tab cố định, không có cơ chế cấu hình đa game. Đây là công cụ cá nhân, không phải sản phẩm phát hành; các giới hạn bảo mật được liệt kê đầy đủ ở [mục 15](#15-bảo-mật).

---

## 4. Tính năng

Chỉ những mục có trong mã nguồn 4.0.0 mới được liệt kê. Các thành phần còn tồn tại trong mã nhưng không có UI hoặc không được gọi sẽ được ghi chú riêng.

### 4.1 Quản lý tài khoản

| Tính năng | Chi tiết trong mã |
|---|---|
| 4 tab cố định | `GameTabManager` tạo sẵn 4 `KeepAliveWebView` ngay khi khởi động |
| Cách ly phiên | `WebViewCompat.setProfile(webView, "Isolated_Cluster_<id>_Tab_<index>")` khi WebView hỗ trợ `MULTI_PROFILE` |
| Cấu hình theo tab | Tài khoản, mật khẩu (che), URL tùy chỉnh, checkbox tự động đăng nhập |
| Kiểm tra URL | Chỉ chấp nhận `https` và host đúng `volamidle.pages.dev` hoặc subdomain; chuỗi rỗng = dùng mặc định |
| Mở / dừng từng tab | Nút `▶ Mở`, `⏹` trên header của từng ô game |
| Tải lại từng tab | Nút `↻` gọi `WebView.reload()` |
| Giả lập UA PC | Nút `PC` đổi User-Agent desktop và reload tab đó |
| Di trú khóa cũ | Đọc lần lượt khóa theo cụm, khóa `pref_*` và khóa `user_tab_N` trước khi bỏ trống |
| Lưu đồng bộ | `SharedPreferences.commit()` khi lưu tài khoản/cấu hình để không mất khi tiến trình bị thu hồi |

Ghi chú trung thực: checkbox **“Cho phép tự động đăng nhập khi bấm [Đăng nhập tất cả]”** được lưu vào prefs nhưng luồng đăng nhập hiện **không đọc cờ này**. Muốn một tab không tự đăng nhập, để trống tài khoản hoặc mật khẩu, hoặc bấm `⏹` cho tab đó.

### 4.2 Giao diện & bố cục

| Tính năng | Chi tiết |
|---|---|
| Bố cục **4 Dọc** | 4 cột song song trong một hàng (`1x4`), mỗi tab ~1/4 chiều ngang |
| Bố cục **Lưới 2x2** | 2 hàng × 2 cột |
| Bố cục **1 Tab** | Một tab toàn màn hình, chuyển bằng chip `T1`–`T4` trên header và trên thanh tab |
| Sidebar | Drawer trượt 320dp ở màn hình dọc; cột cố định 265dp ở màn hình ngang |
| Master controls | `ĐĂNG NHẬP HẾT` mở và đăng nhập cả 4 tab; `DỪNG TẤT CẢ` dừng cả 4 tab và service |
| Đồng bộ “Màn hình đen” | Checkbox trong sidebar bật/tắt cài đặt `Hệ thống → Hiển Thị → Màn hình đen` của game trên cả 4 tab |
| Header | Badge lưu lượng, nút FPS, `🌙 Treo máy`, nút chuyển VIEW |
| Treo máy | Overlay đen OLED, hạ độ sáng cửa sổ xuống `0.01`, chạm đúp để mở khóa |
| Giữ màn hình sáng | `FLAG_KEEP_SCREEN_ON` được bật trong `MainActivity` |
| Viewport cho cột hẹp | Chèn CSS: tắt `backdrop-filter`, đặt `min-width` cho input, `width:100%` dưới 280px |
| Không nháy khi đổi bố cục | Chỉ tháo/gắn lại view khi layout hoặc tab được chọn thay đổi |

### 4.3 Tự động đăng nhập

| Tính năng | Chi tiết |
|---|---|
| Điền tài khoản/mật khẩu | Đặt giá trị qua native setter của `HTMLInputElement`, bắn `focus`, `keydown`, `input`, `change`, `keyup`, `blur` để React/Vue nhận |
| Tích “Nhớ tài khoản” | Tìm checkbox theo nhãn đã bỏ dấu, bắn `change`, không đụng checkbox hiện mật khẩu/mật mã |
| Bấm “Xác Định” | Tìm nút theo điểm ưu tiên; dự phòng `Enter` và `form.submit()` |
| Bấm “Bắt Đầu Trò Chơi” | Mở khung đăng nhập, sau đó thử điền lại ở các mốc 180/380/650/950ms |
| Đồng ý điều khoản | Tự bấm “Tôi Đồng Tình” / “Tôi Đã Hiểu” |
| Xử lý tranh chấp phiên | Tự bấm “Chơi ở đây”, “Chơi ở tab này”, “Tiếp tục ở đây”, “Kết nối lại”, “Thử lại” |
| Chọn nhân vật | Bấm thẻ nhân vật rồi bấm “Xác Định” / “Vào Game” / “Bắt Đầu” |
| Bơm lại sau khi trang xong | Chạy lại script ở 350ms và 800ms nếu tab vẫn ở trạng thái `LOGGING_IN` |
| Vòng lặp khi kẹt đăng nhập | Mỗi 850ms bơm lại script cho tab `LOGGING_IN`, tạm dừng 5s sau khi người dùng chạm tab |
| Phát hiện bị văng | Watchdog nhận diện màn hình đăng nhập/chọn nhân vật và gọi `onGameKickedDetected` để đăng nhập lại |

### 4.4 Tự động trong game

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

Cấu hình chi tiết ở [mục 5](#5-bảng-tham-chiếu-cấu-hình-tự-động). Các cổng an toàn: mặc định tổng **TẮT**; các khoản mua bằng KNB thường mặc định `0`/tắt (riêng `ldtMua` — Lệnh Bài Dã Tẩu bằng KNB khóa — mặc định bật); chỉ đổi chế độ/đi khấn/mở bảng khi tab không bị chạm 60 giây; bản game < 990 thì không làm gì.

### 4.5 Tối ưu tài nguyên

| Cơ chế | Chi tiết |
|---|---|
| Watchdog luân phiên | Mỗi 1200ms quét **một** tab; mỗi tab được quét lại sau ~4.8s |
| Dọn DOM luân phiên | Mỗi 90s dọn **một** tab; mỗi tab dọn lại sau ~6 phút |
| Dọn theo id thật | Chỉ dùng `#chatLines`, `#u20lines`, `.floating-text`, `.damage-text`, `.damage-number`; giữ 30 dòng chat và 10 hiệu ứng gần nhất |
| Không dọn khi đang gõ | Bỏ qua nếu `activeElement` là `INPUT`, `TEXTAREA`, `SELECT` hoặc `contentEditable` |
| Cập nhật UI có điều kiện | Chỉ phát `StateFlow` khi 1 trong 11 trường hiển thị thực sự đổi |
| Giới hạn FPS | 30 hoặc 60 FPS; override `requestAnimationFrame`; nhịp nền 20 FPS; kẹp delta ≤ 50ms |
| Không ép lớp cứng | Không dùng `LAYER_TYPE_HARDWARE`; để Chromium tự composite |
| Quét tên có giới hạn | Tối đa 400 node cho mỗi lượt tìm tên nhân vật |

### 4.6 Giữ kết nối nền

| Cơ chế | Chi tiết |
|---|---|
| Foreground service | `KeepAliveService`, `foregroundServiceType="specialUse"`, subtype “24/7 background game idle manager and partial wakelock” |
| WakeLock | `PARTIAL_WAKE_LOCK` 24 giờ, tự acquire lại ở mỗi `onStartCommand` |
| WifiLock | `WIFI_MODE_FULL_LOW_LATENCY` trên Android 10+, `WIFI_MODE_FULL_HIGH_PERF` bên dưới |
| Giữ WebView “hiển thị” | `KeepAliveWebView` ép `VISIBLE`/có focus xuống Chromium khi app ra nền |
| Giả lập tab hiển thị | JS ghi đè `document.hidden`, `visibilityState`, `hasFocus`; chặn `visibilitychange` và `pagehide` |
| Nhịp nền | Khi app ra nền, `requestAnimationFrame` chuyển sang `setTimeout` 50ms (20 FPS) |
| Audio giữ renderer | Tạo `AudioContext` với gain `0.00001` sau lần chạm đầu tiên |
| Thông báo thường trực | “Võ Lâm Idle Manager — Đang chạy ngầm 24/7…” |

Android 13+ khai báo `POST_NOTIFICATIONS` nhưng app **không xin quyền runtime**. Nếu không thấy thông báo, bật thủ công trong Cài đặt ứng dụng.

### 4.7 Giám sát & phục hồi

| Tính năng | Chi tiết |
|---|---|
| Trạng thái tab | `IDLE` (⚪ Chờ mở), `LOGGING_IN` (🟡 Đang vào), `IN_GAME` (🟢 Online), `OFFLINE` (🔴 Đã dừng) |
| Thông số nhân vật | Tên, phái, cấp, % EXP, Đại Hồng Bao, Hồng Bao, kỷ lục tháp, Boss Hoàng Kim, nhiệm vụ Dã Tẩu |
| Dòng trạng thái | Mỗi thẻ tab hiển thị `lastStatus` do script hoặc bridge ghi về |
| Lưu lượng | Tổng byte (Rx+Tx) và tốc độ tải xuống, cập nhật mỗi 2 giây; nút reset đặt lại mốc |
| Renderer bị thu hồi | `onRenderProcessGone` tháo WebView khỏi cây view, `destroy()`, tạo WebView mới và gắn lại đúng ô qua `registerSlot` |
| Lỗi tải trang | Tối đa 3 lần, giãn cách 2.5s → 5s → 7.5s; `ERROR_HOST_LOOKUP` dừng ngay; hủy hẹn giờ cũ trước khi đặt lần mới |
| Không báo sai thành công | `onPageFinished` không ghi “Trang đã sẵn sàng” khi đang ở trang lỗi của Chromium |
| Tự đăng nhập lại | Khi watchdog thấy màn hình đăng nhập hoặc mất phiên, tab về `LOGGING_IN` và được đăng nhập lại |

---

## 5. Bảng tham chiếu cấu hình Tự động

Toàn bộ cấu hình được lưu thành **một chuỗi JSON** dưới khóa `pref_auto_cfg`. Nhãn dưới đây là chuỗi hiển thị trong hộp thoại `🤖 Tự động`.

| Trường | Nhãn trong UI | Mặc định | Khoảng / ghi chú |
|---|---|---|---|
| `on` | Bật tự động | `false` | Công tắc tổng; không bao giờ tự bật |
| `dt` | Dã Tẩu tối đa: bật Trợ thủ, Chế độ Nhiệm Vụ khi còn lượt | `true` | Yêu cầu cấp ≥ 20 khi vào nhánh Dã Tẩu |
| `ldtMua` | Mua Lệnh Bài Dã Tẩu bằng KNB khóa (20 / cái) | `true` | Chỉ mua khi còn thiếu và chưa đạt trần ngày |
| `pd` | Bình Phúc Duyên Lộ + Rương Phúc Duyên (lấy Lệnh Bài Dã Tẩu) | `true` | Đóng bình khi có bình hoặc ≥10 Phúc Duyên; mở rương theo bội số 10 |
| `st` | Sát Thủ: tự gọi khi có Lệnh Bài | `true` | Cần có Lệnh Bài Sát Thủ trong túi |
| `pb` | Tự chạy phó bản: Chế độ Phó Bản tới khi hết lượt, rồi Dã Tẩu, rồi về chế độ cũ | `true` | Phát hiện hết lượt sau 2 phút không vào phòng |
| `qua` | Nhận quà Điểm danh + Mốc cấp | `true` | Chờ 60 giây giữa hai lần thử |
| `khan` | Khấn tượng mỗi ngày (+20% kinh nghiệm 8 giờ) | `true` | Chỉ khi không ở phó bản/tháp/truy sát và tab rảnh 60s |
| `evDhb` | Sự kiện: đổi Đại Hồng Bao (100 mỗi loại, tối đa 1/ngày) | `false` | Trần ngày cứng = 1 |
| `evHb` | Sự kiện: đổi Hồng Bao (20 mỗi loại, tối đa 3/ngày) | `false` | Trần ngày cứng = 3 |
| `ttl` | Tiên Thảo Lộ hết thì dùng trong túi | `true` | Bỏ qua khi game báo giá trị hiệu lực > 60000 |
| `ttlMua` | … túi hết thì mua (20 KNB khóa) | `false` | Mua 1 cái mỗi lần |
| `tt2` | Tiên Thảo Lộ Đặc Biệt hết thì dùng trong túi | `true` | Bỏ qua khi game báo giá trị hiệu lực > 60000 |
| `tt2Mua` | … túi hết thì mua (300 KNB khóa) | `false` | Mua 1 cái mỗi lần |
| `k2k` | Đổi KNB → KNB khóa tối đa ⟨N⟩ KNB / ngày (0 = không đổi) | `0` | `0..100000`; mỗi lệnh tối thiểu 10 KNB |
| `lbMua` | Mua Lệnh Bài Sát Thủ ⟨N⟩ cái / ngày (100 KNB / cái, 0 = không mua) | `0` | `0..100`; mỗi lệnh tối đa 10 cái |
| `knbMua` | Mua Kim Nguyên Bảo ở Kỳ Trân Các ⟨N⟩ KNB / ngày (1 vạn lượng / cái, 0 = không mua) | `0` | `0..1000`; trần game 1000/ngày |
| `knbGiu` | Giữ lại ít nhất ⟨N⟩ lượng | `0` | `0..10000000`; là **sàn** ngân lượng, không phải số được tiêu |

### 5.1 Thứ tự và cooldown

Thứ tự chạy cố định trong mỗi nhịp:

`troThu → khan → tienThao → qua → daTau → muaLb → doiHongBao → muaKnb → cheDo`

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

### 5.2 Bộ đếm ngày và cổng an toàn

| Cơ chế | Chi tiết |
|---|---|
| Lưu bộ đếm | `localStorage`, khóa `vlm_auto_<k>`, giá trị `{d:'YYYY-M-D', n:N}` |
| Múi giờ | UTC+7 (giờ Việt Nam) |
| Trần cứng | `knb` 1000, `evDhb` 1, `evHb` 3 |
| Khóa phụ | `vlm_auto_prev` lưu chế độ cũ để quay về; `vlm_auto_pbx` đánh dấu đã xong phó bản trong ngày |
| Cổng “người dùng rảnh” | Chỉ đổi chế độ, đi khấn, mở bảng Kỳ Trân Các/sự kiện sau 60 giây không chạm tab |
| Cổng phiên bản | `st.v < 990` → ghi log một lần và không chạy |
| Chống tiêm hai lần | Đầu script kiểm tra `window.__vlmAuto.ver`; đã đúng bản thì chỉ cập nhật cấu hình |
| Chống mất đồng bộ | Không đọc được bộ đếm của game thì **không** cho phép mua (Kỳ Trân Các, sự kiện) |
| Đặt lại | Nút **Đặt lại bộ đếm** xóa các khóa `vlm_auto_*` (giữ `vlm_auto_prev`) trên các tab đang mở |

### 5.3 Lưu trữ liên quan

| Khóa | Nội dung |
|---|---|
| `pref_auto_cfg` | Toàn bộ 18 trường dưới dạng một chuỗi JSON |
| `pref_fps` | 30 hoặc 60 |
| `pref_cluster` | Tên cụm cũ; vẫn được đọc nhưng **không có bộ chọn trong 4.0.0** |
| `pref_rx_offset` / `pref_tx_offset` | Mốc reset lưu lượng |
| `user_cluster_*`, `pass_cluster_*`, `url_cluster_*`, `auto_cluster_*` | Tài khoản theo tab, ghi đồng thời nhiều định dạng khóa |
| `pref_user_tab_N`, `pref_pass_tab_N`, `pref_url_tab_N`, `pref_auto_tab_N` | Khóa legacy để đọc dữ liệu từ bản cũ |
| `user_tab_N`, `pass_tab_N` | Khóa legacy dạng ngắn |

---

## 6. Kiến trúc

```
MainActivity (Jetpack Compose)
  +-- HeaderBar ............... lưu lượng, FPS, treo máy, VIEW
  +-- SidebarView ............. master, 4 thẻ tab, đồng bộ, 🤖
  +-- GameWebViewContainer .... 3 bố cục, mini header từng tab, idle overlay
  +-- AccountDialog / AutoConfigDialog
        |
        | StateFlow + callback
        v
MainViewModel (AndroidViewModel)
  +-- TrafficMonitor .......... TrafficStats theo UID, cập nhật mỗi 2s
  +-- PreferencesManager ...... SharedPreferences (tài khoản, auto, FPS)
  +-- GameTabManager
        +-- 4 x KeepAliveWebView (profile riêng) --evaluateJavascript-->+
        +-- GameAutoPilot ...... giữ script #vlmAuto, đẩy cấu hình -----+
        +-- KeepAliveService .... FGS specialUse + WakeLock + WifiLock
        +-- GameScriptInjector .. sinh JS: login, quét, select, FPS, GC
                                                                        |
                                                                        v
                                                          Trang game (DOM + #vlmAuto)
                                                                        |
                                                                        v
                                                          https://volamidle.pages.dev
```

### 6.1 Luồng đăng nhập

1. Người dùng bấm **ĐĂNG NHẬP HẾT** → `MainViewModel.onMasterLoginAllClicked()` → `GameTabManager.loginAll()`.
2. `isSystemRunning = true`, `KeepAliveService` được start, vòng lặp đi qua 4 tab: đặt `LOGGING_IN`, chọn URL (custom hoặc mặc định), gọi `loadUrl`, nghỉ 800ms giữa các tab.
3. `onPageFinished` bơm script giới hạn FPS và script viewport, sau đó bơm `buildAutoLoginStepScript` nếu tab đang active.
4. Script xử lý theo thứ tự: hộp thoại tranh chấp phiên → form đăng nhập → chọn nhân vật → “Bắt Đầu Trò Chơi” → kiểm tra HUD trận đấu.
5. Khi thấy HUD (`Hành trang` + `Kỹ năng`/`Võ công`/`Hệ thống` hoặc `Chế độ:`), script gọi `AndroidBridge.onInGameDetected(tabIdx)`.
6. Bridge đặt tab sang `IN_GAME`, `isOnline = true`; UI hiện 🟢 Online.
7. Nếu tab còn `LOGGING_IN`, vòng lặp 850ms bơm lại script; tab bị chạm trong 5 giây thì bỏ qua nhịp đó.

### 6.2 Luồng watchdog

1. Mỗi 1200ms, `GameTabManager` chọn một tab theo vòng tròn.
2. Chỉ quét khi tab active, đang `IN_GAME`, URL không phải `about:blank`, và không bị chạm trong 5 giây.
3. Script watchdog ưu tiên bấm nút tranh chấp phiên; sau đó kiểm tra dấu hiệu màn hình đăng nhập/chọn nhân vật.
4. Nếu phát hiện màn hình đăng nhập → `onGameKickedDetected` → tab về `LOGGING_IN` và được đăng nhập lại.
5. Nếu vẫn trong game → quét 9 thông số, gọi `onGameStateScanned`; Kotlin chỉ phát `StateFlow` khi dữ liệu hiển thị đổi.
6. Song song, mỗi 90.000 ms (90 giây) vòng GC dọn một tab; script tự bỏ qua nếu người dùng đang focus ô nhập.

### 6.3 Luồng tự động trong game

1. `GameAutoPilot` chạy nhịp Kotlin 30 giây; mỗi 4 nhịp bơm lại toàn bộ script, các nhịp còn lại chỉ đẩy cấu hình.
2. Script được cài lại khi URL của tab đổi hoặc sau khi tab được mở lại; cổng `window.__vlmAuto.ver` chặn cài trùng.
3. Trong trang, `setInterval` 20 giây gọi `tick()`; `tick()` chỉ chạy khi `cfg.on = true`, trạng thái game tươi dưới 10 giây và bản game ≥ 990.
4. Sau 30 giây kể từ lúc vào game, `tick()` chạy lần lượt 9 hành động, mỗi hành động tự kiểm tra cooldown và trần ngày.
5. Mọi kết quả được ghi qua `AndroidBridge.onGameLog` và hiện ở dòng trạng thái của thẻ tab.

### 6.4 Luồng chạy nền

1. `MainActivity.onPause()` / `onStop()` gọi `onAppBackgrounded()`: bật cờ `keepAliveInBackground`, `resumeTimers()`, đặt renderer priority `IMPORTANT`, đặt `window.__isAppInBackground = true`.
2. `KeepAliveWebView` ép `onWindowVisibilityChanged`, `onVisibilityChanged`, `onWindowFocusChanged` luôn là hiển thị/có focus.
3. Script FPS giả lập `document.hidden = false`, chặn `visibilitychange`/`pagehide`, và chuyển `requestAnimationFrame` sang nhịp `setTimeout` 50ms.
4. `onResume()` gọi `onAppForegrounded()`: tắt cờ giả lập, chạy GC một lần, trả VSync về bình thường.

---

## 7. Cấu trúc mã nguồn

```
app/src/main/java/com/example/
├── MainActivity.kt                     Activity chính; dựng header, sidebar, workspace, dialog
├── KeepAliveService.kt                 Foreground service, PARTIAL_WAKE_LOCK, WifiLock, notification
├── data/
│   └── PreferencesManager.kt           Tài khoản, cấu hình auto, FPS, cụm, offset lưu lượng, validate URL
├── model/
│   └── GameState.kt                    AccountConfig, CharacterStats, NetworkDataInfo, enum trạng thái/bố cục
├── network/
│   └── TrafficMonitor.kt               Đọc TrafficStats theo UID, tính tổng và tốc độ
├── ui/
│   ├── MainViewModel.kt                State toàn app; nối UI với GameTabManager và PreferencesManager
│   └── components/
│       ├── AccountDialog.kt             Hộp thoại tài khoản: user, pass, URL, checkbox, validate URL
│       ├── AutoConfigDialog.kt          Hộp thoại 🤖: 13 checkbox + 4 ô số + nút đặt lại bộ đếm
│       ├── GameWebViewContainer.kt      Dựng 4 ô game, 3 bố cục, mini header từng tab, idle overlay
│       ├── HeaderBar.kt                 Lưu lượng, FPS, treo máy, chuyển VIEW, chip T1–T4
│       ├── OledBlackScreen.kt           Overlay đen + đồng hồ + chạm đúp mở khóa
│       └── SidebarView.kt               Nút master, 4 thẻ tab, thông số, đồng bộ chế độ, màn hình đen, 🤖
├── ui/theme/                            Color.kt, Theme.kt, Type.kt — theme Material 3 tối
└── webview/
    ├── GameTabManager.kt                Vòng đời 4 WebView, login, watchdog, GC, phục hồi renderer, bridge
    ├── GameScriptInjector.kt            8 builder sinh JavaScript bơm vào trang game
    └── GameAutoPilot.kt                 AutoConfig (18 trường) + engine giữ script #vlmAuto
```

Tài nguyên và test:

```
app/src/main/res/values/                 strings.xml (app_name), colors.xml, themes.xml
app/src/main/res/xml/                    backup_rules.xml, data_extraction_rules.xml
app/src/test/java/com/example/           ExampleUnitTest.kt (7 test), ExampleRobolectricTest.kt (1 test)
app/src/androidTest/java/com/example/    ExampleInstrumentedTest.kt (kiểm tra applicationId)
```

---

## 8. Yêu cầu hệ thống

| Hạng mục | Yêu cầu |
|---|---|
| Android | 7.0 (API 24) trở lên; `targetSdk` 36 |
| WebView | Bản Chromium hỗ trợ `WebViewFeature.MULTI_PROFILE`. Không hỗ trợ thì app vẫn chạy nhưng 4 tab dùng chung profile mặc định — mất cách ly cookie/session |
| RAM | Thực tế cần cho 4 renderer game cùng lúc; đo trên LDPlayer Android 14: PSS của app ~222 MB, ổn định sau 3 phút. Thiết bị ít RAM dễ bị hệ điều hành thu hồi renderer; khuyến nghị 6–8 GB |
| Mạng | Wi-Fi hoặc 4G ổn định; app giữ WifiLock nhưng không đảm bảo mạng của nhà mạng |
| Bộ nhớ trong | APK debug + dữ liệu WebView; không có yêu cầu đặc biệt |
| Quyền | Chỉ 7 quyền khai báo: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS` |
| Tối ưu pin | Cần tắt tối ưu pin / bật “Không giới hạn” cho app, nếu không OEM sẽ giết foreground service |

App bật `android:largeHeap="true"`, nhưng đây chỉ là gợi ý cho bộ nhớ heap của Java/Kotlin, không tăng RAM cho 4 tiến trình renderer.

---

## 9. Cài đặt

### 9.1 Từ APK

1. Tải `app-debug.apk` từ release hoặc build tại chỗ (xem [mục 11](#11-build-từ-mã-nguồn)).
2. Kiểm tra checksum trước khi cài:

```bash
sha256sum app-debug.apk
# hoặc trên Windows
certutil -hashfile app-debug.apk SHA256
```

Đối chiếu với checksum công bố kèm bản phát hành. README không nhúng checksum vì APK build lại sẽ khác.

3. Cài đặt:

```bash
adb install -r app-debug.apk
# hoặc mở trực tiếp file APK trên điện thoại và cho phép "Cài từ nguồn không xác định"
```

4. Mở app. Lần đầu chạy, 4 tab ở trạng thái `⚪ Chờ mở (Idle)`.

### 9.2 Lần chạy đầu

1. Cấp quyền thông báo trong Cài đặt ứng dụng (Android 13+) nếu muốn thấy thông báo service.
2. Tắt tối ưu pin cho `Võ Lâm Idle`.
3. Bấm `🔑 TK (Đổi)` ở từng tab để nhập tài khoản.
4. Bấm `▶ ĐĂNG NHẬP HẾT`.

---

## 10. Hướng dẫn sử dụng

### 10.1 Cấu hình 4 tài khoản

1. Mở sidebar (nút ☰ ở góc trái header trên màn hình dọc).
2. Với mỗi tab, bấm `🔑 TK (Đổi)`.
3. Nhập **Tài khoản**, **Mật khẩu**. Ô **URL Tùy chỉnh** để trống nếu dùng `https://volamidle.pages.dev`.
4. Nếu nhập URL, chỉ chấp nhận `https://` trên `volamidle.pages.dev` hoặc subdomain. URL sai hiện dòng đỏ “Chỉ chấp nhận HTTPS trên volamidle.pages.dev”.
5. Bấm **Lưu cấu hình**. Nút trên thẻ đổi thành `🔑 <tên tài khoản> (Đổi)`.

### 10.2 Đăng nhập

1. Bấm **▶ ĐĂNG NHẬP HẾT** để mở cả 4 tab, hoặc `▶ Mở` trên từng ô để mở riêng.
2. Theo dõi trạng thái: `🟡 Đang vào` → `🟢 Online`.
3. Dòng trạng thái dưới mỗi thẻ cho biết app đang làm gì: “Đang tải trang…”, “Đã ấn [Xác Định]…”, “⚡ Đã tự bấm [Chơi ở đây]…”, “🟢 Đang chạy ổn định”.
4. Dừng toàn bộ bằng **⏹ DỪNG TẤT CẢ**; dừng riêng bằng `⏹` trên ô game.

### 10.3 Chọn bố cục

1. Bấm nút **VIEW** trên header để xoay vòng, hoặc bấm mũi tên `▼` để chọn trực tiếp:
   - `⊞ 4 Cột Dọc (1x4)`
   - `⊞ Lưới 2x2 (4 Ngang)`
   - `🔲 1 Tab (Toàn màn hình)`
2. Ở chế độ 1 Tab, dùng chip `T1`–`T4` trên header hoặc thanh tab phía trên vùng game.
3. Đổi bố cục sẽ gọi `adjustAllViewports()` để chèn lại CSS cho cột hẹp, không reload trang.

### 10.4 Đồng bộ chế độ

1. Trong sidebar, mục **ĐỒNG BỘ AUTO CẢ 4 TAB**.
2. Chọn `📜 Nhiệm Vụ`, `⚔️ Phó Bản` hoặc `🔥 Luyện Công`.
3. Script tìm `<select>` của game, đặt `selectedIndex`, bắn `input`/`change`; nếu game dùng nút thay vì select, script bấm nút tương ứng và tránh thanh menu đáy.
4. Kết quả hiện ở dòng trạng thái: `⚡ Chế độ: Phó Bản`.

Ngay dưới hàng nút đồng bộ là checkbox **Màn hình đen**: bật/tắt cài đặt `Hệ thống → Hiển Thị → Màn hình đen` của game trên cả 4 tab. Dòng phụ hiển thị `🟢 Đang bật (4 Tab)` hoặc `⚙️ Hệ thống ➔ Hiển Thị`.

### 10.5 Bật tự động trong game

1. Bấm **🤖 Tự động** (nút hổ phách = đang tắt, xanh = đang bật).
2. Bật công tắc **Bật tự động**.
3. Chọn các nhóm muốn dùng. Mặc định bật các mục miễn phí; các khoản mua bằng KNB thường mặc định tắt/0, riêng `ldtMua` mặc định bật.
4. Bấm **Lưu**. Cấu hình được đẩy xuống các tab đang mở ngay, không chờ nhịp 30 giây.

**Khuyến nghị lần đầu:** chỉ bật tự động cho **một tài khoản**, đặt `knbMua = 0`, `lbMua = 0`, `k2k = 0`, `ttlMua = false`, `tt2Mua = false`, `evHb = false`, `evDhb = false`, và **tắt `ldtMua`** nếu muốn tuyệt đối không tiêu KNB khóa. Theo dõi dòng trạng thái vài nhịp để xác nhận app chỉ làm các việc miễn phí (`🤖 bật Trợ thủ`, `🤖 nhận quà: điểm danh`) trước khi cho phép tiêu KNB.

Nút **Đặt lại bộ đếm** xóa hạn mức ngày trong `localStorage` của các tab đang mở. Dùng khi bộ đếm cục bộ lệch với bộ đếm của game.

### 10.6 Chế độ treo máy

1. Bấm **🌙 Treo máy** trên header.
2. Màn hình chuyển sang overlay đen, độ sáng cửa sổ còn `0.01`, đồng hồ vẫn chạy.
3. Chạm đúp để mở khóa. App vẫn giữ `FLAG_KEEP_SCREEN_ON` và service trong suốt thời gian này.

### 10.7 Đổi FPS

Bấm nút `⚡ 60FPS` / `⚡ 30FPS` trên header. Giá trị được lưu vào `pref_fps` và áp ngay cho cả 4 tab. 30 FPS giảm tải GPU/CPU đáng kể khi chạy 4 renderer.

---

## 11. Build từ mã nguồn

### 11.1 Chuẩn bị

| Thành phần | Ghi chú |
|---|---|
| JDK | `gradle/gradle-daemon-jvm.properties` ghim `toolchainVersion=25`; Foojay resolver trong `settings.gradle.kts` có thể tự tải JDK 25 nếu máy chưa có. Mã nguồn biên dịch với Java 11. Không dùng JRE |
| Android SDK | `platforms;android-36`, `build-tools;36.0.0`, `platform-tools` |
| Gradle | Dùng wrapper trong repo (`gradlew` / `gradlew.bat`), phân phối Gradle 9.3.1 |
| Keystore debug | `debug.keystore` ở thư mục gốc project (đã gitignore) |

```bash
sdkmanager --licenses
sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
```

Nếu build tag cũ (`v2.0.0`, `v3.0.0`), các tag đó dùng `compileSdk { version = release(36) { minorApiLevel = 1 } }` nên cần thêm `platforms;android-36.1`. Repo hiện tại dùng `compileSdk = 36`.

### 11.2 `local.properties`

Tạo ở thư mục gốc project, **không commit**:

```properties
# Windows
sdk.dir=C\:\\Users\\<ban>\\AppData\\Local\\Android\\Sdk
# Linux/macOS
# sdk.dir=/home/<ban>/Android/Sdk
```

### 11.3 Keystore debug

`app/build.gradle.kts` trỏ cứng `debugConfig` tới `${rootDir}/debug.keystore` với `storePassword = "android"`, `keyAlias = "androiddebugkey"`, `keyPassword = "android"`. Nếu file chưa có:

```bash
keytool -genkeypair -v -keystore debug.keystore \
  -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```

### 11.4 Build và kiểm thử

```bash
# Windows dùng gradlew.bat
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

APK ra ở `app/build/outputs/apk/debug/app-debug.apk`.

### 11.5 Build release

`release` dùng signing config trỏ tới `${rootDir}/my-upload-key.jks` hoặc biến môi trường `KEYSTORE_PATH`:

```bash
export KEYSTORE_PATH=/duong/dan/my-upload-key.jks
export STORE_PASSWORD='...'
export KEY_PASSWORD='...'
./gradlew :app:assembleRelease
```

Release hiện **không minify** (`isMinifyEnabled = false`). Không có keystore release thì `assembleRelease` dừng ở bước đóng gói. Không dùng APK debug để phát hành.

### 11.6 Cảnh báo Windows: đường dẫn có ký tự có dấu

Nếu đường dẫn project chứa ký tự ngoài ASCII (ví dụ `C:\Code vs AI\võ-lâm-idle`), Gradle ghi classpath vào file `@argfile` bằng UTF-8 nhưng JVM đọc file này bằng codepage ANSI của Windows. Kết quả: test báo `ClassNotFoundException` cho **mọi** class dù code biên dịch đúng.

Cách xử lý: clone/build trong thư mục ASCII tuyệt đối, ví dụ `C:\v4rel`. `android.overridePathCheck=true` trong `gradle.properties` chỉ tắt kiểm tra đường dẫn của AGP, **không** sửa lỗi encoding của argfile.

---

## 12. Kiểm thử

8 unit test trong `app/src/test/`. Đây là test kiểm tra sự hiện diện của chuỗi/khối logic trong JavaScript do Kotlin sinh ra, **không chạy JavaScript thật**.

| Test | Nội dung |
|---|---|
| `ExampleUnitTest.addition_isCorrect` | Sanity check JUnit |
| `ExampleUnitTest.testViewLayoutModes` | Có đúng 3 bố cục: `FOUR_COLUMNS`, `TWO_BY_TWO`, `SINGLE_TAB` |
| `ExampleUnitTest.testConflictResolutionScripts` | Script login và watchdog chứa “Chơi ở đây”, “Chơi ở tab này”, `let bodyText`, `const allElements` |
| `ExampleUnitTest.testSyncAutoModeScript` | Script đồng bộ chứa “Nhiệm Vụ”, “Phó Bản”, “Luyện Công”, `HTMLSelectElement`, `isBottomMenuBarElement` |
| `ExampleUnitTest.testSyncBlackScreenScript` | Script màn hình đen chứa `findHeThongButton`, `findHienThiTab`, `findBlackScreenCheckbox`, “man hinh den”; kết thúc bằng `(true);` / `(false);` |
| `ExampleUnitTest.testBackgroundKeepAliveAndAntiJitterScript` | Script FPS chứa `visibilityState`, `__isAppInBackground`, `safeDelta`, `initSilentKeepAliveAudio` |
| `ExampleUnitTest.testNewLoginUiScript` | Script login có `function isVisible(el)`, không dùng regex `(hidden\|d-none`, hỗ trợ “bat dau tro choi”, “mat ma”, bỏ qua “hien mat”, tích “nho tai khoan”, bấm “xac dinh”, có `findLoginSubmitButton` |
| `ExampleRobolectricTest.read string from context` | `R.string.app_name == "Võ Lâm Idle"` với Robolectric `sdk = 36` |

Chạy:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Có thêm 1 instrumented test (`ExampleInstrumentedTest`) kiểm tra `applicationId`, chạy bằng `./gradlew :app:connectedDebugAndroidTest` với thiết bị/emulator.

---

## 13. Xử lý sự cố

| Triệu chứng | Nguyên nhân | Cách xử lý |
|---|---|---|
| Chỉ 1 tab Online, 3 tab kẹt “Đang vào” | Lỗi lịch sử: script xác định “đã vào game” dựa trên việc không còn ô nhập, nhưng màn hình game có ô chat → `onInGameDetected` không bao giờ được gọi; watchdog chỉ quét tab `IN_GAME` nên các tab kẹt không bao giờ được cứu | Đã sửa trong 4.0: ưu tiên HUD trận đấu, loại ô chat khỏi danh sách ô nhập. Kiểm tra đang chạy đúng 4.0.0; xem dòng trạng thái từng tab; thử `↻` hoặc `▶ Mở` |
| Không mở/chọn được dropdown trong game | Vòng lặp đăng nhập 850ms bắn click ra ngoài làm đóng `<select>` đang mở | Đã sửa: vòng lặp có touch-guard 5 giây và chỉ chạy khi tab `LOGGING_IN`. Nếu vẫn gặp, chờ 5 giây không chạm; kiểm tra tab có kẹt `LOGGING_IN` không |
| Tab hiện “Đang kết nối lại máy chủ…” mãi | Bản cũ thử lại vô hạn khi lỗi main-frame | 4.0 giới hạn 3 lần (2.5s → 5s → 7.5s). Sau 3 lần, tab báo “❌ Máy chủ không phản hồi…”. Kiểm tra mạng/DNS và URL; `ERROR_HOST_LOOKUP` dừng ngay |
| Test báo `ClassNotFoundException` cho mọi class | Đường dẫn project có ký tự có dấu; JVM đọc `@argfile` bằng ANSI | Build tại đường dẫn ASCII, ví dụ `C:\v4rel` |
| Build lỗi SDK licence / không tìm thấy platform 36 | Chưa cài platform hoặc chưa chấp nhận licence | `sdkmanager --licenses`, cài `platforms;android-36` và `build-tools;36.0.0`; tag cũ cần `platforms;android-36.1` |
| `Keystore file not found` | Thiếu `debug.keystore` ở thư mục gốc, hoặc build release khi thiếu `my-upload-key.jks` | Tạo `debug.keystore` theo [11.3](#113-keystore-debug); release cần `KEYSTORE_PATH` trỏ tới upload key thật |
| Bỏ tick “tự động đăng nhập” nhưng app vẫn đăng nhập | Cờ `autoLoginEnabled` được lưu nhưng luồng login không đọc | Để trống tài khoản/mật khẩu của tab đó, hoặc bấm `⏹` |
| Không thấy thông báo service | Android 13+ chưa cấp `POST_NOTIFICATIONS`; app không xin runtime | Bật thông báo trong Cài đặt ứng dụng |
| Mất kết nối khi khóa màn hình / để lâu | OEM tối ưu pin giết foreground service | Tắt tối ưu pin, bật “Không giới hạn”, khóa app trong recent apps nếu OEM yêu cầu |
| Treo máy không mở khóa được | Overlay chỉ nhận chạm đúp | Chạm đúp 2 lần liên tiếp; hoặc bấm Back (Back cũng thoát sleep mode) |
| Tự động không làm gì | `on = false`; game < 990; chưa vào game đủ 30 giây; đang trong phó bản/tháp/truy sát/đã chết; tab chưa `IN_GAME` | Bật công tắc tổng, kiểm tra dòng trạng thái có log `🤖`; chờ ngoài phó bản; kiểm tra phiên bản game |
| Mua KNB nhưng không nhận được | Không đọc được bộ đếm/trạng thái ví của game; giao dịch chưa xác nhận | Xem log “Kỳ Trân Các: đã bấm mua nhưng không nhận được KNB”; đặt `knbMua = 0` và kiểm tra thủ công |
| 4 tab quá nóng / tụt pin | 4 renderer game cùng chạy | Giảm FPS xuống 30; dừng bớt tab bằng `⏹`; kiểm tra không có app khác chiếm GPU |

---

## 14. Hiệu năng

Số đo A/B trên LDPlayer Android 14, 4 tài khoản thật, cùng thiết bị, đo bằng jiffies `/proc` cho tiến trình app:

| Chỉ số | Bản trước tối ưu | Bản 4.0.0 |
|---|---|---|
| CPU tiến trình app | 81% | 80% |
| Tab Online | 1–2 / 4 | 4 / 4 |
| RAM PSS của app | — | ~222 MB, ổn định sau 3 phút |

Cách đọc con số: mức CPU gần như không đổi, nhưng cùng chi phí đó nuôi được 4 tab thay vì 1–2 tab. Phần lớn tải đến từ 4 tiến trình renderer của Chromium đang vẽ game, không phải từ Kotlin/Compose. Muốn giảm thêm đáng kể chỉ có ba cách thực chất: giảm số tab, hạ FPS xuống 30, hoặc giảm tần suất watchdog/GC.

Các thay đổi tạo ra kết quả này:

| Thay đổi | Tác động |
|---|---|
| Watchdog 4 tab/2.5s → 1 tab/1.2s | Giảm ~2 lần số lượt quét DOM mỗi giây |
| GC 45s → 90s và luân phiên | Mỗi tab dọn lại sau ~6 phút thay vì mỗi 45s |
| So sánh 11 trường trước khi phát `StateFlow` | Không recompose Sidebar/Header mỗi nhịp quét |
| Bỏ `LAYER_TYPE_HARDWARE` | Bỏ 4 texture lớp phủ, giảm RAM và compositing |
| Giới hạn 400 node khi quét tên | Tránh duyệt toàn bộ DOM mỗi lượt |
| Shim `cancelAnimationFrame` | Không rò rỉ `setTimeout` mỗi frame |

---

## 15. Bảo mật

Bản 4.0.0 **chưa được hardened**. Bảng dưới là các vấn đề đã xác nhận trong mã hiện tại, không phải giả định.

| Mức | Vấn đề | Hệ quả |
|---|---|---|
| Nghiêm trọng | `onReceivedSslError` gọi `handler?.proceed()` vô điều kiện | Chấp nhận chứng chỉ hết hạn, sai host hoặc tự ký; người đứng giữa đọc được mật khẩu và phiên |
| Nghiêm trọng | `onPageFinished` bơm script điền mật khẩu vào **bất kỳ** trang nào tải xong khi hệ thống đang chạy và tab active; không kiểm tra host tại thời điểm inject | Một redirect hoặc XSS trên origin tin cậy có thể nhận được mật khẩu đã điền |
| Cao | `addJavascriptInterface(..., "AndroidBridge")` không giới hạn origin | JavaScript trong trang gọi được callback đổi trạng thái, ghi log, đăng nhập lại |
| Cao | Mật khẩu lưu plaintext trong `SharedPreferences`, ghi lặp qua 5 định dạng khóa | Root, `adb run-as` (APK debug) hoặc forensics đọc được toàn bộ |
| Cao | APK đang dùng (`app-debug.apk`) là bản debug: `debuggable=true`, ký bằng `debug.keystore` | `adb run-as` đọc được prefs; không thể phát hành công khai hoặc nâng cấp liên tục bằng khóa khác |
| Trung bình | `mixedContentMode = MIXED_CONTENT_ALWAYS_ALLOW` | Tài nguyên HTTP có thể bị chèn vào trang HTTPS |
| Trung bình | `isMinifyEnabled = false`; nhiều dependency khai báo nhưng không dùng (Firebase, Room, Retrofit, Moshi, OkHttp, logging-interceptor) | APK lớn, bề mặt tấn công rộng hơn cần thiết |
| Thấp | Không certificate pinning | Phụ thuộc hoàn toàn vào CA tin cậy của hệ thống |

Đã tắt đúng trong mã hiện tại (không cần xử lý thêm): `android:allowBackup="false"`, `android:usesCleartextTraffic="false"`, `allowFileAccess = false`, `allowContentAccess = false`. Lưu ý: `backup_rules.xml` và `data_extraction_rules.xml` vẫn là mẫu rỗng, nên nếu ai đó bật lại `allowBackup`, toàn bộ prefs (gồm mật khẩu) sẽ được đưa vào backup.

Lưu ý lịch sử: bản 3.0.1 từng từ chối lỗi TLS, đặt `MIXED_CONTENT_NEVER_ALLOW`, giới hạn điều hướng top-level và chỉ inject trong origin tin cậy. Các thay đổi đó đã bị revert trong quá trình làm 4.0 (commit `c186416`), nên `SECURITY.md` (đánh giá cho 3.0.1) không còn mô tả đúng trạng thái 4.0.

### 15.1 Nếu muốn tự hardening

Thứ tự ưu tiên:

1. `onReceivedSslError` → `handler.cancel()` (chỉ thêm ngoại lệ nếu thật sự cần CDN nội bộ).
2. Đặt `mixedContentMode = MIXED_CONTENT_NEVER_ALLOW`.
3. Khôi phục `shouldOverrideUrlLoading` chỉ cho phép `https://volamidle.pages.dev` và subdomain.
4. Chỉ inject script đăng nhập khi `view.url` khớp allowlist tại thời điểm inject, không chỉ lúc nhập URL.
5. Thay `addJavascriptInterface` bằng `WebViewCompat.addWebMessageListener` với `allowedOriginRules`.
6. Mã hóa mật khẩu bằng Android Keystore + AES-GCM (hoặc `EncryptedSharedPreferences`), bỏ các bản sao khóa legacy sau migration.
7. Ký release bằng upload key thật, đặt `isDebuggable = false`, bật `isMinifyEnabled = true` và `shrinkResources`.
8. Xóa dependency không dùng khỏi `app/build.gradle.kts`.
9. Thêm `network_security_config` nếu cần giới hạn CA.
10. Không dùng app trên thiết bị dùng chung hoặc thiết bị đã root.

---

## 16. Lịch sử phiên bản

| Phiên bản | Nội dung chính |
|---|---|
| **1.0** | Bản nhập ban đầu (pre-refactor): 4 tab WebView, tự đăng nhập, có VPN và chọn Cụm |
| **2.0.0** | Nhánh `main`: gỡ VPN và chọn Cụm; bảo toàn tài khoản khi nâng cấp, không hồi sinh tài khoản đã xóa; chặn URL rác và báo lỗi khi không tải được trang; thêm Gradle wrapper cho Windows; `versionCode` 2 |
| **3.0.0** | Nhánh `v3`: gỡ VPN, giữ chọn Cụm, thêm bước đầu tự động hóa trong game. Gói build ghi `versionName "1.0"` / `versionCode 1` (lỗi đánh số) |
| **3.0.1** | Gỡ bộ chọn Cụm; hardening WebView: từ chối lỗi TLS, chặn mixed content, giới hạn điều hướng top-level, thêm kiểm tra URL (`isTrustedGameUrl` chỉ được bổ sung ở commit sửa sau); retry có giới hạn; `versionCode` 2 |
| **4.0.0** | Port bộ tự động `#vlmAuto` từ bản PC (18 mục); sửa treo khung sau khi chat; DOM GC theo id thật; watchdog luân phiên 1.2s; `versionCode` 4. Một số hardening của 3.0.1 bị revert trong quá trình làm bản này |

Chi tiết đầy đủ: `git log` trên nhánh `v3`. Commit `bd53f09` sửa lỗi biên dịch còn sót sau commit gỡ bộ chọn Cụm.

---

## 17. Đóng góp & giấy phép

Đây là dự án cá nhân, không có tệp `LICENSE` trong repo. Theo mặc định của luật bản quyền, không có giấy phép nghĩa là **không** cấp quyền tái sử dụng, phân phối lại hoặc tạo tác phẩm phái sinh.

Không có quy trình đóng góp, không có CI, không có issue template. Nếu bạn dùng lại mã, hãy giữ nguyên các ghi chú về giới hạn bảo mật và tự đánh giá rủi ro trên thiết bị của mình.

---

## 18. Tài liệu khác

| Tài liệu | Nội dung |
|---|---|
| [`docs/PORT-DISCORD.md`](docs/PORT-DISCORD.md) | Hướng dẫn port cảnh báo Discord webhook từ bản PC sang Android. **Đây chỉ là tài liệu thiết kế/hướng dẫn; app 4.0.0 chưa có tính năng Discord** |
| [`SECURITY.md`](SECURITY.md) | Đánh giá bảo mật cho bản 3.0.1. Một phần nội dung không còn đúng với 4.0.0 — đối chiếu [mục 15](#15-bảo-mật) |
