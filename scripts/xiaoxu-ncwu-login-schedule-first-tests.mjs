import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {connectSchoolAccount,refreshSchoolAccount,schoolSessionStates,prepareNcwuAccountReview,confirmNcwuAccountReview,SchoolLoginError} from '../lib/academic-school-login.mjs';
import {readConnectionCenter} from '../lib/academic-connection-center.mjs';
import {isActiveSchoolAuthorization} from '../functions/api/v1/connections.js';

globalThis.crypto??=webcrypto;
const sqlite=new DatabaseSync(':memory:');
sqlite.exec('PRAGMA foreign_keys=ON');
for(const file of ['0001_academic_core.sql','0003_academic_verified_snapshots.sql',
 '0008_academic_second_class_source.sql','0011_academic_provider_sessions.sql']){
 sqlite.exec(readFileSync(new URL('../migrations/'+file,import.meta.url),'utf8'));
}
const db={
 prepare(sql){let args=[];return {bind(...v){args=v;return this;},
  first(){return sqlite.prepare(sql).get(...args)??null;},
  all(){return {results:sqlite.prepare(sql).all(...args)};},
  run(){return sqlite.prepare(sql).run(...args);}};},
 async batch(commands){sqlite.exec('BEGIN');try{
  const v=commands.map(x=>x.run());sqlite.exec('COMMIT');return v;
 }catch(e){sqlite.exec('ROLLBACK');throw e;}}
};
const owner='synthetic-owner';
const now=new Date().toISOString();
sqlite.prepare('INSERT INTO academic_users(id,created_at) VALUES(?,?)').run(owner,now);
const secret=Buffer.from(webcrypto.getRandomValues(new Uint8Array(32))).toString('base64url');
const syntheticCookie='TEST_SESSION_NOT_A_REAL_SCHOOL_COOKIE';
const password='TEST_PASSWORD_NO_SCHOOL_ACCOUNT';
let schedules=0;
const expectedSchedule={kind:'schedule',complete:true,records:[{
 externalId:'synthetic-term-1-class-01',term:'2026-2027-1',title:'虚构课程A',
 teacher:'合成老师',location:'虚构教室101',weekday:3,startSection:1,endSection:2,weeks:[1,2,3,4]
}]};
function adapter({origin='https://jwmis.ncwu.edu.cn',ownerBound=owner}={}){
 return {
  provider:'ncwu',
  async login({username,password:secretInput}){
   assert.equal(username,'test-user-no-real-login');
   assert.equal(secretInput,password);
   return {session:{synthetic:syntheticCookie},
    expiresAt:new Date(Date.now()+3600000).toISOString()};
  },
  async forSession({userId,session}){
   assert.equal(userId,owner);
   assert.deepEqual(session,{synthetic:syntheticCookie});
   return {provider:'ncwu',
    verifySession:async()=>({authorized:true,scope:'self',school:'ncwu',
     provider:'ncwu',identityBound:true,principalBoundTo:ownerBound,
     officialOrigin:origin,session:{synthetic:syntheticCookie}}),
    readSection:async({kind,session})=>{
     assert.equal(kind,'schedule','login and refresh must not query grades/exams');
     assert.equal(session.synthetic,syntheticCookie);
     schedules++;return expectedSchedule;
    }};
  }
 };
}
const account=await connectSchoolAccount({db,userId:owner,provider:'ncwu',
 adapter:adapter(),username:'test-user-no-real-login',password,secret});
assert.equal(account.connected,true);
assert.equal(schedules,1);
assert.equal(sqlite.prepare("SELECT status FROM academic_connections WHERE user_id=?").get(owner).status,'partial');
const kinds=sqlite.prepare("SELECT kind FROM academic_verified_batches WHERE user_id=? ORDER BY kind")
 .all(owner).map(x=>x.kind);
assert.deepEqual(kinds,['schedule'],'must never fabricate grades/exams records');
const overview=await readConnectionCenter(db,owner);
assert.equal(overview.platforms[0].status,'partial');
assert.deepEqual(overview.platforms[0].trustedKinds,['schedule']);
assert.equal(overview.platforms[0].accountAuthorization,'not_verified');
const sessions=await schoolSessionStates(db,owner,secret);
assert.deepEqual(sessions,{ncwu:'active'});
assert.equal(isActiveSchoolAuthorization(overview.platforms[0],sessions.ncwu,true),true,
 'only a trusted active service-side adapter may prove account authorization');
assert.equal(isActiveSchoolAuthorization(overview.platforms[0],'expired',true),false);
assert.equal(isActiveSchoolAuthorization(overview.platforms[0],'active',false),false);
assert.equal(isActiveSchoolAuthorization({...overview.platforms[0],status:'error'},'active',true),false);
assert.equal(isActiveSchoolAuthorization({...overview.platforms[0],trustedKinds:[]},'active',true),false);
assert.equal(isActiveSchoolAuthorization({...overview.platforms[0],trustedKinds:['grades']},'active',true),false);
assert.equal(isActiveSchoolAuthorization({...overview.platforms[0],trustedKinds:['schedule'],status:'partial'},'active',true),true);
const sealed=sqlite.prepare('SELECT ciphertext,nonce FROM academic_provider_sessions WHERE user_id=?')
 .get(owner);
assert.ok(sealed.ciphertext && sealed.nonce);
assert.ok(!JSON.stringify(sealed).includes(syntheticCookie),'session must be AES encrypted');
assert.ok(!JSON.stringify(sealed).includes(password),'password must never be persisted');
const refreshed=await refreshSchoolAccount({db,userId:owner,provider:'ncwu',adapter:adapter(),secret});
assert.equal(refreshed.refreshed,true);
assert.equal(schedules,2,'refresh only requests the same verified timetable');
assert.deepEqual(sqlite.prepare("SELECT DISTINCT kind FROM academic_verified_records WHERE user_id=?")
 .all(owner).map(x=>x.kind),['schedule']);
const savedBefore=sqlite.prepare('SELECT COUNT(*) AS n FROM academic_provider_sessions WHERE user_id=?')
 .get(owner).n;
await assert.rejects(
 ()=>connectSchoolAccount({db,userId:owner,provider:'ncwu',adapter:adapter({origin:'https://not-official.example'}),
  username:'test-user-no-real-login',password,secret}),
 e=>e instanceof SchoolLoginError&&e.code==='authorization_failed');
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_provider_sessions WHERE user_id=?')
 .get(owner).n,savedBefore,'failed verification must not add a session');

/* First real-connector contact must NOT silently turn a school timetable into
 * trusted data. Keep the only pending session encrypted in the existing vault. */
const preview=await prepareNcwuAccountReview({db,userId:owner,adapter:adapter(),
 username:'test-user-no-real-login',password,secret});
assert.equal(preview.reviewRequired,true);
assert.equal(preview.schedule[0].title,'虚构课程A');
assert.ok(!JSON.stringify(preview).includes(syntheticCookie));
assert.ok(!JSON.stringify(preview).includes(password));
assert.deepEqual(await schoolSessionStates(db,owner,secret),{ncwu:'review_pending'});
await assert.rejects(()=>refreshSchoolAccount({db,userId:owner,provider:'ncwu',
 adapter:adapter(),secret}),e=>e.code==='review_pending');
await assert.rejects(()=>confirmNcwuAccountReview({db,userId:owner,
 adapter:adapter(),secret,reviewHash:'x'.repeat(43)}),e=>e.code==='review_pending');
const changed=adapter();
const originalRead=changed.forSession.bind(changed);
changed.forSession=async args=>{
 const reader=await originalRead(args);
 const originalSection=reader.readSection;
 return {...reader,readSection:async opts=>{
  const section=await originalSection(opts);
  return {...section,records:section.records.map(row=>({...row,title:'合成课程发生变化'}))};
 }};
};
await assert.rejects(()=>confirmNcwuAccountReview({db,userId:owner,
 adapter:changed,secret,reviewHash:preview.reviewHash}),e=>e.code==='schedule_changed');
assert.deepEqual(await schoolSessionStates(db,owner,secret),{ncwu:'review_pending'},
 'school mismatch must not promote the pending session');
const reviewed=await confirmNcwuAccountReview({db,userId:owner,
 adapter:adapter(),secret,reviewHash:preview.reviewHash});
assert.equal(reviewed.connected,true);
assert.deepEqual(await schoolSessionStates(db,owner,secret),{ncwu:'active'});
const finalRow=sqlite.prepare("SELECT ciphertext,nonce FROM academic_provider_sessions WHERE user_id=? AND provider='ncwu'").get(owner);
assert.ok(!JSON.stringify(finalRow).includes(syntheticCookie));
assert.ok(!JSON.stringify(finalRow).includes(password));

assert.equal(sqlite.prepare('PRAGMA integrity_check').get().integrity_check,'ok');
assert.deepEqual(sqlite.prepare('PRAGMA foreign_key_check').all(),[]);
sqlite.close();
console.log('NCWU login bridge: verified schedule only, partial status, session vault, failed proof, no grades/exams and no secret leakage passed');
