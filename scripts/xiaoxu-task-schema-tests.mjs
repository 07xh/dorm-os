import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {classifyTaskGraphSchema,validateRecoveryBookmark,assertCoreCountsUnchanged} from './ensure-task-dependency-schema.mjs';

const names=['academic_task_dependencies','academic_task_graph_versions',
 'academic_task_graph_guards'];
assert.equal(classifyTaskGraphSchema([],{}),'missing');
assert.equal(classifyTaskGraphSchema([{name:names[0]}],{}),'partial');
assert.throws(()=>classifyTaskGraphSchema('invalid'),/invalid_schema/);
const bookmark='00000085-0000024c-00004c6d-8e61117bf38d7adb71b934ebbf891683';
assert.equal(validateRecoveryBookmark({bookmark}),true);
for(const invalid of [null,{}, {bookmark:''},{bookmark:'bad'}, {bookmark:123},
 {bookmark:'../../private/token'}])
 assert.throws(()=>validateRecoveryBookmark(invalid),/bookmark_unavailable/);
// No private values are logged. A differing count blocks deployment for
// manual review, including benign concurrent edits (fail closed).
const coreTableNames=['academic_users','academic_connections','academic_courses',
 'academic_assignments','academic_assignment_sources','academic_assignment_events',
 'academic_verified_records'];
const zeroSnapshot=Object.fromEntries(coreTableNames.map(x=>[x,0]));
const oneSnapshot={...zeroSnapshot,academic_assignments:1};
assert.equal(assertCoreCountsUnchanged(zeroSnapshot,{...zeroSnapshot}),true);
assert.throws(()=>assertCoreCountsUnchanged(zeroSnapshot,oneSnapshot),
 /core_data_changed_during_migration_manual_review/);
assert.throws(()=>assertCoreCountsUnchanged({...zeroSnapshot,academic_users:-1},zeroSnapshot),
 /core_count_snapshot_invalid/);
assert.throws(()=>assertCoreCountsUnchanged(zeroSnapshot,{...zeroSnapshot,
 academic_verified_records:'0'}),/core_count_snapshot_invalid/);
const sqlite=new DatabaseSync(':memory:');
sqlite.exec('PRAGMA foreign_keys=ON');
const migration=name=>readFileSync(new URL('../migrations/'+name,import.meta.url),'utf8');
sqlite.exec(migration('0001_academic_core.sql'));
sqlite.exec(migration('0005_academic_workspace.sql'));
const tables=()=>sqlite.prepare("SELECT name FROM sqlite_master WHERE type='table'").all();
const columns=()=>Object.fromEntries(names.filter(n=>tables().some(r=>r.name===n))
 .map(n=>[n,sqlite.prepare('PRAGMA table_info('+n+')').all()]));
const triggers=()=>sqlite.prepare("SELECT name FROM sqlite_master WHERE type='trigger'").all();
assert.equal(classifyTaskGraphSchema(tables(),columns(),triggers()),'missing');
sqlite.exec(migration('0019_academic_task_dependencies.sql'));
assert.equal(classifyTaskGraphSchema(tables(),columns(),triggers()),'ready');
assert.equal(classifyTaskGraphSchema(tables(),columns(),
 [{name:'trg_academic_task_graph_guard'}]),'partial',
 'production schema must not be marked ready without the completion guard');
sqlite.exec(migration('0019_academic_task_dependencies.sql'));
assert.equal(classifyTaskGraphSchema(tables(),columns(),triggers()),'ready',
 'migration may be safely rechecked without deleting personal data');
sqlite.exec("INSERT INTO academic_users(id,created_at) VALUES ('u','2026-11-09T00:00:00Z')");
sqlite.exec(`INSERT INTO academic_assignments
 (id,user_id,title,kind,created_at,updated_at) VALUES
 ('a','u','A','manual','t','t'),
 ('b','u','B','manual','t','t')`);
sqlite.exec(`INSERT INTO academic_task_graph_versions(user_id,revision,updated_at)
 VALUES ('u',0,'t')`);
assert.throws(()=>sqlite.exec(`INSERT INTO academic_task_graph_guards
 (id,user_id,task_id,expected_graph_revision,expected_task_revision)
 VALUES ('stale','u','b',1,0)`),/graph_revision_conflict/);
sqlite.exec(`INSERT INTO academic_task_graph_guards
 (id,user_id,task_id,expected_graph_revision,expected_task_revision)
 VALUES ('valid','u','b',0,0)`);
sqlite.exec("DELETE FROM academic_task_graph_guards WHERE id='valid'");
sqlite.exec(`INSERT INTO academic_task_dependencies
 (user_id,task_id,prerequisite_id,created_at) VALUES ('u','b','a','t')`);
assert.throws(()=>sqlite.exec(`UPDATE academic_assignments
 SET local_status='done' WHERE id='b' AND user_id='u'`),
 /prerequisites_incomplete/,
 'direct database writes cannot bypass unfinished dependencies');
sqlite.exec("UPDATE academic_assignments SET local_status='done' WHERE id='a'");
sqlite.exec("UPDATE academic_assignments SET local_status='done' WHERE id='b'");
assert.equal(sqlite.prepare("SELECT local_status FROM academic_assignments WHERE id='b'")
 .get().local_status,'done');
assert.throws(()=>sqlite.exec(`INSERT INTO academic_task_dependencies
 (user_id,task_id,prerequisite_id,created_at) VALUES ('u','a','a','t')`),/CHECK constraint/);
assert.equal(sqlite.prepare('PRAGMA foreign_key_check').all().length,0);
assert.equal(sqlite.prepare('PRAGMA quick_check').get().quick_check,'ok');
const migrationGuard=readFileSync(new URL('./ensure-task-dependency-schema.mjs',import.meta.url),'utf8');
assert.match(migrationGuard,/time_travel\/bookmark/,
 'production D1 migration must verify a restorable Time Travel checkpoint');
assert.ok(migrationGuard.indexOf('validateRecoveryBookmark(checkpoint)')<
 migrationGuard.indexOf("localWrangler(['d1','execute'"),
 'migration must fail closed if no recovery bookmark was obtained');
assert.ok(migrationGuard.indexOf('const countsBefore=coreCounts(db)')<
 migrationGuard.indexOf("localWrangler(['d1','execute'"),
 'collect all existing core table counts before migration');
assert.ok(migrationGuard.indexOf('assertCoreCountsUnchanged(countsBefore,coreCounts(db))')>
 migrationGuard.indexOf("localWrangler(['d1','execute'"),
 'verify legacy row counts before release');
assert.doesNotMatch(migrationGuard,/console\.log\([^\n]*countsBefore/,
 'aggregate private record counts must not appear in CI logs');
const workflow=readFileSync(new URL('../.github/workflows/deploy-cloudflare.yml',import.meta.url),'utf8');
const gate=workflow.indexOf('run: node scripts/ensure-task-dependency-schema.mjs');
const publish=workflow.indexOf('name: Publish verified static website');
assert.ok(gate>0&&publish>gate,'task graph migration must precede public release');
assert.ok(workflow.includes("steps.publish_guard.outputs.fresh == 'true'"));
assert.equal(workflow.includes('continue-on-error: true\n        run: node scripts/ensure-task-dependency-schema.mjs'),false,
 'schema errors must block release');
sqlite.close();
console.log('NextTodo D1 schema: additive migration, trigger CAS, idempotence and fail-closed release order PASS');
