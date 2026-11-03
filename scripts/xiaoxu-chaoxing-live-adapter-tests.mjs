import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
import {createChaoxingSchoolAdapter} from '../lib/academic-chaoxing-provider.mjs';
import {onRequest} from '../functions/api/v1/_middleware.js';
globalThis.crypto??=webcrypto;
const profile='<div aria-label="账号：测试本人" id="user"></div>'+
 '<div id="siteName" title="华北水利水电大学"></div><script>var uid = "1234567";</script>';
const item={idCode:'A'.repeat(32),uuid:'B'.repeat(42),tag:'courseId123',
 title:'作业发布',content:'课程名称：Python\r作业名称：练习一\r结束时间：2026-11-10 20:30',
 isread:0,sendTime:'2026-11-03 18:45:00'};
const urls=[],notices=[];
let invalidSession=false;
async function fakeFetch(url,options){
 const u=new URL(url),name=u.pathname,headers=options.headers;
 urls.push({host:u.hostname,path:name,body:options.body||'',cookie:headers.get('cookie')||''});
 if(name==='/fanyalogin'){
  assert.equal(options.method,'POST');
  assert.ok(!options.body.includes('test-password'),'must not post plaintext');
  return new Response('{"status":true}',{status:200,headers:{
   'set-cookie':'cx_s=ok; Domain=.chaoxing.com; Path=/; HttpOnly'}});
 }
 if(name==='/base'){
  if(invalidSession)return new Response('<html>passport2.chaoxing.com/login</html>',{status:200});
  assert.ok(headers.get('cookie').includes('cx_s=ok'));
  return new Response(profile,{status:200});
 }
 if(name==='/pc/notice/getNoticeList'){
  assert.equal(headers.get('origin'),'https://notice.chaoxing.com');
  assert.equal(headers.get('x-requested-with'),'XMLHttpRequest');
  const form=new URLSearchParams(options.body);
  assert.equal(form.get('type'),'2');
  assert.equal(form.get('year'),'2026');
  notices.push(form.get('lastValue'));
  if(notices.length===1)return new Response(JSON.stringify({status:true,notices:{
   lastPage:0,lastGetId:'F'.repeat(32),list:[item]}}),{status:200});
  return new Response('{"status":true}',{status:200});
 }
 throw Error('unexpected request');
}
const adapter=createChaoxingSchoolAdapter({fetchImpl:fakeFetch,clock:()=>Date.parse('2026-11-03T13:00:00Z')});
assert.equal(adapter.provider,'chaoxing');
const login=await adapter.login({username:'my-account',password:'test-password'});
assert.equal(login.session.schema,'chaoxing-session-v1');
assert.equal(login.session.uid,'1234567');
assert.ok(!JSON.stringify(login).includes('test-password'));
assert.ok(!JSON.stringify(login).includes('my-account'));
const read=await adapter.forSession({session:login.session,userId:'owner1'});
const proof=await read.verifySession();
assert.equal(proof.principalBoundTo,'owner1');
assert.equal(proof.provider,'chaoxing');
const page1=await read.readNoticePage({cursor:null});
assert.equal(page1.complete,false,'HAR lastPage=0 is not complete with 12 items');
assert.equal(page1.nextCursor,'F'.repeat(32));
const page2=await read.readNoticePage({cursor:page1.nextCursor});
assert.equal(page2.complete,true);
assert.equal(page2.terminalInferred,true);
assert.deepEqual(notices,['','F'.repeat(32)]);
invalidSession=true;
await assert.rejects(read.verifySession(),e=>e.code==='authorization_failed');
await assert.rejects(read.readNoticePage({cursor:'?' }),e=>e.code==='authorization_failed'||e.code==='invalid_response');
// Pages adapter must be registered only behind the existing D1 + key gate.
let invoked=false,seen;
const db={prepare(sql){return {all:async()=>{if(sql.includes('academic_chaoxing_notices')&&invalidSession)throw Error('missing');return {results:[]};}};}};
const ctx={request:new Request('https://site.example/api/v1/connections'),
 env:{ACADEMIC_DB:db,SCHOOL_SESSION_KEY:'A'.repeat(43)},data:{},
 next:async()=>{invoked=true;seen=ctx.data.academicSchoolAdapters;return new Response('next');}};
await onRequest(ctx);
assert.equal(invoked,true);
assert.ok(!seen?.chaoxing,'missing DB schema blocks login');
invalidSession=false;invoked=false;
await onRequest(ctx);
assert.equal(invoked,true);
assert.equal(seen?.chaoxing?.provider,'chaoxing');
assert.equal(typeof seen.chaoxing.login,'function');
assert.ok(!urls.some(x=>x.cookie.includes('test-password')));
console.log('Chaoxing trusted adapter: real request shape, cookie-scoped identity, HAR paging and connection registration passed');
