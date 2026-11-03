import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {runTrustedConnector,ConnectorRunError} from '../lib/academic-connector-runner.mjs';

const sqlite=new DatabaseSync(':memory:');
sqlite.exec('PRAGMA foreign_keys=ON');
for(const name of ['0001_academic_core.sql','0002_academic_legacy_links.sql',
 '0003_academic_verified_snapshots.sql','0004_academic_source_course.sql',
 '0005_academic_workspace.sql']){
 sqlite.exec(readFileSync(new URL('../migrations/'+name,import.meta.url),'utf8'));
}
const db={
 prepare(sql){let args=[];return{
  bind(...v){args=v;return this;},
  first(){return sqlite.prepare(sql).get(...args)??null;},
  all(){return {results:sqlite.prepare(sql).all(...args)};},
  run(){return {meta:{changes:sqlite.prepare(sql).run(...args).changes}};}
 }},
 async batch(stmts){
  sqlite.exec('BEGIN');
  try{const result=stmts.map(s=>s.run());sqlite.exec('COMMIT');return result;}
  catch(e){sqlite.exec('ROLLBACK');throw e;}
 }
};
let seq=0;
const opts={id:()=> 'connector-id-'+(++seq),
 now:()=> '2026-11-03T12:00:00.000Z'};
const task=i=>({externalId:'work:'+i,title:'真实来源模拟作业 '+i,
 course:'Python',status:'todo',due:'2026-11-20',dueAt:''});
const pages=[Array.from({length:20},(_,i)=>task(i)),
 [task(20)]];
const authorized={provider:'chaoxing',verifySession:async()=>({
 authorized:true,scope:'self',provider:'chaoxing',school:'ncwu',
 identityBound:true,principalBoundTo:'owner-1',
 session:{opaque:'test-only-memory'}
}),readPage:async({cursor})=>cursor===null?
 {tasks:pages[0],complete:false,nextCursor:'page2'}:
 {tasks:pages[1],complete:true,nextCursor:null}};
const first=await runTrustedConnector(db,'owner-1',authorized,opts);
assert.equal(first.pages,2);
assert.equal(first.received,21);
assert.equal(first.inserted,21);
assert.equal(first.coverage,'complete');
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,21);
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_sync_runs').get().n,1,
 'all pages share one transactional write');
const connection=sqlite.prepare('SELECT * FROM academic_connections').get();
assert.equal(connection.provider,'chaoxing');
assert.equal(connection.status,'connected');
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_users').get().n,1);
assert.ok(!JSON.stringify(connection).includes('test-only-memory'),'no session is stored in D1');
const repeat=await runTrustedConnector(db,'owner-1',authorized,opts);
assert.equal(repeat.inserted,0);
assert.equal(repeat.updated,0);
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,21);
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_sync_runs').get().n,2);
await assert.rejects(()=>runTrustedConnector(db,'owner-2',authorized,opts),
 e=>e instanceof ConnectorRunError&&e.code==='authorization_failed');
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_users').get().n,1,
 'wrong school account cannot write to second owner');

const partial={...authorized,readPage:async({cursor})=>{
 if(cursor)throw Error('DO NOT LOG RAW AUTH COOKIE');
 return {tasks:[task(99)],complete:false,nextCursor:'missing'};
}};
await assert.rejects(()=>runTrustedConnector(db,'owner-1',partial,opts),
 e=>e instanceof ConnectorRunError&&e.code==='upstream_unavailable');
const afterFail=sqlite.prepare('SELECT * FROM academic_connections').get();
assert.equal(afterFail.status,'error');
assert.equal(afterFail.last_error_code,'upstream_unavailable');
assert.ok(!JSON.stringify(afterFail).includes('COOKIE'));
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,21,
 'later page failure must not persist partially fetched tasks');
const recovered=await runTrustedConnector(db,'owner-1',{
 ...authorized,readPage:async()=>({tasks:[task(99)],complete:true,nextCursor:null})
},opts);
assert.equal(recovered.inserted,1);
assert.equal(sqlite.prepare('SELECT status FROM academic_connections').get().status,'connected');
await assert.rejects(()=>runTrustedConnector(db,'owner-1',{
 ...authorized,verifySession:async()=>({authorized:false,provider:'chaoxing'})
},opts),e=>e instanceof ConnectorRunError&&e.code==='authorization_failed');
assert.equal(sqlite.prepare('SELECT status FROM academic_connections').get().status,'expired');
await assert.rejects(()=>runTrustedConnector(db,'owner-1',{
 ...authorized,readPage:async()=>({tasks:[],complete:true,nextCursor:null,verifiedEmpty:false})
},opts),e=>e instanceof ConnectorRunError&&e.code==='invalid_response');
assert.equal(sqlite.prepare('SELECT status FROM academic_connections').get().status,'error');
const verifiedEmpty=await runTrustedConnector(db,'owner-1',{
 ...authorized,readPage:async()=>({tasks:[],complete:true,nextCursor:null,verifiedEmpty:true})
},opts);
assert.equal(verifiedEmpty.received,0);
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,22,
 'even verified empty response never erases historical tasks');
await assert.rejects(()=>runTrustedConnector(db,'owner-2',{
 provider:'unknown',verifySession:async()=>({authorized:true}),
 readPage:async()=>({tasks:[],complete:true,verifiedEmpty:true})
},opts),e=>e instanceof ConnectorRunError&&e.code==='connector_not_configured');
console.log('校序连接器 M4：可信适配口、分页、去重、失败保护、过期/恢复和空结果防删除已通过');
