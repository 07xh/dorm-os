/* Synthetic full HTTP owner login -> NCWU review, without a duplicate
 * preflight or real school credentials/network access. */
import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {onRequestPost as ownerLogin} from '../functions/api/v1/auth/login.js';
import {onRequestPost as ncwuLogin} from '../functions/api/v1/connections/[provider]/login.js';
globalThis.crypto??=webcrypto;

const sqlite=new DatabaseSync(':memory:');
sqlite.exec('PRAGMA foreign_keys=ON');
for(let n=1;n<=11;n++){
 const migrations={
  1:'0001_academic_core.sql',2:'0002_academic_legacy_links.sql',
  3:'0003_academic_verified_snapshots.sql',4:'0004_academic_source_course.sql',
  5:'0005_academic_workspace.sql',6:'0006_academic_scheduler.sql',
  7:'0007_academic_reminder_inbox.sql',8:'0008_academic_second_class_source.sql',
  9:'0009_ncwu_second_class_official_scores.sql',10:'0010_academic_owner_auth.sql',
  11:'0011_academic_provider_sessions.sql'
 };
 sqlite.exec(readFileSync(new URL('../migrations/'+migrations[n],import.meta.url),'utf8'));
}
const db={
 prepare(sql){let args=[];return {
  bind(...values){args=values;return this;},
  first:async()=>sqlite.prepare(sql).get(...args)??null,
  all:async()=>({results:sqlite.prepare(sql).all(...args)}),
  run:async()=>sqlite.prepare(sql).run(...args)
 };},
 async batch(commands){sqlite.exec('BEGIN');try{
  const results=[];for(const command of commands)results.push(await command.run());
  sqlite.exec('COMMIT');return results;
 }catch(error){sqlite.exec('ROLLBACK');throw error;}}
};
const origin='https://school-connector.example';
const ownerSecret='synthetic-owner-login-secret-unique-for-route-tests';
const schoolSecret=Buffer.from(webcrypto.getRandomValues(new Uint8Array(32))).toString('base64url');
const env={ACADEMIC_AUTH_MODE:'owner_password',OWNER_LOGIN_SECRET:ownerSecret,
 SCHOOL_SESSION_KEY:schoolSecret,ACADEMIC_DB:db};
const schoolAccount='TEST_USER_04',schoolPassword='TEST_ONLY_NEVER_REAL_PASSWORD';
const session={studentId:schoolAccount,semester:'2026-2027-1',
 cookies:[{name:'TEST_QG_SESSION',value:'fake-encrypted-only',domain:'jwmis.ncwu.edu.cn',
  path:'/hsjw/',hostOnly:true}]};
let preflightCalls=0,schoolLoginCalls=0,verifiedReads=0;
const adapter={
 provider:'ncwu',
 async preflight(){preflightCalls++;throw new Error('redundant_preflight_must_not_run');},
 async login(credentials){
  schoolLoginCalls++;
  assert.equal(credentials.username,schoolAccount);
  assert.equal(credentials.password,schoolPassword);
  return {session,expiresAt:new Date(Date.now()+3600000).toISOString()};
 },
 async forSession({session:received,userId}){
  assert.equal(userId,'local-owner-v1');
  assert.deepEqual(received,session);
  return {
   provider:'ncwu',
   verifySession:async()=>({authorized:true,scope:'self',school:'ncwu',
    provider:'ncwu',identityBound:true,principalBoundTo:userId,
    officialOrigin:'https://jwmis.ncwu.edu.cn',
    session:{studentId:schoolAccount,semester:'2026-2027-1'}}),
   readSection:async({kind,session:proof})=>{
    verifiedReads++;
    assert.equal(kind,'schedule');
    assert.equal(proof.studentId,schoolAccount);
    return {kind:'schedule',complete:true,records:[{
     externalId:'synthetic-qingguo-course',
     term:'2026-2027-1',title:'合成高数',
     teacher:'虚构教师',location:'虚构教室',weekday:2,
     startSection:1,endSection:2,weeks:[1,2,3,4]
    }]};
   }
  };
 }
};
const ownerRequest=new Request(origin+'/api/v1/auth/login',{
 method:'POST',headers:{origin,'content-type':'application/json'},
 body:JSON.stringify({passphrase:ownerSecret})
});
try{
 const login=await ownerLogin({request:ownerRequest,env});
 assert.equal(login.status,200);
 const cookie=(login.headers.get('set-cookie')||'').split(';')[0];
 assert.match(cookie,/__Host-xiaoxu_session=/);
 const request=()=>new Request(origin+'/api/v1/connections/ncwu/login',{
  method:'POST',headers:{origin,'content-type':'application/json',cookie},
  body:JSON.stringify({username:schoolAccount,password:schoolPassword})
 });
 const context=()=>({request:request(),env,params:{provider:'ncwu'},
  data:{academicSchoolAdapters:{ncwu:adapter}}});
 const response=await ncwuLogin(context());
 assert.equal(response.status,200);
 const preview=await response.json();
 assert.equal(preview.reviewRequired,true);
 assert.equal(preview.schedule.length,1);
 assert.equal(preview.schedule[0].title,'合成高数');
 assert.equal(schoolLoginCalls,1,'only one actual adapter login per request');
 assert.equal(preflightCalls,0,'no separate school probe before login');
 assert.equal(verifiedReads,1);
 assert.equal(sqlite.prepare("SELECT COUNT(*) AS n FROM academic_provider_sessions WHERE user_id=? AND provider='ncwu'")
  .get('local-owner-v1').n,1,'only the encrypted owner review state is retained');
 const dbRow=sqlite.prepare("SELECT ciphertext,nonce FROM academic_provider_sessions WHERE user_id='local-owner-v1' AND provider='ncwu'").get();
 assert.ok(dbRow.ciphertext&&dbRow.nonce);
 assert.ok(!JSON.stringify(dbRow).includes('fake-encrypted-only'));
 assert.ok(!JSON.stringify(preview).includes(schoolPassword));

 // A genuine interactive school challenge still stops at the trusted
 // adapter, before any encrypted review is replaced or course data written.
 const prior=sqlite.prepare("SELECT ciphertext FROM academic_provider_sessions WHERE user_id='local-owner-v1' AND provider='ncwu'").get().ciphertext;
 adapter.login=async()=>{schoolLoginCalls++;throw Object.assign(
  new Error('synthetic_challenge'),{code:'interactive_login_required'}
 );};
 const blocked=await ncwuLogin(context());
 assert.equal(blocked.status,409);
 assert.deepEqual(await blocked.json(),{error:'interactive_login_required'});
 assert.equal(preflightCalls,0);
 assert.equal(verifiedReads,1);
 assert.equal(sqlite.prepare("SELECT ciphertext FROM academic_provider_sessions WHERE user_id='local-owner-v1' AND provider='ncwu'").get().ciphertext,
  prior,'failed second attempt must preserve pending encrypted owner review');
 assert.equal(sqlite.prepare('PRAGMA integrity_check').get().integrity_check,'ok');
 console.log('NCWU one-session login route: owner authorization, no double preflight, encrypted preview and challenge refusal passed');
}finally{sqlite.close();}
