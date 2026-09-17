#!/usr/bin/env python3
"""Actual Android process boundaries, only against the disposable QA application."""
from pathlib import Path
import re
import subprocess

PACKAGE = "tw.techtarian.browser.qa"
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"
TEST = "tw.techtarian.browser.TabPreviewColdRestartTest"
out = Path("validation")
out.mkdir(exist_ok=True)
for method in ("seed", "restoreAndClose", "closedTabStaysDeleted"):
    subprocess.run(["adb", "shell", "am", "force-stop", PACKAGE], check=True, timeout=20)
    result = subprocess.run(["adb", "shell", "am", "instrument", "-w", "-r", "-e", "class", TEST + "#" + method, RUNNER],
                            text=True, capture_output=True, timeout=150)
    text = result.stdout + result.stderr
    (out / ("preview-restart-" + method + ".txt")).write_text(text)
    print(text, flush=True)
    if result.returncode or not re.search(r"OK \(1 test\)", text) or "FAILURES!!!" in text or "INSTRUMENTATION_FAILED" in text:
        raise SystemExit("Cold-restart verification failed: " + method)
print("Three separate app processes verified; retained previews survived and closed-tab preview stayed deleted.")
