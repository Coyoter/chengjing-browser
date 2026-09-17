#!/usr/bin/env python3
"""Verify bulk closing survives a real process boundary, only in the disposable QA app."""
from pathlib import Path
import re
import subprocess

package = "tw.techtarian.browser.qa"
runner = package + ".test/androidx.test.runner.AndroidJUnitRunner"
test = "tw.techtarian.browser.CloseAllColdRestartTest"
out = Path("validation")
out.mkdir(exist_ok=True)
for method in ("closeAndCheckpoint", "allClosedPagesRemainClosedAfterProcessRestart"):
    subprocess.run(["adb", "shell", "am", "force-stop", package], check=True, timeout=20)
    result = subprocess.run(["adb", "shell", "am", "instrument", "-w", "-r", "-e", "class", test + "#" + method, runner],
                            text=True, capture_output=True, timeout=150)
    text = result.stdout + result.stderr
    (out / ("close-all-restart-" + method + ".txt")).write_text(text)
    print(text, flush=True)
    if result.returncode or not re.search(r"OK \(1 test\)", text) or "FAILURES!!!" in text or "INSTRUMENTATION_FAILED" in text:
        raise SystemExit("Close-all restart verification failed: " + method)
print("Bulk-closed tabs and thumbnails did not return in the new app process.")
