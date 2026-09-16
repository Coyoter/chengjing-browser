import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('publisher', Path(__file__).with_name('publish-release.py'))
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)

class ReleaseLookupTests(unittest.TestCase):
    def test_draft_is_found_by_list_then_numeric_id(self):
        draft = {'id': 42, 'tag_name': 'v1.3.0', 'draft': True}
        def api(path, **kwargs):
            self.assertNotIn('releases/tags/', path)
            return [draft] if '?' in path else draft
        with patch.object(publisher, 'api', side_effect=api):
            self.assertEqual(draft, publisher.release_by_tag('v1.3.0'))

    def test_absent_release_is_none(self):
        with patch.object(publisher, 'api', return_value=[]):
            self.assertIsNone(publisher.release_by_tag('v1.3.0'))

    def test_duplicate_tag_fails_closed(self):
        with patch.object(publisher, 'api', return_value=[{'id':1,'tag_name':'v1.3.0'},{'id':2,'tag_name':'v1.3.0'}]):
            with self.assertRaises(RuntimeError):
                publisher.release_by_tag('v1.3.0')

    def test_paginated_draft_lookup(self):
        draft={'id':42,'tag_name':'v1.3.0','draft':True}
        pages=[[{'id':n,'tag_name':f'v0.0.{n}'} for n in range(100)],[draft],draft]
        with patch.object(publisher, 'api', side_effect=pages) as api:
            self.assertEqual(draft,publisher.release_by_tag('v1.3.0'))
            self.assertEqual('releases?per_page=100&page=2',api.call_args_list[1].args[0])

    def test_existing_draft_upload_verify_publish_and_idempotent_retry(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            (root/'app').mkdir()
            (root/'app/build.gradle.kts').write_text('versionName = "1.3.0"\nversionCode = 19\n')
            out=root/'release/1.3.0';out.mkdir(parents=True)
            prefix='ChengJing-Browser-1.3.0'
            for suffix in ['Android.apk','GooglePlay.aab','Source.zip']:
                (out/f'{prefix}-{suffix}').write_bytes(b'test fixture')
            (out/f'{prefix}-BUILD.json').write_text(json.dumps({'commit':'abc','version':'1.3.0','packageName':'tw.techtarian.browser'}))
            hashes={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in out.iterdir()}
            (out/f'{prefix}-SHA256SUMS.txt').write_text(''.join(f'{v}  {n}\n' for n,v in hashes.items()))
            draft={'id':42,'tag_name':'v1.3.0','target_commitish':'abc','draft':True,'assets':[], 'html_url':'fixture'}
            def api(path, **kwargs):
                self.assertNotIn('releases/tags/',path)
                if path.startswith('git/ref/'):
                    return {'object':{'sha':'abc','type':'commit'}}
                if path.startswith('releases?'):
                    return [copy.deepcopy(draft)]
                self.assertEqual('releases/42',path)
                if kwargs.get('method')=='PATCH':
                    self.assertEqual(5,len(draft['assets']))
                    draft['draft']=False
                return copy.deepcopy(draft)
            def gh(*args, **kwargs):
                self.assertEqual(('api','--method','POST'),args[:3])
                self.assertIn('/releases/42/assets?name=',args[3])
                path=Path(args[-1]);name=path.name
                draft['assets'].append({'name':name,'digest':'sha256:'+hashlib.sha256(path.read_bytes()).hexdigest(),'size':path.stat().st_size,'state':'uploaded'})
                return '{}'
            with patch.object(publisher,'ROOT',root),patch.object(publisher,'api',side_effect=api),patch.object(publisher,'gh',side_effect=gh) as upload,patch.object(publisher.subprocess,'check_output',return_value='abc\n'):
                publisher.main()
                self.assertFalse(draft['draft'])
                self.assertEqual(5,upload.call_count)
                publisher.main()
                self.assertEqual(5,upload.call_count)
                draft['assets'][0]['digest']='sha256:bad'
                with self.assertRaises(RuntimeError):
                    publisher.main()
                self.assertEqual(5,upload.call_count)

if __name__=='__main__':
    unittest.main()
