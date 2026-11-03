import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {webcrypto} from 'node:crypto';
import {runInNewContext} from 'node:vm';
import {projectSecondClass} from '../lib/academic-second-class-view.mjs';
import {verifyAcademicAccess} from '../lib/academic-access.mjs';
import {onRequestGet} from '../functions/api/v1/second-class.js';
import {onRequestPost} from '../functions/api/v1/verified/[section].js';

const projected=projectSecondClass([
 {externalId:'a1',title:'活动一',hours:1.5,points:3,activityStatus:'completed',reviewStatus:'approved'},
 {externalId:'a2',title:'活动二',reviewStatus:'unknown'}
]);
assert.equal(projected.summary.recordCount,2);
assert.deepEqual(projected.summary.recordedHours,{knownCount:1,sum:1.5});
assert.deepEqual(projected.summary.recordedPoints,{knownCount:1,sum:3});
assert.equal(projected.summary.recordedCredits.sum,null,'not reported is unknown, never zero');
assert.equal(projected.summary.isOfficialAggregate,false);
assert.equal(projected.summary.activityStatusCounts.unknown,1);
assert.equal(projectSecondClass([]).summary.recordedHours.sum,null);
assert.throws(()=>projectSecondClass([{externalId:'wrong',title:'invalid',points:-2}]),/超出范围/);
assert.throws(()=>projectSecondClass([{externalId:'wrong',title:'invalid',cookie:'secret'}]),/不支持/);

const sql=new DatabaseSync(':memory:');
sql.exec('PRAGMA foreign_keys=ON');
for(const n of ['0001_academic_core.sql','0003_academic_verified_snapshots.sql',
 '0008_academic_second_class_source.sql','0009_ncwu_second_class_official_scores.sql']){
 sql.exec(readFileSync(new URL('../migrations/'+n,import.meta.url),'utf8'));
}
const db={
 prepare(query){let args=[];return{
  bind(...values){args=values;return this;},
  first(){return sql.prepare(query).get(...args)??null;},
  all(){return {results:sql.prepare(query).all(...args)};},
  run(){return sql.prepare(query).run(...args);}
 };},
 async batch(commands){
  sql.exec('BEGIN');
  try{const result=commands.map(x=>x.run());sql.exec('COMMIT');return result;}
  catch(e){sql.exec('ROLLBACK');throw e;}
 }
};
const domain='personal-second-class.cloudflareaccess.com',aud='second-class-test';
const pair=await webcrypto.subtle.generateKey({
 name:'RSASSA-PKCS1-v1_5',modulusLength:2048,
 publicExponent:new Uint8Array([1,0,1]),hash:'SHA-256'
},true,['sign','verify']);
const jwk=await webcrypto.subtle.exportKey('jwk',pair.publicKey);
Object.assign(jwk,{kid:'test-kid',alg:'RS256',use:'sig'});
const b64=x=>Buffer.from(JSON.stringify(x)).toString('base64url');
async function sign(sub){
 const at=Math.floor(Date.now()/1000);
 const payload={iss:'https://'+domain,aud:[aud],sub,iat:at-20,exp:at+3600};
 const p=b64({alg:'RS256',kid:jwk.kid})+'.'+b64(payload);
 const sig=await webcrypto.subtle.sign('RSASSA-PKCS1-v1_5',pair.privateKey,
   new TextEncoder().encode(p));
 return p+'.'+Buffer.from(sig).toString('base64url');
}
const jwtA=await sign('owner-a'),jwtB=await sign('owner-b');
const env={ACCESS_TEAM_DOMAIN:domain,ACCESS_AUD:aud,ACADEMIC_DB:db};
const oldFetch=globalThis.fetch;
globalThis.fetch=async()=>new Response(JSON.stringify({keys:[jwk]}),{
 status:200,headers:{'content-type':'application/json'}
});
function req(jwt,environment=env){
 return {request:new Request('https://xiaoxu.test/api/v1/second-class',{
  headers:{'Cf-Access-Jwt-Assertion':jwt}
 }),env:environment};
}
function addBatch(userId,provenance,records,capturedAt,id){
 sql.prepare('INSERT OR IGNORE INTO academic_users(id,created_at) VALUES (?,?)')
  .run(userId,capturedAt);
 sql.prepare('INSERT INTO academic_verified_batches(user_id,kind,source,last_success_at,batch_id,updated_at,provenance) VALUES (?, ?, ?, ?, ?, ?, ?)')
  .run(userId,'second-class','second_class',capturedAt,id,capturedAt,provenance);
 for(const record of records){
  sql.prepare('INSERT INTO academic_verified_records(user_id,kind,source,external_id,term,payload,batch_id,updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)')
   .run(userId,'second-class','second_class',record.externalId,record.term||'',JSON.stringify(record),id,capturedAt);
 }
}
try{
 assert.equal((await onRequestGet(req(''))).status,401);
 assert.equal((await onRequestGet(req(jwtA,{}))).status,503);
 const noDatabase={ACCESS_TEAM_DOMAIN:domain,ACCESS_AUD:aud};
 assert.equal((await onRequestGet(req(jwtA,noDatabase))).status,503);
 let r=await onRequestGet(req(jwtA));
 assert.equal(r.status,200);
 assert.equal(r.headers.get('cache-control'),'private, no-store');
 let body=await r.json();
 assert.equal(body.status,'not_connected');
 assert.deepEqual(body.records,[]);
 assert.equal(body.summary,null);
 assert.equal(body.automaticSync,false);
 assert.equal(body.officialScore,null,'imports have no official totals');
 const idA=(await verifyAcademicAccess(req(jwtA).request,env)).userId;
 const idB=(await verifyAcademicAccess(req(jwtB).request,env)).userId;
 const at=new Date().toISOString();
 addBatch(idA,'user_import',[
  {externalId:'activity-1',title:'<script>private</script>',hours:2,points:5,
   activityStatus:'completed',reviewStatus:'approved'},
  {externalId:'activity-2',title:'没有学时的活动'}
 ],at,'personal-1');
 addBatch(idB,'trusted_readonly',[{externalId:'other-1',title:'另一用户的私有活动',hours:7}],
  at,'trusted-b');
 r=await onRequestGet(req(jwtA));
 body=await r.json();
 assert.equal(body.status,'ready');
 assert.equal(body.provenance,'user_import');
 assert.equal(body.records.length,2);
 assert.equal(body.summary.recordedHours.knownCount,1);
 assert.equal(body.summary.recordedHours.sum,2);
 assert.equal(body.summary.recordedPoints.sum,5);
 assert.equal(body.lastReadOnlyAt,null);
 assert.equal(body.automaticSync,false);
 assert.equal(JSON.stringify(body).includes('另一用户的私有活动'),false);
 const other=await (await onRequestGet(req(jwtB))).json();
 assert.equal(other.records.length,1,'users cannot read each other');
 assert.equal(other.records[0].title,'另一用户的私有活动');
 // New school-attested batch and audit: do not fabricate background scheduling.
 sql.prepare("UPDATE academic_verified_batches SET provenance='trusted_readonly' WHERE user_id=? AND kind='second-class'")
  .run(idA);
 sql.prepare("INSERT INTO academic_second_class_sync_audit(id,user_id,source,status,observed_at,item_count) VALUES (?,?,'second_class','success',?,?)")
  .run('audit-a',idA,at,2);
 body=await (await onRequestGet(req(jwtA))).json();
 assert.equal(body.provenance,'trusted_readonly');
 assert.equal(body.lastReadOnlyAt,at);
 assert.equal(body.automaticSync,false);
 const payload={
  schema:'xiaoxu/academic-snapshot-v1',source:'second_class',
  kind:'second-class',verifiedSchool:'ncwu',capturedAt:new Date().toISOString(),
  records:[{externalId:'new-record',title:'恶意覆盖'}],complete:true
 };
 const attempt=await onRequestPost({params:{section:'snapshot'},env,
   request:new Request('https://xiaoxu.test/api/v1/verified/snapshot',{
     method:'POST',headers:{'Cf-Access-Jwt-Assertion':jwtA,'Content-Type':'application/json'},
     body:JSON.stringify(payload)
   })
 });
 assert.equal(attempt.status,409,'user import must not downgrade a trusted source');
 assert.equal((await attempt.json()).status,'trusted_source_readonly');
 assert.equal((await (await onRequestGet(req(jwtA))).json()).records.length,2);
 sql.prepare('UPDATE academic_verified_batches SET last_success_at=? WHERE user_id=?')
  .run('2026-10-01T00:00:00.000Z',idA);
 assert.equal((await (await onRequestGet(req(jwtA))).json()).status,'stale');
 assert.equal((await onRequestGet(req(jwtA,{
   ACCESS_TEAM_DOMAIN:domain,ACCESS_AUD:aud,
   ACADEMIC_DB:{prepare(){throw Error('fake database down');}}
 }))).status,503);
}finally{globalThis.fetch=oldFetch;sql.close();}

const ui=readFileSync(new URL('../public/xiaoxu-academic-client.js',import.meta.url),'utf8');
const html=readFileSync(new URL('../public/index.html',import.meta.url),'utf8');
const sw=readFileSync(new URL('../public/sw.js',import.meta.url),'utf8');
const release=readFileSync(new URL('./verify-release.mjs',import.meta.url),'utf8');
assert.ok(release.includes("'/api/v1/second-class'"),'production deny gate');
assert.ok(html.includes("XiaoxuAcademic.panel('second-class')"));
assert.ok(sw.includes('v90-second-class'));
const calls=[];
const mock={kind:'second-class',status:'ready',provenance:'user_import',
 records:[{externalId:'A1',title:'<script>unsafe</script>',hours:2,points:5}],
 lastSuccessAt:'2026-11-03T00:00:00.000Z',
 summary:{recordCount:1,recordedHours:{sum:2,knownCount:1},
 recordedPoints:{sum:5,knownCount:1}},automaticSync:false};
const context={fetch:async (url)=>{
 calls.push(url);
 const data=url==='/api/v1/verified/connections'?{sources:[]}:
  url==='/api/v1/second-class'?mock:{kind:'grades',status:'not_connected',records:[]};
 return new Response(JSON.stringify(data),{status:200,headers:{'content-type':'application/json'}});
},Response};
runInNewContext(ui,context);
const client=context.XiaoxuAcademic;
assert.equal(calls.length,0,'no private query before connection click');
await client.connect();
assert.ok(calls.includes('/api/v1/second-class'),'client reads own second class endpoint');
const rendered=client.panel('second-class');
assert.ok(rendered.includes('已保存活动记录'));
assert.ok(rendered.includes('字段缺失时不按零计算'));
assert.ok(!rendered.includes('<script>'),'escaped private activity titles');
client.disconnect();
assert.equal(client.hasData('second-class'),false);
console.log('校序二课 API: owner-scoped D1, Access signature, no fake aggregate, trusted provenance, read-only guard, UI client, no prior access all passed');
