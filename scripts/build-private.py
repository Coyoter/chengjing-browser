#!/usr/bin/env python3
"""Build both release packages using the existing identity, without printing secrets."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
EXPECTED_CERT = 'ab9d27ce9722e34f946556ee015414054bae2893ffdd57ff38126ff4c5d04cf0'


def run(args, env, capture=False):
    return subprocess.run([str(a) for a in args], cwd=ROOT, env=env, check=True,
                          stdout=subprocess.PIPE if capture else None,
                          stderr=subprocess.PIPE if capture else None).stdout


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def main():
    env = os.environ.copy()
    config = (ROOT / 'app/build.gradle.kts').read_text()
    version = re.search(r'\bversionName\s*=\s*"(\d+\.\d+\.\d+)"', config).group(1)
    code = int(re.search(r'\bversionCode\s*=\s*(\d+)', config).group(1))
    if env.get('EXPECTED_VERSION', version) != version:
        raise RuntimeError('Requested version does not match app/build.gradle.kts.')
    if run(['git', 'status', '--porcelain', '--untracked-files=no'], env, True).strip():
        raise RuntimeError('Tracked files have local changes; commit them before building a release.')
    sha = run(['git', 'rev-parse', 'HEAD'], env, True).decode().strip()
    keystore = Path(env.get('CHENGJING_BROWSER_KEYSTORE_PATH', str(ROOT / 'signing/browser.jks'))).resolve()
    password_file = ROOT / 'signing/password'
    password = env.get('CHENGJING_BROWSER_STORE_PASSWORD')
    if not password and password_file.is_file():
        password = password_file.read_text().strip()
    if not keystore.is_file() or not password:
        raise RuntimeError('Original release keystore/password missing. No debug-key fallback is allowed.')
    env['CHENGJING_BROWSER_STORE_PASSWORD'] = password
    env['CHENGJING_BROWSER_KEYSTORE_PATH'] = str(keystore)
    if not env.get('JAVA_HOME'):
        studio = Path('/Applications/Android Studio.app/Contents/jbr/Contents/Home')
        if studio.is_dir():
            env['JAVA_HOME'] = str(studio)
    if env.get('JAVA_HOME'):
        env['PATH'] = str(Path(env['JAVA_HOME']) / 'bin') + os.pathsep + env.get('PATH', '')
    sdk = Path(env.get('ANDROID_HOME') or env.get('ANDROID_SDK_ROOT') or str(Path.home() / 'Library/Android/sdk'))
    tools = sdk / 'build-tools/36.0.0'
    if not (tools / 'apksigner').is_file():
        raise RuntimeError('Android SDK build-tools 36.0.0 is required.')
    cert = run(['keytool', '-exportcert', '-keystore', keystore, '-alias', 'chengjing-browser',
                '-storepass:env', 'CHENGJING_BROWSER_STORE_PASSWORD'], env, True)
    if hashlib.sha256(cert).hexdigest() != EXPECTED_CERT:
        raise RuntimeError('Signing certificate differs from the existing published browser identity.')
    out = ROOT / 'release' / version
    if out.exists():
        raise RuntimeError('Output directory already exists: ' + str(out) + '; existing packages will not be overwritten.')
    print(f'Building ChengJing Browser {version} ({code}) from {sha}', flush=True)
    run([ROOT / 'gradlew', '--no-daemon', '--console=plain', ':app:testReleaseUnitTest',
         ':app:lintRelease', ':app:assembleRelease', ':app:bundleRelease'], env)
    apk = ROOT / 'app/build/outputs/apk/release/app-release.apk'
    aab = ROOT / 'app/build/outputs/bundle/release/app-release.aab'
    badging = run([tools / 'aapt', 'dump', 'badging', apk], env, True).decode()
    package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    if not package or package.groups() != ('tw.techtarian.browser', str(code), version) or 'application-debuggable' in badging:
        raise RuntimeError('APK identity, version, or debug flag is incorrect.')
    checked = run([tools / 'apksigner', 'verify', '--verbose', '--print-certs', apk], env, True).decode()
    certs = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)', checked)
    if [x.lower() for x in certs] != [EXPECTED_CERT]:
        raise RuntimeError('APK signing certificate verification failed.')
    run([tools / 'zipalign', '-c', '-P', '16', '-v', '4', apk], env, True)
    verified = run(['jarsigner', '-J-Duser.language=en', '-verify', aab], env, True).decode()
    if 'jar verified.' not in verified:
        raise RuntimeError('AAB signature verification failed.')
    bundle_cert = run(['keytool', '-J-Duser.language=en', '-printcert', '-jarfile', aab], env, True).decode()
    bundle_fingerprints = re.findall(r'SHA256:\s*([0-9A-Fa-f:]+)', bundle_cert)
    if not bundle_fingerprints or any(x.replace(':', '').lower() != EXPECTED_CERT for x in bundle_fingerprints):
        raise RuntimeError('AAB signing certificate differs from the published identity.')
    out.mkdir(parents=True)
    prefix = f'ChengJing-Browser-{version}'
    shutil.copy2(apk, out / f'{prefix}-Android.apk')
    shutil.copy2(aab, out / f'{prefix}-GooglePlay.aab')
    run(['git', 'archive', '--format=zip', f'--prefix={prefix}/', '-o', out / f'{prefix}-Source.zip', sha], env)
    metadata = {'version': version, 'versionCode': code, 'commit': sha,
                'packageName': 'tw.techtarian.browser', 'certificateSha256': EXPECTED_CERT,
                'releaseChecks': ['testReleaseUnitTest', 'lintRelease', 'APK signature', 'APK 16 KB zip alignment', 'AAB signature'],
                'physicalDeviceOrLiveGoogleDriveAcceptance': False}
    (out / f'{prefix}-BUILD.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n')
    rows = [f'{digest(p)}  {p.name}' for p in sorted(out.iterdir()) if p.is_file()]
    (out / f'{prefix}-SHA256SUMS.txt').write_text('\n'.join(rows) + '\n')
    print('Verified release packages: ' + str(out), flush=True)


if __name__ == '__main__':
    try:
        main()
    except subprocess.CalledProcessError as error:
        sys.exit(f'Build/verification command failed (exit {error.returncode}); nothing was published.')
    except Exception as error:
        sys.exit(str(error))
