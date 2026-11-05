# Thử nghiệm riêng: ReVanced có chặn quảng cáo tốt hơn cách InnerTube của Tube Sạch không?

> **Bản nháp** – còn chờ kết quả chạy thử ReVanced không microG trên giả lập (các chỗ `<!-- … -->` sẽ được điền).

> Thư mục này **không liên quan** tới app Tube Sạch: không sửa dòng nào trong `app/`, không nằm trong bản dựng APK của app.
> Chỉ có công cụ đo, workflow thử ReVanced và báo cáo này.

## Trả lời ngắn

1. **Về chặn quảng cáo: ReVanced không tốt hơn.** Tube Sạch không có quảng cáo ngay từ cách làm. App chỉ đọc đúng những loại mục nó biết (video, Shorts, kênh) và bỏ qua mọi thứ khác, nên quảng cáo không bao giờ được tải. ReVanced phải gỡ quảng cáo ra khỏi app YouTube, vốn được làm ra để hiện quảng cáo. Nó dùng danh sách khoảng 40 mẫu tên quảng cáo và phải cập nhật mỗi khi YouTube đổi giao diện.
2. **Cách lấy video thì giống hệt nhau.** Bản vá "Spoof video streams" của ReVanced lấy link phát bằng các máy khách `VISIONOS`, `ANDROID_VR`, `ANDROID_REEL`. `VISIONOS` cũng chính là máy khách Tube Sạch đang dùng. Khi YouTube chặn mẹo này, cả hai cùng hỏng.
3. **Không microG:** xem mục 4. ReVanced có tuỳ chọn trỏ thẳng vào Google Play Services thật, nên tôi thử cách này trên giả lập.

<!-- KET_LUAN_GIA_LAP -->

## 1. ReVanced chặn quảng cáo thế nào (đọc mã nguồn)

Mã ReVanced hiện ở GitLab ([gitlab.com/ReVanced/revanced-patches](https://gitlab.com/ReVanced/revanced-patches)), vì repo GitHub không còn (404). Bộ vá dùng ở đây là v6.2.1, hỗ trợ YouTube tới 20.40.45.

| Bản vá | Cách làm |
|---|---|
| **Video ads** | Chèn `return` vào đầu hàm tải quảng cáo video của app YouTube. Quảng cáo trước/giữa video không được tải. |
| **Hide ads** | Bộ lọc "litho": so tên thành phần giao diện với khoảng 40 mẫu (`ads_video_with_context`, `full_width_square_image_layout`, `statement_banner`, `product_carousel`…). Ngoài ra còn tự đóng hộp thoại quảng cáo toàn màn hình, ẩn view `ad_attribution` và ẩn banner Premium. |
| **Hide ads** (mẹo phía máy chủ) | Khi app gọi trang chủ và tìm kiếm, khai `osName = "Android Automotive"` (giả làm màn hình ô tô) để YouTube gửi ít quảng cáo hơn. |
| **Spoof video streams** | Lấy link phát bằng máy khách khác (`ANDROID_REEL`, `ANDROID_VR`, `VISIONOS`, `ANDROID_CREATOR`) vì máy khách Android thật cần chứng thực của Google Play. |
| **GmsCore support** | Đổi tên gói thành `app.revanced.android.youtube` để cài song song YouTube gốc mà không cần root. Mọi lời gọi Google Play Services được chuyển sang GmsCore (= microG) của ReVanced. |

Cả hai nhánh của ReVanced đều cần microG khi không root:
- **ReVanced chính thức:** nếu không thấy GmsCore, app hiện thông báo, mở trang tải GmsCore rồi **tự thoát** (`GmsCoreSupport.checkInstallation` → `System.exit(0)`).
- **Morphe** (nhánh tách ra năm 2026): cũng thoát như vậy, và còn không có tuỳ chọn đổi nơi gọi.

## 2. Tube Sạch chặn quảng cáo thế nào

- `innertube/Parser.kt` chỉ đọc `videoRenderer`, `lockupViewModel`, `shortsLockupViewModel`, `reelItemRenderer`, `channelRenderer`… Quảng cáo YouTube luôn gói trong `adSlotRenderer`, `searchPyvRenderer`, `promotedVideoRenderer`… nên bị bỏ qua mà không cần biết tên.
- `innertube/DirectStreams.kt` chỉ lấy `streamingData`. Các mục `adPlacements`, `playerAds`, `adBreakHeartbeatParams` trong phản hồi `/player` không bao giờ được dùng, nên quảng cáo video không được tải.
- Tài trợ do chính người làm video nói trong video thì không có cách nào "chặn". Tube Sạch dùng SponsorBlock, giống ReVanced.

## 3. Kết quả đo (`experiments/ad-audit`)

Công cụ đo là dự án Gradle riêng. Nó biên dịch **đúng** `InnerTube.kt`, `Parser.kt`, `DirectStreams.kt` của app (chỉ chép sang, không sửa), rồi gọi YouTube thật bằng tiếng Việt, khu vực VN, khách ẩn danh. Kết quả đầy đủ: [`ad-audit/ket-qua.md`](ad-audit/ket-qua.md).

| Đo gì | Kết quả |
|---|---|
| 18 trang (trang chủ, thịnh hành, top nhạc, 10 từ khoá dễ có quảng cáo, 5 trang xem) | YouTube gửi 5 khối quảng cáo (`adsEngagementPanelContentRenderer` ở trang xem). **Lọt ra màn hình Tube Sạch: 0.** |
| `/player` bằng visionOS (Tube Sạch) | Có `adBreakHeartbeatParams` (quảng cáo được gửi kèm), Tube Sạch không dùng. Độ dài luồng **bằng** độ dài video, nên **không có quảng cáo chèn thẳng vào luồng** (SSAI). |
| Mẫu quảng cáo dựng sẵn đúng hình dạng YouTube (adSlotRenderer, searchPyvRenderer, promotedVideoRenderer, compactPromotedVideoRenderer, banner Premium) | **Lọt: 0** |
| Trường hợp giả định: quảng cáo **giả làm video thường**, chỉ có nhãn "Được tài trợ" | **Lọt: 1.** Tube Sạch hiện tại không kiểm nhãn. YouTube hiện chưa làm vậy, và bộ lọc của ReVanced (theo tên thành phần) cũng không bắt được kiểu này. |
| Máy khách ANDROID (thứ app YouTube thật nhận) và mẹo "Android Automotive" | Từ máy đo, YouTube không gửi quảng cáo danh sách cho cả hai, nên không thấy khác biệt. |

Giới hạn: lượng quảng cáo YouTube gửi phụ thuộc IP, khu vực và lịch sử xem. Máy đo là máy chủ ở nước ngoài, nên YouTube gửi rất ít quảng cáo danh sách. Vì thế có thêm phần thử bằng mẫu dựng sẵn.

## 4. Thử ReVanced không microG (`experiments/revanced`, workflow "Thử ReVanced không microG")

GitHub Actions tự làm các bước sau:
1. Tải ReVanced CLI 6.0.0 (GitHub) và bộ vá v6.2.1 (`api.revanced.app`), **kiểm tra chữ ký PGP** của bộ vá.
2. Tải YouTube 20.40.45 bản gốc trên APKMirror, **kiểm tra chữ ký Google** (khoá `3d7a1223…994a`).
3. Vá thành 2 bản:
   - `youtube-revanced-khong-microg.apk`: tên gói riêng, tuỳ chọn `gmsCoreVendorGroupId=com.google`, tức mọi lời gọi đi thẳng vào Google Play Services thật của máy. **Không cần microG, không cần root, cài song song YouTube gốc.**
   - `youtube-revanced-chuan.apk`: ReVanced như mọi người vẫn cài (cần GmsCore/microG), dùng để đối chứng.
4. Chạy giả lập Android 15 có Google Play, **không có microG**. Cài lần lượt YouTube gốc, bản không microG, bản chuẩn; mở cùng một video, chờ 60 giây, ghi lại: app còn chạy không, có văng không, có đang phát không, chữ trên màn hình và ảnh chụp.

<!-- KET_QUA_GIA_LAP -->

## 5. So sánh

| | Tube Sạch (InnerTube) | ReVanced + microG | ReVanced không microG (thử ở đây) |
|---|---|---|---|
| Chặn quảng cáo | Không tải quảng cáo (danh sách trắng) | Gỡ quảng cáo khỏi app YouTube (danh sách đen, phải cập nhật) | Như ReVanced |
| Lấy link video | visionOS | VISIONOS / ANDROID_VR / ANDROID_REEL | Như ReVanced |
| Cần microG | Không | **Có** | Không |
| Đăng nhập Google | Không | Có (qua microG) | <!-- DANG_NHAP --> |
| Khi YouTube đổi | Sửa `Parser.kt` + có NewPipe dự phòng | Chờ ReVanced ra bản vá **và** tự vá lại APK | Như ReVanced |
| Giao diện | Làm riêng, chữ to cho người lớn tuổi | Giao diện YouTube thật | Giao diện YouTube thật |
| Cập nhật cho người dùng | App tự kiểm tra bản mới | Phải vá lại mỗi lần đổi bản YouTube | Như ReVanced |

## 6. Khuyến nghị

<!-- KHUYEN_NGHI -->

## Chạy lại

```bash
# Công cụ đo quảng cáo (cần mạng; ghi kết quả vào experiments/ad-audit/ket-qua.md)
./gradlew -p experiments/ad-audit run --args="experiments/ad-audit/ket-qua.md"
```

Workflow ReVanced chạy trên GitHub Actions: *Actions → Thử ReVanced không microG → Run workflow* (có thể đổi mã video). APK và ảnh chụp nằm trong *Artifacts* của lần chạy.
