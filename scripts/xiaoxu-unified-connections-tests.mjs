import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {projectConnectionCenter,readConnectionCenter} from '../lib/academic-connection-center.mjs';
import {onRequestGet} from '../functions/api/v1/connections.js';

const all=projectConnectionCenter([],
 [{kind:'assignments',source:'chaoxing',provenance:'user_import',
   last_success_at:'2026-11-03T09:00:00.000Z'}]);
assert.deepEqual(all.map(x=>x.id),['ncwu','chaoxing','second_class']);
assert.equal(all[1].status,'not_connected','import is not a school login');
assert.deepEqual(all[1].importedKinds,['assignments']);
assert.equal(all[1].accountAuthorization,'not_verified');
assert.equal(all[1].automaticSync,false);

const historical=projectConnectionCenter([],[{kind:'second-class',
 source:'second_class',provenance:'trusted_readonly',
 last_success_at:'2026-11-01T05:00:00Z'}]);
assert.equal(historical[2].status,'not_connected',
 'historical data does not prove a persistent second-class login');
assert.deepEqual(historical[2].trustedKinds,['second-class']);

const trusted=projectConnectionCenter([
 {provider:'ncwu',mode:'readonly',status:'connected',last_success_at:'2026-11-03T10:00:00Z'},
 {provider:'chaoxing',mode:'import',status:'connected',last_success_at:'2026-11-03T10:00:00Z'}
],[{kind:'schedule',source:'ncwu',provenance:'trusted_readonly',
 last_success_at:'2026-11-03T10:00:00Z'}]);
assert.equal(trusted[0].status,'connected');
assert.deepEqual(trusted[0].trustedKinds,['schedule']);
assert.equal(trusted[1].status,'not_connected','import mode is never authenticated');
assert.equal(projectConnectionCenter([
 {provider:'ncwu',mode:'readonly',status:'connected',last_success_at:null}
],[])[0].status,'not_connected','no verified sync is not connected');
assert.ok(!JSON.stringify(trusted).includes('password'));

const seen=[];
const db={prepare(sql){
 return {bind(userId){
  seen.push({sql,userId});
  return {async all(){return {results:sql.includes('academic_connections')
   ?[{provider:'chaoxing',mode:'readonly',status:'expired',last_success_at:'2026-11-01T06:00:00Z'}]
   :[{kind:'assignments',source:'chaoxing',provenance:'trusted_readonly',last_success_at:'2026-11-01T06:00:00Z'}]};}};
 }};
}};
const read=await readConnectionCenter(db,'only-owner');
assert.equal(read.schema,'xiaoxu/connections-v1');
assert.equal(read.platforms[1].status,'expired');
assert.equal(read.platforms[1].lastSuccessAt,'2026-11-01T06:00:00Z');
assert.ok(seen.every(row=>row.userId==='only-owner' && row.sql.includes('user_id=?')),
 'all queries must be scoped to the authenticated owner');

const unauthenticated=await onRequestGet({request:new Request('https://test.example/api/v1/connections'),env:{}});
assert.equal(unauthenticated.status,503);
assert.match(unauthenticated.headers.get('cache-control')||'',/no-store/);
assert.equal((await unauthenticated.json()).error,'auth_not_configured');
// Do not suggest a campus SSO password works on the unrelated Chaoxing
// independent-account endpoint. Official school SSO is an external page,
// not a third-party grant to Xiaoxu.
const client=readFileSync(new URL('../public/xiaoxu-academic-client.js',import.meta.url),'utf8');
assert.ok(client.includes('超星独立密码'));
assert.ok(client.includes('学校统一身份认证'));
assert.ok(client.includes('fysso.chaoxing.com%2Fcassso%2Fncwu'));
assert.ok(client.includes('data-xu-school-login="chaoxing"'));
assert.ok(client.includes('不能把校园密码直接当成超星独立密码'));
assert.ok(client.includes('recoverQrAttempt()'),'pending QR status survives same-tab refresh');
assert.ok(client.includes('root.sessionStorage.setItem(CHAOXING_QR_STORAGE'));
assert.ok(client.includes('authenticated_pending_sync'),'verified QR distinguishes failed inbox refresh');
assert.ok(client.includes('xu-school-manage'),'secondary source details collapse by default');


console.log('Unified connection center: source scope, status proof, old imports, and anonymous safety passed');
