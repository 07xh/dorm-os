/* Private school-login contract. No provider is enabled without a registered,
 * real server-side adapter. A school site opened in another tab grants NOTHING.
 * Upstream passwords are ephemeral and never written to D1 or browser storage.
 */
import {runVerifiedNcwuSync,collectVerifiedNcwu} from './academic-ncwu-verified-sync.mjs';
import {runVerifiedChaoxingInboxSync} from './academic-chaoxing-notices.mjs';
import {runVerifiedNcwuSecondClassSync} from './academic-ncwu-second-class-sync.mjs';
const SUPPORTED=new Set(['ncwu','chaoxing','second_class']);
const ENCODE=new TextEncoder(),DECODE=new TextDecoder();
const USER=/^[A-Za-z0-9_-]{1,100}$/;
const ERROR_CODES=new Set(['not_configured','invalid_credentials','interactive_login_required','authorization_failed','session_expired','database_unavailable','sync_failed','schedule_changed','review_pending','upstream_unavailable','invalid_cookie_format']);
const SECOND_CLASS_FAILURE_STAGES=new Set(['cas_return','personal_data','identity_evidence']);
export class SchoolLoginError extends Error{
 constructor(code,failureStage=null){
  super(ERROR_CODES.has(code)?code:'sync_failed');this.code=this.message;this.name='SchoolLoginError';
  if(this.code==='authorization_failed'&&SECOND_CLASS_FAILURE_STAGES.has(failureStage))
   this.failureStage=failureStage;
 }
}
const fail=(code,failureStage=null)=>{throw new SchoolLoginError(code,failureStage);};
const encoded=bytes=>btoa(String.fromCharCode(...bytes)).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
const decoded=str=>{
 if(typeof str!=='string'||!(/^[A-Za-z0-9_-]+$/).test(str))fail('not_configured');
 const binary=atob(str.replace(/-/g,'+').replace(/_/g,'/')+'='.repeat((4-str.length%4)%4));
 return Uint8Array.from(binary,c=>c.charCodeAt(0));
};
async function key(secret){
 if(typeof secret!=='string'||!(/^[A-Za-z0-9_-]{43}$/).test(secret))fail('not_configured');
 const data=decoded(secret);
 if(data.length!==32)fail('not_configured');
 return crypto.subtle.importKey('raw',data,{name:'AES-GCM'},false,['encrypt','decrypt']);
}
const aad=(userId,provider)=>ENCODE.encode('xiaoxu-school-session-v1|'+userId+'|'+provider);
const validTime=t=>typeof t==='string'&&Number.isFinite(Date.parse(t));
export function availableSchoolAdapter(context,provider){
 if(!SUPPORTED.has(provider))return null;
 // This is only trusted when set by server-side Pages middleware. Browser
 // headers, JSON and query params can never register an adapter.
 const adapter=context?.data?.academicSchoolAdapters?.[provider];
 return adapter?.provider===provider&&typeof adapter.login==='function'&&
  typeof adapter.forSession==='function'?adapter:null;
}
/* Shared owner-and-provider rate limit for password and session import.
 * Avoid a second auth table or another independent login system. */
export async function schoolConnectAttemptStatus(db,userId,provider){
 if(!db?.prepare||!USER.test(userId||'')||!SUPPORTED.has(provider))
  return 'rate_limit_unavailable';
 try{
  const now=Date.now(),bucket='school-login:'+userId+':'+provider;
  const row=await db.prepare(
   'INSERT INTO academic_auth_login_attempts(bucket,hits,window_end) VALUES(?,1,?) '+
   'ON CONFLICT(bucket) DO UPDATE SET hits=CASE WHEN window_end<=? THEN 1 ELSE hits+1 END, '+
   'window_end=CASE WHEN window_end<=? THEN excluded.window_end ELSE window_end END '+
   'RETURNING hits'
  ).bind(bucket,now+900000,now,now).first();
  if(!Number.isInteger(row?.hits))return 'rate_limit_unavailable';
  return row.hits>6?'too_many_attempts':'ok';
 }catch{return 'rate_limit_unavailable';}
}
export function validSchoolLoginInput(value){
 return value&&typeof value==='object'&&!Array.isArray(value)&&
  typeof value.username==='string'&&value.username.trim().length>0&&value.username.length<=128&&
  typeof value.password==='string'&&value.password.length>0&&value.password.length<=512&&
  (value.academicUsername===undefined&&value.academicPassword===undefined||
   typeof value.academicUsername==='string'&&value.academicUsername.trim().length>0&&
   value.academicUsername.length<=128&&typeof value.academicPassword==='string'&&
   value.academicPassword.length>0&&value.academicPassword.length<=512);
}
export function checkSchoolMutation(request){
 const url=new URL(request.url),origin=request.headers.get('origin');
 return url.protocol==='https:'&&origin===url.origin&&
  request.headers.get('sec-fetch-site')!=='cross-site'&&
  (request.headers.get('content-type')||'').toLowerCase().startsWith('application/json');
}
async function seal(session,secret,userId,provider){
 const plain=JSON.stringify(session);
 if(!plain||plain.length>16000)fail('authorization_failed');
 const nonce=crypto.getRandomValues(new Uint8Array(12));
 const ciphertext=await crypto.subtle.encrypt(
  {name:'AES-GCM',iv:nonce,additionalData:aad(userId,provider)},
  await key(secret),ENCODE.encode(plain));
 return {ciphertext:encoded(new Uint8Array(ciphertext)),nonce:encoded(nonce)};
}
async function unseal(row,secret,userId,provider){
 try{
  const plain=await crypto.subtle.decrypt(
   {name:'AES-GCM',iv:decoded(row.nonce),additionalData:aad(userId,provider)},
   await key(secret),decoded(row.ciphertext));
  return JSON.parse(DECODE.decode(plain));
 }catch{fail('authorization_failed');}
}
function validSessionLogin(result){
 return result&&typeof result==='object'&&!Array.isArray(result)&&
  result.session&&typeof result.session==='object'&&!Array.isArray(result.session)&&
  validTime(result.expiresAt)&&Date.parse(result.expiresAt)>Date.now()&&
  Date.parse(result.expiresAt)<=Date.now()+30*86400000;
}
// A rotated cookie jar must remain bound to the same independently verified
// NCWU principal/semester. It never reaches the browser or a different user.
function renewedNcwuSession(previous,rotated){
 if(rotated===undefined)return previous;
 if(!previous||!rotated||typeof previous.studentId!=='string'||
    !previous.studentId||rotated.studentId!==previous.studentId||
    rotated.semester!==previous.semester||!Array.isArray(rotated.cookies))
  fail('authorization_failed');
 return rotated;
}
// Never persist cookies from a different second-class principal.
function renewedSecondClassSession(previous,rotated){
 if(rotated===undefined)return previous;
 if(!previous||!rotated||typeof previous.studentCode!=='string'||
    !previous.studentCode||rotated.studentCode!==previous.studentCode||
    !Array.isArray(rotated.cookies)||rotated.cookies.length>32)
  fail('authorization_failed');
 return rotated;
}
const NCWU_VAULT_MAX_AGE_MS=3*86400000; // Upper bound only; actual school validity is checked every read.
async function syncWith(db,userId,provider,adapter,session,{sync}={}){
 const read=await adapter.forSession({session,userId});
 if(!read||read.provider!==provider||typeof read.verifySession!=='function')fail('authorization_failed');
 try{
  const outcome=await (sync?sync(db,userId,provider,read):
   // A verified timetable is allowed to precede grades/exams. No client input
   // can select kinds or waive the original owner/session identity proof.
   provider==='ncwu'?runVerifiedNcwuSync(db,userId,read,{kinds:['schedule']}):
   provider==='second_class'?runVerifiedNcwuSecondClassSync(db,userId,read):
   runVerifiedChaoxingInboxSync(db,userId,read));
  // Only a complete, authenticated NCWU read may export its updated cookie jar.
  return (provider==='ncwu'||provider==='second_class')&&!sync&&
   typeof read.getRefreshedSession==='function'?
   {...outcome,refreshedSession:read.getRefreshedSession()}:outcome;
 }catch(e){fail(e?.code==='authorization_failed'?'authorization_failed':'sync_failed',
  provider==='second_class'?e?.failureStage:null);}
}
export async function connectSchoolAccount({db,userId,provider,adapter,username,password,
 academicUsername,academicPassword,secret,sync}){
 // Only a verified school CAS server adapter may accept credentials.
 if(!db?.prepare||!USER.test(userId||'')||!SUPPORTED.has(provider))fail('not_configured');
 // Do not use the retired server password transport for second-class.
 if(provider==='second_class')fail('interactive_login_required');
 if(!adapter||adapter.provider!==provider||typeof adapter.login!=='function'||
   typeof adapter.forSession!=='function')fail('not_configured');
 if(!validSchoolLoginInput({username,password,academicUsername,academicPassword})||
   provider!=='ncwu'&&(academicUsername!==undefined||academicPassword!==undefined))
  fail('invalid_credentials');
 // Fail closed before accepting credentials if the session vault is missing.
 await key(secret);
 try{await db.prepare('SELECT provider FROM academic_provider_sessions WHERE user_id=? LIMIT 1').bind(userId).all();}
 catch{fail('not_configured');}
 let result;
 try{result=await adapter.login({username,password,academicUsername,academicPassword});}
 catch(e){fail(['interactive_login_required','invalid_credentials','upstream_unavailable',
  'session_expired'].includes(e?.code)?e.code:'authorization_failed');}
 if(!validSessionLogin(result))fail('authorization_failed');
 // This trusted read must independently establish the school principal,
 // owner identity, and complete academic data before any session is saved.
 return saveAuthenticatedSchoolAccount({db,userId,provider,adapter,
  session:result.session,expiresAt:result.expiresAt,secret,sync});
}

/* A browser's own Chaoxing cookie export may be used instead of a second
 * Chaoxing password. The adapter checks the official school profile and the
 * EXISTING verified inbox sync must succeed BEFORE storing any session.
 * Nothing in this flow reads cookies from a different website's JS origin.
 */
export async function connectImportedChaoxingSession({db,userId,adapter,cookies,secret}){
 if(!db?.prepare||!USER.test(userId||'')||adapter?.provider!=='chaoxing'||
    typeof adapter.importSession!=='function'||typeof adapter.forSession!=='function')
  fail('not_configured');
 await key(secret);
 try{await db.prepare('SELECT provider FROM academic_provider_sessions LIMIT 0').all();}
 catch{fail('not_configured');}
 let result;
 try{result=await adapter.importSession({cookies});}
 catch(e){
  fail(['invalid_cookie_format','authorization_failed','upstream_unavailable',
   'interactive_login_required'].includes(e?.code)?e.code:'authorization_failed');
 }
 if(!validSessionLogin(result))fail('authorization_failed');
 return saveAuthenticatedSchoolAccount({db,userId,provider:'chaoxing',
  adapter,session:result.session,expiresAt:result.expiresAt,secret});
}

/* Qingguo first-connection review shares the EXISTING encrypted session vault.
 * Until the owner compares the actual course list, it has reviewPending=true,
 * is never treated as connected, and can never refresh trusted records.
 * No password, cookie, HTML, student identifier or CAS ticket reaches the UI.
 */
// The QR challenge remains short-lived and authenticated to this owner.
 // Browsers receive only authenticated ciphertext (never raw upstream Cookies).
export async function sealChaoxingQrChallenge({challenge,userId,secret}){
 if(!USER.test(userId||'')||challenge?.schema!=='chaoxing-qr-v1'||
    !Number.isSafeInteger(challenge.issuedAt)||Math.abs(Date.now()-challenge.issuedAt)>180000)
  fail('authorization_failed');
 const value=await seal(challenge,secret,userId,'chaoxing-qr');
 return value.nonce+'.'+value.ciphertext;
}
export async function unsealChaoxingQrChallenge({ticket,userId,secret}){
 if(!USER.test(userId||'')||typeof ticket!=='string'||ticket.length>24000||
    !/^[A-Za-z0-9_-]{16}\.[A-Za-z0-9_-]{20,23900}$/.test(ticket))
  fail('authorization_failed');
 const [nonce,ciphertext]=ticket.split('.');
 const challenge=await unseal({nonce,ciphertext},secret,userId,'chaoxing-qr');
 if(challenge?.schema!=='chaoxing-qr-v1'||!Number.isSafeInteger(challenge.issuedAt)||
    challenge.issuedAt>Date.now()+5000||Date.now()-challenge.issuedAt>165000)
  fail('session_expired');
 return challenge;
}
export async function connectAuthenticatedChaoxingQr({db,userId,adapter,session,expiresAt,secret}){
 if(!db?.prepare||!USER.test(userId||'')||adapter?.provider!=='chaoxing'||
    typeof adapter.forSession!=='function'||typeof adapter.pollQrLogin!=='function')
  fail('not_configured');
 if(!validSessionLogin({session,expiresAt}))fail('authorization_failed');
 await key(secret);
 try{await db.prepare('SELECT provider FROM academic_provider_sessions LIMIT 0').all();}
 catch{fail('not_configured');}
 try{
  return await saveAuthenticatedSchoolAccount({db,userId,provider:'chaoxing',
   adapter,session,expiresAt,secret});
 }catch(error){
  // A successful official QR approval must survive a transient inbox outage.
  // Store a session ONLY after an independent owner-bound school profile check.
  // Do not mark any academic records as synchronized in this fallback.
  if(error?.code!=='sync_failed')throw error;
  const reader=await adapter.forSession({session,userId});
  const proof=await reader?.verifySession?.();
  if(proof?.authorized!==true||proof.school!=='ncwu'||
     proof.principalBoundTo!==userId||proof.identityBound!==true)
   fail('authorization_failed');
  const encrypted=await seal(session,secret,userId,'chaoxing');
  const now=new Date().toISOString();
  try{
   await db.prepare(`INSERT INTO academic_provider_sessions
    (user_id,provider,ciphertext,nonce,expires_at,updated_at)
    VALUES (?,?,?,?,?,?)
    ON CONFLICT(user_id,provider) DO UPDATE SET ciphertext=excluded.ciphertext,
     nonce=excluded.nonce,expires_at=excluded.expires_at,updated_at=excluded.updated_at`)
    .bind(userId,'chaoxing',encrypted.ciphertext,encrypted.nonce,expiresAt,now).run();
  }catch{fail('database_unavailable');}
  return {provider:'chaoxing',connected:false,syncPending:true,expiresAt,
   lastSuccessAt:null};
 }
}

async function saveAuthenticatedSchoolAccount({db,userId,provider,adapter,session,
 expiresAt,secret,sync}){
 const outcome=await syncWith(db,userId,provider,adapter,session,{sync});
 const persisted=provider==='ncwu'?
  renewedNcwuSession(session,outcome.refreshedSession):
  provider==='second_class'?renewedSecondClassSession(session,outcome.refreshedSession):session;
 const encrypted=await seal(persisted,secret,userId,provider);
 const now=new Date().toISOString();
 try{
  await db.prepare(`INSERT INTO academic_provider_sessions
   (user_id,provider,ciphertext,nonce,expires_at,updated_at)
   VALUES (?,?,?,?,?,?)
   ON CONFLICT(user_id,provider) DO UPDATE SET ciphertext=excluded.ciphertext,
    nonce=excluded.nonce,expires_at=excluded.expires_at,updated_at=excluded.updated_at`)
   .bind(userId,provider,encrypted.ciphertext,encrypted.nonce,expiresAt,now).run();
 }catch{fail('database_unavailable');}
 return {provider,connected:true,expiresAt,
  lastSuccessAt:outcome.lastSuccessAt??outcome.capturedAt??now};
}
async function verifiedScheduleForReview(adapter,session,userId){
 const reader=await adapter.forSession({session,userId});
 if(reader?.provider!=='ncwu')fail('authorization_failed');
 const result=await collectVerifiedNcwu(reader,userId,{kinds:['schedule']})
  .catch(()=>fail('sync_failed'));
 const records=result.records.schedule;
 if(!records.length)fail('sync_failed');
 const digest=await crypto.subtle.digest('SHA-256',ENCODE.encode(
  JSON.stringify(records.slice().sort((a,b)=>a.externalId.localeCompare(b.externalId)))));
 return {records,hash:encoded(new Uint8Array(digest)),
  ...(typeof reader.getRefreshedSession==='function'?
   {refreshedSession:reader.getRefreshedSession()}:{})};
}
export async function prepareNcwuAccountReview({db,userId,adapter,username,password,
 academicUsername,academicPassword,secret}){
 if(!db?.prepare||!USER.test(userId||'')||adapter?.provider!=='ncwu'||
    typeof adapter.login!=='function'||typeof adapter.forSession!=='function')
  fail('not_configured');
 await key(secret);
 if(!validSchoolLoginInput({username,password,academicUsername,academicPassword}))
  fail('invalid_credentials');
 // Check schema before sending any password to the school.
 try{await db.prepare('SELECT provider FROM academic_provider_sessions LIMIT 0').all();}
 catch{fail('not_configured');}
 let authenticated;
 try{authenticated=await adapter.login({username,password,academicUsername,academicPassword});}
 catch(e){fail(['interactive_login_required','invalid_credentials','upstream_unavailable',
  'session_expired'].includes(e?.code)?e.code:'authorization_failed');}
 if(!validSessionLogin(authenticated))fail('authorization_failed');
 const {records,hash,refreshedSession}=await verifiedScheduleForReview(adapter,authenticated.session,userId);
 const expiresAt=new Date(Math.min(Date.parse(authenticated.expiresAt),
  Date.now()+10*60000)).toISOString();
 const pending={...renewedNcwuSession(authenticated.session,refreshedSession),
  reviewPending:true,reviewHash:hash};
 const encrypted=await seal(pending,secret,userId,'ncwu');
 try{
  await db.prepare(`INSERT INTO academic_provider_sessions
   (user_id,provider,ciphertext,nonce,expires_at,updated_at)
   VALUES (?,'ncwu',?,?,?,?)
   ON CONFLICT(user_id,provider) DO UPDATE SET ciphertext=excluded.ciphertext,
    nonce=excluded.nonce,expires_at=excluded.expires_at,updated_at=excluded.updated_at`)
   .bind(userId,encrypted.ciphertext,encrypted.nonce,expiresAt,new Date().toISOString()).run();
 }catch{fail('database_unavailable');}
 return {provider:'ncwu',reviewRequired:true,reviewHash:hash,expiresAt,
  schedule:records.map(({title,teacher,location,weekday,startSection,endSection,weeks})=>
   ({title,teacher,location,weekday,startSection,endSection,weeks}))};
}
export async function confirmNcwuAccountReview({db,userId,adapter,secret,reviewHash}){
 if(!db?.prepare||!USER.test(userId||'')||adapter?.provider!=='ncwu'||
    !/^[A-Za-z0-9_-]{43}$/.test(reviewHash||''))fail('invalid_credentials');
 await key(secret);
 let row;
 try{row=await db.prepare(
  "SELECT ciphertext,nonce,expires_at FROM academic_provider_sessions WHERE user_id=? AND provider='ncwu'"
 ).bind(userId).first();}catch{fail('database_unavailable');}
 if(!row||!validTime(row.expires_at)||Date.parse(row.expires_at)<=Date.now())
  fail('session_expired');
 const pending=await unseal(row,secret,userId,'ncwu');
 if(pending?.reviewPending!==true||pending.reviewHash!==reviewHash)
  fail('review_pending');
 const session={...pending};
 delete session.reviewPending;delete session.reviewHash;
 const {records,hash,refreshedSession}=await verifiedScheduleForReview(adapter,session,userId);
 if(hash!==reviewHash)fail('schedule_changed');
 const currentSession=renewedNcwuSession(session,refreshedSession);
 const reader=await adapter.forSession({session:currentSession,userId});
 // The records passed into the normal verified sync were just re-read from the
 // official school, matched to the owner's preview, and are NOT browser input.
 const reviewedReader={
  provider:'ncwu',verifySession:()=>reader.verifySession(),
  readSection:async({kind})=>{
   if(kind!=='schedule')fail('authorization_failed');
   return {kind:'schedule',complete:true,records};
  }
 };
 return saveAuthenticatedSchoolAccount({db,userId,provider:'ncwu',adapter,
  session:currentSession,expiresAt:new Date(Date.now()+NCWU_VAULT_MAX_AGE_MS).toISOString(),secret,
  sync:async(database,owner)=>{
   const outcome=await runVerifiedNcwuSync(database,owner,reviewedReader,{kinds:['schedule']});
   return typeof reader.getRefreshedSession==='function'?
    {...outcome,refreshedSession:reader.getRefreshedSession()}:outcome;
  }});
}

export async function refreshSchoolAccount({db,userId,provider,adapter,secret,sync}){
 if(!SUPPORTED.has(provider)||!USER.test(userId||'')||!db?.prepare||!adapter)fail('not_configured');
 let row;
 try{row=await db.prepare('SELECT ciphertext,nonce,expires_at FROM academic_provider_sessions WHERE user_id=? AND provider=?')
  .bind(userId,provider).first();}catch{fail('database_unavailable');}
 if(!row)fail('session_expired');
 if(!validTime(row.expires_at)||Date.parse(row.expires_at)<=Date.now())fail('session_expired');
 const session=await unseal(row,secret,userId,provider);
 if(session?.reviewPending===true)fail('review_pending');
 let outcome;
 try{outcome=await syncWith(db,userId,provider,adapter,session,{sync});}
 catch(e){
  if(e?.code==='authorization_failed'){
   // A stale sync must never revoke a newer owner login created meanwhile.
   await revokeSchoolAccount(db,userId,provider,{
    ciphertext:row.ciphertext,nonce:row.nonce
   }).catch(()=>{});
   fail('session_expired');
  }
  throw e;
 }
 if(provider==='chaoxing'||provider==='ncwu'||provider==='second_class'){
  const at=new Date().toISOString();
  const expiresAt=new Date(Date.now()+(provider==='second_class'?2*3600000:3*86400000)).toISOString();
  const persisted=provider==='ncwu'?
   renewedNcwuSession(session,outcome.refreshedSession):
   provider==='second_class'?renewedSecondClassSession(session,outcome.refreshedSession):session;
  const encrypted=await seal(persisted,secret,userId,provider);
  try{
   // Match the exact encrypted session read above: never restore a session
   // that was revoked or replaced by another login during this network sync.
   const update=await db.prepare('UPDATE academic_provider_sessions SET ciphertext=?,nonce=?,expires_at=?,updated_at=? WHERE user_id=? AND provider=? AND ciphertext=? AND nonce=?')
    .bind(encrypted.ciphertext,encrypted.nonce,expiresAt,at,userId,provider,row.ciphertext,row.nonce).run();
   if((update?.meta?.changes??update?.changes??1)!==1)fail('session_expired');
  }catch(e){if(e instanceof SchoolLoginError)throw e;fail('database_unavailable');}
 }
 return {provider,refreshed:true,lastSuccessAt:outcome.lastSuccessAt??outcome.capturedAt??new Date().toISOString()};
}
export async function revokeSchoolAccount(db,userId,provider,expectedSession=null){
 if(!SUPPORTED.has(provider)||!USER.test(userId||'')||!db?.prepare||typeof db.batch!=='function')
  fail('not_configured');
 // User-requested disconnect still revokes unconditionally. Only the internal
 // old-sync-failed path supplies the ciphertext/nonce it actually read.
 const guarded=expectedSession!==null;
 if(guarded&&(typeof expectedSession?.ciphertext!=='string'||
   !expectedSession.ciphertext||typeof expectedSession?.nonce!=='string'||
   !expectedSession.nonce))fail('not_configured');
 try{
  if(guarded){
   // D1 batch is transactional. If a different successful login or sync has
   // replaced the vault row, do not delete it or expire its connection status.
   const results=await db.batch([
    db.prepare('DELETE FROM academic_provider_sessions WHERE user_id=? AND provider=? AND ciphertext=? AND nonce=?')
     .bind(userId,provider,expectedSession.ciphertext,expectedSession.nonce),
    db.prepare("UPDATE academic_connections SET status='expired',last_error_code='authorization_failed' WHERE user_id=? AND provider=? AND mode='readonly' AND NOT EXISTS (SELECT 1 FROM academic_provider_sessions WHERE user_id=? AND provider=?)")
     .bind(userId,provider,userId,provider)
   ]);
   return {provider,disconnected:(results?.[0]?.meta?.changes??results?.[0]?.changes??0)===1};
  }
  await db.batch([
   db.prepare('DELETE FROM academic_provider_sessions WHERE user_id=? AND provider=?').bind(userId,provider),
   db.prepare("UPDATE academic_connections SET status='expired',last_error_code='authorization_failed' WHERE user_id=? AND provider=? AND mode='readonly'").bind(userId,provider)
  ]);
 }catch{fail('database_unavailable');}
 return {provider,disconnected:true};
}
export async function schoolSessionVaultReady(db,secret){
 if(!db?.prepare)return false;
 try{
  await key(secret);
  await db.prepare('SELECT provider FROM academic_provider_sessions LIMIT 1').all();
  return true;
 }catch{return false;}
}
/* An expiry timestamp is not proof that an encrypted session can still be
 * opened after SCHOOL_SESSION_KEY rotation or vault corruption. This performs
 * a local authenticity check only; actual school-side validity is rechecked on
 * the next trusted readSection sync. No plaintext leaves this module.
 */
export async function schoolSessionStates(db,userId,secret,now=Date.now()){
 if(!db?.prepare||!USER.test(userId||'')||!Number.isFinite(now))return {};
 // Fail closed when the secret or vault schema is missing.
 try{await key(secret);}
 catch{return {};}
 try{
  const result=await db.prepare(
   'SELECT provider,expires_at,ciphertext,nonce FROM academic_provider_sessions WHERE user_id=?'
  ).bind(userId).all();
  const states={};
  for(const row of result.results||[]){
   if(!SUPPORTED.has(row.provider))continue;
   if(!validTime(row.expires_at)||Date.parse(row.expires_at)<=now){
    states[row.provider]='expired';
    continue;
   }
   try{
    const session=await unseal(row,secret,userId,row.provider);
    states[row.provider]=session&&typeof session==='object'&&!Array.isArray(session)?
     session.reviewPending===true?'review_pending':'active':'unreadable';
   }catch{
    // Wrong encryption key, corrupted ciphertext or failed owner AAD.
    // Do not disclose plaintext, error causes, or cached school session.
    states[row.provider]='unreadable';
   }
  }
  return states;
 }catch{return {};} // A missing vault schema must not imply authorization.
}

/* Extend the existing Chaoxing vault ONLY after a same-owner official
 * profile was verified on this run. Protect concurrent logouts/reconnections
 * using the exact prior ciphertext+nonce. Never extend without proof.
 */
export async function renewVerifiedChaoxingSession({db,userId,secret,proof}){
 if(!db?.prepare||!USER.test(userId||'')||proof?.authorized!==true||
    proof?.provider!=='chaoxing'||proof?.school!=='ncwu'||
    proof?.scope!=='self'||proof?.identityBound!==true||
    proof?.principalBoundTo!==userId||!proof?.session)
  fail('authorization_failed');
 await key(secret);
 const row=await db.prepare(
  "SELECT ciphertext,nonce,expires_at FROM academic_provider_sessions WHERE user_id=? AND provider='chaoxing'"
 ).bind(userId).first();
 if(!row||!validTime(row.expires_at)||Date.parse(row.expires_at)<=Date.now())
  fail('session_expired');
 const session=await unseal(row,secret,userId,'chaoxing');
 if(session?.schema!=='chaoxing-session-v1'||
    session.uid!==proof.session.uid)fail('authorization_failed');
 const sealed=await seal(session,secret,userId,'chaoxing');
 const at=new Date().toISOString(),expires=new Date(Date.now()+3*86400000).toISOString();
 const updated=await db.prepare(
  "UPDATE academic_provider_sessions SET ciphertext=?,nonce=?,expires_at=?,updated_at=? "+
  "WHERE user_id=? AND provider='chaoxing' AND ciphertext=? AND nonce=?"
 ).bind(sealed.ciphertext,sealed.nonce,expires,at,
  userId,row.ciphertext,row.nonce).run();
 if((updated?.meta?.changes??updated?.changes??0)!==1)fail('session_expired');
 return true;
}
/* User-authenticated, owner-scoped read of the existing encrypted connection.
 * Never exposes the upstream cookie jar or permits arbitrary browser URLs.
 * All provider access is delegated to a registered trusted server adapter.
 */
export async function readConnectedChaoxing({db,userId,adapter,secret,reader}){
 if(!USER.test(userId||'')||!db?.prepare||adapter?.provider!=='chaoxing'||
    typeof adapter.forSession!=='function'||typeof reader!=='function')
   fail('not_configured');
 await key(secret);
 let row;
 try{
  row=await db.prepare('SELECT ciphertext,nonce,expires_at FROM academic_provider_sessions WHERE user_id=? AND provider=?')
   .bind(userId,'chaoxing').first();
 }catch{fail('database_unavailable');}
 if(!row||!validTime(row.expires_at)||Date.parse(row.expires_at)<=Date.now())
  fail('session_expired');
 const session=await unseal(row,secret,userId,'chaoxing');
 let trusted;
 try{trusted=await adapter.forSession({session,userId});}
 catch{fail('authorization_failed');}
 if(trusted?.provider!=='chaoxing'||typeof trusted.verifySession!=='function')
  fail('authorization_failed');
 let proof;
 try{proof=await trusted.verifySession();}
 catch(e){fail(e?.code==='upstream_unavailable'?'upstream_unavailable':'session_expired');}
 if(proof?.authorized!==true||proof?.scope!=='self'||proof?.school!=='ncwu'||
   proof?.provider!=='chaoxing'||proof?.identityBound!==true||
   proof?.principalBoundTo!==userId||!proof?.session)
   fail('authorization_failed');
 return reader(trusted,proof);
}
