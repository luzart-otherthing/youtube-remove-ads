#!/usr/bin/env bash
# Vá YouTube bằng ReVanced (bản chính thức) thành 2 APK để thử, KHÔNG cần microG/GmsCore:
#
#   youtube-goc.apk(m)              YouTube gốc của Google, tải từ APKMirror, đã kiểm tra chữ ký (đối chứng)
#   youtube-revanced-khong-microg   ReVanced, tên gói riêng (app.revanced.android.youtube),
#                                   trỏ thẳng vào Google Play Services thật của máy
#                                   (tuỳ chọn gmsCoreVendorGroupId=com.google) → không cần microG
#   youtube-revanced-chuan.apk      ReVanced như mọi người vẫn dùng (cần ReVanced GmsCore = microG).
#                                   Dùng để đối chứng: máy không có microG thì bản này làm gì.
#
# Cách dùng:  bash experiments/revanced/build.sh <thư-mục-ra>
# Cần: java 17+, curl, python3 + beautifulsoup4, gh (có GH_TOKEN), apksigner (Android SDK build-tools).
set -euo pipefail

OUT=${1:-out}
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)
WORK=$(mktemp -d)

# Phiên bản YouTube mới nhất mà bộ vá ReVanced v6.2.1 hỗ trợ.
YT_VERSION=${YT_VERSION:-20.40.45}
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
echo "SHA-256 patches.rvp: $(sha256sum "$WORK/patches.rvp" | cut -d' ' -f1)" >> "$OUT/phien-ban.txt"

log "2b. Kiểm tra chữ ký PGP của bộ vá (sai chữ ký → dừng)"
# Khoá công khai lấy từ API của ReVanced – đúng nguồn mà ReVanced Manager dùng để kiểm tra.
curl -fsSL "$PATCHES_API/patches.rvp.asc" -o "$WORK/patches.rvp.asc"
curl -fsSL "$PATCHES_API/patches/keys" -o "$WORK/keys.json"
python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["patches_public_key"])' "$WORK/keys.json" > "$WORK/revanced-public-key.asc"
export GNUPGHOME="$WORK/gnupg"
mkdir -p -m 700 "$GNUPGHOME"
gpg --batch --quiet --import "$WORK/revanced-public-key.asc"
gpg --batch --show-keys --with-fingerprint "$WORK/revanced-public-key.asc" | grep -E '^ +[0-9A-F ]{40,}|^uid' | sed 's/^/khoá ReVanced: /' | tee -a "$OUT/phien-ban.txt"
if gpg --batch --status-fd 1 --verify "$WORK/patches.rvp.asc" "$WORK/patches.rvp" 2>/dev/null | grep '^\[GNUPG:\] VALIDSIG' | tee -a "$OUT/phien-ban.txt"; then
  echo "Chữ ký PGP của bộ vá: HỢP LỆ" | tee -a "$OUT/phien-ban.txt"
else
  echo "Chữ ký PGP của bộ vá KHÔNG hợp lệ → dừng lại."
  exit 1
fi
# CLI v6 còn đòi thêm "chứng thực build" (sigstore) từ repo GitHub của bộ vá. Repo đó không còn trên GitHub (404,
# ReVanced chuyển sang GitLab) nên không có file chứng thực → chữ ký PGP đã kiểm ở trên bằng gpg, CLI được báo bỏ qua (-b).
echo "CLI: $(basename "$CLI")" >> "$OUT/phien-ban.txt"
echo "YouTube: $YT_VERSION" >> "$OUT/phien-ban.txt"

verify_google() { # Chữ ký phải hợp lệ VÀ một trong các khoá ký phải là khoá YouTube của Google.
  # YouTube đã xoay khoá (APK Signature Scheme v3.1): APK có nhiều người ký, khoá gốc của Google
  # (3d7a…) vẫn nằm trong đó cùng khoá mới. apksigner chỉ báo thành công khi MỌI chữ ký đều đúng,
  # nên có khoá Google trong danh sách nghĩa là nội dung APK do Google ký.
  local out certs
  if ! out=$("$APKSIGNER" verify --print-certs "$1" 2>&1); then
    echo "Chữ ký $(basename "$1") KHÔNG hợp lệ:"; echo "$out" | head -5
    return 1
  fi
  certs=$(echo "$out" | grep 'certificate SHA-256 digest' | awk '{print $NF}' | sort -u | tr '\n' ' ')
  echo "Chứng chỉ $(basename "$1"): $certs"
  echo "$out" | grep -E '^Signer' | grep 'SHA-256' | sed 's/^/   /'
  grep -q "$YT_CERT_SHA256" <<<"$certs"
}

# Lấy bản gốc trên APKMirror rồi tự kiểm tra chữ ký Google (không tin nguồn tải mù quáng).
log "3. Tải YouTube $YT_VERSION gốc từ APKMirror và kiểm tra chữ ký Google"
python3 -c 'import bs4' 2>/dev/null \
  || pip install --quiet --break-system-packages beautifulsoup4 2>/dev/null \
  || sudo apt-get install -y -qq python3-bs4
HERE=$(cd "$(dirname "$0")" && pwd)
# Ưu tiên APK một file chứa mọi kiến trúc (universal) – chạy được cả trên giả lập x86_64 lẫn điện thoại.
DL=$(python3 "$HERE/tai_apkmirror.py" "$YT_VERSION" "$WORK/dl" ${YT_ARCHES:-universal x86_64 arm64-v8a})
echo "Đã tải: $DL"
case "$DL" in
  *.apk)
    verify_google "$DL" || { echo "APK YouTube KHÔNG do Google ký → dừng lại."; exit 1; }
    cp "$DL" "$WORK/youtube.apk"
    cp "$DL" "$OUT/youtube-goc.apk"
    ;;
  *.apkm)
    # Gói nhiều phần: kiểm tra chữ ký TỪNG phần, rồi gộp lại để vá.
    mkdir -p "$WORK/splits"
    unzip -q -o "$DL" '*.apk' -d "$WORK/splits"
    for f in "$WORK"/splits/*.apk; do
      verify_google "$f" || { echo "Phần $(basename "$f") KHÔNG do Google ký → dừng lại."; exit 1; }
    done
    gh release download -R REAndroid/APKEditor -p 'APKEditor-*.jar' -D "$WORK" --clobber
    java -jar "$(ls "$WORK"/APKEditor-*.jar | head -1)" m -i "$DL" -o "$WORK/youtube.apk" -f
    cp "$DL" "$OUT/youtube-goc.apkm"
    ;;
  *) echo "Không tải được YouTube"; exit 1 ;;
esac

log "4. Danh sách bản vá cho YouTube (ghi vào danh-sach-ban-va.txt)"
java -jar "$CLI" list-patches --help > "$OUT/cli-list-patches-help.txt" 2>&1 || true
java -jar "$CLI" patch --help > "$OUT/cli-patch-help.txt" 2>&1 || true
cat "$OUT/cli-patch-help.txt"
java -jar "$CLI" list-patches -p "$WORK/patches.rvp" -b --packages --versions --options \
  --filter-package-name com.google.android.youtube > "$OUT/danh-sach-ban-va.txt" 2>&1 || true
# Nhiều app có bản vá trùng tên "GmsCore support" (YouTube, YouTube Music, Photos, News…). Bật theo TÊN
# thì tuỳ chọn có thể rơi vào bản vá của app khác → phải dùng CHỈ SỐ của đúng bản vá cho YouTube.
GMS_INDEX=$(python3 - "$OUT/danh-sach-ban-va.txt" <<'PY'
import re, sys
text = open(sys.argv[1], encoding="utf-8", errors="replace").read()
for block in re.split(r"\n\s*\n", text):
    idx = re.search(r"Index: (\d+)", block)
    if idx and re.search(r"^Name: GmsCore support\s*$", block, re.M) and "com.google.android.youtube" in block:
        print(idx.group(1))
        break
PY
)
echo "Chỉ số bản vá GmsCore support cho YouTube: ${GMS_INDEX:-KHÔNG TÌM THẤY}" | tee -a "$OUT/phien-ban.txt"
grep -n -B2 -A14 "^Name: GmsCore support" "$OUT/danh-sach-ban-va.txt" | head -60 || true
[ -n "$GMS_INDEX" ] || { echo "Không tìm thấy bản vá GmsCore support cho YouTube"; exit 1; }

patch() { # $1 = tên file ra, các tham số còn lại = tuỳ chọn thêm cho CLI
  local name=$1; shift
  log "5. Vá: $name"
  java -jar "$CLI" patch \
    -p "$WORK/patches.rvp" -b \
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
# Theo tài liệu CLI: -O đứng NGAY SAU -e/--ei của bản vá mà nó áp dụng.
# ReVanced Patcher v22 tra tuỳ chọn theo TÊN ("GmsCore vendor group ID"); bộ vá đời cũ dùng khoá
# "gmsCoreVendorGroupId". Truyền cả hai – cái không tồn tại chỉ bị CLI cảnh báo rồi bỏ qua.
patch youtube-revanced-khong-microg --ei "$GMS_INDEX" \
  "-OGmsCore vendor group ID=com.google" -OgmsCoreVendorGroupId=com.google || FAILED=1
# Bản chuẩn (cần microG) – để đối chứng.
patch youtube-revanced-chuan || FAILED=1

log "6. Kiểm tra: tuỳ chọn có thật sự đổi nơi gọi Google Play Services không?"
count_gms() { unzip -p "$1" 'classes*.dex' | grep -a -o "$2" | wc -l || true; }
for name in youtube-revanced-khong-microg youtube-revanced-chuan; do
  [ -f "$OUT/$name.apk" ] || continue
  echo "$name: $(count_gms "$OUT/$name.apk" 'app\.revanced\.android\.gms') chỗ trong mã (dex) gọi app.revanced.android.gms," \
    "$(count_gms "$OUT/$name.apk" 'com\.google\.android\.gms') chỗ gọi com.google.android.gms" | tee -a "$OUT/phien-ban.txt"
done
if [ -f "$OUT/youtube-revanced-khong-microg.apk" ] && [ -f "$OUT/youtube-revanced-chuan.apk" ] &&
   [ "$(count_gms "$OUT/youtube-revanced-khong-microg.apk" 'app\.revanced\.android\.gms')" -ge \
     "$(count_gms "$OUT/youtube-revanced-chuan.apk" 'app\.revanced\.android\.gms')" ]; then
  echo "Tuỳ chọn KHÔNG được áp dụng: bản 'không microG' vẫn gọi GmsCore như bản chuẩn." | tee -a "$OUT/phien-ban.txt"
  FAILED=1
fi

ls -la "$OUT"
exit $FAILED
