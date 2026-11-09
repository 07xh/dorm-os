import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {
 classifyD1Preflight,requireReadonlyPreflightContext
} from './audit-task-graph-d1-production.mjs';

assert.equal(classifyD1Preflight('missing'),'migration_pending',
 'a fresh 0019 gap should be reported, not filled automatically');
assert.equal(classifyD1Preflight('ready'),'already_ready');
for(const invalid of ['partial','invalid','',undefined,null]){
 assert.throws(()=>classifyD1Preflight(invalid),
  /task_graph_schema_partial_manual_review_required/);
}
assert.equal(requireReadonlyPreflightContext(
 '07xh/dorm-os','refs/heads/main','workflow_dispatch'),true);
for(const [repository,ref,event] of [
 ['other/dorm-os','refs/heads/main','workflow_dispatch'],
 ['07xh/dorm-os','refs/heads/dev','workflow_dispatch'],
 ['07xh/dorm-os','refs/heads/main','push'],
 ['07xh/dorm-os','refs/heads/main','workflow_run'],
 ['07xh/dorm-os','refs/pull/432/merge','pull_request']
]){
 assert.throws(()=>requireReadonlyPreflightContext(repository,ref,event),
  /readonly_preflight_main_manual_only/);
}
const script=readFileSync(new URL('./audit-task-graph-d1-production.mjs',import.meta.url),'utf8');
const workflow=readFileSync(new URL('../.github/workflows/audit-task-graph-d1.yml',import.meta.url),'utf8');
assert.match(script,/inspectTaskGraphSchema\(db\)/);
assert.match(script,/verifyTaskGraphIntegrity\(db\)/);
assert.match(script,/validateRecoveryBookmark\(result\)/);
assert.match(script,/time_travel\/bookmark\?timestamp=/);
assert.doesNotMatch(script,/\bensureTaskGraphSchema\s*\(/,
 'the preflight must never call the migration function');
assert.doesNotMatch(script,/\b(localWrangler|spawnSync|writeFileSync|updateRef)\s*\(/,
 'the preflight must only reuse read-only helpers');
assert.doesNotMatch(script,/\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE TABLE)\b/i,
 'the preflight must not contain mutating SQL');
assert.match(workflow,/workflow_dispatch:/);
assert.match(workflow,/github\.repository == '07xh\/dorm-os'/);
assert.match(workflow,/github\.ref == 'refs\/heads\/main'/);
assert.match(workflow,/node scripts\/audit-task-graph-d1-production\.mjs/);
assert.doesNotMatch(workflow,/node scripts\/ensure-task-dependency-schema\.mjs/);
assert.doesNotMatch(workflow,/wrangler.*(?:execute|deploy|publish)/i,
 'the read-only workflow must not run D1 writes or deploy Pages');
assert.doesNotMatch(workflow,/approve_task_dependency_migration/,
 'the audit workflow must not accept a migration approval');
console.log('Read-only production D1 preflight: branch/event security, missing/ready/partial, audit-only workflow PASS');
