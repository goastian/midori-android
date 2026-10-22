#!/usr/bin/env python3
"""Measure an explicitly selected debug browser tab on a physical Android device."""

import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
from threading import Thread
import time
from urllib.parse import urlparse
import xml.etree.ElementTree as ET


class GeckoDebugger:
    def __init__(self, port, browser_id=None):
        self.socket = socket.create_connection(("127.0.0.1", port), timeout=3)
        self.read()
        if browser_id is None:
            return
        tabs = self.request("root", "listTabs")["tabs"]
        tab = next(tab for tab in tabs if tab["browserId"] == browser_id)
        self.target = self.request(tab["actor"], "getTarget")["frame"]

    def read(self):
        header = b""
        while not header.endswith(b":"):
            part = self.socket.recv(1)
            if not part:
                raise EOFError("Gecko debugger disconnected")
            header += part
        remaining = int(header[:-1])
        body = bytearray()
        while remaining:
            part = self.socket.recv(remaining)
            if not part:
                raise EOFError("Gecko debugger disconnected")
            body.extend(part)
            remaining -= len(part)
        return json.loads(body)

    def request(self, actor, kind, **fields):
        packet = json.dumps(dict(to=actor, type=kind, **fields)).encode()
        self.socket.sendall(str(len(packet)).encode() + b":" + packet)
        expected = {"listTabs": "tabs", "getTarget": "frame", "evaluateJSAsync": "resultID"}[kind]
        while True:
            response = self.read()
            if response.get("from") != actor:
                continue
            if "error" in response:
                raise RuntimeError(response)
            if expected in response:
                return response

    def evaluate(self, expression):
        result_id = self.request(self.target["consoleActor"], "evaluateJSAsync", text=expression)["resultID"]
        while True:
            response = self.read()
            if response.get("type") != "evaluationResult" or response.get("resultID") != result_id:
                continue
            if response.get("exceptionMessage"):
                raise RuntimeError(response["exceptionMessage"])
            return json.loads(response["result"])

    def close(self):
        self.socket.close()


METRICS = """JSON.stringify({url:location.href,timeOrigin:performance.timeOrigin,
  ready:document.readyState,visible:document.visibilityState,
  errorPage:document.documentURI.startsWith('about:neterror'),
  rootStyles:[document.documentElement,document.body].filter(Boolean).map(e=>({
    tag:e.tagName,filter:getComputedStyle(e).filter,display:getComputedStyle(e).display,
    visibility:getComputedStyle(e).visibility,opacity:getComputedStyle(e).opacity})),
  scripts:document.scripts.length,
  bodyCharacters:document.body?.innerText.length||0,
  navigation:performance.getEntriesByType('navigation').map(n=>({
    ttfb:n.responseStart,dom:n.domContentLoadedEventEnd,load:n.loadEventEnd,bytes:n.transferSize})),
  paint:performance.getEntriesByType('paint').map(n=>({name:n.name,time:n.startTime})),
  resources:performance.getEntriesByType('resource').length})"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="Explicit adb serial; never selects an arbitrary device")
    parser.add_argument("--browser-id", type=int, help="Dedicated, selected Gecko test tab")
    parser.add_argument("--level", choices=["OFF", "TRACKERS", "TRACKERS_AND_ADS"])
    parser.add_argument("--package", default="org.midorinext.android.debug")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--list-tabs", action="store_true", help="List Gecko browser IDs without navigating")
    parser.add_argument("--opaque-frames", action="store_true", help="Check and remove about/data/blob frames first")
    parser.add_argument("urls", nargs="*")
    args = parser.parse_args()
    if not args.list_tabs and (args.browser_id is None or not args.level or not args.output or not args.urls):
        parser.error("Measurements require --browser-id, --level, --output and test URLs")
    adb = shutil.which("adb") or str(Path(os.environ.get("ANDROID_HOME", Path.home() / "Android/Sdk")) / "platform-tools/adb")

    def command(*parts):
        return subprocess.check_output([adb, "-s", args.serial, *parts])

    if args.list_tabs:
        port = int(command("forward", "tcp:0", f"localabstract:{args.package}/firefox-debugger-socket"))
        debugger = None
        try:
            debugger = GeckoDebugger(port)
            print(json.dumps(debugger.request("root", "listTabs")["tabs"], ensure_ascii=False, indent=2))
        finally:
            if debugger:
                debugger.close()
            command("forward", "--remove", f"tcp:{port}")
        return

    preferences = ET.fromstring(command("exec-out", "run-as", args.package, "cat", "shared_prefs/native_adblock.xml"))
    level = next(node.text for node in preferences if node.get("name") == "level")
    if level != args.level:
        raise RuntimeError(f"Select {args.level} in Midori settings first; actual level is {level}")
    exceptions = [item.text for node in preferences if node.get("name") == "site_levels" for item in node]
    for url in args.urls:
        if urlparse(url).scheme not in ("https", "http"):
            raise ValueError("Only public HTTP(S) test URLs are supported")
        if any(item.split("|", 1)[0] == urlparse(url).hostname for item in exceptions):
            raise RuntimeError(f"Test URL has a site exception: {url}")

    args.output.mkdir(parents=True, exist_ok=True)
    port = int(command("forward", "tcp:0", f"localabstract:{args.package}/firefox-debugger-socket"))

    def evaluate(expression):
        debugger = GeckoDebugger(port, args.browser_id)
        try:
            return debugger.evaluate(expression)
        finally:
            debugger.close()

    try:
        if evaluate(METRICS)["visible"] != "visible":
            raise RuntimeError("Select the dedicated test tab before starting")
        if args.opaque_frames:
            original_pid = command("shell", "pidof", args.package).strip()

            class Fixture(BaseHTTPRequestHandler):
                def do_GET(self):
                    body = b"<!doctype html><h1>Midori frame integration</h1>"
                    self.send_response(200)
                    self.send_header("Content-Type", "text/html; charset=utf-8")
                    self.send_header("Content-Length", str(len(body)))
                    self.end_headers()
                    self.wfile.write(body)

                def log_message(self, *_):
                    pass

            server = ThreadingHTTPServer(("127.0.0.1", 0), Fixture)
            Thread(target=server.serve_forever, daemon=True).start()
            device_port = int(command("reverse", "tcp:0", f"tcp:{server.server_port}"))
            reverse = f"tcp:{device_port}"
            try:
                fixture_url = f"http://127.0.0.1:{device_port}/"
                evaluate("JSON.stringify((setTimeout(()=>location.href=" + json.dumps(fixture_url) + ",50),true))")
                deadline = time.monotonic() + 10
                while time.monotonic() < deadline:
                    time.sleep(.25)
                    page = evaluate(METRICS)
                    if page["url"] == fixture_url and page["ready"] == "complete":
                        break
                assert page["url"] == fixture_url, "Local frame fixture did not load"
                evaluate("""JSON.stringify((globalThis.__midoriFrameAudit={loaded:[],blob:
                  URL.createObjectURL(new Blob(['<p>Midori integration</p>'],{type:'text/html'}))},
                  ['about:blank','data:text/html,<p>Midori integration</p>',__midoriFrameAudit.blob,'/frame'].forEach((src,i)=>{
                    const frame=document.createElement('iframe');frame.hidden=true;frame.dataset.midoriAudit=String(i);
                    frame.onload=()=>__midoriFrameAudit.loaded.push(i);frame.src=src;document.body.append(frame);
                  }),true))""")
                deadline = time.monotonic() + 10
                loaded = []
                while time.monotonic() < deadline:
                    time.sleep(.25)
                    loaded = evaluate("JSON.stringify(__midoriFrameAudit.loaded)")
                    if sorted(loaded) == [0, 1, 2, 3]:
                        break
                assert sorted(loaded) == [0, 1, 2, 3], f"Frames did not load: {loaded}"
                assert command("shell", "pidof", args.package).strip() == original_pid, "App process changed"
                result = {"frames": ["about:blank", "data:", "blob:", "http:"],
                          "loaded": loaded, "processSurvived": True}
                (args.output / "frames.json").write_text(json.dumps(result, indent=2) + "\n")
                print(json.dumps(result), flush=True)
            finally:
                command("reverse", "--remove", reverse)
                server.shutdown()
                server.server_close()

        failures = []
        for index, url in enumerate(args.urls):
            before = evaluate(METRICS)
            if before["visible"] != "visible":
                raise RuntimeError("Test tab lost foreground visibility")
            started = time.monotonic()
            evaluate("JSON.stringify((setTimeout(()=>location.href=" + json.dumps(url) + ",50),true))")
            first_content = None
            sample = None
            errors = []
            while time.monotonic() - started < 25:
                try:
                    sent = time.monotonic()
                    sample = evaluate(METRICS)
                    sample["debuggerRoundtripMs"] = round((time.monotonic() - sent) * 1000, 1)
                    fresh = sample["timeOrigin"] != before["timeOrigin"]
                    painted = fresh and sample["paint"] and sample["bodyCharacters"] and sample["visible"] == "visible"
                    if painted and first_content is None:
                        first_content = round((time.monotonic() - started) * 1000)
                    if painted and sample["ready"] == "complete":
                        break
                except (OSError, EOFError, RuntimeError, KeyError, StopIteration) as error:
                    errors.append(str(error))
                time.sleep(.35)
            row = dict(level=args.level, requestedUrl=url, elapsedMs=round((time.monotonic()-started)*1000),
                       observedContentMs=first_content, metrics=sample, debuggerErrors=errors)
            if first_content is None or not sample or sample["errorPage"] or sample["visible"] != "visible":
                failures.append(url)
                row["status"] = "failed"
            else:
                row["status"] = "rendered" if sample["ready"] == "complete" else "rendered-pending-resources"
            with (args.output / "measurements.jsonl").open("a") as report:
                report.write(json.dumps(row, ensure_ascii=False) + "\n")
            (args.output / f"{args.level.lower()}-{index}.png").write_bytes(command("exec-out", "screencap", "-p"))
            print(json.dumps(row, ensure_ascii=False), flush=True)
        if failures:
            raise RuntimeError(f"Pages did not render in the selected browser tab: {failures}")
    finally:
        command("forward", "--remove", f"tcp:{port}")


if __name__ == "__main__":
    main()
