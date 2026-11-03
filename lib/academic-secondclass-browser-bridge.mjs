/* Owner-scoped pairing for user's own logged-in second-class browser.
 * This is NOT university authorization; synced snapshots remain user_import.
 */
const TABLE='academic_secondclass_browser_bridges';
const TOKEN=/^[A-Za-z0-9_-]{43}$/;
const HEX=/^[a-f0-9]{64}$/;
const ENC=new TextEncoder();
export const BRIDGE_TTL_MS=30*86400_000;
const encode=bytes=>btoa(String.fromCharCode(...bytes))
 .replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
const bytes=()=>crypto.getRandomValues(new Uint8Array(32));
const hash=async token=>{
 const raw=await crypto.subtle.digest('SHA-256',ENC.encode(token));
 return [...new Uint8Array(raw)].map(x=>x.toString(16).padStart(2,'0')).join('');
};
export class BridgeError extends Error{
 constructor(code){super(code);this.code=code;this.name='BridgeError';}
}
const error=code=>{throw new BridgeError(code);};
const validUser=x=>typeof x==='string'&&/^[A-Za-z0-9_-]{1,100}$/.test(x);
export function checkOwnerMutation(request){
 const url=new URL(request.url);
 return url.protocol==='https:'&&request.headers.get('origin')===url.origin&&
  request.headers.get('sec-fetch-site')!=='cross-site'&&
  (request.headers.get('content-type')||'').toLowerCase().startsWith('application/json');
}
export function bridgeTokenFromRequest(req){
 const header=req.headers.get('authorization')||'';
 if(!/^Bearer [A-Za-z0-9_-]{43}$/.test(header))error('invalid_bridge_token');
 return header.slice(7);
}
export async function bridgeStatus(db,userId,now=Date.now()){
 if(!validUser(userId))error('invalid_owner');
 const row=await db.prepare('SELECT expires_at,last_sync_at,last_status FROM '+TABLE+' WHERE user_id=?').bind(userId).first();
 if(!row)return {state:'not_paired',lastSyncAt:null,expiresAt:null};
 return {state:Date.parse(row.expires_at)<=now?'expired':
  row.last_status==='synced'?'browser_sync_enabled':'paired_not_synced',
  lastSyncAt:row.last_sync_at||null,expiresAt:row.expires_at};
}
export async function createBrowserBridge(db,userId,now=Date.now()){
 if(!db?.prepare||!validUser(userId))error('not_configured');
 const token=encode(bytes()),tokenHash=await hash(token);
 if(!TOKEN.test(token)||!HEX.test(tokenHash))error('not_configured');
 const createdAt=new Date(now).toISOString(),expiresAt=new Date(now+BRIDGE_TTL_MS).toISOString();
 await db.prepare('INSERT INTO '+TABLE+
  ' (user_id,token_hash,created_at,expires_at,last_sync_at,last_status,last_error_code)'+
  " VALUES (?,?,?,?,NULL,'pending',NULL)"+
  " ON CONFLICT(user_id) DO UPDATE SET token_hash=excluded.token_hash,"+
  "created_at=excluded.created_at,expires_at=excluded.expires_at,"+
  "last_sync_at=NULL,last_status='pending',last_error_code=NULL")
  .bind(userId,tokenHash,createdAt,expiresAt).run();
 return {token,expiresAt,state:'paired_not_synced'};
}
export async function revokeBrowserBridge(db,userId){
 if(!db?.prepare||!validUser(userId))error('not_configured');
 await db.prepare('DELETE FROM '+TABLE+' WHERE user_id=?').bind(userId).run();
 return {state:'not_paired'};
}
export async function validateBrowserBridge(db,token,now=Date.now()){
 if(!db?.prepare||!TOKEN.test(token||''))error('invalid_bridge_token');
 const tokenHash=await hash(token);
 const row=await db.prepare('SELECT user_id,expires_at,last_sync_at FROM '+TABLE+' WHERE token_hash=?').bind(tokenHash).first();
 if(!row||!validUser(row.user_id))error('invalid_bridge_token');
 if(Date.parse(row.expires_at)<=now)error('bridge_expired');
 if(row.last_sync_at&&now-Date.parse(row.last_sync_at)<10*60_000)
  error('sync_too_soon');
 return {userId:row.user_id,tokenHash};
}
export async function markBridgeSynced(db,userId,tokenHash,stamp){
 const result=await db.prepare("UPDATE "+TABLE+" SET last_sync_at=?,last_status='synced',last_error_code=NULL WHERE user_id=? AND token_hash=?")
  .bind(stamp,userId,tokenHash).run();
 return result?.meta?.changes??result?.changes??null;
}
