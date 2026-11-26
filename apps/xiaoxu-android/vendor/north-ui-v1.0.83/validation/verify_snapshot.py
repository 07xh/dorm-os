from pathlib import Path
import hashlib
import json

root = Path(__file__).resolve().parent.parent
expected = {}
for manifest in root.glob('*-manifest.json'):
    data = json.loads(manifest.read_text(encoding='utf-8'))
    assert data['commit'] == 'e3f2833a2fb1e184968724ee4c7f71ebc816a2dd', manifest
    for item in data['files']:
        path = item['path']
        sha = item['blob']
        assert path not in expected or expected[path] == sha, path
        expected[path] = sha
for path, sha in expected.items():
    content = (root / path).read_bytes()
    actual = hashlib.sha1(b'blob ' + str(len(content)).encode() + b'\0' + content).hexdigest()
    assert actual == sha, (path, sha, actual)
actual_paths = {str(p.relative_to(root)) for p in (root / 'app').rglob('*') if p.is_file()}
actual_paths.update(str(p.relative_to(root)) for p in (root / 'pending-business-adapters').rglob('*') if p.is_file())
actual_paths.add('LICENSE')
assert actual_paths == set(expected), (actual_paths - set(expected), set(expected) - actual_paths)
print(f'Verified {len(expected)} unchanged upstream files.')
