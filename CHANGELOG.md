# Changelog

Mọi thay đổi đáng chú ý của **Võ Lâm Idle Manager** được ghi trong tệp này.

Định dạng theo [Keep a Changelog](https://keepachangelog.com/vi/1.1.0/). Phiên bản theo [Semantic Versioning](https://semver.org/lang/vi/). Ngày phát hành chính xác nằm ở tag tương ứng trên GitHub Releases — tệp này không ghi ngày để tránh sai lệch khi tag được dời.

Từ 5.0.0, `versionCode` theo công thức `major * 10000 + minor * 100 + patch`.

---

## [5.1.0]

Bản này tồn tại vì **game đổi giao diện** (build 1041, bộ tài nguyên `1041h5.211`: `ui20.css`,
thanh tab dưới, toàn bộ DOM cũ bị thay). Lớp đọc DOM của app phải viết lại.

### Sửa

- **Nhận diện “đã vào game” sai hoàn toàn.** Điều kiện cũ dò chữ “Hành trang” và “Võ công” trong
  `bodyText`. Giao diện mới có hai chữ đó **luôn hiện** ở thanh tab dưới, kể cả tại màn hình
  đăng nhập, nên tab nào cũng bị coi là đã vào game và **auto-login không bao giờ chạy**. Nay
  dò cấu trúc: `#modeSw` hiện, `body.u20`/`u2m`, `#stageLbl` có chữ.
- **Ô “Nhớ tài khoản” đổi từ checkbox sang `<button id="ngRem" class="cb on">`.** Code cũ chỉ
  quét `input[type=checkbox]` nên không tích được. Nay đọc/ghi theo id, giữ đường checkbox làm
  dự phòng.
- **Chọn nhân vật có thể gây vòng lặp tải lại.** `#csGo` bị `disabled` khi ô đang chọn trống;
  `cselEnter()` ghi `SLOT_PTR` rồi `location.reload()`. Nay chỉ bấm `#csGo` khi nó bật, ô trống
  thì nhảy sang thẻ nhân vật khác thay vì bấm vào ô rỗng.
- **Nút “Thử lại” của game giờ là `location.reload()`** — bấm mỗi nhịp quét sẽ thành vòng lặp
  tải lại. Nay giới hạn 60 giây một lần.
- **Gọi `#vlmAuto` với `k = "knb"` luôn ném lỗi** vì `VLM_AUTO_KTC = ["ldt","tt","ttsp"]`.
  Đã bỏ nhánh đó.
- **Dòng trạng thái của thẻ tab bị watchdog ghi đè sau chưa tới 1 giây**, nên mọi thông báo
  `🤖 …` của bộ tự động không ai đọc được — kể cả khi QA. Nay giữ log 8 giây.

### Thay đổi

- Đọc chỉ số nhân vật qua `postMessage({vlm:'st'})` do chính game phát (tên, phái, cấp, %EXP,
  Dã Tẩu) thay vì quét 400 node DOM.
- Đồng bộ chế độ dùng `#vlmAuto` → `cheDo{m}` với id `farm`/`dg`/`quest`/`tower`, thay cho việc
  bấm `<select>` của giao diện cũ.
- Loại trừ thêm khỏi nhận diện form đăng nhập: `#logBox`, `u20txt`, `chatTxt`, `hhChTxt` và từ
  khoá “mật ngữ”.

### Hạn chế đã biết

- **Đại Hồng Bao, Hồng Bao, kỷ lục tháp, Boss Hoàng Kim không còn đọc được.** Giao diện mới
  không có phần tử thường trực nào cho bốn số này — chúng chỉ nằm trong modal (🧧, Leo Tháp).
  App giữ giá trị cũ thay vì ghi đè bằng 0. Muốn có lại thì cần game bổ sung vào `info`.
- Tự tạo nhân vật mới: không làm, vì đường đó dẫn tới `cselEnter()` với ô rỗng và tải lại trang.

---

## [5.0.0]

### Thay đổi phá vỡ tương thích

- **Lần đầu ký bằng khóa release thật.** 4.0.0 và 4.1.0 được ký bằng Android debug key; 5.0.0 ký bằng khóa `upload` (PKCS12, RSA 4096, SHA384withRSA). Android từ chối cài APK khác chữ ký lên bản đang cài (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), nên **nâng cấp từ bất kỳ bản 4.x nào bắt buộc phải gỡ ứng dụng trước**. Gỡ ứng dụng xoá toàn bộ tài khoản đã lưu trong `SharedPreferences`. Không có đường nâng cấp tại chỗ.
- **Đổi quy ước `versionCode`.** 4.1.0 dùng `versionCode = 5`; 5.0.0 dùng `50000` theo công thức mới. Bản 4.1.0 so sánh `versionCode` với release mới vẫn đọc đúng, nhưng bước cài vẫn thất bại vì chữ ký khác — thông báo trong app là “Không cài được: bản cập nhật được ký bằng khóa khác với bản đang cài”.
- **Kể từ bản này, khóa ký không được đổi nữa.** Mất file khóa ⇒ không bản nào tự cập nhật được. Lộ file ⇒ người khác ký được APK mà Android sẽ chấp nhận làm bản cập nhật.

### Thêm

- `CHANGELOG.md` (tệp này) và README viết lại theo cấu trúc mới, có cảnh báo migration đặt ngay đầu tài liệu.
- Công bố fingerprint SHA-256 của chứng chỉ ký release để người dùng đối chiếu bằng `apksigner verify --print-certs`.

### Thay đổi

- `main` trở thành nhánh chính thức duy nhất. Các tag cũ `v2.0.0`, `v3.0.0`, `v4.0.0`, `v4.1.0` vẫn giữ nguyên.
- `versionCode` theo công thức `major * 10000 + minor * 100 + patch`: `5.0.0` → `50000`, `5.1.0` → `50100`, `5.1.2` → `50102`.
- Bộ kiểm tra cập nhật đọc `versionCode: N` trong body release trước, chỉ suy từ tag khi body không khai báo; nhánh suy từ tag nay tính theo công thức mới nên release chỉ có tag vẫn so sánh đúng.
- Bản release không còn `debuggable`: `isDebuggable = false`, `adb run-as` không đọc được `SharedPreferences` của bản phát hành.
- Bản release được minify và shrink, giảm kích thước APK và bớt mã không dùng.
- Tài liệu được tách theo mục đích: README cho người dùng và người build, `SECURITY.md` cho đánh giá bảo mật, `docs/RELEASE.md` cho quy trình phát hành.

### Ghi chú

- APK phát hành: `volamidle-manager.apk` (asset của release), tag `v5.0.0`.
- Chứng chỉ: DN `CN=Vo Lam Idle Manager, OU=Release Signing, O=Thien Co Labs, L=Ho Chi Minh City, ST=Ho Chi Minh, C=VN`, hiệu lực đến 2054-03-29, SHA-256 `3A:44:D8:D1:CA:33:4B:6E:49:7E:29:95:B9:70:5A:59:BE:B5:A2:AC:AE:25:BB:C3:1E:9F:72:16:BF:86:90:A7`.
- Các vấn đề bảo mật của WebView và lưu trữ **chưa** được sửa trong bản này. Xem [`SECURITY.md`](SECURITY.md).

## [4.1.0]

### Thêm

- **Dải trạng thái 32dp** ở đáy màn hình thay cho chrome quản trị cũ: `☰` mở bảng điều khiển, chip tab đang chọn, các chip `T2`, `T3` …, `⋯` mở sheet nhanh. Dải là sibling của vùng game nên không ăn chạm vào WebView.
- **Sheet điều khiển nhanh 9 hàng**: mở/PC/tải lại/dừng tab, ba chế độ xem, số tab, FPS và treo máy, lưu lượng và reset, đồng bộ chế độ, `🤖 Tự động`, bảng điều khiển, phiên bản và cập nhật. Sheet là overlay nên mở/đóng không làm game reflow.
- **Số cửa sổ game 1–8** qua `TabLimits` (`MIN = 1`, `DEFAULT = 4`, `RECOMMENDED_MAX = 4`, `HARD_MAX = 8`); máy `isLowRamDevice` bị chặn ở 4. Mảng 8 ô cấp phát một lần và không cấp lại.
- **Tự cập nhật từ GitHub Releases**: kiểm tra mỗi 12 giờ ± 30 phút jitter với `ETag`; tải thủ công; cài không bao giờ tự động; allowlist 4 host cho từng hop redirect; kiểm tra trước khi cài bằng `getPackageArchiveInfo` (package, `versionCode` lớn hơn, signer trùng); đối chiếu SHA-256 với `assets[].digest`.
- `TabLayoutPlan` và `TabLayoutPlanTest` (7 test) cho quy hoạch bố cục; `UpdateRulesTest` (8 test) cho luật phiên bản và allowlist host.

### Thay đổi

- Chrome quản trị 108dp thu về 32dp; vùng game chiếm phần còn lại của màn hình.
- Đồng bộ chế độ và `🤖 Tự động` chuyển từ drawer sang sheet nhanh.
- Bố cục 4 Dọc: `cols = min(N, 4)`, quá 4 tab thì xuống hàng và giữ nguyên 1/4 chiều ngang mỗi ô; N ≤ 4 vẫn đi nhánh bố cục cũ.
- Vượt 4 tab, app tự hạ 60 → 30 FPS một lần và hiện toast.
- `versionCode` tăng lên 5.

## [4.0.0]

### Thêm

- Port bộ tự động trong game từ bản PC qua **kênh lệnh ẩn `#vlmAuto`** (yêu cầu bản game ≥ 990): 18 trường cấu hình, thứ tự hành động cố định, cooldown theo từng hành động và trần ngày lưu trong `localStorage`.
- Watchdog luân phiên: mỗi 1200ms quét một tab thay vì quét cả bốn tab mỗi 2,5 giây.
- Dọn DOM luân phiên mỗi 90 giây, chỉ trên các id thật của game; giữ 30 dòng chat và 10 hiệu ứng gần nhất.
- So sánh dữ liệu hiển thị trước khi phát `StateFlow` để không recompose UI mỗi nhịp quét.
- Giới hạn 400 node cho mỗi lượt tìm tên nhân vật; shim `cancelAnimationFrame` để không rò rỉ `setTimeout` mỗi frame.

### Sửa

- **Hết treo khung sau khi chat**: ô chat trong game bị loại khỏi danh sách ô nhập của form đăng nhập, nên gõ chat không còn bị hiểu là đang ở màn hình login.
- **Bốn tab cùng online**: nhận diện “đã vào game” chuyển sang ưu tiên HUD trận đấu thay vì chỉ dựa trên việc không còn ô nhập.
- **Dropdown trong game mở được**: vòng lặp đăng nhập 850ms có touch-guard 5 giây và chỉ chạy khi tab `LOGGING_IN`, không còn bắn click ra ngoài làm đóng `<select>`.
- Bỏ `LAYER_TYPE_HARDWARE` để giảm RAM và compositing.

### Ghi chú

- Một phần hardening của 3.0.1 bị revert trong quá trình làm bản này (commit `c186416`): `onReceivedSslError` quay lại `proceed()`, mixed content quay lại `ALWAYS_ALLOW`, bỏ `shouldOverrideUrlLoading`, inject không còn kiểm tra origin. Đây là thay đổi có chủ ý để chạy được game, và là lý do `SECURITY.md` của 3.0.1 không còn mô tả đúng trạng thái mã.
- `versionCode` tăng lên 4.

## [3.0.1]

### Thay đổi

- Gỡ bộ chọn Cụm khỏi UI và luồng đăng nhập.
- Hardening WebView: từ chối lỗi TLS, đặt `MIXED_CONTENT_NEVER_ALLOW`, giới hạn điều hướng top-level.
- Retry khi lỗi main-frame có giới hạn thay vì thử lại vô hạn.

### Sửa

- Bổ sung `isTrustedGameUrl` và sửa lỗi biên dịch còn sót sau commit gỡ bộ chọn Cụm (`bd53f09`).

### Ghi chú

- Các thay đổi hardening ở trên bị revert ở 4.0.0. Không dựa vào bản đánh giá bảo mật của 3.0.1 để đánh giá mã hiện tại.
- `versionCode` là 2.

## [3.0.0]

### Thêm

- Nhánh `v3`, giữ bộ chọn Cụm.
- Bước đầu của tự động hóa trong game.

### Ghi chú

- Gói build ghi `versionName "1.0"` / `versionCode 1` do lỗi đánh số; giá trị này không phản ánh phiên bản thực tế.

## [2.0.0]

### Thay đổi

- Nhánh `main`.
- Gỡ VPN và bộ chọn Cụm.

### Sửa

- Bảo toàn tài khoản khi nâng cấp; không hồi sinh tài khoản đã xóa.
- Chặn URL rác và báo lỗi rõ khi không tải được trang.

### Thêm

- Gradle wrapper cho Windows.

### Ghi chú

- `versionCode` là 2.

## [1.0]

### Thêm

- Bản nhập ban đầu trước khi refactor: 4 tab WebView, tự đăng nhập, có VPN và bộ chọn Cụm.

---

[5.0.0]: https://github.com/gvhoang/volam-idle-manager/releases/tag/v5.0.0
[4.1.0]: https://github.com/gvhoang/volam-idle-manager/releases/tag/v4.1.0
[4.0.0]: https://github.com/gvhoang/volam-idle-manager/releases/tag/v4.0.0
[3.0.1]: https://github.com/gvhoang/volam-idle-manager/releases/tag/v3.0.1
[3.0.0]: https://github.com/gvhoang/volam-idle-manager/releases/tag/v3.0.0
[2.0.0]: https://github.com/gvhoang/volam-idle-manager/releases/tag/v2.0.0
[1.0]: https://github.com/gvhoang/volam-idle-manager/releases/tag/v1.0
