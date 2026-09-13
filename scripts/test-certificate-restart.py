#!/usr/bin/env python3
"""Run only on the isolated QA package; never erase production browser data."""
import os, pathlib, subprocess
root=pathlib.Path(__file__).resolve().parents[1]
env=dict(os.environ,JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home',CHENGJING_BROWSER_STORE_PASSWORD=(root/'signing/password').read_text().strip())
adb=str(pathlib.Path.home()/'Library/Android/sdk/platform-tools/adb')
with (root/'qa/certificate-process-restart.log').open('w') as log:
    subprocess.run([str(root/'gradlew'),':app:assembleRelease',':app:assembleReleaseAndroidTest','-PqaInstall=true','-PtestBuildType=release','--console=plain'],cwd=root,env=env,stdout=log,stderr=subprocess.STDOUT,check=True)
    for path in ['app/build/outputs/apk/release/app-release.apk','app/build/outputs/apk/androidTest/release/app-release-androidTest.apk']:
        subprocess.run([adb,'install','-r',str(root/path)],stdout=log,stderr=subprocess.STDOUT,check=True)
    for phase in ['prepare','verify']:
        subprocess.run([adb,'shell','am','force-stop','tw.techtarian.browser.qa'],check=True)
        result=subprocess.run([adb,'shell','am','instrument','-w','-e','class','tw.techtarian.browser.CertificateProcessRestartTest','-e','certificateRestartPhase',phase,'tw.techtarian.browser.qa.test/androidx.test.runner.AndroidJUnitRunner'],text=True,capture_output=True,check=True)
        log.write(phase+'\n'+result.stdout+result.stderr);log.flush()
        if 'OK (1 test)' not in result.stdout: raise RuntimeError('Process restart test failed; see QA log')
print('Persistent exception verified across separate Android processes.')
