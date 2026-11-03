/* Synthetic integration: no school credentials, no live upstream access. */
import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
import {DatabaseSync} from 'node:sqlite';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import {bridgeStatus,createBrowserBridge,validateBrowserBridge,revokeBrowserBridge,
 BridgeError,BRIDGE_TTL_MS} from '../lib/academic-secondclass-browser-bridge.mjs';
import {onRequestPost as ingest,onRequestOptions} from '../functions/api/v1/second-class/bridge/ingest.js';
import {validateSnapshot} from '../lib/academic-contract.mjs';
globalThis.crypto??=webcrypto;
const sql=new DatabaseSync(':memory:');
sql.exec('PRAGMA foreign_keys=ON');
for(const file of [
 '0001_academic_core.sql','0003_academic_verified_snapshots.sql',
 '0013_academic_secondclass_browser_bridge.sql'
]) sql.exec(readFileSync(new URL('../migrations/'+file,import.meta.url),'utf8'));
const db={prepare(statement){
 let args=[];
 return {bind(...values){args=values;return this;},
  first(){return sql.prepare(statement).get(...args)??null;},
  all(){return {results:sql.prepare(statement).all(...args)};},
  run(){return sql.prepare(statement).run(...args);}
 };
},async batch(statements){
 sql.exec('BEGIN');
 try{const result=statements.map(item=>item.run());sql.exec('COMMIT');return result;}
 catch(e){sql.exec('ROLLBACK');throw e;}
}};
const owner='only-owner',other='other-owner';
const now=new Date().toISOString();
for(const user of [owner,other])
 sql.prepare('INSERT INTO academic_users(id,created_at) VALUES(?,?)').run(user,now);
assert.deepEqual(await bridgeStatus(db,owner),{state:'not_paired',lastSyncAt:null,expiresAt:null});
const paired=await createBrowserBridge(db,owner);
assert.match(paired.token,/^[A-Za-z0-9_-]{43}$/);
assert.equal((await bridgeStatus(db,owner)).state,'paired_not_synced');
assert.equal(JSON.stringify(sql.prepare('SELECT * FROM academic_secondclass_browser_bridges').all()).includes(paired.token),false);
assert.ok(BRIDGE_TTL_MS>=7*86400_000);
const {userId,tokenHash}=await validateBrowserBridge(db,paired.token);
assert.equal(userId,owner);assert.match(tokenHash,/^[a-f0-9]{64}$/);
const at=new Date().toISOString();
const snapshot=validateSnapshot({
 schema:'xiaoxu/academic-snapshot-v1',source:'second_class',kind:'second-class',
 verifiedSchool:'ncwu',capturedAt:at,complete:true,verifiedEmpty:false,
 records:[{externalId:'activity-1',term:'2026-2027-1',title:'合成志愿活动',
  activityStatus:'registered',reviewStatus:'unknown',settlementStatus:'settled',points:1.5}]
});
const url='https://dorm-os-dk9.pages.dev/api/v1/second-class/bridge/ingest';
const send=(token,body,origin='https://dekt.ncwu.edu.cn')=>ingest({
 request:new Request(url,{method:'POST',headers:{origin,authorization:'Bearer '+token,
  'content-type':'application/json'},body:JSON.stringify(body)}),
 env:{ACADEMIC_DB:db}
});
assert.equal((await onRequestOptions({request:new Request(url,{method:'OPTIONS',headers:{origin:'https://evil.invalid'}})})).status,403);
assert.equal((await onRequestOptions({request:new Request(url,{method:'OPTIONS',headers:{origin:'https://dekt.ncwu.edu.cn'}})})).status,204);
const badOrigin=await send(paired.token,snapshot,'https://evil.invalid');
assert.equal(badOrigin.status,403);
assert.equal((await send('x'.repeat(43),snapshot)).status,401);
assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM academic_verified_records').get().n,0);
const done=await send(paired.token,{
 schema:'xiaoxu/academic-snapshot-v1',source:'second_class',kind:'second-class',
 verifiedSchool:'ncwu',capturedAt:at,complete:true,verifiedEmpty:false,
 records:snapshot.records
});
assert.equal(done.status,202);
const body=await done.json();
assert.equal(body.provenance,'user_import');
assert.equal(body.syncMode,'user_browser_bridge');
assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM academic_verified_records').get().n,1);
assert.equal(sql.prepare('SELECT provenance FROM academic_verified_batches').get().provenance,'user_import');
assert.equal((await bridgeStatus(db,owner)).state,'browser_sync_enabled');
assert.equal((await send(paired.token,snapshot)).status,429,'ten-minute throttle');
const newKey=await createBrowserBridge(db,owner);
assert.notEqual(newKey.token,paired.token);
assert.equal((await send(paired.token,snapshot)).status,401,'rotated token must fail');
const badSection={...snapshot,kind:'assignments',source:'chaoxing'};
assert.equal((await send(newKey.token,badSection)).status,422,'token cannot write unrelated sections');
await revokeBrowserBridge(db,owner);
assert.equal((await send(newKey.token,snapshot)).status,401,'revoked token must fail');
assert.equal((await bridgeStatus(db,owner)).state,'not_paired');
assert.equal(sql.prepare('SELECT count(*) AS n FROM academic_verified_records WHERE user_id=?').get(owner).n,1,'revoke retains old history');
assert.equal(sql.prepare('SELECT count(*) AS n FROM academic_verified_records WHERE user_id=?').get(other).n,0);
sql.prepare("UPDATE academic_verified_batches SET provenance='trusted_readonly' WHERE user_id=?").run(owner);
const rekey=await createBrowserBridge(db,owner);
const denied=await send(rekey.token,{...snapshot,capturedAt:new Date(Date.now()+1000).toISOString()});
assert.equal(denied.status,409);
assert.equal((await denied.json()).error,'trusted_source_readonly');
assert.equal(sql.prepare('SELECT provenance FROM academic_verified_batches').get().provenance,'trusted_readonly');
assert.deepEqual(sql.prepare('PRAGMA foreign_key_check').all(),[]);
assert.equal(sql.prepare('PRAGMA quick_check').get().quick_check,'ok');
const script=readFileSync(new URL('../public/xiaoxu-second-class-export.user.js',import.meta.url),'utf8');
assert.match(script,/@connect\s+dorm-os-dk9\.pages\.dev/);
assert.match(script,/@grant\s+GM\.xmlHttpRequest/);
assert.match(script,/anonymous:true/,'browser cross-origin upload sends no cookie');
assert.match(script,/BRIDGE_CADENCE_MS=30\*60_000/);
assert.match(script,/GM\.setValue\(BRIDGE_TOKEN_KEY/);
assert.match(script,/body:new URLSearchParams\(form\)/);
const sandbox={};vm.runInNewContext(script,sandbox,{timeout:1000});
assert.ok(sandbox.XiaoxuSecondClassLocalExport?.collect);
const client=readFileSync(new URL('../public/xiaoxu-academic-client.js',import.meta.url),'utf8');
assert.match(client,/data-xu-bridge-create/);
assert.match(client,/data-xu-bridge-revoke/);
assert.ok(client.includes('/api/v1/second-class/bridge'));
sql.close();
console.log('M20 browser bridge: token isolation and rotation, per-owner snapshot, CORS, throttle, trusted-write protection, ScriptCat grants passed');
