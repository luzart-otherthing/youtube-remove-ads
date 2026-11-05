# Ngữ cảnh làm bản iOS (để phiên sau làm tiếp)

Nhánh: `claude/great-brahmagupta-enb3tq` (chưa merge vào `main`, chưa có PR).

## Đã xong

- `ios/TubeSachCore/`: gói Swift lõi, build và test được trên Linux.
  - Gồm JSON giữ thứ tự khoá, Parser (trang chủ, tìm kiếm, trang xem, bình luận, kênh, Shorts), InnerTube, PlayerData (HLS), SponsorBlock, Dislikes, WatchTracker, kênh đăng ký.
  - Kênh đăng ký đọc RSS; RSS hay trả 404 nên có dự phòng bằng tab Video qua InnerTube.
  - 18 test đều qua, kể cả `ONLINE=1` gọi YouTube thật. Test dùng chung fixture với bản Android: `app/src/test/resources/fixtures`.
- `ios/TubeSach/`: app SwiftUI cho iOS 16 trở lên, chạy trên iPhone và iPad.
  - Có 4 tab, tìm kiếm (gợi ý, dán link), trang xem dùng AVPlayerViewController (HLS, hình trong hình, phát nền, màn hình khoá), SponsorBlock có Hoàn tác, bình luận, trang kênh, Shorts (TabView xoay 90°), Cài đặt.
  - Mở thẳng một màn hình khi khởi động bằng tham số: `-openVideo ID`, `-openSearch "…"`, `-openChannel UC…`, `-openTab shorts|subscriptions|library`.
- `ios/project.yml`: cấu hình dự án Xcode cho XcodeGen. File `.xcodeproj` không commit.
- `.github/workflows/ios.yml`:
  - Job Linux chạy `swift test`, rồi job macOS-15 (Xcode 16.4) build file `.ipa` chưa ký và đưa lên artifact.
  - Trên `main`: tạo release `ios-v1.0.N` với `--latest=false` để không làm hỏng việc tự cập nhật của app Android.
  - Commit có `[screenshots]`: chạy app trên iPhone 16 và iPad Pro giả lập, chụp màn hình rồi in base64 ra log. Giải mã bằng script đọc các khối `=====BEGIN x.jpg=====`.
- `build.yml` (Android) bỏ qua các thay đổi chỉ nằm trong `ios/**`.
- README có mục "Bản iPhone / iPad (iOS)": cách cài bằng Sideloadly, AltStore/SideStore hoặc tài khoản Developer trả phí, kèm ảnh `docs/screenshots/ios_*.jpg`.

## Đã kiểm chứng

- Lần chạy CI 1 và 2: build thành công, không lỗi.
- Ảnh trên máy ảo: trang chủ (thịnh hành khi mới mở), tìm kiếm, trang xem có video đang phát, kênh, tab Bạn, giao diện tối, iPad lưới 3 cột đều đúng.
- Shorts trên máy ảo báo "Đăng nhập để xác nhận bạn không phải là robot". Đây là do IP máy chủ GitHub. Chạy từ máy khác, có mã khách (visitorData), thì Shorts phát được.
  - Lưu ý: gọi player **không có** visitorData thì Shorts luôn bị chặn. App luôn gửi visitorData qua `ensureVisitor()`.

## Chưa xong / việc tiếp theo

1. Kiểm tra lần chạy CI thứ 3 (commit `a5b69be`). Lần này sửa: nền đen và nút Thử lại cho Shorts, video liên quan 2 cột trên iPad dọc. Lúc dừng phiên, lần chạy này chưa có kết quả.
2. Hỏi người dùng có muốn tạo PR hoặc merge vào `main` không. Chỉ khi merge, GitHub Actions mới tạo Release có file `.ipa`. Trước đó tải file ở Actions → Build iOS → Artifacts.
3. Ý tưởng làm thêm (chưa làm):
   - Share Extension để mở link YouTube chia sẻ từ Zalo.
   - Khi gặp lỗi "robot", thử lại một lần với visitorData mới.
   - Nhớ tốc độ phát người dùng đã chọn.
4. Lỗi nhỏ bên Android, nên làm ở việc riêng: `Parser.lockup` trong `app/.../innertube/Parser.kt` đọc sai metadata của video trong tab kênh (chỉ có một hàng "lượt xem • ngày"). Bản Swift đã sửa (`looksLikeViews` trong `Parser.swift`).

## Lệnh hay dùng

```bash
cd ios/TubeSachCore && swift test
cd ios/TubeSachCore && ONLINE=1 swift test --filter OnlineTests
```

- Repo đang để riêng tư, nên mỗi phút chạy runner macOS bị tính gấp 10 lần. Chỉ thêm `[screenshots]` vào commit khi thật cần.
- Phiên trước đã typecheck AppModel và LocalStore trên Linux bằng module SwiftUI giả (stub). Thư mục stub nằm trong scratchpad của phiên đó, không có trong repo.
