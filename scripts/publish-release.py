#!/usr/bin/env python3
"""Publish only verified, same-commit release files; never overwrite published assets."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(os.environ.get('CHENGJING_RELEASE_SOURCE_ROOT', str(Path(__file__).resolve().parents[1]))).resolve()
REPO = 'Coyoter/chengjing-browser'


def gh(*args, optional=False, input_data=None):
    p = subprocess.run(['gh', *args], cwd=ROOT, text=True, input=input_data,
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode:
        if optional and ('HTTP 404' in p.stderr or 'Not Found' in p.stderr):
            return None
        raise RuntimeError('GitHub operation failed: ' + p.stderr.strip())
    return p.stdout


def api(path, optional=False, method='GET', payload=None):
    args = ['api', '--method', method, f'repos/{REPO}/{path}']
    if payload is not None:
        args += ['--input', '-']
    value = gh(*args, optional=optional,
               input_data=json.dumps(payload) if payload is not None else None)
    return None if value is None else json.loads(value)


def release_by_tag(tag):
    # Enumerate authenticated releases (including drafts) and retain their numeric ID.
    matches = []
    for page in range(1, 101):
        rows = api(f'releases?per_page=100&page={page}')
        if not isinstance(rows, list):
            raise RuntimeError('Unexpected release-list response.')
        matches.extend(row for row in rows if row.get('tag_name') == tag)
        if len(rows) < 100:
            if len(matches) > 1:
                raise RuntimeError('Multiple releases use this tag; refusing to guess.')
            return api(f'releases/{matches[0]["id"]}') if matches else None
    raise RuntimeError('Release enumeration exceeded its safety limit.')


def main():
    config = (ROOT / 'app/build.gradle.kts').read_text()
    version = re.search(r'\bversionName\s*=\s*"(\d+\.\d+\.\d+)"', config).group(1)
    sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    if os.environ.get('EXPECTED_VERSION', version) != version:
        raise RuntimeError('Requested version differs from build version.')
    if api('git/ref/heads/main')['object']['sha'] != sha:
        raise RuntimeError('main moved after checkout; refusing to publish a stale build.')
    prefix = f'ChengJing-Browser-{version}'
    out = ROOT / 'release' / version
    expected = [f'{prefix}-Android.apk', f'{prefix}-GooglePlay.aab', f'{prefix}-Source.zip',
                f'{prefix}-BUILD.json', f'{prefix}-SHA256SUMS.txt']
    if tuple(map(int, version.split('.'))) >= (1, 4, 2):
        expected.append(f'{prefix}-R8.zip')
    if not out.is_dir() or sorted(p.name for p in out.iterdir()) != sorted(expected):
        raise RuntimeError('Release output does not match the exact expected package/evidence list.')
    build = json.loads((out / f'{prefix}-BUILD.json').read_text())
    if build['commit'] != sha or build['version'] != version or build['packageName'] != 'tw.techtarian.browser':
        raise RuntimeError('Build metadata does not match this commit.')
    if f'{prefix}-R8.zip' in expected and build.get('r8', {}).get('obfuscationPercent', 0) < 25:
        raise RuntimeError('Required R8 verification is missing.')
    hashes = {}
    for name in expected:
        path = out / name
        h = hashlib.sha256()
        with path.open('rb') as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                h.update(chunk)
        hashes[name] = h.hexdigest()
    lines = (out / f'{prefix}-SHA256SUMS.txt').read_text().splitlines()
    manifest = dict((name, value) for value, name in (line.split('  ', 1) for line in lines))
    wanted = {k: v for k, v in hashes.items() if not k.endswith('-SHA256SUMS.txt')}
    if manifest != wanted:
        raise RuntimeError('Checksum manifest mismatch.')
    tag = f'v{version}'
    existing = release_by_tag(tag)
    ref = api(f'git/ref/tags/{tag}', optional=True)
    if ref and (ref['object']['type'] != 'commit' or ref['object']['sha'] != sha):
        raise RuntimeError('Existing tag does not point at the build commit; no tags were moved.')
    if existing and existing.get('target_commitish') != sha:
        raise RuntimeError('Existing release belongs to another commit; it will not be modified.')
    if not ref:
        gh('api', '--method', 'POST', f'repos/{REPO}/git/refs', '-f', f'ref=refs/tags/{tag}', '-f', f'sha={sha}')
    if not existing:
        notes = (ROOT / f'docs/releases/{version}.md').read_text()
        notes += f'\n\nBuild commit: `{sha}`\n'
        existing = api('releases', method='POST', payload={
            'tag_name': tag, 'target_commitish': sha,
            'name': f'ChengJing Browser {version}', 'body': notes, 'draft': True,
        })
    release_id = existing['id']
    remote = {a['name']: a for a in existing.get('assets', [])}
    if set(remote) - set(expected):
        raise RuntimeError('Unexpected existing release assets; nothing will be deleted.')
    for name in expected:
        asset = remote.get(name)
        if asset:
            if asset.get('digest') != 'sha256:' + hashes[name] or asset['size'] != (out / name).stat().st_size or asset['state'] != 'uploaded':
                raise RuntimeError('Existing asset differs from this build; refusing to overwrite: ' + name)
        else:
            if not existing['draft']:
                raise RuntimeError('Published release is incomplete; refusing to mutate it.')
            gh('api', '--method', 'POST',
               f'https://uploads.github.com/repos/{REPO}/releases/{release_id}/assets?name={name}',
               '--header', 'Content-Type: application/octet-stream', '--input', str(out / name))
    final = api(f'releases/{release_id}')
    remote = {a['name']: a for a in final.get('assets', [])}
    if set(remote) != set(expected) or any(remote[n].get('digest') != 'sha256:' + hashes[n] or remote[n]['state'] != 'uploaded' for n in expected):
        raise RuntimeError('Remote asset verification failed; release remains a draft.')
    if api('git/ref/heads/main')['object']['sha'] != sha:
        raise RuntimeError('main changed during upload; release remains a draft.')
    if final['draft']:
        api(f'releases/{release_id}', method='PATCH', payload={'draft': False, 'make_latest': 'true'})
    final = api(f'releases/{release_id}')
    if final['draft'] or final['tag_name'] != tag or api(f'git/ref/tags/{tag}')['object']['sha'] != sha:
        raise RuntimeError('Final publication verification failed.')
    print(final['html_url'])
    print('Published and verified: ' + ', '.join(expected))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        sys.exit(str(error))
