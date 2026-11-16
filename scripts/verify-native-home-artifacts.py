#!/usr/bin/env python3

import argparse
import json
from pathlib import Path
from zipfile import ZipFile


BUNDLED_EXTENSIONS = {
    "midori_sync": "sync-native@midori.astian.org",
    "midori_vpn": "midorivpn@astian.org",
    "readerview": "readerview@mozac.org",
}
COMPONENT_EXTENSIONS = {"ads", "browser-icons", "search", "webcompat"}


def verify(path):
    prefix = "base/assets/" if path.suffix == ".aab" else "assets/"
    with ZipFile(path) as archive:
        names = set(archive.namelist())
        extension_prefix = prefix + "extensions/"
        extensions = {
            name.removeprefix(extension_prefix).split("/")[0]
            for name in names
            if name.startswith(extension_prefix) and name != extension_prefix
        }
        expected = BUNDLED_EXTENSIONS.keys() | COMPONENT_EXTENSIONS
        if extensions != expected:
            raise ValueError(
                f"Unexpected extensions: {sorted(extensions - expected)}; "
                f"missing: {sorted(expected - extensions)}"
            )
        for name in names:
            if not name.startswith(extension_prefix) or not name.endswith("/manifest.json"):
                continue
            manifest = json.loads(archive.read(name))
            if "newtab" in manifest.get("chrome_url_overrides", {}):
                raise ValueError(f"Extension overrides native home: {name}")
        for directory, expected_id in BUNDLED_EXTENSIONS.items():
            manifest = json.loads(archive.read(f"{extension_prefix}{directory}/manifest.json"))
            settings = manifest.get("browser_specific_settings", manifest.get("applications", {}))
            if settings.get("gecko", {}).get("id") != expected_id:
                raise ValueError(f"Unexpected extension ID: {directory}")
        for name in ("adblock/content/manifest.json", "adblock/compiled/rust/"):
            if not any(entry.startswith(prefix + name) for entry in names):
                raise ValueError(f"Missing native blocker asset: {name}")
    print(f"{path}: native home; {', '.join(sorted(extensions))}; native blocker preserved")


def main():
    parser = argparse.ArgumentParser(description="Check native home packaging in APK/AAB outputs.")
    parser.add_argument("artifacts", nargs="+", type=Path)
    args = parser.parse_args()
    for path in args.artifacts:
        if path.suffix not in {".apk", ".aab"}:
            parser.error(f"Expected an APK or AAB: {path}")
        verify(path)


if __name__ == "__main__":
    main()
