/* Private school-login contract. No provider is enabled without a registered,
 * real server-side adapter. A school site opened in another tab grants NOTHING.
 * Upstream passwords are ephemeral and never written to D1 or browser storage.
 */
import {runVerifiedNcwuSync} from './academic-ncwu-verified-sync.mjs';
import {runTrustedConnector} from './academic-connector-runner.mjs';
import {runVerifiedNcwuSecondClassSync} from './academic-ncwu-second-class-sync.mjs';
const SUPPORTED=new Set(['ncwu','chaoxing','second_class']);
const ENCODE=new TextEncoder(),DECODE=new TextDecoder();
const USER=/^[A-Za-z0-9_-]{1,100}$/;
const ERROR_CODES=new Set(['not_configured','invalid_credentials','interactive_login_required','authorization_failed','session_expired','database_unavailable','sync_failed']);
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
  typeof value.password==='string'&&value.password.length>0&&value.password.length<=512;
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
   provider==='ncwu'?runVerifiedNcwuSync(db,userId,read):
   provider==='second_class'?runVerifiedNcwuSecondClassSync(db,userId,read):
   runTrustedConnector(db,userId,read));
 }catch(e){fail(e?.code==='authorization_failed'?'authorization_failed':'sync_failed');}
}
export async function connectSchoolAccount({db,userId,provider,adapter,username,password,secret,sync}){
 if(!db?.prepare||!USER.test(userId||'')||!SUPPORTED.has(provider))fail('not_configured');
 if(!adapter||adapter.provider!==provider||typeof adapter.login!=='function'||
   typeof adapter.forSession!=='function')fail('not_configured');
 if(!validSchoolLoginInput({username,password}))fail('invalid_credentials');
 // Fail closed before accepting credentials if the session vault is missing.
 await key(secret);
 try{await db.prepare('SELECT provider FROM academic_provider_sessions WHERE user_id=? LIMIT 1').bind(userId).all();}
 catch{fail('not_configured');}
 let result;
 try{result=await adapter.login({username,password});}
 catch(e){fail(e?.code==='interactive_login_required'?'interactive_login_required':'authorization_failed');}
 if(!validSessionLogin(result))fail('authorization_failed');
 // This trusted read must independently establish the school principal,
 // owner identity, and complete academic data before any session is saved.
 const outcome=await syncWith(db,userId,provider,adapter,result.session,{sync});
 const encrypted=await seal(result.session,secret,userId,provider);
 const now=new Date().toISOString();
 try{
  await db.prepare(`INSERT INTO academic_provider_sessions
   (user_id,provider,ciphertext,nonce,expires_at,updated_at)
   VALUES (?,?,?,?,?,?)
   ON CONFLICT(user_id,provider) DO UPDATE SET ciphertext=excluded.ciphertext,
    nonce=excluded.nonce,expires_at=excluded.expires_at,updated_at=excluded.updated_at`)
   .bind(userId,provider,encrypted.ciphertext,encrypted.nonce,result.expiresAt,now).run();
 }catch{fail('database_unavailable');}
 return {provider,connected:true,expiresAt:result.expiresAt,lastSuccessAt:outcome.lastSuccessAt??outcome.capturedAt??now};
}
export async function refreshSchoolAccount({db,userId,provider,adapter,secret,sync}){
 if(!SUPPORTED.has(provider)||!USER.test(userId||'')||!db?.prepare||!adapter)fail('not_configured');
 let row;
 try{row=await db.prepare('SELECT ciphertext,nonce,expires_at FROM academic_provider_sessions WHERE user_id=? AND provider=?')
  .bind(userId,provider).first();}catch{fail('database_unavailable');}
 if(!row)fail('session_expired');
 if(!validTime(row.expires_at)||Date.parse(row.expires_at)<=Date.now())fail('session_expired');
 const session=await unseal(row,secret,userId,provider);
 let outcome;
 try{outcome=await syncWith(db,userId,provider,adapter,session,{sync});}
 catch(e){
  if(e?.code==='authorization_failed'){
   await revokeSchoolAccount(db,userId,provider).catch(()=>{});
   fail('session_expired');
  }
  throw e;
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
export async function schoolSessionStates(db,userId){
 if(!db?.prepare||!USER.test(userId||''))return {};
 try{
  const result=await db.prepare('SELECT provider,expires_at FROM academic_provider_sessions WHERE user_id=?').bind(userId).all();
  return Object.fromEntries((result.results||[]).filter(row=>SUPPORTED.has(row.provider)).map(row=>[
   row.provider,validTime(row.expires_at)&&Date.parse(row.expires_at)>Date.now()?'active':'expired'
  ]));
 }catch{return {};} // No schema => existing read-only overview still works.
}
