#!/usr/bin/env python3
"""Verify the actual optimized QA APK's language preference on an isolated Android 9–12L emulator."""
from pathlib import Path
import re,subprocess

def adb(*args):
    return subprocess.check_output(['adb',*args],text=True,stderr=subprocess.STDOUT,timeout=180)
serial=adb('get-serialno').strip()
assert serial.startswith('emulator-'), 'Use an isolated emulator, never a personal device.'
sdk=int(adb('shell','getprop','ro.build.version.sdk').strip())
assert 28<=sdk<33, 'Expected Android without system per-app language settings.'
phone=adb('shell','getprop','persist.sys.locale').strip() or adb('shell','getprop','ro.product.locale').strip()
assert phone.startswith('en'), 'Use the English default emulator language.'
for folder in ('app/build/outputs/apk/release','release-smoke/build/outputs/apk/debug','release-smoke/build/outputs/apk/androidTest/debug'):
    apks=list(Path(folder).glob('*.apk'));assert len(apks)==1,folder
    print(adb('install','-r','-t',str(apks[0])),flush=True)
output=adb('shell','am','instrument','-w','-r','-e','class','tw.techtarian.browser.smoketests.LegacyLanguageTest','tw.techtarian.browser.smoketests.test/androidx.test.runner.AndroidJUnitRunner')
out=Path('qa/localization/legacy-checks');out.mkdir(parents=True,exist_ok=True)
(out/'language-process-restart.txt').write_text(output)
print(output)
assert re.search(r'OK \(1 test\)',output) and not any(s in output for s in ('FAILURES!!!','Process crashed','INSTRUMENTATION_FAILED')), 'Legacy language persistence failed.'
print('Japanese and Urdu survived real optimized-app process restarts on SDK',sdk)
