#!/usr/bin/env bash
# Vá YouTube bằng ReVanced (bản chính thức) thành 2 APK để thử, KHÔNG cần microG/GmsCore:
#
#   youtube-goc.apk                 YouTube gốc của Google (đối chứng, có quảng cáo)
#   youtube-revanced-khong-microg   ReVanced, tên gói riêng (app.revanced.android.youtube),
#                                   trỏ thẳng vào Google Play Services thật của máy
#                                   (tuỳ chọn gmsCoreVendorGroupId=com.google) → không cần microG
#   youtube-revanced-chuan.apk      ReVanced như mọi người vẫn dùng (cần ReVanced GmsCore = microG).
#                                   Dùng để đối chứng: máy không có microG thì bản này làm gì.
#
# Cách dùng:  bash experiments/revanced/build.sh <thư-mục-ra>
# Cần: java 17+, curl, gh (đã đăng nhập hoặc có GH_TOKEN), apksigner (Android SDK build-tools).
set -euo pipefail

OUT=${1:-out}
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)
WORK=$(mktemp -d)

# Phiên bản YouTube mà bộ vá ReVanced hỗ trợ. Bản 20.40.45 có sẵn trên archive.org.
YT_VERSION=${YT_VERSION:-20.40.45}
YT_ITEM="com.google.android.youtube-${YT_VERSION}-all.apk"
# Chứng chỉ ký của Google cho YouTube (SHA-256). APK không khớp chứng chỉ này sẽ bị từ chối.
YT_CERT_SHA256="3d7a1223019aa39d9ea0e3436ab7c0896bfb4fb679f4de5fe7c23f326c8f994a"
CLI_TAG=${CLI_TAG:-v6.0.0}
PATCHES_API=${PATCHES_API:-https://api.revanced.app/v5}

log() { printf '\n=== %s\n' "$*"; }

find_apksigner() {
  if command -v apksigner >/dev/null 2>&1; then command -v apksigner; return; fi
  local sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}
  ls -d "$sdk"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1
}
APKSIGNER=$(find_apksigner)
[ -n "$APKSIGNER" ] || { echo "Không tìm thấy apksigner"; exit 1; }

log "1. Tải ReVanced CLI ($CLI_TAG)"
gh release download "$CLI_TAG" -R ReVanced/revanced-cli -p '*-all.jar' -D "$WORK" --clobber
CLI=$(ls "$WORK"/*-all.jar | head -1)
echo "CLI: $(basename "$CLI")"

log "2. Tải bộ vá ReVanced từ $PATCHES_API"
curl -fsSL "$PATCHES_API/patches" -o "$WORK/patches.json"
python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); print("Bộ vá:", d.get("version"), "ngày", d.get("created_at"))' "$WORK/patches.json" | tee "$OUT/phien-ban.txt"
curl -fsSL "$PATCHES_API/patches.rvp" -o "$WORK/patches.rvp"
echo "CLI: $(basename "$CLI")" >> "$OUT/phien-ban.txt"
echo "YouTube: $YT_VERSION" >> "$OUT/phien-ban.txt"

log "3. Tải YouTube $YT_VERSION (archive.org) và kiểm tra chữ ký Google"
curl -fL --retry 3 -o "$WORK/youtube.apk" "https://archive.org/download/$YT_ITEM/$YT_ITEM"
CERT=$("$APKSIGNER" verify --print-certs "$WORK/youtube.apk" | grep -m1 'certificate SHA-256 digest' | awk '{print $NF}')
echo "Chứng chỉ: $CERT"
if [ "$CERT" != "$YT_CERT_SHA256" ]; then
  echo "APK YouTube KHÔNG do Google ký → dừng lại."
  exit 1
fi
cp "$WORK/youtube.apk" "$OUT/youtube-goc.apk"

log "4. Danh sách bản vá cho YouTube (ghi vào danh-sach-ban-va.txt)"
java -jar "$CLI" list-patches --help > "$OUT/cli-list-patches-help.txt" 2>&1 || true
java -jar "$CLI" patch --help > "$OUT/cli-patch-help.txt" 2>&1 || true
cat "$OUT/cli-patch-help.txt"
java -jar "$CLI" list-patches --with-packages --with-versions --with-options \
  --filter-package-name com.google.android.youtube "$WORK/patches.rvp" > "$OUT/danh-sach-ban-va.txt" 2>&1 \
  || java -jar "$CLI" list-patches -p "$WORK/patches.rvp" > "$OUT/danh-sach-ban-va.txt" 2>&1 || true
head -c 4000 "$OUT/danh-sach-ban-va.txt"

patch() { # $1 = tên file ra, các tham số còn lại = tuỳ chọn thêm cho CLI
  local name=$1; shift
  log "5. Vá: $name"
  java -jar "$CLI" patch \
    -p "$WORK/patches.rvp" \
    -o "$OUT/$name.apk" \
    --keystore "$WORK/$name.keystore" \
    -t "$WORK/tmp-$name" \
    "$@" \
    "$WORK/youtube.apk" 2>&1 | tee "$OUT/$name.log"
  [ -f "$OUT/$name.apk" ] || { echo "Vá $name thất bại"; return 1; }
}

# Bản không microG: GmsCore support vẫn đổi tên gói (để cài song song YouTube gốc, không cần root)
# nhưng trỏ mọi lời gọi Google Play Services về chính com.google.android.gms của máy.
FAILED=0
patch youtube-revanced-khong-microg -e "GmsCore support" -OgmsCoreVendorGroupId=com.google || FAILED=1
# Bản chuẩn (cần microG) – để đối chứng.
patch youtube-revanced-chuan || FAILED=1

ls -la "$OUT"
exit $FAILED
