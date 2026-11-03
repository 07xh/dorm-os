import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {webcrypto} from 'node:crypto';
import {validateSnapshot,normalizeRecord,KINDS} from '../lib/academic-contract.mjs';
import {onRequestGet,onRequestPost} from '../functions/api/v1/verified/[section].js';

const at=new Date().toISOString();
const sample={schema:'xiaoxu/academic-snapshot-v1',source:'chaoxing',kind:'assignments',
 verifiedSchool:'ncwu',capturedAt:at,complete:true,records:[{
 externalId:'work:123:456',term:'2026-2027-1',title:'课程作业',
 course:'高等数学',platformStatus:'todo',dueAt:'2026-11-20T23:59:00+08:00'
 }]};
assert.equal(KINDS.length,5);
const normalized=validateSnapshot(sample);
assert.equal(normalized.records[0].externalId,'work:123:456');
assert.throws(()=>validateSnapshot({...sample,complete:false}),/不完整/);
assert.throws(()=>validateSnapshot({...sample,source:'ncwu'}),/平台/);
assert.throws(()=>validateSnapshot({...sample,records:[]}),/空列表/);
assert.throws(()=>validateSnapshot({...sample,records:[sample.records[0],sample.records[0]]}),/重复/);
assert.throws(()=>validateSnapshot({...sample,records:[{...sample.records[0],cookie:'secret'}]}),/不支持/);
assert.throws(()=>validateSnapshot({...sample,records:[{...sample.records[0],url:'https://example.com/?enc=signed'}]}),/不支持/);
assert.throws(()=>validateSnapshot({...sample,records:[{...sample.records[0],dueAt:'not a time'}]}),/无效/);
assert.throws(()=>normalizeRecord('schedule',{externalId:'course-1',title:'画图课',term:'2026-1',weekday:1,startSection:1,endSection:2,weeks:[1,1]}),/重复/);

const database=new DatabaseSync(':memory:');
database.exec('PRAGMA foreign_keys=ON');
database.exec(readFileSync(new URL('../migrations/0001_academic_core.sql',import.meta.url),'utf8'));
database.exec(readFileSync(new URL('../migrations/0003_academic_verified_snapshots.sql',import.meta.url),'utf8'));
database.exec(readFileSync(new URL('../migrations/0008_academic_second_class_source.sql',import.meta.url),'utf8'))
for(const table of ['academic_users','academic_assignments','academic_verified_batches','academic_verified_records','academic_verified_ingests']){
 assert.ok(database.prepare("SELECT name FROM sqlite_master WHERE name=?").get(table),'missing table '+table);
}
const db={
 prepare(sql){
  let params=[];
  return {
   bind(...data){params=data;return this;},
   first(){return database.prepare(sql).get(...params)??null;},
   all(){return {results:database.prepare(sql).all(...params)};},
   run(){return database.prepare(sql).run(...params);}
  };
 },
 async batch(commands){
  database.exec('BEGIN');
  try {const result=commands.map(x=>x.run());database.exec('COMMIT');return result;}
  catch(e){database.exec('ROLLBACK');throw e;}
 }
};
const team='private-test.cloudflareaccess.com',aud='private-app-aud';
const pair=await webcrypto.subtle.generateKey({name:'RSASSA-PKCS1-v1_5',modulusLength:2048,
 publicExponent:new Uint8Array([1,0,1]),hash:'SHA-256'},true,['sign','verify']);
const publicKey=await webcrypto.subtle.exportKey('jwk',pair.publicKey);
Object.assign(publicKey,{kid:'verified-test-kid',alg:'RS256',use:'sig'});
const b64=x=>Buffer.from(typeof x==='string'?x:JSON.stringify(x)).toString('base64url');
const ts=Math.floor(Date.now()/1000);
async function signed(subject){
 const payload={iss:'https://'+team,aud:[aud],sub:subject,iat:ts-5,exp:ts+3600};
 const body=b64({kid:'verified-test-kid',alg:'RS256'})+'.'+b64(payload);
 const signature=await webcrypto.subtle.sign('RSASSA-PKCS1-v1_5',pair.privateKey,new TextEncoder().encode(body));
 return body+'.'+Buffer.from(signature).toString('base64url');
}
const jwt=await signed('student-a'),other=await signed('student-b');
const env={ACADEMIC_DB:db,ACCESS_TEAM_DOMAIN:team,ACCESS_AUD:aud};
function context(section,authorization='',payload){
 const headers={'Cf-Access-Jwt-Assertion':authorization};
 if(payload)headers['content-type']='application/json';
 return {params:{section},env,request:new Request('https://academic.test/api/v1/verified/'+section,
  {method:payload?'POST':'GET',headers,body:payload?JSON.stringify(payload):undefined})};
}
const previous=globalThis.fetch;
globalThis.fetch=async()=>new Response(JSON.stringify({keys:[publicKey]}),{status:200});
try{
 const noConfig=await onRequestGet({...context('connections',jwt),env:{}});
 assert.equal(noConfig.status,503);
 const noIdentity=await onRequestGet(context('connections'));
 assert.equal(noIdentity.status,401);
 const start=await onRequestGet(context('grades',jwt));
 assert.equal(start.status,200);
 assert.equal((await start.json()).status,'not_connected');
 const inserted=await onRequestPost(context('snapshot',jwt,sample));
 assert.equal(inserted.status,202,'Signed owner writes verified snapshot');
 const listing=await onRequestGet(context('assignments',jwt));
 const body=await listing.json();
 assert.equal(body.status,'ready');
 assert.equal(body.records.length,1);
 assert.equal(body.records[0].title,'课程作业');
 assert.equal(body.provenance,'user_import','browser upload cannot gain trusted source provenance');
 const connBody=await (await onRequestGet(context('connections',jwt))).json();
 assert.equal(connBody.sources.find(x=>x.kind==='assignments').state,'personal_snapshot');
 const otherUser=await onRequestGet(context('assignments',other));
 assert.equal((await otherUser.json()).records.length,0,'User isolation blocks other identities');
 const invalid=await onRequestPost(context('snapshot',jwt,{...sample,records:[{...sample.records[0],password:'secret'}]}));
 assert.equal(invalid.status,422);
 const empty=await onRequestPost(context('snapshot',jwt,{...sample,records:[],verifiedEmpty:true}));
 assert.equal(empty.status,202);
 const afterEmpty=await onRequestGet(context('assignments',jwt));
 assert.equal((await afterEmpty.json()).records.length,0,'Verified empty current snapshot hides older rows');
 const total=database.prepare('SELECT count(*) AS c FROM academic_verified_records').get().c;
 assert.equal(total,1,'Older verified record stays stored for audit without current presentation');
}finally{globalThis.fetch=previous;database.close();}
console.log('Academic OS M1+M2: SQLite schemas, signed Access, five-domain contracts, isolation, fail-closed and zero-data verification passed');
