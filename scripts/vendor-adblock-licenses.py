#!/usr/bin/env python3
import json
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parent.parent
metadata = json.loads(subprocess.check_output([
    "cargo", "+1.98.0", "metadata", "--offline", "--locked", "--format-version", "1",
    "--filter-platform", "aarch64-linux-android", "--manifest-path", str(root / "native/adblock/Cargo.toml"),
]))
output = root / "app/src/main/assets/adblock/rust-licenses"
output.mkdir(exist_ok=True)
resolved = {node["id"] for node in metadata["resolve"]["nodes"]}
packages = []
for package in sorted(metadata["packages"], key=lambda item: (item["name"], item["version"])):
    if package["id"] not in resolved or package["source"] is None:
        continue
    entry = {key: package.get(key) for key in ["name", "version", "license", "repository", "authors", "source"]}
    directory = Path(package["manifest_path"]).parent
    licenses = [path for path in directory.iterdir() if path.is_file() and
                (path.name.upper().startswith(("LICENSE", "LICENCE", "COPYING", "COPYRIGHT", "NOTICE")) or
                 path.name == package.get("license_file"))]
    target = output / (package["name"] + "-" + package["version"])
    target.mkdir(exist_ok=True)
    for path in licenses:
        shutil.copy2(path, target / path.name)
    supplements = root / "native/adblock/licenses" / target.name
    if supplements.exists():
        for path in supplements.iterdir():
            if path.is_file(): shutil.copy2(path, target / path.name)
    entry["files"] = [path.relative_to(output).as_posix() for path in sorted(target.iterdir()) if path.is_file()]
    packages.append(entry)
(output / "dependencies.json").write_text(json.dumps(packages, indent=2, ensure_ascii=False) + "\n")
print("Attributed", len(packages), "Rust dependencies")
