// Single-owner, self-hosted Cloudflare Pages login. No public registration.
// Owner passphrase is a Cloudflare Pages encrypted Secret, NEVER in the repo.
// Explicit ACADEMIC_AUTH_MODE=owner_password is required to activate this.
import {OWNER_SESSION_COOKIE} from './academic-owner-session.mjs';
const OWNER_USER='local-owner-v1';
const IDENTITY='local-owner';
const encoder=new TextEncoder();
const DEFAULT_SESSION_DAYS=7;
const MIN_SESSION_DAYS=1;
const MAX_SESSION_DAYS=30;
function sessionDays(value){
 if(value===undefined)return DEFAULT_SESSION_DAYS;
 if(typeof value!=='number'||!Number.isInteger(value)||
   value<MIN_SESSION_DAYS||value>MAX_SESSION_DAYS)return null;
 return value;
}
const WINDOW_MS=15*60*1000;
const LIMIT_IP=9;
const LIMIT_GLOBAL=48;

const bytesToHex=bytes=>Array.from(bytes,x=>x.toString(16).padStart(2,'0')).join('');
async function digest(str,subtle=crypto.subtle){
 return bytesToHex(new Uint8Array(await subtle.digest('SHA-256',encoder.encode(str))));
}
function equalHex(a,b){
 if(typeof a!=='string'||typeof b!=='string'||a.length!==b.length)return false;
 let diff=0;
 for(let i=0;i<a.length;i++)diff|=a.charCodeAt(i)^b.charCodeAt(i);
 return diff===0;
}
function base64url(bytes){
 let str='';for(const b of bytes)str+=String.fromCharCode(b);
 return btoa(str).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
}
function ownerConfig(env){
 return env?.ACADEMIC_AUTH_MODE==='owner_password' &&
  typeof env?.OWNER_LOGIN_SECRET==='string' &&
  env.OWNER_LOGIN_SECRET.length>=8&&env.OWNER_LOGIN_SECRET.length<=256 &&
  !!env?.ACADEMIC_DB?.prepare && !!env?.ACADEMIC_DB?.batch;
}
function sameOrigin(request){
 const origin=request.headers.get('origin');
 const fetchSite=request.headers.get('sec-fetch-site');
 return origin===new URL(request.url).origin &&
   fetchSite!=='cross-site';
}
async function attempt(db,bucket,now){
 const end=now+WINDOW_MS;
 const row=await db.prepare(
  'INSERT INTO academic_auth_login_attempts(bucket,hits,window_end) VALUES(?,1,?) '+
  'ON CONFLICT(bucket) DO UPDATE SET hits=CASE WHEN window_end<=? THEN 1 ELSE hits+1 END, '+
  'window_end=CASE WHEN window_end<=? THEN excluded.window_end ELSE window_end END '+
  'RETURNING hits'
 ).bind(bucket,end,now,now).first();
 if(!row||!Number.isInteger(row.hits))throw Error('auth_rate_limit_unavailable');
 return row.hits;
}
export function sessionSetCookie(token,days=DEFAULT_SESSION_DAYS){
 const valid=sessionDays(days);
 if(valid===null)throw Error('invalid_session_duration');
 return OWNER_SESSION_COOKIE+'='+token+
   '; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age='+(valid*24*3600);
}
export function sessionClearCookie(){
 return OWNER_SESSION_COOKIE+'=; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=0';
}
export function ownerModeEnabled(env){return ownerConfig(env);}
export function validateLoginOrigin(request){return sameOrigin(request);}
export async function createOwnerSession(request,env,{clock=()=>Date.now(),subtle=crypto.subtle}={}){
 if(!ownerConfig(env))return {ok:false,status:503,error:'auth_not_configured'};
 const db=env.ACADEMIC_DB;
 const ip=request.headers.get('CF-Connecting-IP')||'unknown';
 const ipBucket='ip:'+await digest(env.OWNER_LOGIN_SECRET+':'+ip,subtle);
 const now=clock();
 try{
  const count=await attempt(db,ipBucket,now);
  const global=await attempt(db,'owner:all',now);
  if(count>LIMIT_IP||global>LIMIT_GLOBAL)
   return {ok:false,status:429,error:'too_many_attempts'};
 }catch{return {ok:false,status:503,error:'auth_rate_limit_unavailable'};}
 let body;
 try{
  if(Number(request.headers.get('content-length')||0)>1024)
   return {ok:false,status:413,error:'payload_too_large'};
  const raw=await request.text();
  if(raw.length>1024)return {ok:false,status:413,error:'payload_too_large'};
  body=JSON.parse(raw);
 }catch{return {ok:false,status:400,error:'invalid_json'};}
 if(!body||Array.isArray(body)||typeof body!=='object'||
    typeof body.passphrase!=='string'||body.passphrase.length>256||
    Object.keys(body).length<1||Object.keys(body).length>2||
    Object.keys(body).some(key=>key!=='passphrase'&&key!=='durationDays'))
   return {ok:false,status:400,error:'invalid_input'};
 const durationDays=sessionDays(body.durationDays);
 if(durationDays===null)return {ok:false,status:400,error:'invalid_session_duration'};
 const [expected,received]=await Promise.all([
  digest(env.OWNER_LOGIN_SECRET,subtle),digest(body.passphrase,subtle)
 ]);
 if(!equalHex(expected,received))
  return {ok:false,status:401,error:'invalid_credentials'};
 const token=base64url(crypto.getRandomValues(new Uint8Array(32)));
 const tokenHash=await digest(token,subtle);
 const date=new Date(now).toISOString();
 const expiry=new Date(now+durationDays*24*3600*1000).toISOString();
 try{
  await db.batch([
   db.prepare('INSERT OR IGNORE INTO academic_users(id,created_at) VALUES(?,?)').bind(OWNER_USER,date),
   db.prepare('INSERT OR IGNORE INTO academic_auth_identities(id,user_id,login_identifier,enrolled_at) VALUES(?,?,?,?)')
     .bind(IDENTITY,OWNER_USER,'owner',date),
   db.prepare('INSERT INTO academic_auth_sessions(id,identity_id,token_hash,created_at,expires_at) VALUES(?,?,?,?,?)')
     .bind(crypto.randomUUID(),IDENTITY,tokenHash,date,expiry)
  ]);
 }catch{return {ok:false,status:503,error:'auth_database_unavailable'};}
 return {ok:true,userId:OWNER_USER,token,expiresAt:expiry,durationDays};
}
export async function revokeOwnerSession(request,env,{subtle=crypto.subtle}={}){
 if(!ownerConfig(env))return {ok:false,status:503,error:'auth_not_configured'};
 const raw=request.headers.get('cookie')||'';
 if(raw.length>8192)return {ok:false,status:401,error:'unauthorized'};
 const matches=raw.split(';').map(x=>x.trim()).filter(x=>x.startsWith(OWNER_SESSION_COOKIE+'='));
 if(matches.length!==1)return {ok:false,status:401,error:'unauthorized'};
 const token=matches[0].slice(OWNER_SESSION_COOKIE.length+1);
 if(!/^[A-Za-z0-9_-]{43}$/.test(token))return {ok:false,status:401,error:'unauthorized'};
 try{
  await env.ACADEMIC_DB.prepare(
   'UPDATE academic_auth_sessions SET revoked_at=? WHERE token_hash=? AND revoked_at IS NULL'
  ).bind(new Date().toISOString(),await digest(token,subtle)).run();
  return {ok:true};
 }catch{return {ok:false,status:503,error:'auth_database_unavailable'};}
}
