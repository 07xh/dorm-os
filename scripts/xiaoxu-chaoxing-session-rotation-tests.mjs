import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {createChaoxingSchoolAdapter} from '../lib/academic-chaoxing-provider.mjs';
import {connectSchoolAccount,refreshSchoolAccount,readConnectedChaoxing,
 revokeSchoolAccount,schoolSessionStates} from '../lib/academic-school-login.mjs';

globalThis.crypto??=webcrypto;
const sqlite=new DatabaseSync(':memory:');
sqlite.exec('PRAGMA foreign_keys=ON');
for(const name of ['0001_academic_core.sql','0011_academic_provider_sessions.sql'])
 sqlite.exec(readFileSync(new URL('../migrations/'+name,import.meta.url),'utf8'));
sqlite.prepare('INSERT INTO academic_users(id,created_at) VALUES (?,?)')
 .run('owner1',new Date().toISOString());
const db={
 prepare(sql){let args=[];return {
  bind(...v){args=v;return this;},
  first(){return sqlite.prepare(sql).get(...args)??null;},
  all(){return {results:sqlite.prepare(sql).all(...args)};},
  run(){return {meta:{changes:sqlite.prepare(sql).run(...args).changes}};}
 };},
 async batch(items){sqlite.exec('BEGIN');try{
  const result=items.map(x=>x.run());sqlite.exec('COMMIT');return result;
 }catch(e){sqlite.exec('ROLLBACK');throw e;}}
};
const secret=Buffer.from(webcrypto.getRandomValues(new Uint8Array(32))).toString('base64url');
const profile='<div aria-label="账号：测试本人"></div>'+
 '<div id="siteName" title="华北水利水电大学"></div>'+
 '<script>var uid="1234567";var fid="333";</script>';
let mode='normal',baseCookies=[],noticeCookies=[];
const fakeFetch=async(url,options)=>{
 const u=new URL(url),cookie=options.headers.get('cookie')||'';
 if(u.pathname==='/fanyalogin')
  return new Response('{"status":true}',{headers:{
   'set-cookie':'cx_s=initial; Domain=.chaoxing.com; Path=/; HttpOnly'}});
 if(u.pathname==='/base'){
  baseCookies.push(cookie);
  if(mode==='offline')return new Response('temporarily down',{status:503});
  if(mode==='expired')return new Response('<html>login required</html>');
  if(!/^cx_s=(initial|rotated|rotated403|rotated401)(?:;|$)/.test(cookie))
   return new Response('<html>login required</html>');
  if(mode==='rotate'&&cookie.includes('cx_s=initial'))
   return new Response(profile,{headers:{
    'set-cookie':'cx_s=rotated; Domain=.chaoxing.com; Path=/; HttpOnly'}});
  return new Response(profile);
 }
 if(u.pathname==='/pc/notice/getNoticeList'){
  noticeCookies.push(cookie);
  if(mode==='downstream403')return new Response('access not available',{status:403,
   headers:{'set-cookie':'cx_s=rotated403; Domain=.chaoxing.com; Path=/; HttpOnly'}});
  if(mode==='downstream401')return new Response('access not available',{status:401,
   headers:{'set-cookie':'cx_s=rotated401; Domain=.chaoxing.com; Path=/; HttpOnly'}});
  assert.match(cookie,/cx_s=(initial|rotated|rotated403|rotated401)/);
  return new Response(JSON.stringify({status:true,notices:{
   list:[],lastPage:true,lastGetId:''}}));
 }
 throw Error('unexpected upstream path: '+u.pathname);
};
const newAdapter=()=>createChaoxingSchoolAdapter({fetchImpl:fakeFetch});
const connect=()=>connectSchoolAccount({db,userId:'owner1',provider:'chaoxing',
 adapter:newAdapter(),username:'owner',password:'synthetic',secret,
 sync:async()=>({lastSuccessAt:new Date().toISOString()})});
const vault=()=>sqlite.prepare(
 "SELECT ciphertext,nonce FROM academic_provider_sessions WHERE user_id='owner1' AND provider='chaoxing'"
).get();
const read=()=>readConnectedChaoxing({db,userId:'owner1',adapter:newAdapter(),secret,
 reader:async(source)=>source.readNoticePage({cursor:null})});

assert.equal((await connect()).connected,true);
assert.deepEqual(await schoolSessionStates(db,'owner1',secret),{chaoxing:'active'});
// A still-valid official login must survive several days without opening the
// app. Each verified read renews the local lease; unverified failures do not.
const realNow=Date.now,startedAt=realNow(),day=86_400_000;
try{
 Date.now=()=>startedAt+4*day;
 await read();
 assert.deepEqual(await schoolSessionStates(db,'owner1',secret),{chaoxing:'active'});
 Date.now=()=>startedAt+33*day;
 await refreshSchoolAccount({db,userId:'owner1',provider:'chaoxing',
  adapter:newAdapter(),secret,sync:async()=>({lastSuccessAt:new Date().toISOString()})});
 const verified=vault();
 mode='offline';
 Date.now=()=>startedAt+62*day;
 await assert.rejects(()=>read(),e=>e.code==='upstream_unavailable');
 assert.deepEqual(vault(),verified,'failed identity checks must not renew the lease');
 mode='normal';
 Date.now=()=>startedAt+64*day;
 const upstreamCalls=baseCookies.length;
 await assert.rejects(()=>read(),e=>e.code==='session_expired');
 assert.equal(baseCookies.length,upstreamCalls,'idle retention remains bounded');
}finally{Date.now=realNow;mode='normal';}
await connect();
const original=vault();
// First trusted read rotates an upstream cookie; a later independent reader
// must use the rotated value from D1, not the original login jar.
mode='rotate';
await read();
const rotated=vault();
assert.notEqual(rotated.ciphertext,original.ciphertext);
assert.match(noticeCookies.at(-1),/cx_s=rotated/);
mode='normal';
baseCookies.length=0;
await read();
assert.match(baseCookies[0],/cx_s=rotated/,'reloaded session must have new cookie');
assert.deepEqual(await schoolSessionStates(db,'owner1',secret),{chaoxing:'active'});

// A flaky profile endpoint is not evidence that the owner has logged out.
mode='offline';
await assert.rejects(()=>refreshSchoolAccount({db,userId:'owner1',
 provider:'chaoxing',adapter:newAdapter(),secret}),
 e=>e.code==='sync_failed');
assert.deepEqual(await schoolSessionStates(db,'owner1',secret),{chaoxing:'active'});

// A verified owner can still receive 403 from one downstream inbox endpoint.
// Do not delete the good owner session or hide verified historical data.
mode='downstream403';
await assert.rejects(()=>read(),e=>e.code==='upstream_unavailable');
await assert.rejects(()=>refreshSchoolAccount({db,userId:'owner1',
 provider:'chaoxing',adapter:newAdapter(),secret,
 sync:async(_db,_user,_provider,source)=>{
  await source.readNoticePage({cursor:null});
 }}),e=>e.code==='sync_failed');
assert.deepEqual(await schoolSessionStates(db,'owner1',secret),{chaoxing:'active'});
// A 403 can rotate the jar even though that request failed. It must survive
// into the next independent read, not force the owner to rescan.
mode='normal';
baseCookies.length=0;
await read();
assert.match(baseCookies[0],/cx_s=rotated403/);
// A downstream 401 after independently verified owner identity is also
// insufficient to prove global logout; persist its rotated cookie as well.
mode='downstream401';
await assert.rejects(()=>read(),e=>e.code==='upstream_unavailable');
assert.deepEqual(await schoolSessionStates(db,'owner1',secret),{chaoxing:'active'});
mode='normal';
baseCookies.length=0;
await read();
assert.match(baseCookies[0],/cx_s=rotated401/);

// A newer login wins against a stale trusted read that completes later.
mode='normal';
const stale=vault();
await assert.rejects(()=>readConnectedChaoxing({db,userId:'owner1',
 adapter:newAdapter(),secret,reader:async()=>{
  await revokeSchoolAccount(db,'owner1','chaoxing');
  await connect();
  return {ok:true};
 }}),e=>e.code==='sync_failed','a newer login must never look like logout');
assert.notEqual(vault().ciphertext,stale.ciphertext);
assert.deepEqual(await schoolSessionStates(db,'owner1',secret),{chaoxing:'active'});
// Manual refresh must follow the same rule: a newer connection wins without
// presenting the old request's CAS miss as a request to log in again.
const manualBefore=vault();
await assert.rejects(()=>refreshSchoolAccount({db,userId:'owner1',
 provider:'chaoxing',adapter:newAdapter(),secret,
 sync:async()=>{
  await connect();
  return {lastSuccessAt:new Date().toISOString()};
 }}),e=>e.code==='sync_failed');
assert.notEqual(vault().ciphertext,manualBefore.ciphertext);
assert.deepEqual(await schoolSessionStates(db,'owner1',secret),{chaoxing:'active'});

// Only an independently failed official identity check expires the session.
mode='expired';
await assert.rejects(()=>refreshSchoolAccount({db,userId:'owner1',
 provider:'chaoxing',adapter:newAdapter(),secret}),e=>e.code==='session_expired');
assert.deepEqual(await schoolSessionStates(db,'owner1',secret),{});
mode='normal';
assert.equal((await connect()).connected,true);
assert.deepEqual(await schoolSessionStates(db,'owner1',secret),{chaoxing:'active'});
assert.equal(sqlite.prepare('PRAGMA integrity_check').get().integrity_check,'ok');
sqlite.close();
console.log('Chaoxing rotated-cookie persistence, restart, failure classes, concurrency and reconnect passed');
