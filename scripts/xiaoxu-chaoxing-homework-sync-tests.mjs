import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {normalizeVerifiedChaoxingHomework,syncVerifiedChaoxingHomework}
 from '../lib/academic-chaoxing-homework-sync.mjs';

const keys={courseId:'123',classId:'456',cpi:'789'};
const course={...keys,title:'工程制图'};
const work=(id,statusCode='todo',title='作业')=>({
 workId:String(id),title,status:statusCode==='submitted'?'已提交':'未提交',
 statusCode,platformVerified:true
});
const listing=(items,verifiedEmpty=false,progress=null)=>({course,items,verifiedEmpty,progress});
const normalize=x=>normalizeVerifiedChaoxingHomework(keys,x);
assert.equal(normalize(listing([work(11)])).tasks[0].externalId,'work:123:456:11');
assert.equal(normalize(listing([work(11,'submitted')])).tasks[0].status,'submitted');
assert.deepEqual(normalize(listing([],true)).tasks,[]);
assert.deepEqual(normalize(listing([work(11)],false,{completed:0,total:1})).progress,
 {completed:0,total:1});
assert.throws(()=>normalize(listing([work(11)],false,{completed:0,total:2})),
 e=>e.code==='work_list_incomplete');
assert.throws(()=>normalize(listing([work(11)],false,{completed:2,total:1})),
 e=>e.code==='invalid_response');
for(const broken of [
 listing([],false),listing([work(11),work(11)]),
 listing([{...work(11),platformVerified:false}]),
 listing([{...work(11),statusCode:'done'}]),
 listing([{...work(11),title:' '}]),
 {...listing([work(11)]),course:{...course,courseId:'999'}},
 {...listing([work(11)]),verifiedEmpty:true},
])assert.throws(()=>normalize(broken));
const sqlite=new DatabaseSync(':memory:');
sqlite.exec('PRAGMA foreign_keys=ON');
for(const name of ['0001_academic_core.sql','0002_academic_legacy_links.sql',
 '0003_academic_verified_snapshots.sql','0004_academic_source_course.sql',
 '0005_academic_workspace.sql'])
 sqlite.exec(readFileSync(new URL('../migrations/'+name,import.meta.url),'utf8'));
const db={
 prepare(sql){let args=[];return {
  bind(...v){args=v;return this;},
  first(){return sqlite.prepare(sql).get(...args)??null;},
  all(){return {results:sqlite.prepare(sql).all(...args)};},
  run(){return sqlite.prepare(sql).run(...args)}
 }},
 async batch(statements){
  sqlite.exec('BEGIN');
  try{const out=statements.map(x=>x.run());sqlite.exec('COMMIT');return out;}
  catch(e){sqlite.exec('ROLLBACK');throw e;}
 }
};
const proof={
 authorized:true,scope:'self',provider:'chaoxing',school:'ncwu',
 identityBound:true,principalBoundTo:'student-1',session:{uid:'1234'}
};
let items=[work(11,'todo','三视图作业'),work(12,'submitted','补缺线')];
let calls=0;
const source={readHomeworkList:async()=>{calls++;return listing(items);}};
let sequence=0;
const options={
 now:()=>new Date('2026-11-04T01:00:00Z').toISOString(),
 id:()=> 'new-'+(++sequence)
};
const first=await syncVerifiedChaoxingHomework(db,'student-1',source,proof,keys,options);
assert.equal(first.sync.inserted,2);
assert.equal(first.sync.coverage,'partial','only one course was checked');
assert.equal(first.items[0].title,'三视图作业');
assert.equal(calls,1,'one upstream list read for one explicit sync');
let rows=sqlite.prepare('SELECT s.external_id,s.platform_status,a.local_status FROM academic_assignment_sources s JOIN academic_assignments a ON a.id=s.assignment_id ORDER BY s.external_id').all();
assert.deepEqual(rows.map(x=>[x.external_id,x.platform_status]),[
 ['work:123:456:11','todo'],['work:123:456:12','submitted']
]);
assert.equal(sqlite.prepare('SELECT status FROM academic_connections').get().status,'partial');
// A mismatch cannot mutate previously verified assignments, even if the
// current page contained otherwise valid work records.
const rowsBeforeMismatch=sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n;
const staleSource={readHomeworkList:async()=>listing([work(11)],false,{completed:1,total:2})};
await assert.rejects(()=>syncVerifiedChaoxingHomework(db,'student-1',staleSource,proof,keys,options),
 e=>e.code==='work_list_incomplete');
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,
 rowsBeforeMismatch);

const again=await syncVerifiedChaoxingHomework(db,'student-1',source,proof,keys,options);
assert.equal(again.sync.inserted,0);
assert.equal(again.sync.updated,0);
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,2);
const target=sqlite.prepare("SELECT a.id FROM academic_assignments a JOIN academic_assignment_sources s ON s.assignment_id=a.id WHERE s.external_id='work:123:456:11'").get().id;
sqlite.prepare("UPDATE academic_assignments SET local_status='done' WHERE id=?").run(target);
items=[work(11,'graded','三视图作业')]; // The missing work 12 is not a deletion.
const updated=await syncVerifiedChaoxingHomework(db,'student-1',source,proof,keys,options);
assert.equal(updated.sync.updated,1);
assert.equal(sqlite.prepare('SELECT local_status FROM academic_assignments WHERE id=?').get(target).local_status,'done');
assert.equal(sqlite.prepare("SELECT platform_status FROM academic_assignment_sources WHERE external_id='work:123:456:11'").get().platform_status,'graded');
assert.equal(sqlite.prepare("SELECT platform_status FROM academic_assignment_sources WHERE external_id='work:123:456:12'").get().platform_status,'submitted');
items=[work(11,'unknown','三视图作业')];
await syncVerifiedChaoxingHomework(db,'student-1',source,proof,keys,options);
assert.equal(sqlite.prepare("SELECT platform_status FROM academic_assignment_sources WHERE external_id='work:123:456:11'").get().platform_status,'graded',
 'unknown response cannot erase earlier verified grade');
items=[];
const nonemptyBefore=sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n;
await assert.rejects(()=>syncVerifiedChaoxingHomework(db,'student-1',source,proof,keys,options),
 e=>e.code==='work_not_available');
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,nonemptyBefore,
 'ambiguous empty list must not modify cloud tasks');
await assert.rejects(()=>syncVerifiedChaoxingHomework(db,'student-2',source,proof,keys,options),
 e=>e.code==='authorization_failed');
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_users').get().n,1);
const verifiedEmptySource={readHomeworkList:async()=>listing([],true)};
const empty=await syncVerifiedChaoxingHomework(db,'student-1',verifiedEmptySource,proof,keys,options);
assert.equal(empty.sync.received,0);
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,nonemptyBefore);
console.log('Official Chaoxing homework: owner scope, partial sync, ID dedup, local done preservation, status transitions, empty defense passed');
