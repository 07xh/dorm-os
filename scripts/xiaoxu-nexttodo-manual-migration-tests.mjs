import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {
 classifyTaskGraphSchema,requireTaskGraphMigrationApproval,
 validateRecoveryBookmark,assertCoreCountsUnchanged
} from './ensure-task-dependency-schema.mjs';

const workflow=readFileSync(new URL('../.github/workflows/apply-nexttodo-d1-0019.yml',import.meta.url),'utf8');
assert.match(workflow,/workflow_dispatch:/);
assert.doesNotMatch(workflow,/^  (?:push|pull_request|workflow_run|schedule):/m);
assert.match(workflow,/default: false/);
assert.match(workflow,/approve_task_dependency_migration/);
assert.match(workflow,/github\.repository == '07xh\/dorm-os'/);
assert.match(workflow,/github\.ref == 'refs\/heads\/main'/);
assert.match(workflow,/inputs\.approve_task_dependency_migration/);
assert.match(workflow,/XIAOXU_TASK_D1_0019_APPLY: APPLY-0019/);
assert.match(workflow,/node scripts\/ensure-task-dependency-schema\.mjs/);
assert.match(workflow,/group: dorm-os-pages/);
assert.match(workflow,/cancel-in-progress: false/);
assert.doesNotMatch(workflow,/^\s*(?:npx\s+--yes\s+)?wrangler(?:@\S+)?\s+pages\s+deploy\b/im,
 'schema-only workflow must not execute Pages deployment');
assert.doesNotMatch(workflow,/\bnode\s+scripts\/stamp-release\.mjs\b/i,
 'schema-only workflow must not stamp a web release');
assert.equal(requireTaskGraphMigrationApproval('workflow_dispatch','APPLY-0019'),true);
for(const tuple of [['push','APPLY-0019'],['workflow_run','APPLY-0019'],
 ['workflow_dispatch','NO'],['workflow_dispatch',undefined]]){
 assert.throws(()=>requireTaskGraphMigrationApproval(...tuple),
  /task_graph_migration_manual_approval_required/);
}
assert.equal(validateRecoveryBookmark({bookmark:'00000085-0000024c-00004c6d-8e61117bf38d7adb71b934ebbf891683'}),true);
assert.throws(()=>validateRecoveryBookmark(null),/bookmark_unavailable/);
const schema=readFileSync(new URL('../migrations/0019_academic_task_dependencies.sql',import.meta.url),'utf8');
const sqlite=new DatabaseSync(':memory:');
sqlite.exec('PRAGMA foreign_keys=ON');
sqlite.exec(readFileSync(new URL('../migrations/0001_academic_core.sql',import.meta.url),'utf8'));
sqlite.exec(readFileSync(new URL('../migrations/0005_academic_workspace.sql',import.meta.url),'utf8'));
sqlite.exec("INSERT INTO academic_users(id,created_at) VALUES ('u','t')");
sqlite.exec("INSERT INTO academic_assignments(id,user_id,title,kind,created_at,updated_at) VALUES('a','u','A','manual','t','t'),('b','u','B','manual','t','t')");
const count=()=>sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n;
assert.equal(count(),2);
sqlite.exec(schema);
sqlite.exec(schema);
assert.equal(count(),2,'0019 must not remove existing task rows');
const tables=()=>sqlite.prepare("SELECT name FROM sqlite_master WHERE type='table'").all();
const columns=()=>Object.fromEntries(['academic_task_dependencies','academic_task_graph_versions','academic_task_graph_guards']
 .map(n=>[n,sqlite.prepare('PRAGMA table_info('+n+')').all()]));
const triggers=()=>sqlite.prepare("SELECT name FROM sqlite_master WHERE type='trigger'").all();
assert.equal(classifyTaskGraphSchema(tables(),columns(),triggers()),'ready');
sqlite.exec("INSERT INTO academic_task_dependencies(user_id,task_id,prerequisite_id,created_at) VALUES('u','b','a','t')");
assert.throws(()=>sqlite.exec("UPDATE academic_assignments SET local_status='done' WHERE id='b'"),/prerequisites_incomplete/);
sqlite.exec("UPDATE academic_assignments SET local_status='done' WHERE id='a'");
sqlite.exec("UPDATE academic_assignments SET local_status='done' WHERE id='b'");
assert.equal(sqlite.prepare('SELECT local_status FROM academic_assignments WHERE id=?').get('b').local_status,'done');
assert.equal(sqlite.prepare('PRAGMA foreign_key_check').all().length,0);
assert.equal(sqlite.prepare('PRAGMA quick_check').get().quick_check,'ok');
assert.equal(assertCoreCountsUnchanged({
 academic_users:1,academic_connections:0,academic_courses:0,
 academic_assignments:2,academic_assignment_sources:0,
 academic_assignment_events:0,academic_verified_records:0
},{
 academic_users:1,academic_connections:0,academic_courses:0,
 academic_assignments:2,academic_assignment_sources:0,
 academic_assignment_events:0,academic_verified_records:0
}),true);
sqlite.close();
console.log('Manual-only D1 0019: authorization, idempotence, row preservation and trigger safety PASS');
