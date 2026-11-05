#!/usr/bin/env bash
# Chạy thử từng APK trên giả lập Android CÓ Google Play (không có microG):
# cài → mở một video → chờ → ghi lại: app còn chạy không, có văng không, có đang phát không,
# màn hình hiện chữ gì (để thấy quảng cáo / thông báo lỗi), ảnh chụp màn hình.
#
# Cách dùng: bash experiments/revanced/test-emulator.sh <thư-mục-apk> <thư-mục-kết-quả>
set -uo pipefail

APKS=$(cd "${1:-out}" && pwd)
RES=${2:-results}
mkdir -p "$RES"
RES=$(cd "$RES" && pwd)
VIDEO=${VIDEO_ID:-kJQP7kiw5Fk}
SUMMARY="$RES/tom-tat.md"

sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}
AAPT2=$(ls -d "$sdk"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)

pkg_of() { # .apk hoặc .apkm (gói nhiều phần của APKMirror)
  case "$1" in
    *.apkm) local d; d=$(mktemp -d); unzip -q -o "$1" base.apk -d "$d"; "$AAPT2" dump packagename "$d/base.apk" 2>/dev/null ;;
    *) "$AAPT2" dump packagename "$1" 2>/dev/null ;;
  esac
}

install_apk() { # .apkm → cài từng phần (giữ nguyên chữ ký Google)
  case "$1" in
    *.apkm)
      local d; d=$(mktemp -d)
      unzip -q -o "$1" '*.apk' -d "$d"
      adb install-multiple -r -d -g "$d"/*.apk 2>&1 | tail -2 | tr '\n' ' ' ;;
    *) adb install -r -d -g "$1" 2>&1 | tail -2 | tr '\n' ' ' ;;
  esac
}

adb wait-for-device
# Tắt Play Protect quét APK cài qua adb (nếu không, giả lập có thể hỏi/chặn).
adb shell settings put global verifier_verify_adb_installs 0 || true
adb shell settings put global package_verifier_enable 0 || true
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true

{
  echo "# Kết quả chạy thử trên giả lập"
  echo
  echo "- Android: $(adb shell getprop ro.build.version.release | tr -d '\r') (API $(adb shell getprop ro.build.version.sdk | tr -d '\r'))"
  echo "- Kiến trúc CPU giả lập: $(adb shell getprop ro.product.cpu.abilist | tr -d '\r')"
  echo "- Google Play Services: $(adb shell dumpsys package com.google.android.gms | grep -m1 versionName | tr -d '\r' | xargs)"
  echo "- microG/GmsCore có trên máy không: $(adb shell pm list packages | grep -iE 'app.revanced.android.gms|org.microg|mgoogle' | tr -d '\r' | xargs || true)"
  echo "- YouTube cài sẵn: $(adb shell pm list packages | grep -i youtube | tr -d '\r' | xargs)"
  echo "- Video thử: https://www.youtube.com/watch?v=$VIDEO"
  echo
} > "$SUMMARY"
[ -f "$APKS/phien-ban.txt" ] && { echo '```'; cat "$APKS/phien-ban.txt"; echo '```'; echo; } >> "$SUMMARY"

ui_texts() { # in ra các chữ/nhãn đang hiện trên màn hình
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml "$1" >/dev/null 2>&1 || return 0
  grep -oE '(text|content-desc)="[^"]+"' "$1" | sed -E 's/^(text|content-desc)="//; s/"$//' | awk '!seen[$0]++' | head -60 | tr '\n' '|'
}

run_case() { # $1 = tên, $2 = file apk
  local name=$1 apk=$2 pkg
  pkg=$(pkg_of "$apk")
  local dir="$RES/$name"
  mkdir -p "$dir"
  echo "::group::Thử $name ($pkg)"
  adb logcat -c || true

  local install
  install=$(install_apk "$apk")
  echo "Cài: $install"

  # Mở thẳng một video trong đúng app này.
  adb shell am start -W -a android.intent.action.VIEW \
    -d "https://www.youtube.com/watch?v=$VIDEO" -p "$pkg" > "$dir/am-start.txt" 2>&1 \
    || adb shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >> "$dir/am-start.txt" 2>&1
  cat "$dir/am-start.txt"

  sleep 25
  adb exec-out screencap -p > "$dir/1-sau-25-giay.png" || true
  local ui1; ui1=$(ui_texts "$dir/ui-1.xml")
  sleep 35
  adb exec-out screencap -p > "$dir/2-sau-60-giay.png" || true
  local ui2; ui2=$(ui_texts "$dir/ui-2.xml")

  local pid alive crash top media audio
  pid=$(adb shell pidof "$pkg" | tr -d '\r')
  alive=$([ -n "$pid" ] && echo "có (pid $pid)" || echo "KHÔNG")
  adb logcat -d > "$dir/logcat.txt" 2>&1 || true
  crash=$(grep -A6 "FATAL EXCEPTION" "$dir/logcat.txt" | grep -m3 -E "Process: $pkg|Exception|Error" | tr '\n' ' ' || true)
  top=$(adb shell dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity' | tr -d '\r' | xargs)
  adb shell dumpsys media_session > "$dir/media_session.txt" 2>&1 || true
  media=$(grep -A40 "package=$pkg" "$dir/media_session.txt" | grep -m1 -oE 'state=PlaybackState \{state=[A-Z_]*\(?[0-9]*\)?, position=[0-9]+' || true)
  adb shell dumpsys audio > "$dir/audio.txt" 2>&1 || true
  audio=$(grep -cE "state:started" "$dir/audio.txt" || true)

  # Những dòng log liên quan tới Google Play Services / ReVanced / phát video.
  grep -iE "revanced|gmscore|GooglePlayServices|GoogleApiManager|SecurityException|GoogleCertificates|not.*Google.*sign|DroidGuard|PoToken|Unknown calling package|SpoofVideoStreams|Playback error|ExoPlaybackException" \
    "$dir/logcat.txt" | grep -v "^--------- beginning" | head -120 > "$dir/log-quan-trong.txt" || true

  {
    echo "## $name"
    echo
    echo "- Gói: \`$pkg\`"
    echo "- Cài đặt: $install"
    echo "- Sau 60 giây app còn chạy: $alive"
    echo "- Màn hình đang ở: \`$top\`"
    echo "- Trạng thái phát (MediaSession): ${media:-không có}"
    echo "- Số luồng âm thanh đang phát trên máy: ${audio:-0}"
    echo "- Văng app: ${crash:-không}"
    echo "- Chữ trên màn hình lúc 25 giây: ${ui1:0:900}"
    echo "- Chữ trên màn hình lúc 60 giây: ${ui2:0:900}"
    echo
    echo "<details><summary>Log quan trọng (đầu)</summary>"
    echo
    echo '```'
    head -40 "$dir/log-quan-trong.txt"
    echo '```'
    echo "</details>"
    echo
  } >> "$SUMMARY"

  adb shell am force-stop "$pkg" || true
  # Hai bản ReVanced trùng tên gói → gỡ bản này trước khi thử bản sau.
  if [ "$pkg" != "com.google.android.youtube" ]; then adb uninstall "$pkg" >/dev/null 2>&1 || true; fi
  echo "::endgroup::"
}

if [ -f "$APKS/youtube-goc.apk" ]; then run_case "1-youtube-goc" "$APKS/youtube-goc.apk"
elif [ -f "$APKS/youtube-goc.apkm" ]; then run_case "1-youtube-goc" "$APKS/youtube-goc.apkm"; fi
[ -f "$APKS/youtube-revanced-khong-microg.apk" ] && run_case "2-revanced-khong-microg" "$APKS/youtube-revanced-khong-microg.apk"
[ -f "$APKS/youtube-revanced-chuan.apk" ] && run_case "3-revanced-chuan-can-microg" "$APKS/youtube-revanced-chuan.apk"

cat "$SUMMARY"
[ -n "${GITHUB_STEP_SUMMARY:-}" ] && cat "$SUMMARY" >> "$GITHUB_STEP_SUMMARY"
exit 0
