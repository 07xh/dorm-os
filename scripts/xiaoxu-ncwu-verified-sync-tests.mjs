import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {collectVerifiedNcwu,runVerifiedNcwuSync,NcwuSyncError}
 from '../lib/academic-ncwu-verified-sync.mjs';
import {readConnectionCenter} from '../lib/academic-connection-center.mjs';
import {onRequestGet} from '../functions/api/v1/connections.js';
const sqlite=new DatabaseSync(':memory:');
sqlite.exec('PRAGMA foreign_keys=ON');
for(const file of ['0001_academic_core.sql','0003_academic_verified_snapshots.sql',
 '0008_academic_second_class_source.sql'])
 sqlite.exec(readFileSync(new URL('../migrations/'+file,import.meta.url),'utf8'));
const db={prepare(sql){
 let args=[];return {bind(...v){args=v;return this;},
 first(){return sqlite.prepare(sql).get(...args)??null;},
 all(){return {results:sqlite.prepare(sql).all(...args)};},
 run(){return sqlite.prepare(sql).run(...args);}};
},async batch(statements){
 sqlite.exec('BEGIN');
 try{const r=statements.map(x=>x.run());sqlite.exec('COMMIT');return r;}
 catch(e){sqlite.exec('ROLLBACK');throw e;}
}};
let counter=0;
const options={now:()=> '2026-11-03T10:30:00.000Z',id:()=> 'sync-id-'+(++counter)};
const testSecret='SENSITIVE-SCHOOL-SESSION-MUST-NOT-BE-STORED';
const proofFor=id=>({authorized:true,scope:'self',school:'ncwu',provider:'ncwu',
 identityBound:true,principalBoundTo:id,officialOrigin:'https://jwmis.ncwu.edu.cn',
 session:{privateSession:testSecret}});
const schedule={kind:'schedule',complete:true,records:[{
 externalId:'term-1-course-1-block-1',term:'2026-2027-1',title:'电路基础',teacher:'甲老师',
 location:'教学楼C101',weekday:1,startSection:1,endSection:2,weeks:[1,2,3]}]};
const grades={kind:'grades',complete:true,records:[{
 externalId:'term-1-grade-1',term:'2026-2027-1',title:'大学英语',score:'88',credit:2.5}]};
const exams={kind:'exams',complete:true,records:[{
 externalId:'term-1-exam-1',term:'2026-2027-1',title:'高等数学',date:'2026-12-21',
 startTime:'08:30',endTime:'10:30',location:'A201'}]};
const fixtures={schedule,grades,exams};
function adapter(owner='owner-a',overrides={}){
 return {provider:'ncwu',
 verifySession:async()=>overrides.proof??proofFor(owner),
 readSection:async({kind,session})=>{
  assert.equal(session.privateSession,testSecret);
  if(overrides.failKind===kind)throw Error('secret cookie='+testSecret);
  return overrides.sections?.[kind]??fixtures[kind];
 }};
}
const rows=await collectVerifiedNcwu(adapter(),'owner-a');
assert.deepEqual(rows.kinds,['schedule','grades','exams']);
assert.equal(rows.records.schedule[0].title,'电路基础');
const result=await runVerifiedNcwuSync(db,'owner-a',adapter(),options);
assert.deepEqual(result.counts,{schedule:1,grades:1,exams:1});
assert.equal(result.provenance,'trusted_readonly');
const saved=sqlite.prepare('SELECT user_id,kind,source,provenance,batch_id FROM academic_verified_batches ORDER BY kind').all();
assert.equal(saved.length,3);
assert.ok(saved.every(x=>x.user_id==='owner-a'&&x.source==='ncwu'&&x.provenance==='trusted_readonly'));
const read=await readConnectionCenter(db,'owner-a');
assert.equal(read.platforms[0].status,'connected');
assert.deepEqual([...read.platforms[0].trustedKinds].sort(),['exams','grades','schedule']);
const current=(kind,owner='owner-a')=>sqlite.prepare(
 "SELECT payload FROM academic_verified_records WHERE user_id=? AND kind=? AND batch_id=(SELECT batch_id FROM academic_verified_batches WHERE user_id=? AND kind=?)"
).all(owner,kind,owner,kind).map(x=>JSON.parse(x.payload));
assert.equal(current('schedule').length,1);
assert.equal(JSON.stringify(current('grades')).includes(testSecret),false);
const dump=JSON.stringify(sqlite.prepare('SELECT * FROM academic_verified_records').all());
assert.equal(dump.includes(testSecret),false,'session never persisted');
assert.equal(dump.includes('privateSession'),false,'no session keys are stored');
assert.equal(sqlite.prepare('SELECT status FROM academic_connections WHERE user_id=?').get('owner-a').status,'connected');

async function rejects(fn,code){await assert.rejects(fn,e=>e instanceof NcwuSyncError&&e.code===code);}
await rejects(()=>runVerifiedNcwuSync(db,'owner-a',adapter('owner-b'),options),'authorization_failed');
assert.equal(sqlite.prepare('SELECT status FROM academic_connections WHERE user_id=?').get('owner-a').status,'expired');
await rejects(()=>collectVerifiedNcwu(adapter('owner-a',{proof:{...proofFor('owner-a'),officialOrigin:'https://evil.example'}}),'owner-a'),'authorization_failed');
await rejects(()=>collectVerifiedNcwu(adapter('owner-a',{proof:{...proofFor('owner-a'),identityBound:false}}),'owner-a'),'authorization_failed');
await rejects(()=>collectVerifiedNcwu(adapter('owner-a',{sections:{schedule:{...schedule,complete:false}}}),'owner-a'),'invalid_response');
await rejects(()=>collectVerifiedNcwu(adapter('owner-a',{sections:{schedule:{...schedule,records:[schedule.records[0],schedule.records[0]]}}}),'owner-a'),'invalid_response');
await rejects(()=>collectVerifiedNcwu(adapter('owner-a',{sections:{grades:{...grades,records:[{title:'无法证实的成绩',term:'2026-2027-1'}]}}}),'owner-a'),'invalid_response');
await rejects(()=>collectVerifiedNcwu(adapter('owner-a',{sections:{exams:{...exams,records:[]}}}),'owner-a'),'invalid_response');
await rejects(()=>runVerifiedNcwuSync(db,'owner-a',adapter('owner-a',{failKind:'exams'}),options),'upstream_unavailable');
assert.equal(current('schedule').length,1,'partial fetch failures must not replace good data');
assert.equal(current('grades').length,1);
assert.equal(current('exams').length,1);
assert.equal(sqlite.prepare('SELECT status FROM academic_connections WHERE user_id=?').get('owner-a').status,'error');
const fresh=await runVerifiedNcwuSync(db,'owner-a',adapter(),options);
assert.equal(fresh.counts.schedule,1);
assert.equal(sqlite.prepare('SELECT status FROM academic_connections WHERE user_id=?').get('owner-a').status,'connected');
const empty={kind:'exams',complete:true,verifiedEmpty:true,records:[]};
const verifiedEmpty=await runVerifiedNcwuSync(db,'owner-a',adapter('owner-a',{sections:{exams:empty}}),options);
assert.equal(verifiedEmpty.counts.exams,0);
assert.equal(current('exams').length,0,'verified empty hides old active batch');
assert.equal(sqlite.prepare("SELECT count(*) AS n FROM academic_verified_records WHERE kind='exams'").get().n,1,'retain history');

await runVerifiedNcwuSync(db,'owner-b',adapter('owner-b'),options);
assert.equal(current('schedule','owner-b').length,1);
assert.equal(current('schedule','owner-a').length,1);
assert.equal(sqlite.prepare('PRAGMA integrity_check').get().integrity_check,'ok');
assert.deepEqual(sqlite.prepare('PRAGMA foreign_key_check').all(),[]);
const denied=await onRequestGet({request:new Request('https://school.test/api/v1/connections'),env:{}});
assert.equal(denied.status,503);
assert.match(denied.headers.get('cache-control')||'',/no-store/);
sqlite.close();
console.log('NCWU verified academic bridge: owner binding, 3 sections, atomic D1, no session persistence, failures and isolation passed');
