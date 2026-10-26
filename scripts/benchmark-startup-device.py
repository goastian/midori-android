#!/usr/bin/env python3
"""Measure cold browser launches on an Android device without clearing app data."""

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import statistics
import subprocess
import time


APPS = {
    "midori_release": (
        "org.midorinext.android",
        "org.midorinext.android/.MainActivity",
    ),
    "midori_debug": (
        "org.midorinext.android.debug",
        "org.midorinext.android.debug/org.midorinext.android.MainActivity",
    ),
    "opera": ("com.opera.browser", "com.opera.browser/com.opera.Launcher3"),
    "firefox": ("org.mozilla.firefox", "org.mozilla.firefox/.App"),
    "edge": ("com.microsoft.emmx", "com.microsoft.emmx/com.microsoft.ruby.Main"),
    "vivaldi": (
        "com.vivaldi.browser",
        "com.vivaldi.browser/org.chromium.chrome.browser.ChromeTabbedActivity",
    ),
}


class Device:
    def __init__(self, serial):
        self.command = ["adb", "-s", serial]

    def run(self, *arguments, check=True):
        return subprocess.run(
            self.command + list(arguments),
            capture_output=True,
            text=True,
            check=check,
            timeout=60,
        ).stdout

    def temperature(self):
        match = re.search(r"temperature: (\d+)", self.run("shell", "dumpsys", "battery"))
        return int(match[1]) / 10 if match else None

    def version(self, package):
        output = self.run("shell", "dumpsys", "package", package)
        fields = {}
        for key in ("versionCode", "versionName"):
            match = re.search(rf"\b{key}=([^\s]+)", output)
            if match:
                fields[key] = match[1]
        fields["debuggable"] = bool(re.search(r"(?:pkgFlags|flags)=\[[^\]]*DEBUGGABLE", output))
        return fields

    def sample(self, name, run, settle_seconds):
        package, component = APPS[name]
        self.run("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        self.run("shell", "input", "keyevent", "KEYCODE_HOME")
        self.run("shell", "am", "force-stop", package)
        time.sleep(1)
        if self.run("shell", "pidof", package, check=False).strip():
            raise RuntimeError(f"The process for {package} did not stop")

        output = self.run(
            "shell", "am", "start", "-W",
            "-a", "android.intent.action.MAIN",
            "-c", "android.intent.category.LAUNCHER",
            "-n", component,
        )
        fields = dict(re.findall(r"^(\w+):\s*(.*)$", output, re.MULTILINE))
        sample = {
            "app": name,
            "package": package,
            "component_requested": component,
            "run": run,
            "launch_state": fields.get("LaunchState"),
            "status": fields.get("Status"),
            "activity": fields.get("Activity"),
        }
        for key in ("ThisTime", "TotalTime", "WaitTime"):
            if fields.get(key, "").isdigit():
                sample[key] = int(fields[key])

        time.sleep(settle_seconds)
        activity_state = self.run("shell", "dumpsys", "activity", "activities")
        sample["app_resumed"] = any(
            package + "/" in line
            for line in activity_state.splitlines()
            if "mResumedActivity:" in line
        )
        sample["temperature_c"] = self.temperature()
        sample["valid_ttid"] = (
            sample["status"] == "ok"
            and sample["launch_state"] == "COLD"
            and sample["app_resumed"]
            and sample.get("TotalTime", 0) > 0
        )
        if name.startswith("midori"):
            pids = self.run("shell", "pidof", package, check=False).split()
            if pids:
                log = self.run("logcat", "-d", "-v", "raw", "--pid", pids[0], "-s", "MidoriStartup:I")
                for field, pattern in (
                    ("startup_marks_ms", r"^([^\n]+?) at (\d+) ms$"),
                    ("startup_durations_ms", r"^([^\n]+?) took (\d+) ms$"),
                ):
                    sample[field] = {
                        stage: int(duration)
                        for stage, duration in re.findall(pattern, log, re.MULTILINE)
                    }
        return sample


def summarize(samples):
    result = {}
    for name in dict.fromkeys(sample["app"] for sample in samples):
        group = [sample for sample in samples if sample["app"] == name]
        values = [sample["TotalTime"] for sample in group if sample["valid_ttid"]]
        result[name] = {"valid_samples": len(values), "invalid_samples": len(group) - len(values)}
        if values:
            result[name].update(
                median_ttid_ms=statistics.median(values),
                min_ttid_ms=min(values),
                max_ttid_ms=max(values),
            )
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apps", nargs="+", choices=APPS, default=list(APPS))
    parser.add_argument("--rounds", type=int, default=5)
    parser.add_argument("--settle-seconds", type=float, default=3)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    if arguments.rounds < 1 or arguments.settle_seconds < 0:
        parser.error("rounds must be positive and settle-seconds must be nonnegative")
    if arguments.output.exists():
        parser.error("output already exists; choose a new path")

    device = Device(arguments.serial)
    result = {
        "measured_at_utc": datetime.now(timezone.utc).isoformat(),
        "device": {
            key: device.run("shell", "getprop", prop).strip()
            for key, prop in (
                ("model", "ro.product.model"),
                ("android", "ro.build.version.release"),
                ("api", "ro.build.version.sdk"),
            )
        },
        "method": "force-stop then MAIN intent; existing profiles; interleaved order",
        "metric": "Android TotalTime (TTID), not full page load; WaitTime is not a substitute",
        "versions": {name: device.version(APPS[name][0]) for name in arguments.apps},
        "temperature_before_c": device.temperature(),
        "samples": [],
    }
    arguments.output.parent.mkdir(parents=True, exist_ok=True)

    def save():
        result["summary"] = summarize(result["samples"])
        arguments.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")

    try:
        for run in range(arguments.rounds):
            offset = run % len(arguments.apps)
            order = arguments.apps[offset:] + arguments.apps[:offset]
            for name in order:
                sample = device.sample(name, run + 1, arguments.settle_seconds)
                result["samples"].append(sample)
                print(json.dumps(sample, ensure_ascii=False), flush=True)
                save()
    finally:
        device.run("shell", "input", "keyevent", "KEYCODE_HOME", check=False)
        result["temperature_after_c"] = device.temperature()
        save()
    print(json.dumps(result["summary"], indent=2))


if __name__ == "__main__":
    main()
