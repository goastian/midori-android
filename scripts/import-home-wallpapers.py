#!/usr/bin/env python3
"""Download or verify the curated Unsplash wallpapers bundled with Midori."""

import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import io
import json
from pathlib import Path
import subprocess
from urllib.parse import urlencode, urlsplit

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / 'app/src/main/assets/wallpapers/catalog.json'
DRAWABLES = ROOT / 'app/src/main/res/drawable-nodpi'


def import_photo(photo):
    source = photo['image_url']
    if urlsplit(source).hostname != 'images.unsplash.com':
        raise ValueError(f'Unexpected image host: {source}')
    query = urlencode({'fm': 'jpg', 'q': 92, 'fit': 'crop', 'crop': 'entropy', 'w': 1080, 'h': 2160})
    result = subprocess.run(['curl', '-sSL', '--fail', '--max-time', '60', source + '?' + query],
                            check=True, capture_output=True)
    image = Image.open(io.BytesIO(result.stdout)).convert('RGB')
    if image.size != (1080, 2160):
        raise ValueError(f"Unexpected image size for {photo['id']}: {image.size}")
    for suffix, size, quality in [('', (1080, 2160), 80), ('_thumb', (432, 864), 78)]:
        destination = DRAWABLES / f"home_wallpaper_{photo['id']}{suffix}.webp"
        output = image if not suffix else image.resize(size, Image.Resampling.LANCZOS)
        output.save(destination, 'WEBP', quality=quality, method=6)
    return photo['id']


def verify_photo(photo):
    for suffix, size in [('', (1080, 2160)), ('_thumb', (432, 864))]:
        path = DRAWABLES / f"home_wallpaper_{photo['id']}{suffix}.webp"
        with Image.open(path) as image:
            if image.format != 'WEBP' or image.size != size:
                raise ValueError(f'Invalid wallpaper: {path}')
            image.load()
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        key = 'thumbnail_sha256' if suffix else 'sha256'
        if photo.get(key) and digest != photo[key]:
            raise ValueError(f'Checksum mismatch: {path}')
        photo[key] = digest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--download', action='store_true', help='Download and encode the catalog images')
    parser.add_argument('--category', help='Only download this category; all bundled files are still verified')
    args = parser.parse_args()
    catalog = json.loads(CATALOG.read_text())
    photos = catalog['photos']
    if args.download:
        selected = [photo for photo in photos if args.category is None or photo['category'] == args.category]
        if not selected:
            parser.error(f'No photos in category: {args.category}')
        with ThreadPoolExecutor(max_workers=4) as pool:
            for id in pool.map(import_photo, selected):
                print(f'Encoded {id}', flush=True)
    for photo in photos:
        verify_photo(photo)
    if args.download:
        CATALOG.write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + '\n')
    total = sum((DRAWABLES / f"home_wallpaper_{photo['id']}{suffix}.webp").stat().st_size
                for photo in photos for suffix in ['', '_thumb'])
    print(f'Verified {len(photos)} photos and {len(photos)} thumbnails: {total / 1024 / 1024:.2f} MiB')


if __name__ == '__main__':
    main()
