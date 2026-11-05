# Ghi chú làm tiếp: hỗ trợ VP9 (tiết kiệm dữ liệu + 2K/4K)

> File này ghi lại bối cảnh để phiên làm việc sau tiếp tục. Làm xong thì **xoá file này** trước khi gộp vào `main`.

## 1. Yêu cầu đã chốt với chủ repo

- **Mặc định dùng VP9** khi máy có bộ giải mã **phần cứng**. Máy không có thì dùng H.264 như cũ. Lợi ích: cùng 1080p, VP9 chỉ tốn khoảng 1/3 dữ liệu so với H.264 (1,5 Mbps so với 4,3 Mbps).
- **2K/4K (1440p/2160p)**:
  - chỉ hiện trong *Chất lượng → Nâng cao*, và chỉ khi máy giải mã nổi đúng độ phân giải đó;
  - chỉ dùng khi người xem tự chọn; các chế độ Tự động / Cao hơn / Tiết kiệm vẫn tối đa 1080p;
  - có ghi chú "Tốn nhiều dữ liệu, máy có thể nóng hơn".
- **3 lớp bảo vệ:**
  1. Chỉ dùng bộ giải mã phần cứng, kiểm tra theo kích thước + khung hình/giây. Android < 7.0 luôn dùng H.264.
  2. Tự quay về H.264 ngay tại chỗ đang xem khi bộ giải mã VP9 báo lỗi (mã lỗi 4xxx), hoặc khi rơi nhiều khung hình (≥ 50 khung trong ≤ 10 giây, **hai lần** cách nhau ≤ 1 phút). App nhớ lại (`vp9Broken`) để lần sau dùng H.264 luôn.
  3. Công tắc "Tiết kiệm dữ liệu (VP9)" trong Cài đặt, mặc định bật. Bật lại thì xoá dấu `vp9Broken` để thử lại.
- **AV1/8K:** để sau.
- Chủ repo muốn: **test thật kỹ** (giống lần trước: kịch bản tự động + test YouTube thật + thử cố tình làm hỏng code), rồi **tạo PR → chờ CI xanh → gộp vào `main`**. Trả lời bằng tiếng Việt.

## 2. Đã làm (nhánh `claude/elegant-ptolemy-brlkl8`, commit "WIP: VP9…")

| File | Nội dung |
|---|---|
| `player/VideoCodecSupport.kt` (mới) | Kiểm tra giải mã VP9 phần cứng bằng `MediaCodecList` (bỏ bộ giải mã phần mềm, thử cả chiều dọc). Có `override` / `resetForTest()` cho test. |
| `innertube/DirectStreams.kt` | `StreamFormat.isAvc/isVp9` (bỏ VP9 HDR `vp09.02`, bỏ AV1); `typealias Vp9Policy`; `availableHeights(vp9)`, `hasVp9(vp9)`; `dashManifest(vp9 = …)` thêm nhóm hình `video/webm` (id 2) cạnh nhóm H.264. `MAX_VP9_QUALITY = 2160`. |
| `player/StreamSourceFactory.kt` | `buildDirect(…, vp9)`. |
| `player/PlaybackController.kt` | `Backend.buildDirect(…, vp9)`; `vp9Policy()`; `applyQuality()` ưu tiên MIME `[VP9, H264]`, Tự động/Cao hơn tối đa 1080p, Nâng cao bỏ giới hạn màn hình + `fixedQualityFor()` (video không có 4K thì dùng mức cao nhất có); `droppedFramesListener` (AnalyticsListener); `disableVp9()`, `reloadCodecs()`, `setVp9Enabled()`, `vp9Available()`; lỗi bộ giải mã VP9 trong `onPlayerError` → `disableVp9()`. |
| `data/LocalStore.kt` | `Settings.preferVp9` (mặc định `true`); `vp9Broken` (StateFlow) + `setVp9Broken()`. |
| `ui/player/PlayerSheets.kt` | `qualityLabel()`: "2160p (4K)", "1440p (2K)"; ghi chú tốn dữ liệu cho mức > 1080p. |
| `ui/screens/ShortsSettingsScreens.kt` | `Vp9Row`: công tắc trong Cài đặt (ẩn khi máy không có VP9 phần cứng). |

## 3. Trạng thái test

- ✅ `VideoCodecSupportTest` (6, mới): không có VP9 / chỉ phần mềm / phần cứng 4K / phần cứng tới 1080p / 4K60 vượt khả năng / Android 6.
- ✅ `ParserTest.vp9AddedOnlyWhenAllowedAndDecodable` (mới): manifest dựng từ dữ liệu YouTube thật (`fixtures/player.json.gz`, video 4K), đọc lại bằng `DashManifestParser` của ExoPlayer.
- ✅ 70 test cũ vẫn qua (đã chạy sau khi thêm VP9, trước khi thêm kịch bản VP9).
- `PlaybackScenarioTest`: 34 test, **4 lỗi**:
  1. **`vp9DecoderErrorSwitchesToH264AtSamePosition`** — đã tìm ra nguyên nhân (bằng log). App **đã** chuyển về H.264 (nhóm hình được chọn đổi thành `video/avc*`). Nhưng renderer giả trong test kiểm tra `formatsRead.lastOrNull()`, mà danh sách này giữ cả định dạng VP9 cũ, nên nó báo lỗi VP9 thêm lần nữa. Lần lỗi đó đi vào nhánh thử lại thường, rồi báo "Video không có sẵn".
     → **Sửa test:** renderer giả chỉ được nhìn định dạng *hiện tại* (ghi lại trong `onFormatChanged`, xoá trong `onStreamChanged`/`onDisabled`).
     → **Nên làm thêm trong app (an toàn hơn):** nếu đã `vp9Broken` mà vẫn gặp lỗi giải mã VP9 thì cũng dựng lại luồng H.264, đừng đi vào nhánh thử lại thường.
  2. **`fourKOnlyWhenChosenInAdvanced`** — dòng `assertTrue(p.viewportWidth != Int.MAX_VALUE)`. Robolectric không có kích thước màn hình nên giới hạn màn hình luôn là `Int.MAX_VALUE`. → **Sửa test:** so với giá trị lúc trình phát mới tạo, hoặc bỏ dòng này. Các kiểm tra 4K (min/max 2160, bỏ giới hạn màn hình, tự dùng 1080 khi máy không nổi) đều đã qua.
  3. **`repeatedHeavyFrameDropsSwitchToH264`** và 4. **`settingsSwitchAppliesImmediately`** — sau `reloadCodecs()`, `player.videoFormat` vẫn là VP9 dù trình phát ở trạng thái READY. **Chưa rõ nguyên nhân.** Có thể vẫn do renderer giả, hoặc `videoFormat` chưa cập nhật. Cần in log (`onTracksChanged`, `onVideoInputFormatChanged`) như đã làm với lỗi 1. Nên kiểm tra theo nhóm hình được chọn (`player.currentTracks`), thay vì chỉ dựa vào `videoFormat`.

## 4. Việc còn lại (theo thứ tự)

1. Sửa 4 test trên. Riêng lỗi 3 và 4: tìm nguyên nhân thật trước, không sửa test cho "qua cho có".
2. Thêm vào `OnlineScenarioTest` một test với YouTube thật: video `dQw4w9WgXcQ` (bản 4K Remaster), `VideoCodecSupport.override = { _, _, _ -> true }`.
   - Chế độ Nâng cao 2160p → phải READY với `videoFormat` là VP9, cao 2160. Như vậy là đã tải luồng WebM DASH thật từ googlevideo.
   - Chế độ Tự động → VP9 ≤ 1080p.
3. Ảnh chụp giao diện (`ScreenshotTest`): công tắc VP9 trong Cài đặt (cần `VideoCodecSupport.override`); danh sách *Nâng cao* có nhãn 4K/2K kèm ghi chú.
4. Thử cố tình làm hỏng code (script mẫu ở mục 6): bỏ `vp9Policy`, bỏ fallback khi lỗi bộ giải mã, bỏ điều kiện "hai lần" khi rơi khung hình, bỏ `max(1080)` ở chế độ Tự động, bỏ kiểm tra phần cứng… Mỗi lần phải có test báo lỗi.
5. Cập nhật README (mục Tính năng / Chất lượng). **Xoá file này.**
6. Chạy toàn bộ test + `assembleRelease` → commit → push → mở PR vào `main` → chờ CI xanh → gộp (`merge`) → hủy theo dõi PR.

## 5. Môi trường (phiên cloud mới phải dựng lại)

```bash
# Android SDK (máy chủ không có sẵn)
mkdir -p /opt/android-sdk/cmdline-tools && cd /tmp && curl -sSLo clt.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip && unzip -q clt.zip -d /opt/android-sdk/cmdline-tools && mv /opt/android-sdk/cmdline-tools/cmdline-tools /opt/android-sdk/cmdline-tools/latest && yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk "platforms;android-36" "build-tools;36.0.0" "platform-tools"
echo "sdk.dir=/opt/android-sdk" > local.properties   # đã có trong .gitignore
```

Maven Central hay trả lỗi 429 (quá nhiều yêu cầu). Dùng bản sao của Google, đặt trong `~/.gradle/init.d/mirror.gradle` (không commit):

```groovy
def mirror = { repos ->
    def m = repos.maven { url 'https://maven-central.storage-download.googleapis.com/maven2/'; name 'gcsMirror' }
    repos.remove(m); repos.addFirst(m)
}
settingsEvaluated { settings ->
    mirror(settings.dependencyResolutionManagement.repositories)
    mirror(settings.pluginManagement.repositories)
}
```

Chạy test (Robolectric cũng cần bản sao):

```bash
ROBOLECTRIC_REPO=https://maven-central.storage-download.googleapis.com/maven2/ ./gradlew :app:testDebugUnitTest
# Thêm -Donline=true: test với YouTube thật.  Thêm -Dscreenshots=true: chụp ảnh giao diện (ra app/build/outputs/roborazzi/)
```

Lưu ý:
- Không có KVM nên không chạy được máy ảo Android. Test kịch bản dùng Robolectric + ExoPlayer thật (bộ giải mã giả `FakeVideoRenderer`, đồng hồ giả `FakeClock`).
- Từ IP máy chủ, YouTube chặn gần hết video (`LOGIN_REQUIRED`/403). Chỉ `dQw4w9WgXcQ` phát được. Test online phải chấp nhận việc này.
- Mỗi lần chạy `./gradlew` thường mất hơn 1 phút; bộ test đầy đủ khoảng 5–8 phút. Lệnh nào có thể quá 10 phút thì chạy nền.
- Script thử làm hỏng code mẫu: xem lịch sử phiên trước. Ý tưởng: thay một đoạn code bằng bản hỏng, chạy đúng test liên quan, rồi khôi phục file. Coi là "bắt được" khi `returncode != 0` và đầu ra có `There were failing tests` mà không có lỗi biên dịch (`e:`).

## 6. Đã xong ở `main` (PR #1, đã gộp)

Sửa đề xuất chỉ ~20 video, Shorts mờ/phóng to, tắt/bật màn hình bị treo, tự phát chậm, tự bỏ qua video tự phát không phát được, đăng nhập Google (thử nghiệm). 66 test + 11 lần thử làm hỏng code đều bị bắt. Chi tiết: https://github.com/luzart-otherthing/youtube-remove-ads/pull/1
