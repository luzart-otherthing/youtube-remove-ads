#!/usr/bin/env python3
"""Tải APK YouTube gốc (do Google ký) từ APKMirror.

Cách dùng: python3 tai_apkmirror.py <phiên-bản> <thư-mục-ra> [kiến-trúc ưu tiên...]
Ví dụ:     python3 tai_apkmirror.py 20.40.45 out x86_64 arm64-v8a

In ra đường dẫn file đã tải (.apk, hoặc .apkm nếu chỉ có dạng gói nhiều phần).
Chữ ký được kiểm tra ở build.sh, không tin APKMirror mù quáng.
"""
import os
import re
import sys
import time
import urllib.request

from bs4 import BeautifulSoup

BASE = "https://www.apkmirror.com"
APP = "/apk/google-inc/youtube"
UA = "Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0"


def get(url: str) -> bytes:
    last = None
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "en-US,en;q=0.8"})
            with urllib.request.urlopen(req, timeout=120) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001
            last = e
            print(f"  lỗi tải {url}: {e} (thử lại)", file=sys.stderr)
            time.sleep(3 * (attempt + 1))
    raise SystemExit(f"Không tải được {url}: {last}")


def soup(url: str) -> BeautifulSoup:
    return BeautifulSoup(get(url), "html.parser")


def main() -> None:
    version, out_dir, *arches = sys.argv[1:]
    arches = arches or ["x86_64", "arm64-v8a"]
    os.makedirs(out_dir, exist_ok=True)

    release = f"{BASE}{APP}/youtube-{version.replace('.', '-')}-release/"
    print(f"Trang phát hành: {release}", file=sys.stderr)
    page = soup(release)

    variants = []  # (loại, kiến trúc, dpi, link)
    for row in page.select("div.table-row"):
        link = row.select_one("a.accent_color")
        if not link or not link.get("href"):
            continue
        cells = [c.get_text(" ", strip=True) for c in row.select("div.table-cell")]
        badge = row.select_one(".apkm-badge")
        kind = badge.get_text(strip=True) if badge else ("BUNDLE" if "bundle" in " ".join(cells).lower() else "APK")
        arch = cells[1] if len(cells) > 1 else ""
        dpi = cells[3] if len(cells) > 3 else ""
        variants.append((kind, arch, dpi, BASE + link["href"]))
    for v in variants:
        print("  biến thể:", v, file=sys.stderr)
    if not variants:
        print(page.get_text(" ", strip=True)[:1500], file=sys.stderr)
        raise SystemExit("Không thấy biến thể nào (APKMirror chặn hoặc đổi giao diện).")

    chosen = None
    for want in arches:
        for kind, arch, dpi, href in variants:
            if kind == "APK" and want in arch:
                chosen = (kind, arch, href)
                break
        if chosen:
            break
    if not chosen:
        for kind, arch, dpi, href in variants:
            if kind == "BUNDLE":
                chosen = (kind, arch, href)
                break
    if not chosen:
        raise SystemExit("Không có biến thể phù hợp.")
    kind, arch, href = chosen
    print(f"Chọn: {kind} {arch} {href}", file=sys.stderr)

    vpage = soup(href)
    btn = vpage.select_one("a.downloadButton") or vpage.select_one("a[href*='/download/']")
    if not btn:
        raise SystemExit("Không thấy nút tải ở trang biến thể.")
    dpage = soup(BASE + btn["href"] if btn["href"].startswith("/") else btn["href"])
    final = dpage.select_one("#download-link") or dpage.select_one("a[href*='download.php']")
    if not final:
        for a in dpage.select("a[rel=nofollow]"):
            if "download" in a.get("href", ""):
                final = a
                break
    if not final:
        raise SystemExit("Không thấy link tải cuối cùng.")
    url = final["href"]
    url = BASE + url if url.startswith("/") else url

    ext = ".apkm" if kind == "BUNDLE" else ".apk"
    safe_arch = re.sub(r"[^a-z0-9_]+", "-", arch.lower()).strip("-") or "all"
    path = os.path.join(out_dir, f"youtube-{version}-{safe_arch}{ext}")
    data = get(url)
    if len(data) < 10_000_000:
        raise SystemExit(f"File tải về quá nhỏ ({len(data)} byte) – có thể là trang chặn.")
    with open(path, "wb") as f:
        f.write(data)
    print(f"Đã tải {len(data) / 1e6:.1f} MB", file=sys.stderr)
    print(path)


if __name__ == "__main__":
    main()
