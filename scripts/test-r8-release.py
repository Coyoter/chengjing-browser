#!/usr/bin/env python3
"""Exercise the uninstrumented optimized APK via independent test-host processes."""
from pathlib import Path
import json
import re
import subprocess
import time

APP = "tw.techtarian.browser.qa"
HOST = "tw.techtarian.browser.smoketests"
RUNNER = HOST + ".test/androidx.test.runner.AndroidJUnitRunner"
TEST = HOST + ".OptimizedReleaseTest"
METHODS = (
    "installedReleaseIsActuallyObfuscatedAndNotDebuggable",
    "findInPageKeepsTheDocumentVisibleAndCountsMatches",
    "nativeJniCanLoadCallAndThrowItsTypedError",
    "aiProviderFieldsStillRecomposeInOptimizedRelease",
    "webViewJavascriptBridgeSurvivesOptimization",
    "externalBackReturnsToCallerWithoutAddingHomeTabs",
    "coldExternalBackAndProcessRestoreDoNotLeaveBlankTabs",
    "regularRootBackKeepsItsPageAfterLeavingTheBrowser",
    "savedRuleEditorAndAiRevisionEntryWorkInOptimizedRelease",
    "menuHistoryAndPrivateTabCollectionsStillOpen",
    "closeAllRegularTabsWorksInOptimizedRelease",
    "homepageOptionsAndDailyQuoteWorkInOptimizedRelease",
    "deleteBrowsingDataWorksInOptimizedRelease",
    "externalDeepLinksWorkInOptimizedRelease",
    "scrolledImagePreviewSurvivesRestartAndBlankReload",
    "imageDownloadPreviewCopyAndShareWorkInOptimizedRelease",
    "blobVideoAndDataAndHttpDownloadsOpenWithRealBytes",
    "appearanceFollowsAppChoiceAcrossSystemModes",
    "searchEngineChoiceAndCustomTemplateWorkInOptimizedRelease",
)

def adb(*args, **kwargs):
    return subprocess.run(["adb", *args], check=True, timeout=30, **kwargs)

out = Path("validation-r8")
out.mkdir(exist_ok=True)
for folder in (Path("release-smoke/build/outputs/apk/debug"), Path("release-smoke/build/outputs/apk/androidTest/debug")):
    apks = list(folder.glob("*.apk"))
    if len(apks) != 1:
        raise SystemExit("Expected exactly one independent host APK in " + str(folder))
    adb("install", "-r", "-t", str(apks[0]))
mapping = Path("app/build/outputs/mapping/release/mapping.txt").read_text()
extra = []
for key, name in (("samplerClass", "SamplerConfig"), ("thinkingClass", "ThinkingConfig")):
    match = re.search(r"^com\.google\.ai\.edge\.litertlm\." + name + r" -> (\S+):$", mapping, re.M)
    if not match:
        raise SystemExit("Missing mapped JNI configuration: " + name)
    extra += ["-e", key, match.group(1)]
results = []
for method in METHODS:
    # Test hosts which loaded another package are killed when that package is stopped.
    # Isolate invocations instead of turning off actual optimized-code/JNI checks.
    adb("shell", "am", "force-stop", HOST)
    adb("shell", "am", "force-stop", APP)
    adb("logcat", "-c")
    started = time.monotonic()
    try:
        result = subprocess.run(["adb", "shell", "am", "instrument", "-w", "-r", "-e", "class", TEST + "#" + method, *extra, RUNNER],
                                text=True, capture_output=True, timeout=150)
        text = result.stdout + result.stderr
        passed = result.returncode == 0 and bool(re.search(r"OK \(1 test\)", text)) and not any(word in text for word in ("FAILURES!!!", "INSTRUMENTATION_FAILED", "Process crashed"))
    except subprocess.TimeoutExpired as error:
        text = "Instrumentation timed out: " + str(error)
        passed = False
    (out / (method + ".txt")).write_text(text)
    print(text, flush=True)
    with (out / (method + "-logcat.txt")).open("w") as log:
        subprocess.run(["adb", "logcat", "-d", "-v", "threadtime"], stdout=log, stderr=subprocess.STDOUT, timeout=30)
    results.append({"method": method, "passed": passed, "seconds": round(time.monotonic() - started, 2)})
(out / "smoke-results.json").write_text(json.dumps(results, indent=2) + "\n")
if not all(row["passed"] for row in results):
    raise SystemExit("Optimized-release checks failed: " + ", ".join(row["method"] for row in results if not row["passed"]))
print(f"All {len(METHODS)} checks passed against the actual non-debuggable R8 APK, without adding app test keep rules.")
