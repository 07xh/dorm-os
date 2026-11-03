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
export class SchoolLoginError extends Error{
 constructor(code){super(ERROR_CODES.has(code)?code:'sync_failed');this.code=this.message;this.name='SchoolLoginError';}
}
const fail=code=>{throw new SchoolLoginError(code);};
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
async function syncWith(db,userId,provider,adapter,session,{sync}={}){
 const read=await adapter.forSession({session,userId});
 if(!read||read.provider!==provider||typeof read.verifySession!=='function')fail('authorization_failed');
 try{
  return await (sync?sync(db,userId,provider,read):
   // A verified timetable is allowed to precede grades/exams. No client input
   // can select kinds or waive the original owner/session identity proof.
   provider==='ncwu'?runVerifiedNcwuSync(db,userId,read,{kinds:['schedule']}):
   provider==='second_class'?runVerifiedNcwuSecondClassSync(db,userId,read):
   runVerifiedChaoxingInboxSync(db,userId,read));
 }catch(e){fail(e?.code==='authorization_failed'?'authorization_failed':'sync_failed');}
}
export async function connectSchoolAccount({db,userId,provider,adapter,username,password,
 academicUsername,academicPassword,secret,sync}){
 if(!db?.prepare||!USER.test(userId||'')||!SUPPORTED.has(provider))fail('not_configured');
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
async function saveAuthenticatedSchoolAccount({db,userId,provider,adapter,session,
 expiresAt,secret,sync}){
 const outcome=await syncWith(db,userId,provider,adapter,session,{sync});
 const encrypted=await seal(session,secret,userId,provider);
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
 return {records,hash:encoded(new Uint8Array(digest))};
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
 const {records,hash}=await verifiedScheduleForReview(adapter,authenticated.session,userId);
 const expiresAt=new Date(Math.min(Date.parse(authenticated.expiresAt),
  Date.now()+10*60000)).toISOString();
 const pending={...authenticated.session,reviewPending:true,reviewHash:hash};
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
 const {records,hash}=await verifiedScheduleForReview(adapter,session,userId);
 if(hash!==reviewHash)fail('schedule_changed');
 const reader=await adapter.forSession({session,userId});
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
  session,expiresAt:new Date(Math.min(Date.now()+2*3600000,
   Date.parse(row.expires_at)+2*3600000)).toISOString(),secret,
  sync:(database,owner)=>runVerifiedNcwuSync(database,owner,reviewedReader,{kinds:['schedule']})});
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
   await revokeSchoolAccount(db,userId,provider).catch(()=>{});
   fail('session_expired');
  }
  throw e;
 }
 if(provider==='chaoxing'){
  const at=new Date().toISOString();
  const expiresAt=new Date(Date.now()+3*86400000).toISOString();
  const encrypted=await seal(session,secret,userId,provider);
  try{
   await db.prepare('UPDATE academic_provider_sessions SET ciphertext=?,nonce=?,expires_at=?,updated_at=? WHERE user_id=? AND provider=?')
    .bind(encrypted.ciphertext,encrypted.nonce,expiresAt,at,userId,provider).run();
  }catch{fail('database_unavailable');}
 }
 return {provider,refreshed:true,lastSuccessAt:outcome.lastSuccessAt??outcome.capturedAt??new Date().toISOString()};
}
export async function revokeSchoolAccount(db,userId,provider){
 if(!SUPPORTED.has(provider)||!USER.test(userId||'')||!db?.prepare)fail('not_configured');
 try{
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
 catch{fail('session_expired');}
 if(proof?.authorized!==true||proof?.scope!=='self'||proof?.school!=='ncwu'||
   proof?.provider!=='chaoxing'||proof?.identityBound!==true||
   proof?.principalBoundTo!==userId||!proof?.session)
   fail('authorization_failed');
 return reader(trusted);
}
