import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {collectAuthorizedSecondClass,runAuthorizedSecondClassSync,SecondClassSyncError}
 from '../lib/academic-second-class-connector.mjs';

const sqlite=new DatabaseSync(':memory:');
sqlite.exec('PRAGMA foreign_keys=ON');
for(const n of ['0001_academic_core.sql','0003_academic_verified_snapshots.sql',
 '0008_academic_second_class_source.sql'])
 sqlite.exec(readFileSync(new URL('../migrations/'+n,import.meta.url),'utf8'));
const db={
 prepare(sql){let args=[];return{
  bind(...values){args=values;return this;},
  first(){return sqlite.prepare(sql).get(...args)??null;},
  all(){return {results:sqlite.prepare(sql).all(...args)};},
  run(){return sqlite.prepare(sql).run(...args);}
 };},
 async batch(statements){
  sqlite.exec('BEGIN');
  try{const out=statements.map(stmt=>stmt.run());sqlite.exec('COMMIT');return out;}
  catch(e){sqlite.exec('ROLLBACK');throw e;}
 }
};
let idCounter=0;
const options={now:()=> '2026-11-03T02:00:00.000Z',id:()=> 'id-'+(++idCounter)};
const proof={authorized:true,scope:'self',school:'ncwu',provider:'second_class',
 identityBound:true,principalBoundTo:'owner-a',session:{ephemeral:true}};
const page1={activities:[{activityId:'a1',title:'合成活动甲',date:'2026-11-01',hours:2,
 points:5,activityStatus:'completed',reviewStatus:'approved',unusedPII:'SHOULD_NOT_PERSIST'}],
 nextCursor:'page2',complete:false};
const page2={activities:[{activityId:'a2',title:'合成活动乙',date:'2026-11-02',
 activityStatus:'registered',reviewStatus:'pending'}],nextCursor:null,complete:true};
function adapter(pages=[page1,page2],identity=proof){
 const cursors=[];
 return {provider:'second_class',cursors,
  verifySession:async()=>identity,
  readPage:async ({cursor})=>{cursors.push(cursor);return pages[cursors.length-1];}
 };
}
const a=adapter();
const collected=await collectAuthorizedSecondClass(a);
assert.equal(collected.records.length,2);
assert.equal(collected.pages,2);
assert.equal(collected.records[0].hours,2);
assert.equal(collected.records[0].points,5);
assert.equal(collected.records[0].reviewStatus,'approved');
assert.equal(Object.hasOwn(collected.records[0],'unusedPII'),false);
assert.deepEqual(a.cursors,[null,'page2']);
const result=await runAuthorizedSecondClassSync(db,'owner-a',adapter(),options);
assert.equal(result.count,2);
assert.equal(result.provenance,'trusted_readonly');
let rows=sqlite.prepare("SELECT user_id,kind,source,provenance,batch_id FROM academic_verified_batches").all();
assert.equal(rows.length,1);assert.equal(rows[0].provenance,'trusted_readonly');
const current=()=>sqlite.prepare("SELECT payload FROM academic_verified_records WHERE user_id='owner-a' AND batch_id=(SELECT batch_id FROM academic_verified_batches WHERE user_id='owner-a' AND kind='second-class') ORDER BY external_id").all().map(x=>JSON.parse(x.payload));
assert.equal(current().length,2);
assert.equal(current()[0].title,'合成活动甲');
assert.equal(JSON.stringify(current()).includes('SHOULD_NOT_PERSIST'),false);
const ownerB=adapter([{activities:[],complete:true,nextCursor:null,verifiedEmpty:true}],
 {...proof,principalBoundTo:'owner-b'});
const second=await runAuthorizedSecondClassSync(db,'owner-b',ownerB,options);
assert.equal(second.count,0);
assert.equal(current().length,2,'second user cannot overwrite owner records');
assert.equal(sqlite.prepare("SELECT count(*) AS n FROM academic_verified_records WHERE user_id='owner-b'").get().n,0);
const count=sqlite.prepare("SELECT count(*) AS n FROM academic_second_class_sync_audit").get().n;
assert.equal(count,2);
const previous=rows[0].batch_id;

async function mustFail(value,code){
 await assert.rejects(()=>value, e=>e instanceof SecondClassSyncError&&e.code===code);
}
await mustFail(collectAuthorizedSecondClass(adapter([page2],{...proof,identityBound:false})), 'authorization_failed');
await mustFail(collectAuthorizedSecondClass(adapter([page2],{...proof,school:'other'})), 'authorization_failed');
await mustFail(collectAuthorizedSecondClass(adapter([{activities:[{...page1.activities[0],points:-2}],complete:true}])), 'invalid_response');
await mustFail(collectAuthorizedSecondClass(adapter([{activities:[{...page1.activities[0],reviewStatus:'fake'}],complete:true}])), 'invalid_response');
await mustFail(collectAuthorizedSecondClass(adapter([page1,{...page2,activities:[{...page1.activities[0]}]}])), 'invalid_response');
await mustFail(collectAuthorizedSecondClass(adapter([{...page1,nextCursor:'page2'}, {...page1,nextCursor:'page2'}])), 'invalid_response');
await mustFail(collectAuthorizedSecondClass(adapter([{activities:[],complete:true}])), 'invalid_response');
await mustFail(collectAuthorizedSecondClass(adapter([{activities:[page1.activities[0]],complete:false,nextCursor:null}])), 'invalid_response');
await mustFail(collectAuthorizedSecondClass({provider:'second_class',verifySession:async()=>proof,
 readPage:async()=>{throw Error('cookie=redacted');}}),'upstream_unavailable');
await mustFail(runAuthorizedSecondClassSync(db,'owner-a',adapter([page2],{...proof,authorized:false}),options),'authorization_failed');
await mustFail(runAuthorizedSecondClassSync(db,'owner-a',adapter([page2],
 {...proof,principalBoundTo:'owner-b'}),options),'authorization_failed');
assert.equal(sqlite.prepare("SELECT batch_id FROM academic_verified_batches WHERE user_id='owner-a'").get().batch_id,
 previous,'failed source read must preserve last good snapshot');
// Full verified empty is allowed only with signed-in self scope, and hides old
// rows from the current batch without deleting history.
const empty=await runAuthorizedSecondClassSync(db,'owner-a',adapter([
 {activities:[],complete:true,verifiedEmpty:true}
]),options);
assert.equal(empty.count,0);
assert.equal(current().length,0);
assert.equal(sqlite.prepare("SELECT count(*) AS n FROM academic_verified_records WHERE user_id='owner-a'").get().n,2);
assert.equal(sqlite.prepare('PRAGMA integrity_check').get().integrity_check,'ok');
assert.deepEqual(sqlite.prepare('PRAGMA foreign_key_check').all(),[]);
sqlite.close();
console.log('Second-class source connector: owner authorization, pagination, normalized activities, strict status, atomic D1 write, empty proof, no credential persistence, privacy isolation passed');
