#!/usr/bin/env python3
"""Publish only verified, same-commit release files; never overwrite published assets."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
REPO = 'Coyoter/chengjing-browser'


def gh(*args, optional=False):
    p = subprocess.run(['gh', *args], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode:
        if optional and ('HTTP 404' in p.stderr or 'Not Found' in p.stderr):
            return None
        raise RuntimeError('GitHub operation failed: ' + p.stderr.strip())
    return p.stdout


def api(path, optional=False):
    value = gh('api', f'repos/{REPO}/{path}', optional=optional)
    return None if value is None else json.loads(value)


def find_release(tag):
    """Tag lookup returns published releases only; the collection also includes drafts."""
    matches = []
    page = 1
    while True:
        rows = api(f'releases?per_page=100&page={page}')
        matches.extend(row for row in rows if row['tag_name'] == tag)
        if len(rows) < 100:
            break
        page += 1
    if len(matches) > 1:
        raise RuntimeError('Multiple releases use the same tag; refusing an ambiguous update.')
    return matches[0] if matches else None


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
    if not out.is_dir() or sorted(p.name for p in out.iterdir()) != sorted(expected):
        raise RuntimeError('Release output must contain exactly APK, AAB, source, build metadata and checksums.')
    build = json.loads((out / f'{prefix}-BUILD.json').read_text())
    if build['commit'] != sha or build['version'] != version or build['packageName'] != 'tw.techtarian.browser':
        raise RuntimeError('Build metadata does not match this commit.')
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
    existing = find_release(tag)
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
        existing = json.loads(gh('api', '--method', 'POST', f'repos/{REPO}/releases',
                                 '-f', f'tag_name={tag}', '-f', f'target_commitish={sha}',
                                 '-f', f'name=ChengJing Browser {version}',
                                 '-f', f'body={notes}', '-F', 'draft=true'))
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
            gh('release', 'upload', tag, str(out / name), '--repo', REPO)
    final = api(f'releases/{release_id}')
    remote = {a['name']: a for a in final.get('assets', [])}
    if set(remote) != set(expected) or any(remote[n].get('digest') != 'sha256:' + hashes[n] or remote[n]['state'] != 'uploaded' for n in expected):
        raise RuntimeError('Remote asset verification failed; release remains a draft.')
    if api('git/ref/heads/main')['object']['sha'] != sha:
        raise RuntimeError('main changed during upload; release remains a draft.')
    if final['draft']:
        gh('api', '--method', 'PATCH', f'repos/{REPO}/releases/{release_id}',
           '-F', 'draft=false', '-f', 'make_latest=true')
    final = api(f'releases/{release_id}')
    if final['draft'] or api(f'git/ref/tags/{tag}')['object']['sha'] != sha:
        raise RuntimeError('Final publication verification failed.')
    print(final['html_url'])
    print('Published and verified: ' + ', '.join(expected))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        sys.exit(str(error))
