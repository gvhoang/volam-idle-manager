# Võ Lâm Idle Manager

Chạy **1–8 tài khoản** webgame [Võ Lâm Idle](https://volamidle.pages.dev) song song trong một ứng dụng Android. Mỗi tab là một phiên riêng, tự đăng nhập, tự vào lại khi bị văng, giữ kết nối qua đêm, tự làm việc trong game và tự cập nhật từ GitHub Releases.

[![Release](https://img.shields.io/badge/release-v5.2.1-2ea44f?style=flat-square)](https://github.com/gvhoang/volam-idle-manager/releases/tag/v5.2.1)
[![versionCode](https://img.shields.io/badge/versionCode-50201-2ea44f?style=flat-square)](CHANGELOG.md)
[![minSdk 24 (Android 7.0)](https://img.shields.io/badge/minSdk-24-3ddc84?style=flat-square)](#yêu-cầu-hệ-thống)
[![License](https://img.shields.io/badge/license-All%20rights%20reserved-lightgrey?style=flat-square)](#giấy-phép)

> [!WARNING]
> ### Đọc trước khi nâng cấp từ 4.x
>
> Bản 4.0.0 và 4.1.0 ký bằng chứng chỉ khác; từ bản 5 trở đi APK ký bằng khoá phát hành riêng của dự án. Android không cho cài một APK khác chữ ký lên bản đang cài — nó dừng với `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Vì vậy **nâng cấp từ 4.x bắt buộc phải gỡ ứng dụng trước, và gỡ ứng dụng sẽ xóa toàn bộ tài khoản đã lưu.**
>
> 1. Mở từng tab trong bản 4.x, ghi lại **tài khoản, mật khẩu, URL tùy chỉnh** (nếu có) và cấu hình `🤖 Tự động` ra ngoài máy.
> 2. Gỡ `Võ Lâm Idle` (Cài đặt → Ứng dụng → Gỡ cài đặt).
> 3. Cài bản mới nhất từ [Releases](https://github.com/gvhoang/volam-idle-manager/releases/latest).
> 4. Nhập lại tài khoản cho từng tab.
>
> Nút **Cập nhật** bên trong bản 4.1.0 sẽ báo *“Không cài được: bản cập nhật được ký bằng khóa khác với bản đang cài”* — đó là hành vi đúng. Từ bản 5 trở đi mọi bản dùng **cùng một khóa**, nên cài đè bình thường và tài khoản được giữ nguyên.

**Mục lục:** [Giới thiệu](#giới-thiệu) · [Tính năng](#tính-năng) · [Cài đặt](#cài-đặt-và-lần-chạy-đầu) · [Sử dụng](#sử-dụng) · [Cấu hình tự động](#tham-chiếu-cấu-hình-tự-động) · [Xử lý sự cố](#xử-lý-sự-cố) · [Hiệu năng](#hiệu-năng) · [Bảo mật](#bảo-mật) · [Build từ mã nguồn](#build-từ-mã-nguồn) · [Lịch sử phiên bản](#lịch-sử-phiên-bản) · [Tài liệu](#tài-liệu) · [About](#about) · [Giấy phép](#giấy-phép)

## Giới thiệu

Võ Lâm Idle là webgame idle chạy hoàn toàn trong trình duyệt. Chơi nhiều tài khoản theo cách thông thường nghĩa là mở nhiều tab, mỗi tab giữ một phiên riêng — và mọi tab đều bị trình duyệt hoặc Android tạm dừng khi chuyển ứng dụng hay tắt màn hình. Nhân vật đứng yên, gói tin dồn lại, và khi quay lại thì game tua nhanh hoặc nhảy vị trí.

Ứng dụng này nhúng 1–8 cửa sổ game trong một app Android. Mỗi cửa sổ có phiên trình duyệt riêng, được giữ “hiển thị” kể cả khi app ra nền, và được đăng nhập lại tự động khi mất phiên. Phạm vi có chủ ý: **một máy chủ game duy nhất**, **1–8 tab**, không có cơ chế cấu hình đa game. Đây là công cụ cá nhân — đọc [Bảo mật](#bảo-mật) trước khi dùng trên máy có dữ liệu thật.

| Trường | Giá trị |
|---|---|
| Tên hiển thị (launcher) | `Võ Lâm Idle` |
| Phiên bản / `versionCode` | `5.2.1` / `50201` |
| `applicationId` | `com.aistudio.volamidle.qkrvtp` |
| `minSdk` / `targetSdk` / `compileSdk` | 24 / 36 / 36 |
| Máy chủ game | `https://volamidle.pages.dev` (mặc định) |
| Giấy phép | Không có tệp `LICENSE`, toàn bộ quyền được bảo lưu |

## Tính năng

### Nhiều tài khoản, nhiều phiên

| Tính năng | Chi tiết |
|---|---|
| 1–8 tab | Đổi số tab áp dụng ngay, không cần khởi động lại; máy RAM thấp bị giới hạn ở 4 |
| Phiên riêng mỗi tab | Cookie, dữ liệu trang và đăng nhập tách biệt; tài khoản, mật khẩu (che), URL tùy chỉnh theo từng tab; URL chỉ nhận `https` trên `volamidle.pages.dev` hoặc subdomain |
| Điều khiển từng tab | `▶ Mở`, `PC` (UA desktop), `↻`, `⏹` |
| Giữ tài khoản khi cập nhật | Cài đè cùng khóa ký thì tài khoản còn nguyên |

### Giao diện

Dải trạng thái 32dp ở đáy (`☰`, chip tab, `⋯`) và sheet điều khiển nhanh; vùng game chiếm phần còn lại. Từ 5 tab, dải chuyển sang cuộn ngang, `☰` và `⋯` ghim hai đầu.

| Tính năng | Chi tiết |
|---|---|
| Ba bố cục | `4 Dọc` (tối đa 4 cột, quá 4 tab tự xuống hàng), `2x2`, `1 Tab` |
| Treo máy | Overlay đen OLED, độ sáng cửa sổ `0.01`, chạm đúp để mở khóa; màn hình vẫn sáng |
| Cột hẹp | Tắt `backdrop-filter`, đặt `min-width` cho input, `width:100%` dưới 280px |

### Tự động đăng nhập

| Tính năng | Chi tiết |
|---|---|
| Điền và gửi form | Điền theo cách form game nhận được, bắn đủ sự kiện như người gõ; tích “Nhớ tài khoản”; bấm “Xác Định”, dự phòng `Enter` và gửi form |
| Vào game | Bấm “Bắt Đầu Trò Chơi” (thử lại ở 180/380/650/950ms), đồng ý điều khoản, chọn nhân vật rồi bấm “Xác Định”/“Vào Game” |
| Tranh chấp phiên | Tự bấm “Chơi ở đây”, “Chơi ở tab này”, “Tiếp tục ở đây”, “Kết nối lại”, “Thử lại”; bị văng thì đăng nhập lại |
| Nhận diện đã vào game | Dựa trên HUD trong game; ô chat không bị nhầm là ô đăng nhập |

Lớp nhận diện bám theo giao diện game build 1041. Game đổi giao diện lần nữa thì phần này cần cập nhật theo.

### Tự động trong game

Điều khiển game qua **kênh lệnh ẩn của chính game** (bản game ≥ 990); không mô phỏng chạm hay bàn phím.

| Nhóm | Hành động |
|---|---|
| Trợ thủ / Dã Tẩu | Bật Dã Tẩu tự trả/nhận, dùng Lệnh Bài, gọi Sát Thủ; mua Lệnh Bài bằng KNB khóa; đóng Bình Phúc Duyên Lộ, mở Rương Phúc Duyên |
| Tiên Thảo | Dùng Tiên Thảo Lộ / Đặc Biệt trong túi; hết túi thì mua bằng KNB khóa |
| Quà tặng | Nhận quà Điểm danh và Mốc cấp; Khấn tượng +20% kinh nghiệm trong 8 giờ |
| Sự kiện | Đổi Đại Hồng Bao (tối đa 1/ngày) và Hồng Bao (tối đa 3/ngày) |
| Phó bản | Chuyển Chế độ Phó Bản tới khi hết lượt, rồi Dã Tẩu, rồi trả về chế độ cũ |
| Kinh tế / Chế độ | Đổi KNB → KNB khóa, mua Lệnh Bài Sát Thủ, mua Kim Nguyên Bảo ở Kỳ Trân Các; tự chuyển Nhiệm Vụ / Phó Bản theo lượt còn lại |

Cổng an toàn: mặc định tổng **TẮT**; các khoản mua bằng KNB mặc định `0`/tắt (riêng mua Lệnh Bài Dã Tẩu mặc định bật); chỉ đổi chế độ, đi khấn hoặc mở bảng khi tab không bị chạm 60 giây; bản game < 990 thì không làm gì. Chi tiết ở [Tham chiếu cấu hình tự động](#tham-chiếu-cấu-hình-tự-động).

### Giữ kết nối và tối ưu tài nguyên

Service nền giữ CPU và Wi-Fi thức qua đêm, cửa sổ game vẫn “hiển thị” khi app ra nền, FPS giới hạn 30 hoặc 60. Bộ nhớ trang được dọn luân phiên từng tab, giữ 30 dòng chat và 10 hiệu ứng gần nhất, không dọn khi đang gõ. Tiến trình trang bị hệ điều hành thu hồi thì app tạo lại đúng tab đó. Lỗi tải trang thử tối đa 3 lần (2,5s → 5s → 7,5s), lỗi DNS dừng ngay.

Android 13+ khai báo quyền thông báo nhưng app **không xin quyền runtime** — không thấy thông báo của service thì bật thủ công trong Cài đặt ứng dụng.

## Cài đặt và lần chạy đầu

### Từ APK phát hành

1. Tải `volamidle-manager.apk` từ [Releases](https://github.com/gvhoang/volam-idle-manager/releases/latest).
2. Kiểm tra toàn vẹn và chữ ký trước khi cài:

```bash
sha256sum volamidle-manager.apk
certutil -hashfile volamidle-manager.apk SHA256   # Windows
apksigner verify --print-certs volamidle-manager.apk
```

Đối chiếu SHA-256 công bố trong phần mô tả release — README không nhúng checksum vì build lại sẽ ra file khác. Chữ ký phải khớp:

| Trường | Giá trị |
|---|---|
| Chủ thể (DN) | `CN=Vo Lam Idle Manager, OU=Release Signing, O=Thien Co Labs, L=Ho Chi Minh City, ST=Ho Chi Minh, C=VN` |
| SHA-256 | `3A:44:D8:D1:CA:33:4B:6E:49:7E:29:95:B9:70:5A:59:BE:B5:A2:AC:AE:25:BB:C3:1E:9F:72:16:BF:86:90:A7` |

Lệch một trong hai giá trị nghĩa là file không phải bản phát hành chính thức — đừng cài.

3. Cài bằng `adb install -r volamidle-manager.apk`, hoặc mở file APK trên máy và cho phép “Cài từ nguồn không xác định”. Lỗi `INSTALL_FAILED_UPDATE_INCOMPATIBLE` nghĩa là máy còn bản 4.x — xem cảnh báo đầu tài liệu.
4. Mở app: các tab đang bật ở `⚪ Chờ mở`.
5. Android 13+: bật quyền thông báo cho `Võ Lâm Idle`. Tắt tối ưu pin cho ứng dụng (Cài đặt → Pin → Không giới hạn), nếu không nhiều máy giết service nền.
6. `☰` → `🔑 TK (Đổi)` ở từng tab để nhập tài khoản, rồi `ĐĂNG NHẬP HẾT` và theo dõi `🟡 Đang vào` → `🟢 Online`.

### Yêu cầu hệ thống

| Hạng mục | Yêu cầu |
|---|---|
| Android | 7.0 (API 24) trở lên |
| WebView | Cần bản Chromium hỗ trợ nhiều profile; không có thì mọi tab dùng chung phiên mặc định, mất cách ly cookie và đăng nhập |
| RAM | 4 tab cần 1,0–1,3 GB; 8 tab ~1,9 GB. Khuyến nghị máy 6–8 GB |
| Mạng và pin | Wi-Fi hoặc 4G ổn định; phải tắt tối ưu pin, nếu không hệ điều hành giết service |

Quyền khai báo — 8 quyền: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `REQUEST_INSTALL_PACKAGES`. Khi đóng gói, thư viện đi kèm thêm `READ_GSERVICES` và `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Không có quyền nào đọc danh bạ, tin nhắn, vị trí, máy ảnh hay tệp cá nhân.

## Sử dụng

### Bảng điều khiển nhanh

Mở bằng `⋯` ở góc phải dải trạng thái. Mở/đóng không đổi kích thước cửa sổ game nên game không reflow và không đóng danh sách đang mở.

| Hàng | Nội dung |
|---|---|
| 1 | `▶ Mở` / `PC` / `↻` / `⏹` cho tab đang chọn |
| 2 | Ba chế độ xem: `4 Dọc`, `2x2`, `1 Tab` |
| 3 | `SỐ TAB` với `−` / `+`, 1–8, hỏi lại khi vượt 4 hoặc khi giảm số tab đang chạy |
| 4 | `⚡ 30FPS` / `⚡ 60FPS` và `🌙 Treo máy` |
| 5 | Lưu lượng: tổng MB, tốc độ tải xuống, nút reset |
| 6 | `ĐỒNG BỘ AUTO CẢ <N> TAB`: `📜 Nhiệm Vụ`, `⚔️ Phó Bản`, `🔥 Luyện Công` |
| 7 | `🤖 Tự động` — cấu hình auto-pilot |
| 8 | `☰ Bảng điều khiển` |
| 9 | `📦 Phiên bản: 5.2.1` và các nút kiểm tra, tải, cài cập nhật |

`☰` mở bảng điều khiển đầy đủ: tài khoản từng tab, `ĐĂNG NHẬP HẾT`, `DỪNG TẤT CẢ`, đồng bộ “Màn hình đen”.

### Số tab và tài khoản

1. `SỐ TAB` áp dụng ngay. Vượt 4 tab, app tự hạ 60 → 30 FPS một lần (có thông báo); trên máy RAM thấp trần là 4.
2. Giảm xuống dưới một tab đang chạy sẽ hỏi lại: tab đó mất phiên và phải đăng nhập lại, tài khoản vẫn được lưu. Tab mới được mở và đăng nhập tự động, giãn cách 800ms.
3. `🔑 TK (Đổi)` để nhập tài khoản, mật khẩu, URL tùy chỉnh (để trống dùng mặc định), rồi **Lưu cấu hình**.
4. `ĐĂNG NHẬP HẾT` mở mọi tab đang bật; `▶ Mở` chỉ áp tab đang chọn; `DỪNG TẤT CẢ` / `⏹` để dừng.
5. Dòng trạng thái dưới mỗi thẻ cho biết app đang làm gì: “Đang tải trang…”, “Đã ấn [Xác Định]…”, “⚡ Đã tự bấm [Chơi ở đây]…”, “🟢 Đang chạy ổn định”.

### Đồng bộ chế độ và tự động

1. `ĐỒNG BỘ AUTO CẢ <N> TAB` đổi chế độ mọi tab sang `📜 Nhiệm Vụ`, `⚔️ Phó Bản` hoặc `🔥 Luyện Công`; kết quả hiện `⚡ Chế độ: Phó Bản`.
2. Ô **Màn hình đen** bật/tắt `Hệ thống → Hiển Thị → Màn hình đen` của game trên mọi tab.
3. `🤖 Tự động` → bật công tắc tổng → chọn nhóm → **Lưu**; cấu hình xuống các tab đang mở ngay.
4. **Đặt lại bộ đếm** xóa hạn mức ngày của các tab đang mở khi bộ đếm lệch với game.

**Khuyến nghị lần đầu:** chỉ bật auto cho **một tài khoản**, đưa các mục mua bằng KNB về `0`/tắt (kể cả mua Lệnh Bài Dã Tẩu nếu muốn tuyệt đối không tiêu KNB khóa), theo dõi dòng trạng thái vài nhịp trước khi cho phép tiêu KNB.

### Treo máy, FPS và cập nhật

- `🌙 Treo máy`: overlay đen, độ sáng `0.01`, chạm đúp hoặc Back để mở khóa; màn hình vẫn sáng và service vẫn chạy.
- `⚡ 30FPS` / `⚡ 60FPS`: áp ngay cho mọi tab; 30 FPS giảm tải đáng kể khi chạy nhiều tab.
Khi có bản mới, lần mở app kế tiếp sẽ hiện hộp thoại **Đã có bản cập nhật** — chọn **Để sau** thì không bị nhắc lại cho bản đó.
- `📦 Phiên bản: 5.2.1` → `Kiểm tra cập nhật` → `Tải về` → `Cài đặt`. Android hỏi xác nhận, cài đè giữ nguyên tài khoản. Cần quyền “Cài ứng dụng không rõ nguồn gốc”; app có nút mở thẳng màn hình đó.

Số phiên bản so bằng `versionCode`, không so chuỗi: `versionCode = major * 10000 + minor * 100 + patch`, ví dụ `5.2.1 → 50201`, `6.0.0 → 60000`.

## Tham chiếu cấu hình tự động

Cấu hình lưu thành một khối trên máy và áp cho mọi tab đang mở.

| Mục trong hộp thoại | Mặc định | Ghi chú |
|---|---|---|
| Công tắc tổng | `false` | Không bao giờ tự bật |
| Dã Tẩu tối đa (Trợ thủ, Nhiệm Vụ khi còn lượt) | `true` | Yêu cầu cấp ≥ 20 khi vào nhánh Dã Tẩu |
| Mua Lệnh Bài Dã Tẩu bằng KNB khóa | `true` | 20 KNB khóa/cái; chỉ mua khi còn thiếu và chưa đạt trần ngày |
| Bình Phúc Duyên Lộ + Rương Phúc Duyên | `true` | Đóng bình khi có bình hoặc ≥ 10 Phúc Duyên; mở rương theo bội số 10 |
| Sát Thủ: tự gọi khi có Lệnh Bài | `true` | Chỉ chạy khi không còn Lệnh Bài và cấp ≥ 20 |
| Tự chạy phó bản | `true` | Hết lượt sau 2 phút không vào phòng, rồi Dã Tẩu, rồi về chế độ cũ |
| Nhận quà Điểm danh + Mốc cấp | `true` | Cách nhau 60 giây |
| Khấn tượng mỗi ngày | `true` | +20% kinh nghiệm 8 giờ; chỉ khi không ở phó bản/tháp/truy sát và tab rảnh 60 giây |
| Sự kiện: đổi Đại Hồng Bao | `false` | 100 mỗi loại, tối đa 1/ngày |
| Sự kiện: đổi Hồng Bao | `false` | 20 mỗi loại, tối đa 3/ngày |
| Tiên Thảo Lộ hết thì dùng trong túi | `true` | Bỏ qua khi game báo giá trị hiệu lực > 60000 |
| … túi hết thì mua | `false` | 20 KNB khóa/cái, mỗi lần 1 cái |
| Tiên Thảo Lộ Đặc Biệt hết thì dùng trong túi | `true` | Bỏ qua khi game báo giá trị hiệu lực > 60000 |
| … túi hết thì mua | `false` | 300 KNB khóa/cái, mỗi lần 1 cái |
| Đổi KNB → KNB khóa tối đa | `0` | `0..100000` KNB/ngày; mỗi lệnh tối thiểu 10; `0` = không đổi |
| Mua Lệnh Bài Sát Thủ | `0` | `0..100` cái/ngày, 100 KNB/cái; mỗi lệnh tối đa 10 cái |
| Mua Kim Nguyên Bảo ở Kỳ Trân Các | `0` | `0..1000` KNB/ngày (1 vạn lượng/cái); trần game 1000/ngày |
| Giữ lại ít nhất | `0` | `0..10000000` lượng; là **sàn** ngân lượng, không phải số được tiêu |

**Cooldown:** Trợ thủ 30 phút · Khấn tượng 10 phút · Tiên Thảo 3 phút · Điểm danh/mốc cấp 60 giây · Dã Tẩu và Lệnh Bài Sát Thủ 5 phút · sự kiện Hồng Bao 60 giây (lỗi xác nhận thì nghỉ 30 phút) · Kỳ Trân Các 60 giây · đổi chế độ 60 giây. Thứ tự mỗi nhịp: Trợ thủ → Khấn tượng → Tiên Thảo → Quà → Dã Tẩu → Lệnh Bài Sát Thủ → Hồng Bao → Kỳ Trân Các → Chế độ.

| Cơ chế | Chi tiết |
|---|---|
| Bộ đếm ngày | Lưu theo từng tab, theo ngày **UTC+7**; sang ngày mới thì hạn mức về mặc định |
| Trần cứng | KNB 1000/ngày, Đại Hồng Bao 1/ngày, Hồng Bao 3/ngày |
| Cổng “người dùng rảnh” | Chỉ đổi chế độ, đi khấn, mở Kỳ Trân Các hoặc sự kiện sau 60 giây không chạm tab |
| Cổng phiên bản và trạng thái | Bản game < 990: ghi log một lần rồi không chạy. Chỉ chạy khi trạng thái game cập nhật trong 10 giây gần nhất; không đọc được bộ đếm của game thì không mua ở Kỳ Trân Các hay sự kiện |

## Xử lý sự cố

| Triệu chứng | Nguyên nhân | Cách xử lý |
|---|---|---|
| Cài bản mới lên bản 4.x báo `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Bản 4.x ký bằng chứng chỉ khác với bản mới | Gỡ bản cũ rồi cài lại — xem cảnh báo đầu tài liệu. Tài khoản đã lưu sẽ mất |
| Vài tab kẹt “Đang vào”, chỉ một tab Online | Bản cũ nhận diện “đã vào game” sai vì ô chat trong game | Dùng bản 5.1.0 trở lên; thử `↻` hoặc `▶ Mở`, xem dòng trạng thái từng tab |
| Không mở hoặc chọn được danh sách trong game | Vòng lặp đăng nhập tự bấm ra ngoài làm đóng danh sách đang mở | Chờ 5 giây không chạm; app tự nhường tab vừa được chạm |
| Tab hiện “Đang kết nối lại máy chủ…” mãi | Bản cũ thử lại vô hạn khi lỗi tải trang | Tối đa 3 lần rồi báo “❌ Máy chủ không phản hồi…”. Kiểm tra mạng, DNS và URL |
| Bỏ tick tự động đăng nhập nhưng app vẫn đăng nhập | Cờ này chưa có tác dụng | Để trống tài khoản hoặc mật khẩu của tab đó, hoặc bấm `⏹` |
| Mất kết nối khi khóa màn hình hoặc để lâu | Tối ưu pin của máy giết service nền | Tắt tối ưu pin, đặt “Không giới hạn”, khóa app trong recent apps nếu máy yêu cầu |
| Treo máy không mở khóa được | Overlay chỉ nhận chạm đúp | Chạm đúp hai lần liên tiếp, hoặc bấm Back |
| Tự động không làm gì | Công tắc tổng tắt; game < 990; chưa vào game đủ 30 giây; đang trong phó bản/tháp/truy sát/đã chết; tab chưa `IN_GAME` | Bật công tắc tổng, xem dòng trạng thái có log `🤖`, chờ ngoài phó bản, kiểm tra phiên bản game |
| Mua KNB nhưng không nhận được | Không đọc được bộ đếm hoặc trạng thái ví của game; giao dịch chưa xác nhận | Đặt mục mua về `0` và kiểm tra thủ công; xem dòng “Kỳ Trân Các: đã bấm mua nhưng không nhận được KNB” |
| Máy quá nóng hoặc tụt pin | Nhiều tiến trình game cùng chạy | Giảm số tab về 4 hoặc ít hơn, hạ FPS xuống 30, dừng bớt tab bằng `⏹` |
| Tăng lên 6–8 tab rồi vài tab tự đăng nhập lại | Hệ điều hành thu hồi tiến trình trang vì thiếu RAM; app tạo lại đúng tab đó | Giảm số tab, đóng app khác, tăng RAM máy. Đây là hành vi đúng, không phải crash |
| Bấm cập nhật nhưng không cài được | Chưa cấp quyền “Cài ứng dụng không rõ nguồn gốc”, hoặc file tải về có chữ ký khác | Cấp quyền trong Cài đặt. Nếu chữ ký khác, phải gỡ bản cũ rồi cài tay một lần |

## Hiệu năng

Đo trên LDPlayer 14, Android 14 x86_64, VM 6 nhân / 6 GB, bằng jiffies `/proc` cho tiến trình app và từng tiến trình trang.

- **4 tab:** ≈ 80% một nhân CPU.
- **8 tab:** ≈ 220% một nhân CPU, ~1,9 GB RSS tổng, 8 tiến trình trang — đúng bằng số tab.

Soak 30 phút ở 8 tab: 0 crash, 0 lần tiến trình trang bị thu hồi.

Chi phí gần như tuyến tính theo số tab: mỗi tiến trình trang đã vào game giữ khoảng 170–290 MB. Đó là lý do khuyến nghị 4 tab và vì sao app hỏi lại khi vượt mốc. Muốn giảm tải thêm thì có ba cách thực chất: giảm số tab, hạ FPS xuống 30, hoặc tắt bớt tab không dùng.

## Bảo mật

Ứng dụng lưu tài khoản game ngay trên máy, **dạng văn bản thường, không mã hóa**. Ai đọc được dữ liệu ứng dụng — máy đã root, bản sao lưu thiết bị, công cụ điều tra máy — đều đọc được mật khẩu game.

App **chưa được hardened**, chỉ dành cho dùng cá nhân, cung cấp nguyên trạng và không kèm bảo hành. Đừng dùng trên máy dùng chung, máy cho người khác mượn, hoặc máy đã root. Dùng mật khẩu game riêng, đừng dùng lại mật khẩu của tài khoản khác.

APK phát hành được ký bằng một khóa duy nhất; cách tự kiểm tra chữ ký ở [Cài đặt](#cài-đặt-và-lần-chạy-đầu). Báo lỗi bảo mật qua kênh riêng tư — xem [`SECURITY.md`](SECURITY.md).

## Build từ mã nguồn

Cần JDK 25 (Gradle tự tải nếu máy chưa có), `platforms;android-36`, `build-tools;36.0.0`, `platform-tools`; dùng wrapper trong repo. Cài bằng `sdkmanager --licenses` rồi `sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"`.

`local.properties` ở gốc project, **không commit**:

```properties
# Windows
sdk.dir=C\:\\Users\\<ban>\\AppData\\Local\\Android\\Sdk
# Linux/macOS
# sdk.dir=/home/<ban>/Android/Sdk
```

Bản release cần khóa ký riêng; file khóa và mật khẩu **không nằm trong repo**. Tạo `keystore.properties` ở gốc project và để ngoài git:

```properties
storeFile=<đường-dẫn>.p12
storePassword=<mật-khẩu>
keyAlias=<alias>
keyPassword=<mật-khẩu>
```

```bash
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease      # app/build/outputs/apk/release/app-release.apk
./gradlew :app:testDebugUnitTest    # 23 unit test
./gradlew :app:lintDebug
```

Thiếu khóa thì `assembleRelease` dừng ở bước đóng gói — dùng `assembleDebug` cho bản thử. Không phát hành APK debug. Bộ test gồm 23 unit test (bố cục tab, quy tắc phiên bản, script đăng nhập, script keepalive, tên ứng dụng) và 1 instrumented test kiểm tra `applicationId` (`./gradlew :app:connectedDebugAndroidTest`).

### Cảnh báo Windows: đường dẫn có ký tự ngoài ASCII

Đường dẫn project có dấu khiến Gradle ghi classpath vào `@argfile` bằng UTF-8 trong khi JVM đọc bằng codepage ANSI của Windows: test báo `ClassNotFoundException` cho **mọi** class dù code biên dịch đúng. Clone và build trong thư mục ASCII tuyệt đối, ví dụ `C:\duong-dan-ascii`. `android.overridePathCheck=true` chỉ tắt kiểm tra đường dẫn của AGP, **không** sửa lỗi encoding.

## Lịch sử phiên bản

| Phiên bản | Nội dung chính |
|---|---|
| **5.1.0** | Tương thích giao diện game build 1041: viết lại lớp nhận diện đã vào game, ô “Nhớ tài khoản”, chọn nhân vật; đọc chỉ số qua dữ liệu game phát ra. Đại Hồng Bao, Hồng Bao, kỷ lục tháp và Boss Hoàng Kim tạm không đọc được |
| **5.0.0** | Ký bằng khóa release thật (**breaking**: phải gỡ bản 4.x, mất tài khoản đã lưu); quy ước `versionCode` mới; thêm `CHANGELOG.md` |
| **4.1.0** | Dải trạng thái 32dp thay thanh chrome 108dp; sheet điều khiển nhanh; số tab 1–8; tự cập nhật từ GitHub Releases |
| **4.0.0** | Bộ tự động trong game (18 trường); sửa treo khung sau khi chat; dọn bộ nhớ trang theo đúng id; watchdog luân phiên |
| **3.0.1** | Gỡ bộ chọn Cụm; thử tải lại có giới hạn |
| **3.0.0** | Bước đầu tự động hóa trong game |
| **2.0.0** | Gỡ VPN và chọn Cụm; giữ tài khoản khi nâng cấp; thêm Gradle wrapper cho Windows |
| **1.0** | Bản đầu: 4 tab, tự đăng nhập, có VPN và chọn Cụm |

Chi tiết thay đổi và lý do: [`CHANGELOG.md`](CHANGELOG.md).

## Tài liệu

| Tài liệu | Nội dung |
|---|---|
| [`CHANGELOG.md`](CHANGELOG.md) | Lịch sử thay đổi theo định dạng Keep a Changelog |
| [`SECURITY.md`](SECURITY.md) | Chính sách bảo mật: dữ liệu lưu ở đâu, giới hạn khi dùng, cách báo lỗi riêng tư |
| [`docs/DESIGN-LAYOUT.md`](docs/DESIGN-LAYOUT.md) | Đặc tả bố cục dải trạng thái 32dp |
| [`docs/DESIGN-TAB-COUNT.md`](docs/DESIGN-TAB-COUNT.md) | Đặc tả số tab 1–8 và quy tắc bố cục |
| [`docs/PORT-DISCORD.md`](docs/PORT-DISCORD.md) | Hướng dẫn tự dựng cảnh báo Discord webhook (tuỳ chọn, app không có sẵn) |

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

Không có quy trình đóng góp chính thức. Báo lỗi và góp ý qua Discord; muốn dùng lại mã nguồn thì cần xin phép trước.
