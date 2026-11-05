import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const yaml=readFileSync(new URL('../.github/workflows/deploy-cloudflare.yml',import.meta.url),'utf8');
const verify=readFileSync(new URL('./verify-release.mjs',import.meta.url),'utf8');
const stamp=readFileSync(new URL('./stamp-release.mjs',import.meta.url),'utf8');
assert.match(yaml,/workflow_run:\s*\n\s*workflows:\s*\["DORM OS CI"\]/,
 'Pages release must be triggered by complete main CI, not simultaneous pushes');
assert.match(yaml,/types:\s*\[completed\]/);
assert.match(yaml,/github\.event\.workflow_run\.conclusion == 'success'/);
assert.match(yaml,/github\.event\.workflow_run\.event == 'push'/);
assert.match(yaml,/github\.event\.workflow_run\.head_branch == 'main'/);
assert.match(yaml,/github\.event\.workflow_run\.head_repository\.full_name == github\.repository/,
 'untrusted same-name fork branches must not acquire deployment secrets');
assert.match(yaml,/github\.event_name == 'workflow_dispatch' && github\.ref == 'refs\/heads\/main'/,
 'manual dispatch must only publish main');
assert.match(yaml,/RELEASE_SHA: \$\{\{ github\.event\.workflow_run\.head_sha \|\| github\.sha \}\}/);
assert.equal((yaml.match(/ref:\s*\$\{\{ env\.RELEASE_SHA \}\}/g)||[]).length,2,
 'verification and deployment must checkout the exact CI-passed commit');
assert.match(yaml,/node scripts\/stamp-release\.mjs/,
 'Publishing must stamp the same SHA into HTML, worker and revision');
assert.match(stamp,/process\.env\.RELEASE_SHA/);
assert.match(stamp,/public\/index\.html/);
assert.match(stamp,/public\/sw\.js/);
assert.match(stamp,/public\/revision\.json/);
assert.match(verify,/Canonical bookmark verified/);
assert.match(verify,/name="xiaoxu-release"/);
assert.match(verify,/process\.env\.RELEASE_SHA\|\|process\.env\.GITHUB_SHA/);
assert.match(yaml,/if \[ "\$GITHUB_EVENT_NAME" = "workflow_dispatch" \]; then\s*\n\s*npm run test:e2e/,
 'manual dispatch still needs full E2E before deploying');
assert.ok(yaml.indexOf('needs: verify')>yaml.indexOf('jobs:\n  verify:'));
assert.ok(!/^on:\s*\n\s*push:/m.test(yaml),'must not run duplicate E2E suite directly on push');

// GitHub Actions `GITHUB_TOKEN` pushes do NOT trigger another push workflow.
// The notice collector therefore MUST explicitly workflow_dispatch the trusted
// Pages release after a verified archive commit. Manual Pages dispatch runs its
// own npm check + E2E before publishing, while ordinary pushes use main CI.
const notices=readFileSync(new URL('../.github/workflows/collect-ncwu-notices.yml',import.meta.url),'utf8');
assert.match(notices,/git push origin HEAD:main/,
 'verified notice archive must remain committed to main');
assert.match(notices,/gh workflow run deploy-cloudflare\.yml --ref main/,
 'bot-authored archive pushes need explicit workflow_dispatch to become visible');
assert.match(notices,/if: steps\.archive\.outputs\.changed == 'true'/,
 'only actual notice changes should trigger a Pages deploy');
assert.match(notices,/actions:\s*write/,
 'the collector needs Actions write to dispatch the Pages workflow');
for(const [name,path] of [
 ['one-time D1 bootstrap','../.github/workflows/bootstrap-academic-d1.yml'],
 ['legacy owner auth upgrade','../.github/workflows/upgrade-owner-auth-d1.yml']
]){
 const job=readFileSync(new URL(path,import.meta.url),'utf8');
 assert.match(job,/workflow_dispatch:/,name+' must retain manual recovery');
 assert.ok(!/^on:\s*\n\s*push:/m.test(job),name+' must never deploy on source edits');
 assert.ok(job.includes("github.ref == 'refs/heads/main'"),
  name+' must remain limited to main');
}

// APK packaging is never triggered by routine source pushes. A reviewed,
// intentionally committed request marker is equivalent to an explicit manual
// build request and cannot be induced by unrelated source or web releases.
const androidBuild=readFileSync(new URL('../.github/workflows/build-xiaoxu-android.yml',import.meta.url),'utf8');
const regularCI=readFileSync(new URL('../.github/workflows/check.yml',import.meta.url),'utf8');
assert.match(androidBuild,/on:\s*\n\s*workflow_dispatch:/);
assert.doesNotMatch(androidBuild,/^\s{2}(?:pull_request|schedule):/m,
 'APK packaging must not run automatically for PRs or scheduled jobs');
const expectedBuildTriggers = [
 'on:',
 '  workflow_dispatch:',
 '  push:',
 '    branches: [main]',
 '    paths:',
 "      - '.github/xiaoxu-apk-build-requests/pending/*.txt'"
].join('\n');
assert.ok(androidBuild.includes(expectedBuildTriggers+'\n\npermissions:'),
 'APK source pushes must never trigger builds except an explicit reviewed request marker');
assert.doesNotMatch(androidBuild,/xiaoxu-apk-build-requests\/\*\*/,
 'Build requests must match pending files only, never the whole archive directory');
assert.match(androidBuild,/xiaoxu-apk-build-requests\/pending\/\*\.txt/,
 'Reviewed pending markers must remain an allowed APK build trigger');
assert.match(androidBuild,/assembleDebug/,'Manual build remains available');
assert.match(androidBuild,/assembleDebug :app:testDebugUnitTest/,
 'Explicit Android builds must also run JVM unit tests, not just compile');
assert.match(androidBuild,/unzip -t "\$out\/app-debug\.apk"/,
 'APK archive must be verified before upload');
assert.match(androidBuild,/sha256sum app-debug\.apk > app-debug\.apk\.sha256/,
 'APK checksum must be emitted alongside its artifact');
const dexCommand = "grep -Eq '^classes[0-9]*[.]dex$'";
assert.ok(androidBuild.includes(dexCommand),
 'APK DEX integrity expression must be a complete, quoted shell command');

assert.match(regularCI,/node scripts\/xiaoxu-android-bootstrap-tests\.mjs/,
 'Every PR still validates Android security and contract boundaries');

console.log('Deploy workflow: trusted main CI success, exact SHA, manual fallback E2E and fail-closed gating passed');
