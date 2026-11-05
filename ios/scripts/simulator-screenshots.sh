#!/usr/bin/env bash
# Chạy app trên iPhone/iPad giả lập (cần Mac + Xcode) và chụp màn hình các trang chính.
# Dùng:  ios/scripts/simulator-screenshots.sh <đường dẫn TubeSach.app> <thư mục ảnh>
set -euo pipefail

APP="$1"
OUT="$2"
BUNDLE=com.luzart.tubesach
VIDEO=dQw4w9WgXcQ
mkdir -p "$OUT"

# Chọn máy ảo mới nhất theo tên (ví dụ "iPhone", "iPad Pro").
pick() {
  xcrun simctl list devices available -j | python3 -c '
import json, sys
want = sys.argv[1]
devs = json.load(sys.stdin)["devices"]
runtimes = sorted((k for k in devs if "iOS" in k), key=lambda k: [int(x) for x in k.split("iOS-")[-1].split("-")])
for rt in reversed(runtimes):
    for d in devs[rt]:
        if d["name"].startswith(want):
            print(d["udid"]); sys.exit(0)
sys.exit(1)
' "$1"
}

shot() { # udid tên giây-chờ
  sleep "$3"
  xcrun simctl io "$1" screenshot "$OUT/$2.png" >/dev/null 2>&1
  echo "Đã chụp $2"
}

launch() { # udid [tham số…]
  local udid="$1"; shift
  xcrun simctl terminate "$udid" "$BUNDLE" >/dev/null 2>&1 || true
  xcrun simctl launch "$udid" "$BUNDLE" "$@" >/dev/null
}

run_device() { # tên-máy tiền-tố
  local udid
  udid=$(pick "$1" || pick "${1%% *}")
  echo "== $1 ($udid)"
  xcrun simctl boot "$udid" >/dev/null 2>&1 || true
  xcrun simctl bootstatus "$udid" -b >/dev/null
  xcrun simctl ui "$udid" appearance light
  xcrun simctl install "$udid" "$APP"

  launch "$udid"
  shot "$udid" "$2_01_trang_chu" 20
  launch "$udid" -openVideo "$VIDEO"
  shot "$udid" "$2_02_xem_video" 25
  if [ "$2" = "iphone" ]; then
    launch "$udid" -openSearch "sơn tùng m-tp"
    shot "$udid" "$2_03_tim_kiem" 15
    launch "$udid" -openTab shorts
    shot "$udid" "$2_04_shorts" 25
    launch "$udid" -openChannel UCuAXFkgsw1L7xaCfnd5JJOw
    shot "$udid" "$2_05_kenh" 15
    launch "$udid" -openTab library
    shot "$udid" "$2_06_ban" 8
    xcrun simctl ui "$udid" appearance dark
    launch "$udid" -openVideo "$VIDEO"
    shot "$udid" "$2_07_xem_video_toi" 25
  fi
  xcrun simctl shutdown "$udid" >/dev/null 2>&1 || true
}

run_device "iPhone 16" iphone
run_device "iPad Pro" ipad
ls -l "$OUT"
