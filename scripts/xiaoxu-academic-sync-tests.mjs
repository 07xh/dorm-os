import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {normalizeReadOnlySnapshot,applyReadOnlySnapshot,AcademicSyncError}
  from '../lib/academic-readonly-sync.mjs';
import {listAssignments} from '../lib/academic-assignments.mjs';

const sqlite=new DatabaseSync(':memory:');
sqlite.exec('PRAGMA foreign_keys=ON');
for(const file of [
  '0001_academic_core.sql',
  '0002_academic_legacy_links.sql',
  '0003_academic_verified_snapshots.sql',
  '0004_academic_source_course.sql'
]) sqlite.exec(readFileSync(new URL('../migrations/'+file,import.meta.url),'utf8'));
const db={
  prepare(sql){
    let params=[];
    return {
      bind(...args){params=args;return this;},
      first(){return sqlite.prepare(sql).get(...params)??null;},
      all(){return {results:sqlite.prepare(sql).all(...params)};},
      run(){return sqlite.prepare(sql).run(...params);}
    };
  },
  async batch(statements){
    sqlite.exec('BEGIN');
    try{
      const r=statements.map(s=>s.run());
      sqlite.exec('COMMIT');return r;
    }catch(e){sqlite.exec('ROLLBACK');throw e;}
  }
};
const initialTime='2026-11-03T11:00:00.000Z';
sqlite.prepare('INSERT INTO academic_users(id,created_at) VALUES (?,?)')
  .run('a',initialTime);
sqlite.prepare(`INSERT INTO academic_connections
  (id,user_id,provider,mode,status) VALUES (?,?,?,?,?)`)
  .run('conn','a','chaoxing','readonly','connected');
sqlite.prepare(`INSERT INTO academic_connections
  (id,user_id,provider,mode,status) VALUES (?,?,?,?,?)`)
  .run('import-only','a','ncwu','import','disconnected');

const make=(externalId,rest={})=>({
  externalId,title:'工程制图作业',course:'工程制图',status:'todo',
  due:'2026-11-20',dueAt:'2026-11-20T23:59:00+08:00',
  ...rest
});
const snapshot=(tasks,source='chaoxing')=>({
  format:'xiaoxu-task-batch-v1',source,tasks
});
assert.throws(()=>normalizeReadOnlySnapshot(snapshot([make('dup'),make('dup')])),AcademicSyncError);
assert.throws(()=>normalizeReadOnlySnapshot(snapshot([make('x',{due:'2026-02-30',dueAt:''})])),AcademicSyncError);
assert.throws(()=>normalizeReadOnlySnapshot(snapshot([make('x',{dueAt:'2026-11-20T23:59:00'})])),AcademicSyncError);
assert.throws(()=>normalizeReadOnlySnapshot(snapshot([make('x',{status:'done'})])),AcademicSyncError);
await assert.rejects(()=>applyReadOnlySnapshot(db,'b','conn',snapshot([make('one')])),AcademicSyncError);
await assert.rejects(()=>applyReadOnlySnapshot(db,'a','import-only',snapshot([make('one')],'ncwu')),AcademicSyncError);
let count=0;
const options=(time,coverage='complete')=>({
  now:()=>time,id:()=> 'sync-key-'+(++count),coverage
});
const first=await applyReadOnlySnapshot(db,'a','conn',snapshot([
  make('work:1'),make('work:2',{title:'Python 练习',due:'2026-11-21',dueAt:''})
]),options(initialTime));
assert.equal(first.inserted,2);
assert.equal(first.events,2);
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,2);
let a=sqlite.prepare(`SELECT a.* FROM academic_assignments a
  JOIN academic_assignment_sources s ON a.id=s.assignment_id
  WHERE s.external_id='work:1'`).get();
assert.equal(a.due_precision,'exact');
assert.equal(a.due_at_utc,'2026-11-20T15:59:00.000Z');
assert.equal(a.course_id,null,'same name alone must not auto-link an official course');
const second=await applyReadOnlySnapshot(db,'a','conn',snapshot([
  make('work:1'),make('work:2',{title:'Python 练习',due:'2026-11-21',dueAt:''})
]),options('2026-11-03T11:01:00.000Z'));
assert.equal(second.inserted,0);
assert.equal(second.updated,0);
assert.equal(second.events,0,'unchanged data does not create duplicate events');

sqlite.prepare("UPDATE academic_assignments SET local_status='done' WHERE id=?").run(a.id);
const update=await applyReadOnlySnapshot(db,'a','conn',snapshot([
  make('work:1',{title:'工程制图补充作业',status:'submitted',
     due:'2026-11-22',dueAt:'2026-11-22T22:30:00+08:00'})
]),options('2026-11-03T12:00:00.000Z','partial'));
assert.equal(update.inserted,0);
assert.equal(update.updated,1);
assert.equal(update.events,3,'title, deadline and platform status changes are tracked');
a=sqlite.prepare('SELECT * FROM academic_assignments WHERE id=?').get(a.id);
assert.equal(a.local_status,'done','local done is never overwritten');
assert.equal(a.due_local_date,'2026-11-22');
const src=sqlite.prepare("SELECT * FROM academic_assignment_sources WHERE external_id='work:1'").get();
assert.equal(src.platform_status,'submitted');
assert.equal(src.course_title,'工程制图');
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,2,
  'partial snapshot missing work:2 must not delete work:2');
assert.equal(sqlite.prepare('SELECT status FROM academic_connections WHERE id=?').get('conn').status,'partial');
const unknown=await applyReadOnlySnapshot(db,'a','conn',snapshot([
  make('work:1',{title:'工程制图补充作业',due:'',dueAt:'',status:'unknown'})
]),options('2026-11-03T13:00:00.000Z'));
assert.equal(unknown.updated,0,'unknown deadline and status must preserve last known values');
a=sqlite.prepare('SELECT * FROM academic_assignments WHERE id=?').get(a.id);
assert.equal(a.due_local_date,'2026-11-22');
assert.equal(sqlite.prepare("SELECT platform_status FROM academic_assignment_sources WHERE external_id='work:1'").get().platform_status,'submitted');

sqlite.prepare(`INSERT INTO academic_assignments
  (id,user_id,title,kind,local_status,created_at,updated_at)
  VALUES ('legacy-one','a','已手动完成的旧作业','platform','done',?,?)`).run(initialTime,initialTime);
sqlite.prepare(`INSERT INTO academic_legacy_links
  (user_id,legacy_id,assignment_id,legacy_source,legacy_external_id,
   original_fingerprint,migrated_at) VALUES (?,?,?,?,?,?,?)`)
  .run('a','old-work-3','legacy-one','chaoxing','work:3','original',initialTime);
const linked=await applyReadOnlySnapshot(db,'a','conn',snapshot([
  make('work:3',{title:'平台确认后的作业',status:'graded'})
]),options('2026-11-03T14:00:00.000Z'));
assert.equal(linked.inserted,0);
assert.equal(linked.linked,1);
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,3);
assert.equal(sqlite.prepare("SELECT local_status FROM academic_assignments WHERE id='legacy-one'").get().local_status,'done');
assert.equal(sqlite.prepare("SELECT assignment_id FROM academic_assignment_sources WHERE external_id='work:3'").get().assignment_id,'legacy-one');

sqlite.prepare(`INSERT INTO academic_assignments
  (id,user_id,title,kind,created_at,updated_at)
  VALUES ('legacy-two','a','重复旧任务','platform',?,?)`).run(initialTime,initialTime);
sqlite.prepare(`INSERT INTO academic_legacy_links
  (user_id,legacy_id,assignment_id,legacy_source,legacy_external_id,
   original_fingerprint,migrated_at) VALUES (?,?,?,?,?,?,?)`)
  .run('a','old-work-4','legacy-two','chaoxing','work:4','duplicate',initialTime);
sqlite.prepare(`INSERT INTO academic_assignments
  (id,user_id,title,kind,created_at,updated_at)
  VALUES ('legacy-three','a','另一旧任务','platform',?,?)`).run(initialTime,initialTime);
sqlite.prepare(`INSERT INTO academic_legacy_links
  (user_id,legacy_id,assignment_id,legacy_source,legacy_external_id,
   original_fingerprint,migrated_at) VALUES (?,?,?,?,?,?,?)`)
  .run('a','old-work-5','legacy-three','chaoxing','work:4','duplicate2',initialTime);
const ambiguous=await applyReadOnlySnapshot(db,'a','conn',snapshot([
  make('work:4',{title:'官方真实作业',status:'submitted'})
]),options('2026-11-03T15:00:00.000Z'));
assert.equal(ambiguous.inserted,1,
  'duplicate legacy IDs must not prevent an independently verified official record');
assert.equal(ambiguous.linked,0,
  'ambiguous legacy rows may not be mutated or silently linked');
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,6);
const canonical=sqlite.prepare(
  "SELECT a.id,a.title,a.local_status,s.platform_status FROM academic_assignment_sources s "+
  "JOIN academic_assignments a ON a.id=s.assignment_id WHERE s.external_id='work:4'"
).get();
assert.equal(canonical.title,'官方真实作业');
assert.equal(canonical.platform_status,'submitted');
assert.equal(canonical.local_status,'todo');
assert.ok(!['legacy-two','legacy-three'].includes(canonical.id));
assert.equal(sqlite.prepare("SELECT title FROM academic_assignments WHERE id='legacy-two'").get().title,
  '重复旧任务');
assert.equal(sqlite.prepare("SELECT title FROM academic_assignments WHERE id='legacy-three'").get().title,
  '另一旧任务');
const replay=await applyReadOnlySnapshot(db,'a','conn',snapshot([
  make('work:4',{title:'官方真实作业',status:'submitted'})
]),options('2026-11-03T16:00:00.000Z'));
assert.equal(replay.inserted,0,'verified source ID reuses one canonical assignment');
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,6);

const ownerRows=await listAssignments(db,'a',{limit:100});
const officialRow=ownerRows.find(row=>row.id===canonical.id);
assert.equal(officialRow.source_external_id,'work:4',
  'official status and source ID must refer to the same canonical source row');
assert.equal(officialRow.source_provider,'chaoxing');
assert.equal(officialRow.platform_status,'submitted');
const importedRows=ownerRows.filter(row=>['legacy-two','legacy-three'].includes(row.id));
assert.equal(importedRows.length,2);
assert.ok(importedRows.every(row=>row.platform_status===null),
  'old imported source records cannot claim official verification');
assert.ok(importedRows.every(row=>row.source_external_id==='work:4'),
  'legacy identity is preserved for safe client display reconciliation');
const allRuns=sqlite.prepare('SELECT COUNT(*) AS n FROM academic_sync_runs').get().n;
assert.equal(allRuns,7);
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignment_events').get().n,9);
console.log('校序 M2：真实 SQLite 只读快照合并、重复历史 ID 隔离、官方新记录核验、个人状态保护通过');
