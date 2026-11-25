#!/usr/bin/env python3
"""Exercise the production diagnostics path on an offline Android emulator."""

import argparse
from datetime import datetime
import io
import json
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET


PACKAGE = "org.midorinext.android.debug"
CACHE = "no_backup/stability_diagnostics"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not args.serial.startswith("emulator-"):
        parser.error("Use a disposable emulator, not a physical device")
    args.output.mkdir(parents=True, exist_ok=True)

    def adb(*command, check=True):
        return subprocess.run(
            ["adb", "-s", args.serial, *command], check=check, capture_output=True,
        ).stdout

    def probe(name):
        adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/org.midorinext.android.diagnostics.SentryProbeActivity",
            "--es", "probe", name)

    def status():
        adb("shell", "run-as", PACKAGE, "rm", "-f", "files/diagnostics-probe-status.json")
        probe("status")
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            raw = adb("exec-out", "run-as", PACKAGE, "cat", "files/diagnostics-probe-status.json", check=False)
            try:
                return json.loads(raw)
            except ValueError:
                time.sleep(0.25)
        raise RuntimeError("Diagnostics status probe did not finish")

    def events():
        files = adb("shell", "run-as", PACKAGE, "find", CACHE, "-type", "f", check=False).decode().splitlines()
        found = {}
        for filename in files:
            if not filename.endswith(".envelope") and "/outbox/" not in filename:
                continue
            raw = adb("exec-out", "run-as", PACKAGE, "cat", filename, check=False)
            stream = io.BytesIO(raw)
            try:
                json.loads(stream.readline())
                while header := stream.readline():
                    if not header.strip():
                        continue
                    item = json.loads(header)
                    payload = stream.read(item["length"])
                    if item.get("type") == "event":
                        event = json.loads(payload)
                        previous = found.get(event["event_id"], {})
                        processed = event.get("tags", {}).get("diagnostics.schema") == "2"
                        if previous.get("tags", {}).get("diagnostics.schema") != "2" or processed:
                            found[event["event_id"]] = event
            except (ValueError, KeyError):
                continue
        return found

    def wait_for(predicate, description):
        deadline = time.monotonic() + 45
        while time.monotonic() < deadline:
            current = events()
            for event in current.values():
                if predicate(event):
                    (args.output / f"{description}.json").write_text(json.dumps(event, indent=2))
                    print(f"CAPTURED: {description}", flush=True)
                    return event
            time.sleep(1)
        raise RuntimeError(f"No persisted event for {description}")

    def exception_type(event):
        return [item.get("type") for item in event.get("exception", {}).get("values", [])]

    def check_privacy(event):
        serialized = json.dumps(event)
        for forbidden in ("diagnostics-private-sentinel", "example.invalid", "account@example", "token=secret"):
            if forbidden in serialized:
                raise RuntimeError(f"Private probe value escaped scrubbing: {forbidden}")
        if event.get("user") or event.get("request") or event.get("extra"):
            raise RuntimeError("Unexpected personal/request/extra data")
        if not event.get("message", {}).get("formatted"):
            raise RuntimeError("Missing diagnostic description")

    adb("shell", "svc", "wifi", "disable")
    adb("shell", "svc", "data", "disable")
    connectivity = adb("shell", "dumpsys", "connectivity").decode()
    if "Active default network: none" not in connectivity:
        raise RuntimeError("The emulator still has an active network; reports must remain local")
    adb("install", "-r", str(args.apk.resolve()))
    probe("disable")
    time.sleep(2)
    try:
        probe("disabled-saved-state-crash")
        time.sleep(3)
        if status()["sentry_enabled"] or events():
            raise RuntimeError("A crash without diagnostics consent initialized Sentry or cached an event")

        probe("enable")
        time.sleep(2)
        current_status = status()
        if not current_status["sentry_uncaught_handler"] or current_status["uncaught_handler"] != "io.sentry.UncaughtExceptionHandlerIntegration":
            raise RuntimeError("Sentry does not own direct JVM crash capture")

        probe("handled-error")
        handled = wait_for(lambda event: "IllegalStateException" in exception_type(event), "handled_error")
        check_privacy(handled)
        runtime = handled.get("contexts", {}).get("midori_runtime", {})
        if not runtime.get("java_heap_limit_bytes") or runtime.get("visibility") != "foreground":
            raise RuntimeError("Missing runtime snapshot from the real Android process")

        before_pressure = set(events())
        adb("shell", "am", "send-trim-memory", PACKAGE, "COMPLETE")
        probe("handled-error")
        pressure = wait_for(lambda event: event.get("event_id") not in before_pressure and
                            "IllegalStateException" in exception_type(event), "android_memory_pressure")
        check_privacy(pressure)
        crumbs = pressure.get("breadcrumbs", [])
        if isinstance(crumbs, dict):
            crumbs = crumbs.get("values", [])
        if pressure.get("contexts", {}).get("midori_runtime", {}).get("last_trim_level") != 80 or not any(
            crumb.get("category") == "midori.memory" and crumb.get("data", {}).get("trim_level") == 80 for crumb in crumbs
        ):
            raise RuntimeError("The Android memory callback did not reach the report")

        probe("crash")
        java = wait_for(lambda event: "SentryTestCrashException" in exception_type(event), "uncaught_java_crash")
        check_privacy(java)
        if not java.get("exception", {}).get("values", [{}])[-1].get("stacktrace", {}).get("frames"):
            raise RuntimeError("Uncaught crash lost its stack trace")

        for mode in ("saved-state-crash", "background-saved-state-crash"):
            time.sleep(2)
            before = set(events())
            probe(mode)
            cast = wait_for(lambda event: event.get("event_id") not in before and
                            "ClassCastException" in exception_type(event), mode)
            check_privacy(cast)
            exception = cast["exception"]["values"][-1]
            if cast.get("level") != "fatal" or exception.get("mechanism", {}).get("handled") is not False:
                raise RuntimeError("Saved-state crash was not captured as an unhandled fatal exception")
            frames = exception.get("stacktrace", {}).get("frames", [])
            if not any(frame.get("filename") == "MapSaver.kt" for frame in frames):
                raise RuntimeError("Saved-state crash lost its Compose restoration stack")
            time.sleep(2)
            matching = [event for key, event in events().items() if key not in before and
                        "ClassCastException" in exception_type(event)]
            if len(matching) != 1:
                raise RuntimeError("Saved-state crash was reported more than once")

        time.sleep(2)
        probe("sigkill")
        time.sleep(4)
        probe("handled-error")
        killed = wait_for(lambda event: "AndroidProcessSigkill" in exception_type(event), "sigkill_next_start")
        check_privacy(killed)
        exit_context = killed["contexts"]["android_process_exit"]
        if exit_context.get("status") != 9 or killed.get("level") != "warning":
            raise RuntimeError("SIGKILL was not reported as a qualified process termination")
        if "midori_runtime" in killed.get("contexts", {}) or killed.get("breadcrumbs", {}).get("values"):
            raise RuntimeError("Historical exit inherited the new process state")
        if killed.get("release") != java.get("release"):
            raise RuntimeError("Historical exit has the wrong release")

        probe("native-crash")
        time.sleep(5)
        probe("replay-native")
        native = None
        deadline = time.monotonic() + 45
        while time.monotonic() < deadline:
            raw = adb("exec-out", "run-as", PACKAGE, "cat", f"{CACHE}/native-probe.json", check=False)
            try:
                native = json.loads(raw)
                break
            except ValueError:
                time.sleep(1)
        if native is None or native.get("platform") != "native" or native.get("tags", {}).get("diagnostics.schema") != "2":
            raise RuntimeError("The cached native envelope did not pass through the production SDK callback")
        (args.output / "native_crash_next_start.json").write_text(json.dumps(native, indent=2))
        check_privacy(native)
        if "midori_runtime" not in native.get("contexts", {}):
            raise RuntimeError("Native scope did not retain the crash process context")
        native_runtime = native["contexts"]["midori_runtime"]
        crash_time = datetime.fromisoformat(native["timestamp"].replace("Z", "+00:00")).timestamp() * 1000
        if not 0 < native_runtime.get("process_started_at_ms", 0) <= crash_time:
            raise RuntimeError("Native report contains state from a process started after the crash")
        stacks = native.get("exception", {}).get("values", []) + native.get("threads", {}).get("values", [])
        if not any(item.get("stacktrace", {}).get("frames") for item in stacks):
            raise RuntimeError("Native crash lost its captured stack frames")
        if native.get("contexts", {}).get("android_process_exit", {}).get("reason") != "native_crash":
            raise RuntimeError("Native report has no correlated Android exit reason")
        print("PASS: native crash processed by the SDK with prior-process context", flush=True)

        before = set(events())
        adb("shell", "am", "force-stop", PACKAGE)
        probe("handled-error")
        wait_for(lambda event: event.get("event_id") not in before and
                 "IllegalStateException" in exception_type(event), "restart_after_native_crash")
        repeated = [event for key, event in events().items() if key not in before and "AndroidProcessSigkill" in exception_type(event)]
        if repeated:
            raise RuntimeError("The previous SIGKILL was reported twice")
        print("PASS: no duplicate process exit", flush=True)
        print("PASS: all integration scenarios and privacy checks", flush=True)
    except Exception:
        for event_id, event in events().items():
            (args.output / f"unvalidated-{event_id}.json").write_text(json.dumps(event, indent=2))
        raise
    finally:
        probe("disable")
        time.sleep(2)
        if events():
            raise RuntimeError("Consent revocation did not remove the diagnostics cache")
        preferences = ET.fromstring(adb("exec-out", "run-as", PACKAGE, "cat", "shared_prefs/stability_diagnostics.xml"))
        if any(item.get("name", "").startswith("exit.") for item in preferences):
            raise RuntimeError("Consent revocation retained the previous-session marker")
        print("PASS: consent revocation removed cached reports and the session marker", flush=True)


if __name__ == "__main__":
    main()
