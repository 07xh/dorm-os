import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
import {createChaoxingSchoolAdapter} from '../lib/academic-chaoxing-provider.mjs';
import {sealChaoxingQrChallenge,unsealChaoxingQrChallenge}
 from '../lib/academic-school-login.mjs';
globalThis.crypto??=webcrypto;

const uuid='UUID123456abcDEF789',enc='ENC123456abcDEF789';
const urls=[],profile='<div aria-label="账号：本人"></div>'+
 '<div id="siteName" title="华北水利水电大学"></div>'+
 '<script>var uid = "1234567";</script>';
let polls=0,invalid=false;
const fakeFetch=async(url,options)=>{
 const u=new URL(url),cookie=options.headers.get('cookie')||'';
 urls.push({host:u.hostname,path:u.pathname,method:options.method,cookie});
 if(u.pathname==='/login')
  return new Response(null,{status:302,headers:{location:'/mlogin',
   'set-cookie':'qr_sid=initial; Path=/; Domain=.chaoxing.com; HttpOnly'}});
 if(u.pathname==='/mlogin'){
  assert.match(cookie,/qr_sid=initial/);
  return new Response('<input id="uuid" value="'+uuid+'"><input id="enc" value="'+enc+'">');
 }
 if(u.pathname==='/getauthstatus'){
  assert.equal(options.method,'POST');
  assert.equal(options.headers.get('origin'),'https://passport2.chaoxing.com');
  assert.equal(new URLSearchParams(options.body).get('uuid'),uuid);
  assert.match(cookie,/qr_sid=initial/);
  polls++;
  if(polls===1)return new Response('{"data":{"status":false,"type":1}}');
  if(polls===2)return new Response('{"data":{"status":false,"type":4}}');
  return new Response('{"data":{"status":true}}',{headers:{
   'set-cookie':'cx_s=ok; Domain=.chaoxing.com; Path=/; HttpOnly'}});
 }
 if(u.pathname==='/base'){
  if(invalid)return new Response('<html>logged out</html>');
  assert.match(cookie,/cx_s=ok/);
  return new Response(profile);
 }
 throw Error('unexpected request: '+u.pathname);
};
const adapter=createChaoxingSchoolAdapter({fetchImpl:fakeFetch});
const started=await adapter.beginQrLogin();
assert.equal(new URL(started.qrImageUrl).origin,'https://passport2.chaoxing.com');
assert.equal(new URL(started.qrImageUrl).pathname,'/createqr');
assert.equal(new URL(started.qrImageUrl).searchParams.get('uuid'),uuid);
assert.equal(started.challenge.schema,'chaoxing-qr-v1');
const secret=Buffer.from(webcrypto.getRandomValues(new Uint8Array(32))).toString('base64url');
const ticket=await sealChaoxingQrChallenge({challenge:started.challenge,userId:'owner1',secret});
assert.ok(!ticket.includes(enc)&&!ticket.includes(uuid)&&!ticket.includes('qr_sid'));
await assert.rejects(unsealChaoxingQrChallenge({ticket,userId:'another-owner',secret}),
 e=>e.code==='authorization_failed');
const decoded=await unsealChaoxingQrChallenge({ticket,userId:'owner1',secret});
assert.equal(decoded.uuid,uuid);
const waiting=await adapter.pollQrLogin({challenge:decoded});
assert.equal(waiting.status,'pending');
const scanned=await adapter.pollQrLogin({challenge:waiting.challenge});
assert.equal(scanned.status,'scanned');
const approved=await adapter.pollQrLogin({challenge:scanned.challenge});
assert.equal(approved.status,'approved');
assert.equal(approved.session.schema,'chaoxing-session-v1');
assert.equal(approved.session.uid,'1234567');
const reader=await adapter.forSession({session:approved.session,userId:'owner1'});
const proof=await reader.verifySession();
assert.equal(proof.school,'ncwu');
assert.equal(proof.principalBoundTo,'owner1');
assert.ok(urls.every(x=>x.host.endsWith('.chaoxing.com')));
invalid=true;
await assert.rejects(reader.verifySession(),e=>e.code==='authorization_failed');
await assert.rejects(adapter.pollQrLogin({challenge:{...decoded,issuedAt:Date.now()-300000}}),
 e=>e.code==='session_expired');
await assert.rejects(unsealChaoxingQrChallenge({ticket:'not-a-real-token',userId:'owner1',secret}),
 e=>e.code==='authorization_failed');
const cancelled=createChaoxingSchoolAdapter({fetchImpl:async(url,options)=>{
 if(new URL(url).pathname==='/getauthstatus')
  return new Response('{"data":{"status":false,"type":6}}');
 throw Error('unexpected');
}});
assert.equal((await cancelled.pollQrLogin({challenge:decoded})).status,'cancelled');
console.log('Chaoxing QR challenge: official URL, cookie jar, scanned/approved, owner-bound encryption and expiry passed');
