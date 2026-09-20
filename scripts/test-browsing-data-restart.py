#!/usr/bin/env python3
"""Verify range deletion across real process death in the isolated QA package."""
from pathlib import Path
import re
import subprocess

PACKAGE = "tw.techtarian.browser.qa"
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"
TEST = "tw.techtarian.browser.BrowsingDataColdRestartTest"
out = Path("validation")
out.mkdir(exist_ok=True)
for method in ("seedAndDelete", "deletedDataStaysDeletedAndRestorationDoesNotResetAge"):
    subprocess.run(["adb", "shell", "am", "force-stop", PACKAGE], check=True, timeout=20)
    result = subprocess.run(["adb", "shell", "am", "instrument", "-w", "-r", "-e", "class", TEST + "#" + method, RUNNER],
                            text=True, capture_output=True, timeout=150)
    text = result.stdout + result.stderr
    (out / ("browsing-data-restart-" + method + ".txt")).write_text(text)
    print(text, flush=True)
    if result.returncode or not re.search(r"OK \(1 test\)", text) or "FAILURES!!!" in text or "INSTRUMENTATION_FAILED" in text:
        raise SystemExit("Browsing-data restart verification failed: " + method)
print("Deleted history, tabs and thumbnails stayed deleted; retained timestamps survived real process restart.")
