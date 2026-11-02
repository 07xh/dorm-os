import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {webcrypto} from 'node:crypto';
import {
  AcademicInputError,normalizeManualAssignment,createManualAssignment,listAssignments
} from '../lib/academic-assignments.mjs';
import {verifyAcademicAccess} from '../lib/academic-access.mjs';
import {onRequestGet,onRequestPost} from '../functions/api/v1/assignments.js';

const database = new DatabaseSync(':memory:');
database.exec('PRAGMA foreign_keys=ON');
const migration = readFileSync(new URL('../migrations/0001_academic_core.sql',import.meta.url),'utf8');
database.exec(migration);
const table = database.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name=?");
for(const name of ['academic_users','academic_connections','academic_courses',
  'academic_assignments','academic_assignment_sources','academic_sync_runs',
  'academic_assignment_events']) assert.ok(table.get(name), 'migration missing '+name);
const db = {
  prepare(sql) {
    let args=[];
    return {
      bind(...items) { args=items; return this; },
      first() { return database.prepare(sql).get(...args) ?? null; },
      all() { return {results:database.prepare(sql).all(...args)}; },
      run() { return database.prepare(sql).run(...args); }
    };
  },
  async batch(stmts) {
    database.exec('BEGIN');
    try {
      const result=stmts.map(s=>s.run());
      database.exec('COMMIT');
      return result;
    } catch(e) { database.exec('ROLLBACK'); throw e; }
  }
};
assert.throws(()=>normalizeManualAssignment({title:'X',deadline:{precision:'date',date:'2026-02-30'}}),AcademicInputError);
assert.throws(()=>normalizeManualAssignment({title:'X',deadline:{precision:'exact',date:'2026-11-20',at:'2026-11-20T23:59:00'}}),AcademicInputError);
assert.throws(()=>normalizeManualAssignment({title:'X',deadline:{precision:'unknown',date:'2026-11-20'}}),AcademicInputError);
assert.throws(()=>normalizeManualAssignment({title:'X',localStatus:'submitted'}),AcademicInputError);
const exact={title:' 工程制图作业 ',deadline:{
  precision:'exact',date:'2026-11-20',at:'2026-11-20T23:59:00+08:00'
}};
const normal=normalizeManualAssignment(exact);
assert.equal(normal.dueAtUtc,'2026-11-20T15:59:00.000Z');
let idx=0;
const added=await createManualAssignment(db,'user-a',exact,{
  id:()=> 'a-'+(++idx),now:()=> '2026-11-03T02:00:00.000Z'
});
assert.equal(added.title,'工程制图作业');
assert.equal(added.duePrecision,'exact');
assert.equal((await listAssignments(db,'user-a')).length,1);
assert.equal((await listAssignments(db,'user-b')).length,0,'separate users cannot read records');
await assert.rejects(()=>createManualAssignment(db,'user-b',{title:'T',courseId:'unknown'}),AcademicInputError);
assert.equal((await listAssignments(db,'user-b')).length,0,'rejected create leaves no record');
database.prepare("INSERT INTO academic_courses(id,user_id,title,created_at) VALUES (?,?,?,?)")
  .run('course-a','user-a','Python','2026-11-03T00:00:00Z');
await assert.rejects(()=>createManualAssignment(db,'user-b',{title:'T',courseId:'course-a'}),AcademicInputError);
await createManualAssignment(db,'user-a',{title:'仅日期',deadline:{precision:'date',date:'2026-11-21'}},{
  id:()=> 'a-2',now:()=> '2026-11-03T03:00:00.000Z'
});
assert.equal((await listAssignments(db,'user-a',{status:'todo'})).length,2);
await assert.rejects(()=>listAssignments(db,'user-a',{limit:101}),AcademicInputError);
assert.throws(()=>database.prepare(
  "INSERT INTO academic_assignments (id,user_id,title,due_precision,created_at,updated_at) VALUES ('bad','user-a','bad','date','x','x')"
).run(),/CHECK constraint/,'date-only without a real date must be rejected');

const team='test-team.cloudflareaccess.com', audience='academic-aud';
const pair=await webcrypto.subtle.generateKey({
  name:'RSASSA-PKCS1-v1_5',modulusLength:2048,
  publicExponent:new Uint8Array([1,0,1]),hash:'SHA-256'
},true,['sign','verify']);
const publicKey=await webcrypto.subtle.exportKey('jwk',pair.publicKey);
Object.assign(publicKey,{kid:'academic-test-key',alg:'RS256',use:'sig'});
const b64=v=>Buffer.from(typeof v==='string'?v:JSON.stringify(v)).toString('base64url');
const now=Math.floor(Date.now()/1000);
async function signed(payload) {
  const content=b64({alg:'RS256',kid:'academic-test-key'})+'.'+b64(payload);
  const sig=await webcrypto.subtle.sign('RSASSA-PKCS1-v1_5',pair.privateKey,new TextEncoder().encode(content));
  return content+'.'+Buffer.from(sig).toString('base64url');
}
const claims={iss:'https://'+team,aud:[audience],sub:'student-unique-id',exp:now+3600,iat:now-10};
const jwt=await signed(claims);
const fetcher=async()=>new Response(JSON.stringify({keys:[publicKey]}),{status:200});
const env={ACCESS_TEAM_DOMAIN:team,ACCESS_AUD:audience,ACADEMIC_DB:db};
const requestFor=(token,init={})=>new Request('https://academic.example/api/v1/assignments',{
  ...init,headers:{'Cf-Access-Jwt-Assertion':token,...(init.headers||{})}
});
const auth=await verifyAcademicAccess(requestFor(jwt),env,{fetcher,subtle:webcrypto.subtle});
assert.equal(auth.ok,true,'valid signed JWT must authenticate');
assert.equal(auth.userId.length,64);
for(const bad of [
  await signed({...claims,aud:['wrong-audience']}),
  await signed({...claims,exp:now-1}),
  await signed({...claims,iss:'https://evil.example'}),
  jwt.slice(0,-3)+'abc'
]) {
  const result=await verifyAcademicAccess(requestFor(bad),env,{fetcher,subtle:webcrypto.subtle});
  assert.equal(result.ok,false,'wrong claims or modified signature must fail');
}
assert.equal((await verifyAcademicAccess(requestFor(jwt),{}, {fetcher})).status,503);
const blocked=await onRequestGet({request:requestFor(jwt),env:{}});
assert.equal(blocked.status,503,'production API refuses unconfigured auth');
const unsigned=await onRequestPost({
  request:new Request('https://academic.example/api/v1/assignments',{
    method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({title:'not allowed'})
  }),env
});
assert.equal(unsigned.status,401,'private writes require signed identity');
assert.throws(()=>normalizeManualAssignment({
  title:'无效个人时间',personalTargetAt:'2026-02-30T12:00:00+08:00'
}),AcademicInputError,'impossible local dates must not be silently normalized');
const oldFetch=globalThis.fetch;
globalThis.fetch=fetcher;
try {
  const payload={title:'来自已验证身份的手动作业',deadline:{
    precision:'date',date:'2026-11-22'
  }};
  const post=await onRequestPost({request:requestFor(jwt,{
    method:'POST',headers:{'Content-Type':'application/json','Origin':'https://academic.example'},
    body:JSON.stringify(payload)
  }),env});
  assert.equal(post.status,201,'signed request creates a real database row');
  const saved=await post.json();
  assert.equal(saved.item.duePrecision,'date');
  const list=await onRequestGet({request:requestFor(jwt),env});
  assert.equal(list.status,200);
  const listed=await list.json();
  assert.equal(listed.items.length,1);
  assert.equal(listed.items[0].title,payload.title);
  const forbidden=await onRequestPost({request:requestFor(jwt,{
    method:'POST',headers:{'Content-Type':'application/json','Origin':'https://evil.example'},
    body:JSON.stringify(payload)
  }),env});
  assert.equal(forbidden.status,403,'cross-site write rejected');
  const missingDb=await onRequestGet({request:requestFor(jwt),env:{
    ACCESS_TEAM_DOMAIN:team,ACCESS_AUD:audience
  }});
  assert.equal(missingDb.status,503,'missing D1 binding fails closed');
} finally {globalThis.fetch=oldFetch;}
console.log('校序 Academic M1：SQLite 迁移、用户隔离、日期精度、JWT 签名和未配置拒绝测试通过');
