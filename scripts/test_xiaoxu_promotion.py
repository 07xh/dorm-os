from pathlib import Path
import hashlib
import tempfile
import unittest


class DraftReleaseRegression(unittest.TestCase):
    def test_all_assets_must_match_before_publication(self):
        source = Path('scripts/promote-xiaoxu-android.py').read_text()
        block = source[source.index('    assets = api('):source.index('    asset = next(')]
        code = '\n'.join(line[4:] for line in block.splitlines())
        digest = lambda data: hashlib.sha256(data).hexdigest()
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            (out/'APK').write_bytes(b'apk')
            (out/'manifest').write_bytes(b'evidence')
            valid = [{'name': p.name, 'digest': 'sha256:'+digest(p.read_bytes())} for p in out.iterdir()]
            def verify(assets):
                exec(compile(code, '<asset-verification>', 'exec'), dict(
                    api=lambda path: {'assets': assets}, release_id=438227551,
                    out=out, sha=digest))
            verify(valid)
            for invalid in (valid[:-1], valid+[valid[0]],
                            [dict(a, digest='sha256:wrong') for a in valid]):
                with self.assertRaises(AssertionError):
                    verify(invalid)

    def test_draft_publication_uses_release_id_even_when_tag_lookup_is_404(self):
        # Execute the actual publication tail against a GitHub draft that is
        # available by ID but deliberately unavailable through /releases/tags/.
        source = Path('scripts/promote-xiaoxu-android.py').read_text()
        tail = source[source.index('    asset = next('):]
        code = '\n'.join(line[4:] for line in tail.splitlines())
        calls = []
        def api(path):
            calls.append(path)
            if path == 'releases/438227551':
                return {'assets': [{'name': 'Xiaoxu-0.8.14.apk', 'digest': 'sha256:abc'}]}
            if path == 'git/ref/heads/main':
                return {'object': {'sha': 'head'}}
            raise RuntimeError('draft tag lookup returns HTTP 404')
        class Process:
            def run(self, args, **kwargs):
                calls.append(args)
        exec(compile(code, '<publication>', 'exec'), dict(
            api=api, release_id=438227551, tag='xiaoxu-android-v0.8.14',
            apk=Path('Xiaoxu-0.8.14.apk'), m={'apk_sha256': 'abc'},
            REPO='07xh/dorm-os', HEAD='head', subprocess=Process()))
        self.assertIn('releases/438227551', calls)
        self.assertTrue(any(isinstance(c, list) and 'PATCH' in c and
                            'repos/07xh/dorm-os/releases/438227551' in c for c in calls))


if __name__ == '__main__':
    unittest.main()
