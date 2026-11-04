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

// A public notice archive commit already triggers the trusted main CI -> Pages
// workflow_run path. Do not also issue workflow_dispatch (which skips that
// exact CI-success trigger and causes a redundant production deployment).
const notices=readFileSync(new URL('../.github/workflows/collect-ncwu-notices.yml',import.meta.url),'utf8');
assert.ok(!notices.includes('gh workflow run deploy-cloudflare.yml'),
 'NCWU notice collector must not directly dispatch another Pages deploy');
assert.ok(!/^\s*actions:\s*write\s*$/m.test(notices),
 'notice collector no longer needs privileged Actions write permission');
assert.ok(!notices.includes('id: archive')&&!notices.includes('$GITHUB_OUTPUT'),
 'dead notice collector output wiring should remain removed');
assert.match(notices,/git push origin HEAD:main/,
 'verified public archive still commits changes that trigger main CI');
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

console.log('Deploy workflow: trusted main CI success, exact SHA, manual fallback E2E and fail-closed gating passed');
