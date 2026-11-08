from pathlib import Path
import unittest


class DraftReleaseRegression(unittest.TestCase):
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
