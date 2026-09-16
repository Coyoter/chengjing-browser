"""Offline regression tests: draft lookup, stable IDs, and idempotent publication."""
import contextlib
import copy
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('publisher', Path(__file__).with_name('publish-release.py'))
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)
SHA = 'a' * 40
TAG = 'v1.3.0'


class ReleaseLookupTests(unittest.TestCase):
    def test_draft_is_found_in_release_collection(self):
        draft = {'id': 91, 'tag_name': TAG, 'draft': True}
        with patch.object(publisher, 'api', return_value=[draft]) as api:
            self.assertEqual(draft, publisher.find_release(TAG))
            api.assert_called_once_with('releases?per_page=100&page=1')

    def test_published_release_is_also_found(self):
        release = {'id': 91, 'tag_name': TAG, 'draft': False}
        with patch.object(publisher, 'api', return_value=[release]):
            self.assertEqual(release, publisher.find_release(TAG))

    def test_missing_release_is_none(self):
        with patch.object(publisher, 'api', return_value=[]):
            self.assertIsNone(publisher.find_release(TAG))

    def test_duplicate_tags_are_rejected(self):
        with patch.object(publisher, 'api', return_value=[{'tag_name': TAG}, {'tag_name': TAG}]):
            with self.assertRaises(RuntimeError):
                publisher.find_release(TAG)

    def test_pagination_can_find_later_drafts(self):
        unrelated = [{'tag_name': f'v0.0.{i}'} for i in range(100)]
        draft = {'id': 92, 'tag_name': TAG, 'draft': True}
        with patch.object(publisher, 'api', side_effect=[unrelated, [draft]]) as api:
            self.assertEqual(draft, publisher.find_release(TAG))
            self.assertEqual(2, api.call_count)

    def test_permission_failure_does_not_mean_missing_release(self):
        with patch.object(publisher, 'api', side_effect=RuntimeError('HTTP 403')):
            with self.assertRaises(RuntimeError):
                publisher.find_release(TAG)


class PublishTests(unittest.TestCase):
    def exercise(self, existing_draft):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'app').mkdir()
            (root / 'app/build.gradle.kts').write_text('versionName = "1.3.0"\n')
            (root / 'docs/releases').mkdir(parents=True)
            (root / 'docs/releases/1.3.0.md').write_text('Test notes')
            out = root / 'release/1.3.0'
            out.mkdir(parents=True)
            prefix = 'ChengJing-Browser-1.3.0'
            for suffix in ('-Android.apk', '-GooglePlay.aab', '-Source.zip'):
                (out / (prefix + suffix)).write_bytes(b'offline test fixture')
            metadata = {'version': '1.3.0', 'commit': SHA, 'packageName': 'tw.techtarian.browser'}
            (out / (prefix + '-BUILD.json')).write_text(json.dumps(metadata))
            lines = [f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}' for p in sorted(out.iterdir())]
            (out / (prefix + '-SHA256SUMS.txt')).write_text('\n'.join(lines) + '\n')
            release = {'id': 91, 'tag_name': TAG, 'target_commitish': SHA, 'draft': True,
                       'assets': [], 'html_url': 'https://github.com/example/release'}
            state = {'release': copy.deepcopy(release) if existing_draft else None, 'posts': 0, 'patches': 0, 'uploads': 0}

            def api(path, optional=False):
                if path == 'git/ref/heads/main' or path == f'git/ref/tags/{TAG}':
                    return {'object': {'type': 'commit', 'sha': SHA}}
                if path == 'releases?per_page=100&page=1':
                    return [copy.deepcopy(state['release'])] if state['release'] else []
                if path == 'releases/91':
                    return copy.deepcopy(state['release'])
                raise AssertionError('Unexpected endpoint (draft must use ID): ' + path)

            def gh(*args, optional=False):
                if args[:2] == ('release', 'upload'):
                    path = Path(args[3])
                    state['uploads'] += 1
                    state['release']['assets'].append({'name': path.name, 'size': path.stat().st_size,
                        'state': 'uploaded', 'digest': 'sha256:' + hashlib.sha256(path.read_bytes()).hexdigest()})
                    return ''
                if args[:3] == ('api', '--method', 'POST'):
                    self.assertEqual(f'repos/{publisher.REPO}/releases', args[3])
                    state['posts'] += 1
                    state['release'] = copy.deepcopy(release)
                    return json.dumps(state['release'])
                if args[:3] == ('api', '--method', 'PATCH'):
                    self.assertEqual(f'repos/{publisher.REPO}/releases/91', args[3])
                    state['patches'] += 1
                    state['release']['draft'] = False
                    return json.dumps(state['release'])
                raise AssertionError('Unexpected mutation: ' + repr(args))

            with patch.object(publisher, 'ROOT', root), patch.object(publisher, 'api', side_effect=api), \
                 patch.object(publisher, 'gh', side_effect=gh), \
                 patch.object(publisher.subprocess, 'check_output', return_value=SHA + '\n'), \
                 patch.dict(publisher.os.environ, {'EXPECTED_VERSION': '1.3.0'}), contextlib.redirect_stdout(io.StringIO()):
                publisher.main()
                self.assertFalse(state['release']['draft'])
                self.assertEqual(5, state['uploads'])
                self.assertEqual(1, state['patches'])
                self.assertEqual(0 if existing_draft else 1, state['posts'])
                publisher.main()
                self.assertEqual(5, state['uploads'])
                self.assertEqual(1, state['patches'])

    def test_existing_draft_publishes_by_id_and_rerun_is_read_only(self):
        self.exercise(True)

    def test_new_draft_uses_post_response_id_and_rerun_is_read_only(self):
        self.exercise(False)


if __name__ == '__main__':
    unittest.main()
