#!/usr/bin/env python3
"""Check the actual AAB and retain its own mapping, never a debug or another version's map."""
import hashlib
import json
from pathlib import Path
import re
import sys
import zipfile

R8_PATH = 'BUNDLE-METADATA/com.android.tools/r8.json'
MAP_PATH = 'BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map'


def verify_bundle(aab: Path, mapping_dir: Path) -> dict:
    mapping = (mapping_dir / 'mapping.txt').read_bytes()
    if not mapping or b'# compiler: R8' not in mapping:
        raise RuntimeError('R8 mapping is absent or has no compiler identity.')
    config = (mapping_dir / 'configuration.txt').read_text()
    if re.search(r'^\s*-(dontobfuscate|dontshrink|dontoptimize|ignorewarnings)\b', config, re.M):
        raise RuntimeError('An optimization bypass was found in the merged R8 configuration.')
    with zipfile.ZipFile(aab) as bundle:
        r8 = json.loads(bundle.read(R8_PATH))
        if bundle.read(MAP_PATH) != mapping:
            raise RuntimeError('AAB embedded mapping differs from this build mapping.')
        options = r8['options']
        for flag in ('isObfuscationEnabled', 'isOptimizationsEnabled', 'isShrinkingEnabled'):
            if options.get(flag) is not True:
                raise RuntimeError('The actual AAB has disabled ' + flag)
        if options.get('isDebugModeEnabled') is not False:
            raise RuntimeError('R8 output is not release-mode code.')
        version = r8.get('version', '')
        numbers = tuple(map(int, version.split('.')))
        if numbers < (9, 1, 29):
            raise RuntimeError('Kotlin 2.4 requires R8 9.1.29 or newer, found ' + version)
        if r8.get('resourceOptimization', {}).get('isOptimizedShrinkingEnabled') is not True:
            raise RuntimeError('Optimized resource shrinking is absent.')
        stats = r8['stats']
        protection = {}
        for raw, label in [('noObfuscationPercentage', 'obfuscationPercent'),
                           ('noOptimizationPercentage', 'optimizationPercent'),
                           ('noShrinkingPercentage', 'shrinkingPercent')]:
            value = stats[raw]
            if isinstance(value, bool) or not isinstance(value, (int, float)) or not 0 <= value <= 100:
                raise RuntimeError('Invalid compiler statistics: ' + raw)
            protection[label] = round(100 - value, 2)
            if protection[label] < 25:
                raise RuntimeError(label + ' is below the screenshot threshold in this AAB.')
        dex = {i.filename: bundle.read(i.filename) for i in bundle.infolist() if i.filename.endswith('.dex')}
        if not dex:
            raise RuntimeError('No DEX found in AAB.')
        checksums = {hashlib.sha256(data).hexdigest() for data in dex.values()}
        recorded = {item['checksum'] for item in r8['dexFiles']}
        if checksums != recorded:
            raise RuntimeError('R8 metadata does not match the packaged DEX.')
    classes = re.findall(r'^([^\s].*?) -> ([^\s]+):$', mapping.decode(), re.M)
    app_classes = [(old, new) for old, new in classes if old.startswith('tw.techtarian.browser.')]
    renamed = sum(old != new for old, new in app_classes)
    if not app_classes or renamed == 0:
        raise RuntimeError('No application class renaming was verified.')
    return {'compiler': 'R8', 'compilerVersion': version, **protection,
            'source': R8_PATH, 'playConsoleReassessment': 'Required after uploading this AAB',
            'appMappedClasses': len(app_classes), 'appRenamedClasses': renamed,
            'dexBytes': sum(len(data) for data in dex.values()), 'dexFiles': len(dex),
            'mappingSha256': hashlib.sha256(mapping).hexdigest(),
            'optimizedResourceShrinking': True, 'nativeInferenceFullyExercised': False}


def archive_evidence(aab: Path, mapping_dir: Path, report: dict, destination: Path):
    with zipfile.ZipFile(aab) as bundle, zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name in ('mapping.txt', 'configuration.txt', 'seeds.txt', 'usage.txt', 'resources.txt'):
            path = mapping_dir / name
            if path.is_file():
                archive.writestr(name, path.read_bytes())
        archive.writestr('r8.json', bundle.read(R8_PATH))
        archive.writestr('verification.json', json.dumps(report, ensure_ascii=False, indent=2) + '\n')


if __name__ == '__main__':
    try:
        aab = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('app/build/outputs/bundle/release/app-release.aab')
        mapping_dir = Path('app/build/outputs/mapping/release')
        report = verify_bundle(aab, mapping_dir)
        Path('validation-r8').mkdir(exist_ok=True)
        Path('validation-r8/verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps(report, ensure_ascii=False, indent=2))
    except Exception as error:
        sys.exit('R8 verification failed: ' + str(error))
