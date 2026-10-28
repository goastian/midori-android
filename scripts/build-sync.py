#!/usr/bin/env python3
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
CRATE = ROOT / "native/sync"
TARGETS = {
    "arm64-v8a": ("aarch64-linux-android", "aarch64-linux-android26"),
    "armeabi-v7a": ("armv7-linux-androideabi", "armv7a-linux-androideabi26"),
    "x86_64": ("x86_64-linux-android", "x86_64-linux-android26"),
    "x86": ("i686-linux-android", "i686-linux-android26"),
}

parser = argparse.ArgumentParser()
parser.add_argument("--host", action="store_true")
parser.add_argument("--abi", action="append", choices=TARGETS)
parser.add_argument("--ndk", type=Path)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
environment = os.environ.copy()
environment["CARGO_TARGET_DIR"] = str(ROOT / "build/rust-sync")
command = ["cargo", "+1.98.0", "build", "--locked", "--release", "--manifest-path", str(CRATE / "Cargo.toml")]
if args.host:
    subprocess.run(command, check=True, env=environment)
    shutil.copy2(Path(environment["CARGO_TARGET_DIR"]) / "release/libmidori_sync_android.so", args.output)
else:
    if args.ndk is None:
        parser.error("--ndk is required for Android")
    tools = args.ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin"
    for abi in args.abi or ["armeabi-v7a", "arm64-v8a", "x86_64"]:
        target, clang = TARGETS[abi]
        build_environment = environment.copy()
        build_environment["CARGO_TARGET_" + target.upper().replace("-", "_") + "_LINKER"] = str(tools / (clang + "-clang"))
        build_environment["RUSTFLAGS"] = "-C link-arg=-Wl,-z,max-page-size=16384"
        subprocess.run(command + ["--target", target], check=True, env=build_environment)
        output = args.output / abi
        output.mkdir(parents=True, exist_ok=True)
        shutil.copy2(Path(environment["CARGO_TARGET_DIR"]) / target / "release/libmidori_sync_android.so", output)
