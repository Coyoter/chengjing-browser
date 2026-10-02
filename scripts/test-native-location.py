#!/usr/bin/env python3
"""Verify actual OS location registrations without GMS, on a disposable emulator only."""
from pathlib import Path
import json
import subprocess
import time

APP = 'tw.techtarian.browser.qa'
TEST = 'tw.techtarian.browser.NativeLocationTest'
OUT = Path('validation/native-location')
OUT.mkdir(parents=True, exist_ok=True)

def adb(*args, check=True):
    return subprocess.run(['adb', *args], text=True, capture_output=True, timeout=60, check=check).stdout

if adb('shell', 'getprop', 'ro.kernel.qemu').strip() != '1':
    raise SystemExit('This check may only change services on a disposable Android emulator.')
adb('shell', 'am', 'force-stop', APP)
adb('uninstall', APP, check=False)
if adb('shell', 'pm', 'path', 'com.google.android.gms').strip():
    adb('shell', 'pm', 'disable-user', '--user', '0', 'com.google.android.gms')
# Let package/service reconfiguration settle; require consecutive healthy ADB replies.
stable = 0
for _ in range(30):
    try:
        healthy = adb('shell', 'getprop', 'sys.boot_completed').strip() == '1'
    except subprocess.SubprocessError:
        healthy = False
    stable = stable + 1 if healthy else 0
    if stable >= 3:
        break
    time.sleep(2)
if stable < 3:
    raise SystemExit('Emulator did not settle after service reconfiguration.')
disabled = adb('shell', 'pm', 'list', 'packages', '-d')
(OUT / 'disabled-packages.txt').write_text(disabled)
assert not adb('shell', 'pm', 'path', 'com.google.android.gms').strip() or 'package:com.google.android.gms\n' in disabled
adb('install', '-r', '-t', 'app/build/outputs/apk/debug/app-debug.apk')
adb('install', '-r', '-t', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
result = subprocess.run(['adb', 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', TEST,
                         APP + '.test/tw.techtarian.browser.BrowserTestRunner'], text=True, capture_output=True, timeout=180)
output = result.stdout + result.stderr
(OUT / 'instrumentation.txt').write_text(output)
print(output, flush=True)
passed = set()
name = ''
for line in output.splitlines():
    if line.startswith('INSTRUMENTATION_STATUS: test='):
        name = line.split('=', 1)[1].strip()
    if line.strip() == 'INSTRUMENTATION_STATUS_CODE: 0':
        passed.add(name)
required = {'aCoarsePermissionDoesNotRequirePrecisePermission', 'bFreshFixUsesAndroidWhileWebViewsProviderIsDisabled',
            'cWatchStopsInBackgroundResumesAndClearWatchReleasesTheService', 'dTimeoutDoesNotLeaveGpsRunningOrReturnAnOldPosition',
            'eNavigationAndRevocationStopNativeRequests', 'fFramesAndPermissionsPolicyCannotBypassNativeConsent'}
(OUT / 'results.json').write_text(json.dumps({'passed': sorted(passed), 'gmsDisabled': True}, indent=2) + '\n')
(OUT / 'location-service.txt').write_text(adb('shell', 'dumpsys', 'location'))
(OUT / 'appops.txt').write_text(adb('shell', 'dumpsys', 'appops', '--package', APP))
if result.returncode or not required.issubset(passed) or 'FAILURES!!!' in output:
    raise SystemExit('Native location checks failed: ' + ', '.join(sorted(required - passed)))
print('All six native location checks passed with Google Play Services disabled.')
