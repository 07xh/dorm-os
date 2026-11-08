"""Publish a locally signed APK after proving it equals a successful main build.
Private signing material never enters Git or the runner. Only a signed public APK
is staged as a Git blob; the promotion commit contains its digest and provenance.
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

PIN = '6E522748F664C5090CE02A502140ADB97B269D547058AD110C49958F0E5B8E80'
APP = 'io.github.x07xh.xiaoxu'
REPO = os.environ['GITHUB_REPOSITORY']
HEAD = os.environ['GITHUB_SHA']
assert REPO == '07xh/dorm-os' and os.environ['GITHUB_REF'] == 'refs/heads/main'

def call(*args):
    return subprocess.check_output(args, text=True).strip()

def api(path):
    return json.loads(call('gh', 'api', f'repos/{REPO}/{path}'))

def sha(data):
    return hashlib.sha256(data).hexdigest()

def require_absent(path):
    result = subprocess.run(['gh','api','--include',f'repos/{REPO}/{path}'],capture_output=True,text=True)
    assert result.returncode != 0, 'Existing release/tag must not be overwritten'
    status = re.search(r'^HTTP/\S+ (\d+)\b',result.stdout,re.M)
    assert status and status[1] == '404', 'Unable to prove release/tag absence'

# The push must add exactly one promotion request, never alter application code.
changed = call('git','diff','--name-only','HEAD^',HEAD).splitlines()
assert len(changed) == 1 and re.fullmatch(r'\.github/xiaoxu-release/promotions/0\.8\.14\.json',changed[0])
m = json.loads(Path(changed[0]).read_text())
assert set(m) == {'source_commit','build_run','artifact_id','apk_blob','apk_sha256','version','version_code'}
assert m['version'] == '0.8.14' and m['version_code'] == 35
assert re.fullmatch('[0-9a-f]{40}',m['source_commit'])
assert re.fullmatch('[0-9a-f]{40}',m['apk_blob']) and re.fullmatch('[0-9a-f]{64}',m['apk_sha256'])
assert type(m['build_run']) is int and type(m['artifact_id']) is int
source = m['source_commit']
assert call('git','diff','--name-only',source,HEAD).splitlines() == changed
assert api('git/ref/heads/main')['object']['sha'] == HEAD
run = api(f'actions/runs/{m["build_run"]}')
assert run['head_sha'] == source and run['head_branch'] == 'main' and run['event'] == 'push'
assert run['name'] == 'Build Xiaoxu Android' and run['conclusion'] == 'success'
assert run['repository']['full_name'] == REPO
ci = json.loads(call('gh','run','list','--repo',REPO,'--workflow','check.yml','--commit',source,'--event','push','--json','headSha,status,conclusion'))
assert ci and ci[0]['headSha'] == source and ci[0]['status'] == 'completed' and ci[0]['conclusion'] == 'success'
artifacts = api(f'actions/runs/{m["build_run"]}/artifacts')['artifacts']
artifact = next(a for a in artifacts if a['id'] == m['artifact_id'])
assert artifact['name'] == 'xiaoxu-android-release-unsigned' and not artifact['expired']
assert artifact['workflow_run']['head_sha'] == source
version_source = Path('apps/xiaoxu-android/app/build.gradle').read_text()
assert re.search(r"versionName '([^']+)'",version_source)[1] == m['version']
assert int(re.search(r'versionCode (\d+)',version_source)[1]) == m['version_code']
tag = 'xiaoxu-android-v' + m['version']
require_absent(f'releases/tags/{tag}')
require_absent(f'git/ref/tags/{tag}')

with tempfile.TemporaryDirectory() as temp:
    root = Path(temp)
    out = Path(os.environ['RUNNER_TEMP']) / 'xiaoxu-promoted-release'
    out.mkdir()
    apk = out / f'Xiaoxu-{m["version"]}.apk'
    blob = api('git/blobs/' + m['apk_blob'])
    assert blob['encoding'] == 'base64' and 0 < blob['size'] < 30_000_000
    binary = base64.b64decode(blob['content'])
    assert len(binary) == blob['size'] and sha(binary) == m['apk_sha256']
    apk.write_bytes(binary)
    archive = root / 'unsigned.zip'
    with archive.open('wb') as stream:
        subprocess.run(['gh','api',f'repos/{REPO}/actions/artifacts/{m["artifact_id"]}/zip'],stdout=stream,check=True)
    assert artifact['digest'] == 'sha256:' + sha(archive.read_bytes())
    with zipfile.ZipFile(archive) as z:
        names = [n for n in z.namelist() if n.endswith('/app-release-unsigned.apk') or n == 'app-release-unsigned.apk']
        assert len(names) == 1
        unsigned = root / 'unsigned.apk'
        unsigned.write_bytes(z.read(names[0]))
    # No v1 signatures or added/removed ZIP entries are allowed. Reject duplicates.
    with zipfile.ZipFile(unsigned) as u, zipfile.ZipFile(apk) as s:
        assert len(u.namelist()) == len(set(u.namelist()))
        assert len(s.namelist()) == len(set(s.namelist()))
        assert sorted(u.namelist()) == sorted(s.namelist())
        assert all(u.read(n) == s.read(n) for n in u.namelist())
    sdk = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0'
    signature = call(str(sdk/'apksigner'),'verify','--verbose','--print-certs',str(apk))
    digests = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)',signature)
    assert digests == [PIN.lower()] or digests == [PIN]
    badging = call(str(sdk/'aapt'),'dump','badging',str(apk))
    expected = f"package: name='{APP}' versionCode='{m['version_code']}' versionName='{m['version']}'"
    assert expected in badging and 'application-debuggable' not in badging
    xml = call(str(sdk/'aapt'),'dump','xmltree',str(apk),'AndroidManifest.xml')
    assert not re.search(r'android:(debuggable|testOnly)[^\n]*\(type 0x12\)0xffffffff',xml)
    (out/'apk-signature.txt').write_text(signature)
    (out/'apk-manifest.txt').write_text(badging)
    cert = Path('.github/xiaoxu-release/android-signing-certificate.pem').read_bytes()
    assert call('openssl','x509','-in','.github/xiaoxu-release/android-signing-certificate.pem','-noout','-fingerprint','-sha256').split('=')[1].replace(':','') == PIN
    (out/'xiaoxu-release-certificate.pem').write_bytes(cert)
    (out/'release-manifest.json').write_text(json.dumps(dict(version=m['version'],versionCode=m['version_code'],applicationId=APP,sourceCommit=source,promotionCommit=HEAD,certificateSha256=PIN,apkSha256=m['apk_sha256'],buildRun=m['build_run'],artifactId=m['artifact_id']),indent=2))
    files = sorted(out.iterdir())
    (out/'SHA256SUMS').write_text(''.join(f'{sha(p.read_bytes())}  {p.name}\n' for p in files))
    assert api('git/ref/heads/main')['object']['sha'] == HEAD
    subprocess.run(['gh','api','--method','POST',f'repos/{REPO}/git/refs','-f',f'ref=refs/tags/{tag}','-f',f'sha={source}'],check=True,stdout=subprocess.DEVNULL)
    assert api(f'git/ref/tags/{tag}')['object']['sha'] == source
    subprocess.run(['gh','release','create',tag,'--repo',REPO,'--verify-tag','--draft','--title','校序 '+m['version'],'--notes-file','.github/xiaoxu-release/notes.md'],check=True)
    for p in sorted(out.iterdir()):
        subprocess.run(['gh','release','upload',tag,str(p),'--repo',REPO],check=True)
    asset = next(a for a in api(f'releases/tags/{tag}')['assets'] if a['name'] == apk.name)
    assert asset['digest'] == 'sha256:' + m['apk_sha256']
    assert api('git/ref/heads/main')['object']['sha'] == HEAD
    subprocess.run(['gh','release','edit',tag,'--repo',REPO,'--draft=false','--prerelease=false','--latest'],check=True)
