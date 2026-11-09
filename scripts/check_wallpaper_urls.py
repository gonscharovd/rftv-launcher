#!/usr/bin/env python3
"""Verify every configured RFTV Unsplash wallpaper URL end-to-end."""
from __future__ import annotations

import io
import re
import sys
import urllib.request
from pathlib import Path

from PIL import Image

SOURCE = Path("app/src/main/java/com/rftv/launcher/MainActivity.kt")
MAX_BYTES = 24 * 1024 * 1024
MIN_WIDTH = 1000
MIN_HEIGHT = 500


def main() -> int:
    source = SOURCE.read_text(encoding="utf-8")
    groups: list[tuple[str, str]] = []
    for name in ("lightPhotos", "darkPhotos"):
        match = re.search(
            rf"private val {name}\s*=\s*listOf\((.*?)^\s*\)",
            source,
            flags=re.DOTALL | re.MULTILINE,
        )
        if not match:
            print(f"ERROR: could not find wallpaper group {name}", file=sys.stderr)
            return 1
        group_urls = re.findall(r'"(https://images\.unsplash\.com/[^"]+)"', match.group(1))
        if len(group_urls) != 10:
            print(f"ERROR: expected 10 URLs in {name}, found {len(group_urls)}", file=sys.stderr)
            return 1
        groups.extend((name, url) for url in group_urls)

    urls = [url for _, url in groups]
    if len(set(urls)) != 20:
        print("ERROR: wallpaper URLs must be unique across day/night groups", file=sys.stderr)
        return 1

    failures = 0
    for index, url in enumerate(urls, start=1):
        request = urllib.request.Request(
            url,
            headers={
                "User-Agent": "RFTV-Launcher-Wallpaper-Validator/0.8",
                "Accept": "image/jpeg,image/*;q=0.9,*/*;q=0.5",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=25) as response:
                status = response.status
                final_url = response.geturl()
                content_type = response.headers.get_content_type().lower()
                body = response.read(MAX_BYTES + 1)

            if status != 200:
                raise ValueError(f"HTTP {status}")
            if not content_type.startswith("image/"):
                raise ValueError(f"unexpected Content-Type {content_type}")
            if len(body) <= 10_000 or len(body) > MAX_BYTES:
                raise ValueError(f"invalid body size {len(body)} bytes")

            with Image.open(io.BytesIO(body)) as image:
                image.verify()
            with Image.open(io.BytesIO(body)) as image:
                image.load()  # Force full pixel decode, not only header/structure validation.
                width, height = image.size
                image_format = image.format
                if width < MIN_WIDTH or height < MIN_HEIGHT:
                    raise ValueError(f"image too small: {width}x{height}")
                if image_format not in {"JPEG", "WEBP", "PNG"}:
                    raise ValueError(f"unsupported image format {image_format}")

            redirect_note = " (redirected)" if final_url != url else ""
            print(
                f"OK {index:02d}/20 [{group}] HTTP {status} {content_type} "
                f"{image_format} {width}x{height} {len(body)} bytes{redirect_note}"
            )
        except Exception as error:  # noqa: BLE001 - report every failed URL.
            failures += 1
            print(f"FAIL {index:02d}/20 {url}\n  {type(error).__name__}: {error}")

    if failures:
        print(f"Wallpaper validation failed: {failures}/20 URLs")
        return 1
    print("All 20 configured wallpaper images passed HTTP, MIME, size and decode checks.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
