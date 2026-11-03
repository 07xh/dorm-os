import assert from 'node:assert/strict';
import {test} from 'node:test';
import {webcrypto} from 'node:crypto';
import {verifyOwnerSession} from '../lib/academic-owner-session.mjs';

const token='A'.repeat(43);
const hash=Buffer.from(await webcrypto.subtle.digest('SHA-256',new TextEncoder().encode(token))).toString('hex');
const request=(cookie)=>new Request('https://example.com/api/v1/health',{headers:cookie?{cookie}:{}});
const db=(record)=>({prepare(sql){
 assert.match(sql,/revoked_at IS NULL/);
 return {bind(value){assert.equal(value,hash);return {first:async()=>record};}};
}});
const opts={subtle:webcrypto.subtle,clock:()=>Date.parse('2026-11-03T10:00:00Z')};

test('missing database fails closed',async()=>{
 assert.equal((await verifyOwnerSession(request('__Host-xiaoxu_session='+token),{},opts)).status,503);
});
test('missing, duplicate and malformed cookies are denied',async()=>{
 for(const cookie of [null,'__Host-xiaoxu_session=short','__Host-xiaoxu_session='+token+'; __Host-xiaoxu_session='+token]){
  assert.equal((await verifyOwnerSession(request(cookie),{ACADEMIC_DB:db(null)},opts)).status,401);
 }
});
test('valid, expired and absent session',async()=>{
 const cookie='__Host-xiaoxu_session='+token;
 const valid={user_id:'owner',expires_at:'2026-11-04T10:00:00Z'};
 assert.deepEqual(await verifyOwnerSession(request(cookie),{ACADEMIC_DB:db(valid)},opts),{ok:true,userId:'owner'});
 for(const record of [null,{...valid,expires_at:'2026-11-02T10:00:00Z'}]){
  assert.equal((await verifyOwnerSession(request(cookie),{ACADEMIC_DB:db(record)},opts)).status,401);
 }
});
test('database failure fails closed',async()=>{
 const broken={prepare(){throw new Error('db down')}};
 assert.equal((await verifyOwnerSession(request('__Host-xiaoxu_session='+token),{ACADEMIC_DB:broken},opts)).status,503);
});
