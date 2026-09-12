#!/usr/bin/env python3
"""Build with the private signing identity; never print signing secrets."""
import os,pathlib,subprocess,shutil
root=pathlib.Path(__file__).resolve().parents[1]
secret=root/'signing/password'
if not secret.exists(): raise SystemExit('Missing local signing/password; restore the private signing folder before building.')
env=dict(os.environ,JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home',CHENGJING_BROWSER_STORE_PASSWORD=secret.read_text().strip())
subprocess.run([str(root/'gradlew'),':app:assembleRelease','--console=plain'],cwd=root,env=env,check=True)
target=root/'release/ChengJing-Browser-0.1.3-Android.apk'
shutil.copy2(root/'app/build/outputs/apk/release/app-release.apk',target)
print(target)
