import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {
 classifyTaskDependencySchema,validateD1Bookmark,requireManualMain
} from './audit-nexttodo-d1-production.mjs';

const tableNames=['academic_task_dependencies','academic_task_graph_versions',
 'academic_task_graph_guards'];
const columns={
 academic_task_dependencies:['user_id','task_id','prerequisite_id','created_at'],
 academic_task_graph_versions:['user_id','revision','updated_at'],
 academic_task_graph_guards:['id','user_id','task_id','expected_graph_revision',
  'expected_task_revision']
};
const guards=['trg_academic_task_graph_guard',
 'trg_academic_task_done_prerequisites'];
assert.equal(classifyTaskDependencySchema([]),'migration_pending');
assert.equal(classifyTaskDependencySchema([tableNames[0]]),
 'partial_schema_manual_review');
assert.equal(classifyTaskDependencySchema(tableNames,columns,guards),'already_ready');
assert.equal(classifyTaskDependencySchema(tableNames,columns,guards.slice(0,1)),
 'partial_schema_manual_review');
assert.equal(classifyTaskDependencySchema(tableNames,{
 ...columns,academic_task_graph_versions:['user_id','revision']
},guards),'partial_schema_manual_review');
assert.throws(()=>classifyTaskDependencySchema('not-an-array'),/invalid_schema_response/);
assert.equal(requireManualMain('07xh/dorm-os','refs/heads/main','workflow_dispatch'),true);
for(const trio of [
 ['other/repo','refs/heads/main','workflow_dispatch'],
 ['07xh/dorm-os','refs/heads/feature','workflow_dispatch'],
 ['07xh/dorm-os','refs/heads/main','push'],
 ['07xh/dorm-os','refs/heads/main','workflow_run'],
 ['07xh/dorm-os','refs/pull/999/merge','pull_request']
]){
 assert.throws(()=>requireManualMain(...trio),/readonly_preflight_main_manual_only/);
}
assert.equal(validateD1Bookmark({bookmark:'00000085-0000024c-00004c6d-8e61117bf38d7adb71b934ebbf891683'}),true);
for(const bad of [null,{}, {bookmark:''},{bookmark:'bad'},
 {bookmark:'../secret'}, {bookmark:43}]){
 assert.throws(()=>validateD1Bookmark(bad),/d1_time_travel_bookmark_unavailable/);
}
const source=readFileSync(new URL('./audit-nexttodo-d1-production.mjs',import.meta.url),'utf8');
const workflow=readFileSync(new URL('../.github/workflows/audit-nexttodo-d1.yml',import.meta.url),'utf8');
assert.match(source,/findPagesBoundD1\(project\)/);
assert.match(source,/exactProductionDatabase\(project,listing\)/);
assert.match(source,/PRAGMA foreign_key_check/);
assert.match(source,/PRAGMA quick_check/);
assert.match(source,/time_travel\/bookmark\?timestamp=/);
assert.match(source,/validateD1Bookmark\(bookmark\)/);
assert.doesNotMatch(source,/\b(localWrangler|spawnSync|writeFileSync|execSync)\s*\(/);
assert.doesNotMatch(source,/\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE TABLE)\b/i,
 'read-only audit must not issue SQL mutation');
assert.match(workflow,/workflow_dispatch:/);
assert.doesNotMatch(workflow,/^  (?:push|pull_request|workflow_run|schedule):/m);
assert.match(workflow,/github\.repository == '07xh\/dorm-os'/);
assert.match(workflow,/github\.ref == 'refs\/heads\/main'/);
assert.match(workflow,/node scripts\/audit-nexttodo-d1-production\.mjs/);
assert.doesNotMatch(workflow,/wrangler.*(?:execute|deploy|publish)/i);
assert.doesNotMatch(workflow,/approve_task_dependency_migration/,
 'a read-only preflight must never request write approval');
assert.match(workflow,/persist-credentials: false/);
console.log('NextTodo production audit (read-only): schema classification, manual main guard, bookmarks and workflow safety PASS');
