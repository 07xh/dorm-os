import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {
 availableSchoolAdapter,checkSchoolMutation,validSchoolLoginInput,
 connectSchoolAccount,refreshSchoolAccount,revokeSchoolAccount,
 schoolSessionStates,SchoolLoginError
} from '../lib/academic-school-login.mjs';
import {onRequestPost as loginPost} from '../functions/api/v1/connections/[provider]/login.js';
import {onRequestGet as loginPreflight} from '../functions/api/v1/connections/[provider]/preflight.js';
globalThis.crypto??=webcrypto;
const random=webcrypto.getRandomValues(new Uint8Array(32));
const secret=Buffer.from(random).toString('base64url');
assert.equal(availableSchoolAdapter({data:{}},'ncwu'),null);
assert.equal(availableSchoolAdapter({data:{academicSchoolAdapters:{ncwu:{provider:'ncwu'}}}},'ncwu'),null);
assert.equal(availableSchoolAdapter({},'second_class'),null);
assert.equal(new SchoolLoginError('authorization_failed','personal_data').failureStage,'personal_data');
assert.equal(new SchoolLoginError('authorization_failed','custom_details').failureStage,undefined);
assert.equal(new SchoolLoginError('invalid_credentials','cas_return').failureStage,undefined);
assert.equal(validSchoolLoginInput({username:'student',password:'pass'}),true);
assert.equal(validSchoolLoginInput({username:'',password:'pass'}),false);
const goodRequest=new Request('https://site.example/api/v1/connections/ncwu/login',{
 method:'POST',headers:{origin:'https://site.example','content-type':'application/json'},
 body:'{}'});
assert.equal(checkSchoolMutation(goodRequest),true);
assert.equal(checkSchoolMutation(new Request('https://site.example/',{method:'POST',
 headers:{origin:'https://elsewhere.example','content-type':'application/json'},body:'{}'})),false);
const denied=await loginPost({request:goodRequest,env:{},params:{provider:'ncwu'}});
assert.equal(denied.status,503);
assert.ok(!JSON.stringify(await denied.json()).includes('password'));
const sqlite=new DatabaseSync(':memory:');
sqlite.exec('PRAGMA foreign_keys=ON');
for(const name of ['0001_academic_core.sql','0010_academic_owner_auth.sql','0011_academic_provider_sessions.sql'])
 sqlite.exec(readFileSync(new URL('../migrations/'+name,import.meta.url),'utf8'));
sqlite.prepare('INSERT INTO academic_users(id,created_at) VALUES (?,?)')
 .run('owner-a',new Date().toISOString());
sqlite.prepare('INSERT INTO academic_users(id,created_at) VALUES (?,?)')
 .run('owner-b',new Date().toISOString());
const db={
 prepare(sql){let args=[];return {bind(...v){args=v;return this;},
  first(){return sqlite.prepare(sql).get(...args)??null;},
  all(){return {results:sqlite.prepare(sql).all(...args)};},
  run(){return sqlite.prepare(sql).run(...args);}};},
 async batch(statements){sqlite.exec('BEGIN');try{
  const results=statements.map(statement=>statement.run());sqlite.exec('COMMIT');return results;
 }catch(e){sqlite.exec('ROLLBACK');throw e;}}
};
const sessionValue='COOKIE-NEVER-STORE-PLAIN';
let credentialCalls=0,verifiedCalls=0,reads=0;
const makeAdapter=()=>({
 provider:'ncwu',
 async login({username,password}){
  credentialCalls++;
  assert.equal(username,'official-student-account');
  assert.equal(password,'ephemeral-school-password');
  return {session:{cookie:sessionValue},expiresAt:new Date(Date.now()+3600000).toISOString()};
 },
 async forSession({session,userId}){
  verifiedCalls++;
  assert.equal(userId,'owner-a');
  assert.deepEqual(session,{cookie:sessionValue});
  return {provider:'ncwu',verifySession:async()=>({authorized:true})};
 }
});
const adapter=makeAdapter();
const downAdapter={...makeAdapter(),
 async login(){throw Object.assign(new Error('synthetic server failure'),{code:'upstream_unavailable'});}};
await assert.rejects(()=>connectSchoolAccount({db,userId:'owner-a',provider:'ncwu',
 adapter:downAdapter,username:'official-student-account',
 password:'ephemeral-school-password',secret}),
 e=>e instanceof SchoolLoginError&&e.code==='upstream_unavailable',
 'school network errors must not be mislabeled as invalid account credentials');

let result;
await assert.rejects(
 ()=>connectSchoolAccount({db,userId:'owner-a',provider:'ncwu',adapter,
 username:'official-student-account',password:'ephemeral-school-password',secret:'bad'}),
 e=>e instanceof SchoolLoginError&&e.code==='not_configured');
assert.equal(credentialCalls,0,'missing server secret never processes credentials');
// Shared library must reject second-class password login even when invoked directly.
const phaseFailureAdapter={provider:'second_class',
 async login(){throw Object.assign(new Error('opaque private upstream error'),
  {code:'authorization_failed',failureStage:'cas_return',casReturnReason:'login_page_remained',
   casReturnHost:'cas_auth_server'});},
 async forSession(){throw Error('must not read when login failed');}
};
await assert.rejects(()=>connectSchoolAccount({db,userId:'owner-a',
 provider:'second_class',adapter:phaseFailureAdapter,username:'official-student-account',
 password:'ephemeral-school-password',secret}),
 e=>e instanceof SchoolLoginError&&e.code==='interactive_login_required');

// Exercise the real authenticated route and session-vault wrapper. All
// tokens, school responses and accounts below are synthetic and local.
const ownerToken='A'.repeat(43),ownerAt=new Date().toISOString();
const tokenHash=Buffer.from(await webcrypto.subtle.digest('SHA-256',new TextEncoder().encode(ownerToken))).toString('hex');
sqlite.prepare('INSERT INTO academic_auth_identities(id,user_id,login_identifier,enrolled_at) VALUES (?,?,?,?)')
 .run('synthetic-identity','owner-a','synthetic-owner',ownerAt);
sqlite.prepare('INSERT INTO academic_auth_sessions(id,identity_id,token_hash,created_at,expires_at) VALUES (?,?,?,?,?)')
 .run('synthetic-session','synthetic-identity',tokenHash,ownerAt,new Date(Date.now()+3600000).toISOString());
// No request body or adapter may be touched by an old cached client.
const retiredRequest=new Request('https://site.example/api/v1/connections/second_class/login',{
 method:'POST',headers:{origin:'https://site.example','content-type':'application/json',
  cookie:'__Host-xiaoxu_session='+ownerToken},body:'SYNTHETIC_CREDENTIALS_NEVER_READ'
});
retiredRequest.text=async()=>{assert.fail('retired route must not read credentials');};
let retiredCalls=0;
const retiredResponse=await loginPost({params:{provider:'second_class'},
 env:{ACADEMIC_DB:db,ACADEMIC_AUTH_MODE:'owner_password',SCHOOL_SESSION_KEY:secret},
 data:{academicSchoolAdapters:{second_class:{...phaseFailureAdapter,
  async login(){retiredCalls++;throw Error('must not contact school');}
 }}},request:retiredRequest});
assert.equal(retiredResponse.status,410);
assert.match(retiredResponse.headers.get('cache-control'),/no-store/);
assert.deepEqual(await retiredResponse.json(),{error:'second_class_official_authorization_required'});
assert.equal(retiredCalls,0);
const retiredPreflight=await loginPreflight({params:{provider:'second_class'},
 env:{ACADEMIC_DB:db,ACADEMIC_AUTH_MODE:'owner_password'},
 data:{academicSchoolAdapters:{second_class:{preflight:async()=>{
  assert.fail('official-device entry must not probe the old cloud password flow');
 }}}},request:new Request('https://site.example/api/v1/connections/second_class/preflight',{
  headers:{cookie:'__Host-xiaoxu_session='+ownerToken}
 })});
assert.equal(retiredPreflight.status,200);
assert.deepEqual(await retiredPreflight.json(),{
 status:'official_app_required',loginSupported:false,nativeLoginReady:false,
 officialLoginUrl:'https://dekt.ncwu.edu.cn/',qrSessionLinked:false,
 schoolAuthorized:false,automaticSync:false
});
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_provider_sessions WHERE provider=?').get('second_class').n,0);
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_auth_login_attempts').get().n,0);

result=await connectSchoolAccount({db,userId:'owner-a',provider:'ncwu',adapter,
 username:'official-student-account',password:'ephemeral-school-password',secret,
 sync:async()=>{reads++;return {lastSuccessAt:'2026-11-03T06:00:00.000Z'};}});
assert.equal(result.connected,true);
assert.equal(credentialCalls,1);
assert.equal(reads,1);
const stored=sqlite.prepare('SELECT * FROM academic_provider_sessions WHERE user_id=?').get('owner-a');
assert.equal(stored.provider,'ncwu');
assert.equal(JSON.stringify(stored).includes(sessionValue),false,'session AES-GCM encrypted');
assert.equal(JSON.stringify(stored).includes('ephemeral-school-password'),false);
assert.deepEqual(await schoolSessionStates(db,'owner-a',secret),{ncwu:'active'});
assert.deepEqual(await schoolSessionStates(db,'owner-a','invalid-key'),{},
 'missing/invalid vault key must not report a usable session');
const rotatedKey=Buffer.from(webcrypto.getRandomValues(new Uint8Array(32))).toString('base64url');
assert.deepEqual(await schoolSessionStates(db,'owner-a',rotatedKey),{ncwu:'unreadable'},
 'a rotated but syntactically valid key must not report active');
const originalCipher=sqlite.prepare(
 'SELECT ciphertext FROM academic_provider_sessions WHERE user_id=? AND provider=?'
).get('owner-a','ncwu').ciphertext;
sqlite.prepare('UPDATE academic_provider_sessions SET ciphertext=? WHERE user_id=? AND provider=?')
 .run('Y'.repeat(43),'owner-a','ncwu');
assert.deepEqual(await schoolSessionStates(db,'owner-a',secret),{ncwu:'unreadable'},
 'corrupted AES-GCM ciphertext must fail closed');
sqlite.prepare('UPDATE academic_provider_sessions SET ciphertext=? WHERE user_id=? AND provider=?')
 .run(originalCipher,'owner-a','ncwu');
assert.deepEqual(await schoolSessionStates(db,'owner-a',secret),{ncwu:'active'},
 'restoring the original valid ciphertext restores local readability');
const originalExpiry=sqlite.prepare(
 'SELECT expires_at FROM academic_provider_sessions WHERE user_id=? AND provider=?'
).get('owner-a','ncwu').expires_at;
sqlite.prepare('UPDATE academic_provider_sessions SET expires_at=? WHERE user_id=? AND provider=?')
 .run('2020-01-01T00:00:00Z','owner-a','ncwu');
assert.deepEqual(await schoolSessionStates(db,'owner-a',secret),{ncwu:'expired'});
sqlite.prepare('UPDATE academic_provider_sessions SET expires_at=? WHERE user_id=? AND provider=?')
 .run(originalExpiry,'owner-a','ncwu');
assert.deepEqual(await schoolSessionStates(db,'owner-b',secret),{},
 'other owners cannot see the current owner school-session status');
await assert.rejects(
 ()=>refreshSchoolAccount({db,userId:'owner-b',provider:'ncwu',adapter,secret,
 sync:async()=>{throw Error('should not sync');}}),
 e=>e instanceof SchoolLoginError&&e.code==='session_expired');
const refreshed=await refreshSchoolAccount({db,userId:'owner-a',provider:'ncwu',adapter,secret,
 sync:async()=>{reads++;return {};}});
assert.equal(refreshed.refreshed,true);assert.equal(reads,2);assert.equal(verifiedCalls,2);
await assert.rejects(
 ()=>refreshSchoolAccount({db,userId:'owner-a',provider:'ncwu',adapter,
 secret:Buffer.from(webcrypto.getRandomValues(new Uint8Array(32))).toString('base64url'),
 sync:async()=>{throw Error('must not sync');}}),
 e=>e instanceof SchoolLoginError&&e.code==='authorization_failed');
const revoked=await revokeSchoolAccount(db,'owner-a','ncwu');
assert.equal(revoked.disconnected,true);
assert.deepEqual(await schoolSessionStates(db,'owner-a',secret),{});
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_provider_sessions').get().n,0);
assert.equal(sqlite.prepare('PRAGMA integrity_check').get().integrity_check,'ok');
sqlite.close();
console.log('School website login contract: fail closed, same-origin, AES-GCM sessions, owner isolation, re-sync and revoke passed');
