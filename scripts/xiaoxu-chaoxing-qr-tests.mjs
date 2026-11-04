import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
import {createChaoxingSchoolAdapter} from '../lib/academic-chaoxing-provider.mjs';
import {sealChaoxingQrChallenge,unsealChaoxingQrChallenge,connectAuthenticatedChaoxingQr}
 from '../lib/academic-school-login.mjs';
globalThis.crypto??=webcrypto;

const uuid='UUID123456abcDEF789',enc='ENC123456abcDEF789';
const urls=[],profile='<div aria-label="账号：本人"></div>'+
 '<div id="siteName" title="华北水利水电大学"></div>'+
 '<script>var uid = "1234567";</script>';
let polls=0,invalid=false;
const qrPng=Uint8Array.from(Buffer.from(
 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAOz8+VkAAAAASUVORK5CYII=','base64'));
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
 if(u.pathname==='/createqr'){
  assert.equal(options.method,'GET');
  assert.match(cookie,/qr_sid=initial/,'QR image must use original login jar');
  assert.equal(u.searchParams.get('uuid'),uuid);
  return new Response(qrPng,{headers:{'content-type':'image/png'}});
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
 if(u.hostname==='i.chaoxing.com'&&u.pathname==='/'){
  assert.match(cookie,/cx_s=ok/);
  return new Response('<html>official QR refer session established</html>');
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
const binary=await adapter.readQrImage({challenge:decoded});
assert.equal(binary.contentType,'image/png');
assert.equal(binary.source,'official');
assert.deepEqual([...binary.body],[...qrPng],'QR PNG is fetched using owner challenge');
assert.ok(urls.some(x=>x.path==='/createqr'&&x.cookie.includes('qr_sid=initial')),
 'QR image must reuse the cookie jar from /mlogin');
await assert.rejects(adapter.readQrImage({challenge:{...decoded,issuedAt:Date.now()-300000}}),
 e=>e.code==='session_expired');
const badImage=createChaoxingSchoolAdapter({fetchImpl:async()=>new Response('<html>not PNG</html>',{
 headers:{'content-type':'text/html'}})});
const fallback=await badImage.readQrImage({challenge:decoded});
assert.equal(fallback.contentType,'image/svg+xml');
assert.equal(fallback.source,'local');
assert.ok(fallback.body.includes('<svg'));
assert.ok(fallback.body.includes('</svg>'));
assert.ok(fallback.body.length>500&&fallback.body.length<220000);
assert.ok(!fallback.body.includes(enc)&&!fallback.body.includes(uuid),
 'fallback SVG must encode ticket only as QR modules, never as raw markup');
const offlineImage=createChaoxingSchoolAdapter({fetchImpl:async()=>{throw Error('network unavailable');}});
const offlineQr=await offlineImage.readQrImage({challenge:decoded});
assert.equal(offlineQr.source,'local','offline QR image must not hide a valid official login challenge');
assert.equal(offlineQr.body,fallback.body,'same official uuid/enc produces same local QR');
const jpeg=Uint8Array.from([255,216,...new Array(65).fill(1),255,217]);
const jpegAdapter=createChaoxingSchoolAdapter({fetchImpl:async()=>new Response(jpeg,{
 headers:{'content-type':'image/jpeg'}})});
const jpegImage=await jpegAdapter.readQrImage({challenge:decoded});
assert.equal(jpegImage.contentType,'image/jpeg');
assert.deepEqual([...jpegImage.body],[...jpeg]);
const waiting=await adapter.pollQrLogin({challenge:decoded});
assert.equal(waiting.status,'pending');
const scanned=await adapter.pollQrLogin({challenge:waiting.challenge});
assert.equal(scanned.status,'scanned');
const approved=await adapter.pollQrLogin({challenge:scanned.challenge});
assert.equal(approved.status,'approved');
assert.equal(approved.session.schema,'chaoxing-session-v1');
assert.equal(approved.session.uid,'1234567');
assert.ok(urls.some(x=>x.host==='i.chaoxing.com'&&x.path==='/'),'QR success must complete the official refer hop');
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
// A QR-authenticated school account must remain reconnectable if the inbox
// read fails, without claiming that any academic records were synchronized.
const writes=[];
const db={prepare(sql){return {
 bind(...args){this.args=args;return this;},
 async all(){return {results:[]};},
 async run(){writes.push({sql,args:this.args});return {success:true};}
};}};
const offlineAdapter={provider:'chaoxing',pollQrLogin:async()=>({}),
 forSession:async()=>({provider:'chaoxing',
  async verifySession(){return {authorized:true,school:'ncwu',
   identityBound:true,principalBoundTo:'owner1'};},
  async readNoticePage(){throw Error('upstream inbox temporarily unavailable');}
 })};
const pending=await connectAuthenticatedChaoxingQr({db,userId:'owner1',
 adapter:offlineAdapter,session:approved.session,secret,
 expiresAt:new Date(Date.now()+3600000).toISOString()});
assert.equal(pending.connected,false);
assert.equal(pending.syncPending,true);
assert.ok(writes.some(x=>x.sql.includes('INSERT INTO academic_provider_sessions')),
 'only the encrypted owner session is saved');
assert.ok(!JSON.stringify(writes).includes('cx_s=ok'),
 'no raw upstream cookie may be persisted');

console.log('Chaoxing QR challenge: official PNG/JPEG and MIT-rendered QR fallback, owner-bound poll and expiry passed');
