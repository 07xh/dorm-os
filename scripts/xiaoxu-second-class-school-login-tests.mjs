/* Synthetic contract test: no real student, platform credential, Cookie or HAR. */
import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {availableSchoolAdapter,connectSchoolAccount,refreshSchoolAccount,
 revokeSchoolAccount,schoolSessionStates,SchoolLoginError}
 from '../lib/academic-school-login.mjs';
import {NCWU_SECOND_CLASS_READS,makeNcwuSecondClassAdapter}
 from '../lib/academic-ncwu-second-class.mjs';

globalThis.crypto??=webcrypto;
const secret=Buffer.from(webcrypto.getRandomValues(new Uint8Array(32))).toString('base64url');
const sql=new DatabaseSync(':memory:');
sql.exec('PRAGMA foreign_keys=ON');
for(const name of [
 '0001_academic_core.sql','0003_academic_verified_snapshots.sql',
 '0008_academic_second_class_source.sql','0009_ncwu_second_class_official_scores.sql',
 '0011_academic_provider_sessions.sql','0012_academic_second_class_sessions.sql'
])sql.exec(readFileSync(new URL('../migrations/'+name,import.meta.url),'utf8'));
const db={
 prepare(statement){let args=[];return{
  bind(...values){args=values;return this;},
  first(){return sql.prepare(statement).get(...args)??null;},
  all(){return {results:sql.prepare(statement).all(...args)};},
  run(){return sql.prepare(statement).run(...args);}
 };},
 async batch(statements){
  sql.exec('BEGIN');
  try{const out=statements.map(statement=>statement.run());sql.exec('COMMIT');return out;}
  catch(e){sql.exec('ROLLBACK');throw e;}
 }
};
const owner='owner-m18',other='owner-other',student='SYNTHETIC_STUDENT';
const activity={id:'synthetic-a1',activity_name:'合成志愿服务',activity_start:'2026-11-02 10:00:00',
 school_year:'2026-2027',school_term:'1',student_code:student,
 signup_state:'1',verify_state:'1',activity_state:'5',score_value:1.5};
const score={student_code:student,school_term_index:100,score_value:6,
 score_plus:2,activity_type1_score:1.5};
let loginCalls=0,readCalls=0;
const makeSchoolAdapter=({badStudent=false}={})=>({
 provider:'second_class',
 async login({username,password}){
  loginCalls++;
  assert.equal(username,'SYNTHETIC_ACCOUNT');
  assert.equal(password,'EPHEMERAL_PASSWORD');
  return {session:{opaque:'PRIVATE_SESSION_VALUE',studentCode:student},
   expiresAt:new Date(Date.now()+3600000).toISOString()};
 },
 async forSession({session,userId}){
  assert.equal(session.studentCode,student);
  return makeNcwuSecondClassAdapter({
   verifySession:async()=>({
    authorized:true,scope:'self',school:'ncwu',provider:'second_class',
    identityBound:true,principalBoundTo:userId,
    session:{studentCode:student}
   }),
   readPost:async({method,path,form})=>{
    readCalls++;
    assert.equal(method,'POST');
    if(path===NCWU_SECOND_CLASS_READS.activities){
     assert.deepEqual(form,{page:'1',pagesize:'50'});
     return {data:[badStudent?{...activity,student_code:'OTHER'}:activity],
      totalSize:1,totalPage:1,startIndex:1};
    }
    if(path===NCWU_SECOND_CLASS_READS.scoreTypes)
     return [{code:'1',name:'志愿服务'}];
    if(path===NCWU_SECOND_CLASS_READS.scores)
     return {data:[score],startIndex:0,totalPage:0,totalSize:1};
    throw Error('unexpected read-only endpoint');
   }
  });
 }
});
const adapter=makeSchoolAdapter();
assert.equal(availableSchoolAdapter({data:{academicSchoolAdapters:{second_class:adapter}}},'second_class'),adapter);
assert.equal(availableSchoolAdapter({data:{academicSchoolAdapters:{second_class:adapter}}},'unknown'),null);
await assert.rejects(
 ()=>connectSchoolAccount({db,userId:owner,provider:'second_class',adapter,
 username:'SYNTHETIC_ACCOUNT',password:'EPHEMERAL_PASSWORD',secret:'invalid'}),
 e=>e instanceof SchoolLoginError&&e.code==='not_configured');
assert.equal(loginCalls,0,'missing encryption key must reject before reading credentials');
const connected=await connectSchoolAccount({db,userId:owner,provider:'second_class',adapter,
 username:'SYNTHETIC_ACCOUNT',password:'EPHEMERAL_PASSWORD',secret});
assert.equal(connected.connected,true);
assert.equal(connected.provider,'second_class');
assert.ok(Number.isFinite(Date.parse(connected.lastSuccessAt)));
assert.equal(readCalls,3,'only the HAR-confirmed activity, type and score paths were read');
assert.deepEqual(await schoolSessionStates(db,owner),{second_class:'active'});
const vault=sql.prepare('SELECT * FROM academic_provider_sessions WHERE user_id=?').get(owner);
assert.equal(vault.provider,'second_class');
assert.ok(!JSON.stringify(vault).includes('EPHEMERAL_PASSWORD'));
assert.ok(!JSON.stringify(vault).includes('PRIVATE_SESSION_VALUE'));
assert.equal(sql.prepare("SELECT provenance FROM academic_verified_batches WHERE user_id=?").get(owner).provenance,'trusted_readonly');
assert.equal(sql.prepare("SELECT count(*) AS n FROM academic_verified_records WHERE user_id=?").get(owner).n,1);
const official=JSON.parse(sql.prepare('SELECT payload FROM academic_second_class_official_scores WHERE user_id=?').get(owner).payload);
assert.equal(official.aggregate.qualityScore,6);
assert.equal(official.aggregate.volunteerHours,2);
assert.ok(!JSON.stringify(official).includes(student));
const refresh=await refreshSchoolAccount({db,userId:owner,provider:'second_class',adapter,secret});
assert.equal(refresh.refreshed,true);
assert.equal(readCalls,6,'refresh re-queries school source');
await assert.rejects(
 ()=>refreshSchoolAccount({db,userId:other,provider:'second_class',adapter,secret}),
 e=>e instanceof SchoolLoginError&&e.code==='session_expired');
await assert.rejects(
 ()=>connectSchoolAccount({db,userId:other,provider:'second_class',
 adapter:makeSchoolAdapter({badStudent:true}),username:'SYNTHETIC_ACCOUNT',
 password:'EPHEMERAL_PASSWORD',secret}),
 e=>e instanceof SchoolLoginError&&e.code==='sync_failed');
assert.equal(sql.prepare('SELECT count(*) AS n FROM academic_provider_sessions WHERE user_id=?').get(other).n,0,
 'identity mismatch must never persist a session');
assert.equal(sql.prepare('SELECT count(*) AS n FROM academic_verified_records WHERE user_id=?').get(other).n,0);
const revoked=await revokeSchoolAccount(db,owner,'second_class');
assert.equal(revoked.disconnected,true);
assert.deepEqual(await schoolSessionStates(db,owner),{});
assert.equal(sql.prepare('SELECT count(*) AS n FROM academic_verified_records WHERE user_id=?').get(owner).n,1,
 'disconnect retains previous verified academic data');
assert.deepEqual(sql.prepare('PRAGMA foreign_key_check').all(),[]);
sql.close();
console.log('M18 second-class: encrypted login, real parser+official totals, refresh, owner isolation and disconnect passed');
