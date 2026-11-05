# Ngữ cảnh làm bản iOS (để phiên sau làm tiếp)

Nhánh: `claude/great-brahmagupta-enb3tq`. Nhánh đã gộp `main` (kể cả các sửa lỗi Android và đăng nhập Google thử nghiệm).

## Đã xong

- `ios/TubeSachCore/`: gói Swift lõi, build và test được trên Linux.
  - Gồm JSON giữ thứ tự khoá, Parser (trang chủ, tìm kiếm, trang xem, video liên quan tải thêm, bình luận, kênh, Shorts, tài khoản), InnerTube, PlayerData (HLS, video dọc), SponsorBlock, Dislikes, WatchTracker, kênh đăng ký (RSS, dự phòng bằng InnerTube).
  - `AccountSession`: cookie youtube.com, SAPISIDHASH (SHA-1 viết bằng Swift thuần), cập nhật cookie xoay vòng từ Set-Cookie.
  - 25 test đều qua (`swift test`), kể cả `ONLINE=1` gọi YouTube thật. Test dùng chung fixture với bản Android: `app/src/test/resources/fixtures`.
- `ios/TubeSach/`: app SwiftUI cho iOS 16 trở lên, chạy trên iPhone và iPad.
  - Có 4 tab, tìm kiếm, trang xem (AVPlayerViewController: HLS, hình trong hình, phát nền, màn hình khoá), SponsorBlock, bình luận, kênh, Shorts, Cài đặt.
  - Các sửa lỗi lấy từ bản Android:
    - tải thêm video liên quan khi cuộn;
    - giữ token khi lỗi mạng;
    - có mạng lại thì tự tải lại (NWPathMonitor);
    - link hết hạn thì lấy link mới trước khi phát tiếp;
    - tải sẵn video tiếp theo ~30 giây trước khi hết video;
    - tự phát gặp video lỗi thì bỏ qua, tối đa 3 video;
    - Shorts phủ kín màn hình khi chỉ phải cắt ≤ 20%;
    - có nút bình luận trong Shorts.
  - Đăng nhập Google (thử nghiệm), ở cuối Cài đặt và có hộp cảnh báo:
    - đăng nhập bằng WKWebView (kho dữ liệu không lưu lại, chuỗi nhận dạng Safari); cookie giữ trong Keychain (`AccountStore`);
    - trang chủ và kênh đăng ký theo tài khoản; nhập các kênh đã đăng ký;
    - đồng bộ đăng ký kênh, thích, không thích; ghi lịch sử xem vào tài khoản; viết bình luận.
  - Mở thẳng một màn hình khi khởi động bằng tham số: `-openVideo ID`, `-openSearch "…"`, `-openChannel UC…`, `-openTab shorts|subscriptions|library`, `-openSettings YES`, `-openLogin YES`.
- `.github/workflows/ios.yml`:
  - Job Linux chạy `swift test`, rồi job macOS-15 (Xcode 16.4) build file `.ipa` chưa ký.
  - Trên `main`: tạo release `ios-v1.0.N` với `--latest=false`.
  - Commit có `[screenshots]` và có sửa file trong `ios/`: chạy máy ảo và chụp màn hình, ảnh in base64 ra log.
  - Workflow chỉ chạy khi đổi `ios/**`, file workflow hoặc fixture. Sửa riêng README thì không chạy.
- README: mục "Bản iPhone / iPad (iOS)" có cách cài (Sideloadly, AltStore/SideStore, tài khoản Developer trả phí) và ảnh chụp.

## Đã kiểm chứng

- Các lần build CI 1–5 đều thành công, không cảnh báo nào từ code app.
- Ảnh máy ảo: trang chủ, tìm kiếm, trang xem có video đang phát, kênh, tab Bạn, giao diện tối, iPad đều đúng.
- Shorts trên máy ảo GitHub bị YouTube bắt "xác nhận không phải robot" (do IP). Từ máy khác, có visitorData, thì phát được.

## Việc tiếp theo

1. Xem kết quả lần chạy `[screenshots]` mới nhất: trang Cài đặt có mục Tài khoản Google, trang đăng nhập Google tải được.
2. Hỏi người dùng có tạo PR / merge vào `main` không. Chỉ khi merge mới có Release kèm file `.ipa`.
3. Lỗi nhỏ bên Android, nên làm ở việc riêng: `Parser.lockup` đọc sai metadata của video trong tab kênh. Bản Swift đã sửa bằng `looksLikeViews`.

## Lệnh hay dùng

```bash
cd ios/TubeSachCore && swift test
cd ios/TubeSachCore && ONLINE=1 swift test --filter OnlineTests
```

Repo đang để riêng tư, nên mỗi phút chạy runner macOS bị tính gấp 10 lần. Chỉ thêm `[screenshots]` khi thật cần.
