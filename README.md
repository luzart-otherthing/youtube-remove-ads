# Tube Sạch – xem YouTube không quảng cáo

Ứng dụng Android có giao diện theo kiểu app YouTube, **không quảng cáo, không cần đăng nhập**, có hình trong hình, phát khi tắt màn hình và SponsorBlock. Toàn bộ nội dung bằng tiếng Việt: trang chủ, đề xuất, thịnh hành, bình luận, phụ đề.

| Trang chủ | Xem video | Toàn màn hình |
|---|---|---|
| ![](docs/screenshots/01_trang_chu.png) | ![](docs/screenshots/04_xem_video.png) | ![](docs/screenshots/06_toan_man_hinh.png) |

| Lần đầu mở | Giao diện tối | Trình phát mini | Tìm kiếm | Bạn |
|---|---|---|---|---|
| ![](docs/screenshots/03_trang_chu_lan_dau.png) | ![](docs/screenshots/05_xem_video_toi.png) | ![](docs/screenshots/07_trinh_phat_mini.png) | ![](docs/screenshots/08_ket_qua_tim_kiem.png) | ![](docs/screenshots/11_ban.png) |

> Ảnh được chụp tự động trên máy tính (Robolectric) với dữ liệu YouTube thật, nên ô ảnh thu nhỏ để trống. Trên điện thoại ảnh hiện bình thường.

## Tính năng

**Giống app YouTube**
- Thanh dưới: *Trang chủ · Shorts · Kênh đăng ký · Bạn*.
- Trang chủ có hàng chip của YouTube (*Tất cả, Âm nhạc, Tin tức, Trò chơi, Danh sách kết hợp, Trực tiếp, Mới tải lên gần đây…*), kệ Shorts, kệ "Tin nổi bật". Kéo xuống để làm mới, cuộn mãi không hết.
- Trang xem có tiêu đề, "…thêm" (nội dung mô tả: link và mốc thời gian bấm được), kênh kèm nút **Đăng ký** / **🔔 Đã đăng ký ⌄**, viên 👍 | 👎, Chia sẻ, Lưu, thẻ Bình luận, video liên quan.
- Trình phát có:
  - nút ⌄ thu nhỏ, phụ đề CC, ⚙ cài đặt;
  - nút trước / phát / tiếp theo;
  - thanh tua đỏ, chạm 2 lần để tua ±10 giây;
  - vuốt xuống để thu nhỏ, vuốt lên để toàn màn hình.
- Toàn màn hình ngang có tiêu đề, hàng nút Thích / Không thích / Bình luận / Lưu / Chia sẻ.
- Bảng ⚙ giống YouTube:
  - **Chất lượng**: Tự động (khuyên dùng) / Chất lượng hình ảnh cao hơn / Tiết kiệm dữ liệu / Nâng cao (1080p…144p);
  - **Phụ đề**, có *Tự động dịch: Tiếng Việt*;
  - **Tốc độ phát** 0,25–2x;
  - **Hẹn giờ ngủ**;
  - **Lặp lại video**;
  - **SponsorBlock**.
- Trình phát mini nằm trên thanh dưới; giao diện sáng/tối theo máy.
- Trang kênh (ảnh bìa, @tên, số người đăng ký, tab Video / Trực tiếp), tab Shorts vuốt dọc, tab Bạn (video đã xem, đã thích, xem sau).

**Hơn app YouTube (học từ ReVanced / RVX "YT Extended")**
- Không quảng cáo.
- Phát khi tắt màn hình (có nút trên màn hình khoá); lúc đó chỉ tải tiếng để tiết kiệm 4G.
- Hình trong hình tự bật khi bấm nút Home.
- **SponsorBlock**: tự bỏ qua đoạn tài trợ, chọn được từng loại (tài trợ, tự quảng cáo, nhắc đăng ký, mở đầu, kết thúc…). Đoạn được tô màu trên thanh tua, có thông báo "Đã bỏ qua… · Hoàn tác". **Bật/tắt bất cứ lúc nào** bằng nút $ trên trình phát, trong bảng ⚙ hoặc trong Cài đặt.
- **Return YouTube Dislike**: hiện lại số lượt không thích.
- Ẩn Shorts; cỡ chữ lớn cho người lớn tuổi.
- Mở link YouTube được chia sẻ từ Zalo/Facebook; tự kiểm tra bản cập nhật.
- **Dùng trên ô tô (Android Auto)**, kể cả điện thoại Android 11: xem video có hình khi đỗ xe, nghe tiếng và chọn video khi đang chạy. Xem [hướng dẫn](#dùng-trên-ô-tô-android-auto).

## Đề xuất tiếng Việt mà không cần đăng nhập

Giống YouTube thật: **lần đầu mở, trang chủ trống** ("Thử tìm kiếm để bắt đầu"); app hiện tạm danh sách *Thịnh hành tại Việt Nam* bên dưới. Sau khi xem vài video, trang chủ có đề xuất tiếng Việt của chính YouTube.

Cách làm: giống trình duyệt chưa đăng nhập. App giữ một **mã khách ẩn danh** (visitor ID) và báo cho YouTube biết đã xem video nào. YouTube dựa vào đó để đề xuất. Không có tài khoản Google, không có tên hay email nào. Có thể tắt hoặc xoá trong *Cài đặt → Quyền riêng tư*.

## Đã tìm hiểu những mã nguồn nào?

| Dự án | Sao GitHub | Cách lấy dữ liệu | Nhận xét |
|---|---|---|---|
| NewPipe | ~40,4 nghìn | NewPipeExtractor (Java) | Thư viện lấy dữ liệu tốt nhất, được bảo trì đều; giao diện riêng thì cũ |
| SmartTube | ~35,9 nghìn | MediaServiceCore: gọi thẳng InnerTube | Dành cho TV; ý tưởng gọi thẳng API của YouTube rất nhanh |
| LibreTube | ~12,5 nghìn | Máy chủ Piped hoặc NewPipeExtractor | Giao diện Material |
| ReVanced / RVX (anddea) | – | Sửa (patch) app YouTube gốc | Giao diện YouTube thật nhưng phải tự vá APK của Google, cần GmsCore |
| yt-dlp | rất nhiều | Python | Hợp cho tải video, không hợp làm app xem trên Android |

**Kết hợp trong Tube Sạch:**
- **Gọi thẳng InnerTube** như SmartTube cho trang chủ, tìm kiếm, trang xem, kênh và Shorts. Cách này giữ nguyên chữ tiếng Việt của YouTube ("1,2 Tr lượt xem", "4 ngày trước"), có ảnh đại diện kênh và chip.
- **Lấy link phát bằng máy khách visionOS**: chỉ 1 yêu cầu, khoảng 0,2–0,5 giây; NewPipe cần 1,4–3 giây. App tự dựng manifest DASH nhiều độ phân giải để ExoPlayer tự đổi chất lượng như YouTube.
- **NewPipeExtractor làm dự phòng** khi YouTube đổi cách hoạt động, và để đọc bình luận, gợi ý tìm kiếm.
- **Tính năng của ReVanced/RVX**: SponsorBlock, Return YouTube Dislike, ẩn Shorts, tự chọn chất lượng, hình trong hình, phát nền. Ở đây chúng được làm lại trên app riêng, không sửa APK của Google.

Tên và logo của app được giữ riêng (không dùng logo YouTube) để tránh vi phạm nhãn hiệu, và để Play Protect không cảnh báo "app giả mạo".

## Cài đặt lên điện thoại

1. Vào **Releases** của repo (hoặc *Actions → lần chạy mới nhất → Artifacts*), tải file `TubeSach-2.0.x.apk`.
2. Mở file, cho phép cài từ nguồn này nếu được hỏi, rồi bấm **Cài đặt**. Bản mới cài đè lên bản cũ, không mất lịch sử.

Yêu cầu Android 6.0 trở lên; hình trong hình cần Android 8.0 trở lên.

Một số máy (Xiaomi, Oppo, Vivo, Samsung) có chế độ tiết kiệm pin quá mạnh, làm mất tiếng khi tắt màn hình. Khi đó vào *Cài đặt → Pin → Tube Sạch → Không giới hạn*.

## Dùng trên ô tô (Android Auto)

Cắm điện thoại vào xe (hoặc kết nối Android Auto không dây). Trên màn hình xe, Tube Sạch có 2 biểu tượng:

- **Tube Sạch Video**: xem video có hình, **chỉ khi xe đứng yên**.
- **Tube Sạch**: chọn video và nghe tiếng, dùng được cả khi xe đang chạy.

| | Tube Sạch Video (xem hình) | Tube Sạch (nghe) |
|---|---|---|
| Điện thoại **Android 13 trở xuống** (ví dụ Android 11) | Có, giống CarStream | Có |
| Điện thoại Android 14 | Không (Android Auto chưa cho) | Có |
| Điện thoại Android 15 trở lên | Có, cách chính thức của Google | Có |

Không có quảng cáo ở mọi cách. Lịch sử, kênh đăng ký và danh sách Xem sau dùng chung với điện thoại.

**Xe chạy thì sao?** Hình tự tắt, video vẫn phát tiếng. Điều khiển bằng nút trên vô lăng, giọng nói hoặc biểu tượng "Tube Sạch". Xe dừng hẳn khoảng 3 giây thì hình hiện lại. App biết xe đứng yên nhờ đọc tốc độ xe (hoặc GPS); nếu không đọc được tốc độ thì app coi như xe đang chạy và chỉ phát tiếng. Người lái đừng xem video khi đang lái.

**Cho hành khách xem khi xe chạy** (máy Android 13 trở xuống): trên điện thoại vào *Cài đặt → Ô tô (Android Auto)* → bật **Cho hành khách xem hình khi xe đang chạy**. Màn hình xe sẽ luôn hiện hình. Mặc định tắt và chỉ bật được trên điện thoại. Màn hình giữa nằm trong tầm mắt người lái: người lái tuyệt đối không nhìn video khi đang lái.

### Máy Android 13 trở xuống (ví dụ Android 11) – giống CarStream

Màn hình xe hiện các mục bằng giao diện chuẩn của Android Auto: lưới video có ảnh, **Tìm kiếm** (bàn phím của xe hoặc nói), **Mục khác** (Kênh đăng ký, Đã xem, Xem sau, Đã thích, Thịnh hành, Nhạc hàng đầu). Khi xem video:

- hình chiếm cả màn hình, có tiêu đề, thời gian và thanh tiến độ;
- nút **Quay lại**, **⏯** (phát/dừng), **⏭** (video tiếp theo), **☰** (danh sách video tiếp theo);
- nút **⏪ / ⏩** (lùi/tới 10 giây);
- chạm vào video để phát/dừng.

Cách làm giống CarStream: Android Auto chỉ cho ứng dụng **bản đồ** vẽ lên màn hình xe, nên Tube Sạch Video khai là ứng dụng bản đồ rồi vẽ video vào chỗ bản đồ.

**Cài (làm một lần trên điện thoại):**

1. Cài Tube Sạch như bình thường (xem phần trên).
2. Mở Tube Sạch → **Bạn** → **Cài đặt** → mục **Ô tô (Android Auto)** → bấm **Cài để hiện trên Android Auto** → bấm **Cài đặt**.
   - Nếu điện thoại báo "không được phép cài ứng dụng từ nguồn này", bấm **Cài đặt**, bật **Cho phép từ nguồn này**, quay lại rồi bấm lần nữa.
   - Tube Sạch được cài lại đúng bản đang dùng, **không mất** lịch sử hay kênh đăng ký.
3. Dòng đó đổi thành **Đã sẵn sàng cho Android Auto** là xong.
4. Cắm điện thoại vào xe. Trên màn hình xe, mở danh sách ứng dụng → **Tube Sạch Video**.
   - Lần đầu xem video, bấm **Bật hình** rồi cho phép **tốc độ xe** và **vị trí** (bấm trên điện thoại).
   - Nếu chưa thấy biểu tượng: Cài đặt Android Auto → **Tuỳ chỉnh trình chạy** (*Customize launcher*) → đánh dấu Tube Sạch Video.

**Vì sao phải "cài lại"?** Android Auto chỉ hiện ứng dụng bản đồ "tải từ CH Play". Nút "Nguồn không xác định" trong cài đặt Android Auto không áp dụng cho loại này. Bước 2 làm giống công cụ [AAAD](https://github.com/shmykelsa/AAAD) (dùng để cài CarStream): mở trình cài đặt của Android và ghi nguồn cài là CH Play. App không tải gì từ CH Play. Android 14 trở lên bỏ qua thông tin này, nên cách này chỉ dùng được đến Android 13.

**Cập nhật:** hãy cập nhật bằng thẻ **"Có phiên bản mới"** ngay trong app; bản mới được cài theo cách trên nên vẫn hiện trên xe. Nếu cài bản mới bằng file tải từ trình duyệt, làm lại bước 2.

### Máy Android 15 trở lên

**Tube Sạch Video** là màn hình chính thức của Android Auto cho ứng dụng dùng khi đỗ xe:

- giao diện giống YouTube với Trang chủ, Tìm kiếm (gõ chữ, bấm micro để nói, hoặc bấm chủ đề gợi ý), Kênh đăng ký, Đã xem (xem tiếp chỗ đang dở), Thư viện;
- video to, nút to, chạm 2 lần để tua ±10 giây.

Xe chạy thì Android Auto tự đóng màn hình này.

| Trang chủ trên xe | Tìm kiếm | Xem video |
|---|---|---|
| ![](docs/screenshots/20_o_to_trang_chu.png) | ![](docs/screenshots/21_o_to_tim_kiem.png) | ![](docs/screenshots/22_o_to_xem_video.png) |

**Cài:** bật **Nguồn không xác định** trong Android Auto (các bước ở phần tiếp theo).

### Bật "Nguồn không xác định" trong Android Auto (cho mục nghe, và máy Android 15+)

1. Cài đặt điện thoại → Ứng dụng → Android Auto → *Cài đặt bổ sung trong ứng dụng* (một số máy mở thẳng app Android Auto).
2. Kéo xuống dưới cùng, bấm **Phiên bản** (*Version and permission info*) **10 lần**, rồi bấm **OK**.
3. Bấm **⋮** góc trên bên phải → **Cài đặt dành cho nhà phát triển** → bật **Nguồn không xác định** (*Unknown sources*).
4. Rút cáp ra cắm lại.

Máy đã làm bước "Cài để hiện trên Android Auto" ở trên thì không cần bước này: Android Auto đã coi Tube Sạch như app tải từ CH Play.

### Ghi chú

- Một số máy Xiaomi, Oppo, Vivo dùng trình cài đặt riêng. Trên các máy này, bước "Cài để hiện trên Android Auto" có thể không có tác dụng. Khi đó báo lại để tìm cách khác.
- Để chạy được trên xe khi đỗ (Android 15+), Tube Sạch tự khai là ứng dụng "trò chơi", nhóm duy nhất Android Auto cho phép. Vì vậy một số máy (ví dụ Samsung *Game Launcher*) có thể xếp Tube Sạch vào mục Trò chơi. Không ảnh hưởng gì.
- Quyền vị trí chỉ dùng để đo tốc độ khi xe không gửi tốc độ. App không lưu và không gửi vị trí đi đâu.
- Mục "Tube Sạch" (nghe) hỗ trợ tìm bằng giọng nói: "Ok Google, mở *nhạc bolero* trên Tube Sạch".

**Đã tìm hiểu:**
- [Tài liệu Android for Cars](https://developer.android.com/training/cars/parked/auto) về ứng dụng khi đỗ xe, mục Media và [dữ liệu xe](https://developer.android.com/training/cars/apps/library/car-hardware-api).
- Mã nguồn [CarStream](https://github.com/krisstakos/Carstream-update-2.0.4): dùng bộ công cụ Android Auto cũ, không chính thức.
- [Fermata Auto](https://github.com/AndreyPavlenko/Fermata): giả làm ứng dụng bản đồ; Tube Sạch Video làm theo cách này.
- [AAAD](https://github.com/shmykelsa/AAAD): cách cài để Android Auto nhận app.
- [AndroidAutoMaker](https://github.com/malebuffy/AndroidAutoMaker).

## Khi YouTube thay đổi làm app lỗi

- Đường phát chính (visionOS) lỗi thì app **tự chuyển sang NewPipeExtractor**.
- Muốn cập nhật thư viện: sửa phiên bản `NewPipeExtractor` trong `app/build.gradle.kts` (xem bản mới tại https://github.com/TeamNewPipe/NewPipeExtractor/releases) rồi push lên `main`. GitHub Actions sẽ tự build APK mới.
- Phần đọc giao diện YouTube nằm trong `innertube/Parser.kt`. Test `ParserTest` chạy với dữ liệu thật đã lưu ở `app/src/test/resources/fixtures`.

## Build và test

```bash
./gradlew :app:assembleRelease                                     # APK ở app/build/outputs/apk/release/
./gradlew :app:testDebugUnitTest                                   # test logic, bộ đọc, mở app
./gradlew :app:testDebugUnitTest -Donline=true --tests '*Online*'  # gọi YouTube thật: ExoPlayer đọc hình + tiếng + phụ đề
./gradlew :app:testDebugUnitTest -Dscreenshots=true --tests '*ScreenshotTest*'  # chụp lại ảnh màn hình (cả màn hình ô tô)
```

APK được ký bằng khoá cố định `app/signing/tubesach.jks` để bản cập nhật cài đè được. Nếu repo để công khai, nên tạo khoá riêng: tạo bằng `keytool`, đổi sang base64, rồi lưu vào các secret `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. Làm việc này **trước khi** cài cho mọi người.

## Cấu trúc mã

```
app/src/main/java/com/luzart/tubesach/
├── innertube/      InnerTube.kt (gọi API), Parser.kt (đọc trang chủ/tìm kiếm/trang xem/kênh/Shorts),
│                   DirectStreams.kt (link phát + DASH + phụ đề), WatchTracker.kt (báo lượt xem ẩn danh)
├── sponsor/        SponsorBlock + Return YouTube Dislike
├── data/           YouTubeRepository (gộp nguồn + dự phòng NewPipe), LocalStore (lịch sử, kênh đăng ký, cài đặt)
├── player/         PlaybackController (chất lượng, tốc độ, lặp, phụ đề, SponsorBlock, hẹn giờ),
│                   PlaybackService (phát nền + thư viện cho Android Auto), SessionPlayer (nhận lệnh phát từ xe)
├── auto/           CarLibrary (các mục trên màn hình xe, tìm kiếm, giọng nói), ArtworkProvider (ảnh thu nhỏ cho xe)
├── car/            CarActivity + CarScreens (xem video trên ô tô khi đỗ xe – điện thoại Android 15+)
├── carapp/         TubeCarApp (Android Auto kiểu ứng dụng bản đồ – điện thoại Android 13 trở xuống),
│                   VideoSurfaceRenderer (vẽ video lên màn hình xe), ParkingGate (chỉ hiện hình khi xe đứng yên)
├── update/         UpdateChecker (bản mới trên GitHub), PlayInstaller (cài để Android Auto nhận)
└── ui/             TubeApp (khung chính), screens/ (Trang chủ, Shorts, Kênh đăng ký, Bạn, Tìm kiếm, Kênh, Xem, Cài đặt),
                    player/ (lớp điều khiển, thanh tua, bảng ⚙, trình phát mini), theme/ (màu sáng/tối)
```

## Lưu ý

- Dùng cho mục đích cá nhân. Xem YouTube qua ứng dụng bên thứ ba không đúng Điều khoản dịch vụ của YouTube. App không liên kết với YouTube hay Google.
- Video giới hạn độ tuổi, video riêng tư và video chỉ dành cho hội viên không xem được vì cần đăng nhập.
- Hãy ủng hộ người làm video bạn yêu thích.

## Giấy phép

GPL-3.0 (theo [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)). Có tham khảo [NewPipe](https://github.com/TeamNewPipe/NewPipe), [SmartTube](https://github.com/yuliskov/SmartTube), [SponsorBlock](https://sponsor.ajay.app), [Return YouTube Dislike](https://returnyoutubedislike.com).
