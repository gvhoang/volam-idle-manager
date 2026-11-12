# Chính sách bảo mật

Võ Lâm Idle Manager là **công cụ cá nhân**, cung cấp nguyên trạng, không kèm bảo hành. Tài liệu
này nói rõ app lưu gì, giới hạn ở đâu, và cách báo một vấn đề.

## App lưu gì trên máy

| Dữ liệu | Nơi lưu | Ghi chú |
|---|---|---|
| Tài khoản, mật khẩu game của từng tab | Bộ nhớ riêng của ứng dụng | **Không mã hoá**, chỉ hệ điều hành bảo vệ |
| Phiên đăng nhập, cookie | Profile WebView riêng của từng tab | Xoá khi gỡ ứng dụng |
| Cấu hình (số tab, FPS, tuỳ chọn tự động) | Bộ nhớ riêng của ứng dụng | Như trên |

Hệ quả cần biết trước khi dùng:

- **Gỡ ứng dụng là mất toàn bộ tài khoản đã lưu.** Không có bản sao, không có khôi phục.
- Máy đã **root**, hoặc máy **dùng chung**, thì người khác đọc được những gì app lưu. Đừng dùng
  ở hai trường hợp đó nếu tài khoản game có giá trị với bạn.
- App lưu mật khẩu để tự đăng nhập lại. Nếu bạn dùng chung mật khẩu game với tài khoản khác,
  hãy đổi cách làm đó trước.

## Mức độ hoàn thiện

App **chưa được hardened**. Nó chạy ổn định trong điều kiện dùng thực tế, nhưng chưa trải qua
đánh giá độc lập, chưa có kiểm thử xâm nhập, và phần nhúng WebView chưa được siết chặt như một
ứng dụng xử lý thông tin đăng nhập nên được siết.

Hãy dùng như một công cụ tiện lợi cho tài khoản cá nhân, không phải như một sản phẩm bảo mật.

## Xác minh bản tải về

Mỗi bản phát hành ghi kèm SHA-256 trong phần mô tả release. Đối chiếu trước khi cài:

```bash
sha256sum volamidle-manager.apk        # Linux/macOS
certutil -hashfile volamidle-manager.apk SHA256   # Windows
```

Chữ ký của APK do khoá phát hành của dự án tạo ra; đối chiếu:

```bash
apksigner verify --print-certs volamidle-manager.apk
```

| Thuộc tính | Giá trị đúng |
|---|---|
| DN | `CN=Vo Lam Idle Manager, OU=Release Signing, O=Thien Co Labs, L=Ho Chi Minh City, ST=Ho Chi Minh, C=VN` |
| SHA-256 chứng chỉ | `3A:44:D8:D1:CA:33:4B:6E:49:7E:29:95:B9:70:5A:59:BE:B5:A2:AC:AE:25:BB:C3:1E:9F:72:16:BF:86:90:A7` |

Đây là dữ liệu công khai: nó nằm trong mọi bản cài và `apksigner` in ra từ tệp bạn đã tải. Giá
trị của nó là để phát hiện một bản bị ký lại, không phải để giữ bí mật.

## Báo một vấn đề

Vấn đề bảo mật: mở **báo cáo riêng tư** ở tab *Security* của repo
(`https://github.com/gvhoang/volam-idle-manager/security/advisories/new`). Đừng mở issue công
khai cho tới khi có bản sửa.

Lỗi dùng bình thường, góp ý, hoặc cần hỗ trợ: tham gia Discord **https://discord.gg/volamidle**.

## Phạm vi

Trong phạm vi: ứng dụng Android trong repo này.

Ngoài phạm vi: máy chủ và nội dung của webgame (không thuộc dự án này), hành vi của WebView do
hệ điều hành cung cấp, và các bản dựng không phát hành từ repo này.
