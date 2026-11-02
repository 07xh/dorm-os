// Trusted internal integration point for future approved read-only sources.
// A browser-supplied "verified" flag is NEVER sufficient authentication.
// Only server-side adapters with real account/session verification may call this.
import {normalizeReadOnlySnapshot,applyReadOnlySnapshot} from './academic-readonly-sync.mjs';

export class ConnectorRunError extends Error {
  constructor(code){super(code);this.name='ConnectorRunError';this.code=code;}
}
const fail=code=>{throw new ConnectorRunError(code);};
const PROVIDERS=new Set(['chaoxing','ncwu']);
const safeId=v=>typeof v==='string'&&/^[A-Za-z0-9_-]{1,100}$/.test(v);
const errorCodes=new Set(['authorization_failed','upstream_unavailable','invalid_response']);

async function markFailure(db,userId,connectionId,code,now){
 if(!connectionId||!errorCodes.has(code))return;
 await db.prepare(`UPDATE academic_connections
   SET status=?,last_attempt_at=?,last_error_code=?
   WHERE id=? AND user_id=? AND mode='readonly'`)
   .bind(code==='authorization_failed'?'expired':'error',now,code,connectionId,userId).run();
}
async function ensureReadOnlyConnection(db,userId,provider,now,id){
 const existing=await db.prepare(`SELECT id,mode FROM academic_connections
   WHERE user_id=? AND provider=? AND profile_key='default'`)
   .bind(userId,provider).first();
 if(existing){
  if(existing.mode!=='readonly')fail('source_mode_conflict');
  // Re-enable a previously expired/error connection only after the adapter
  // has independently checked a valid session and delivered a valid page.
  await db.prepare(`UPDATE academic_connections
    SET status='partial',last_attempt_at=?,last_error_code=NULL
    WHERE id=? AND user_id=? AND mode='readonly'`).bind(now,existing.id,userId).run();
  return existing.id;
 }
 const connId=id();
 if(!safeId(connId))fail('invalid_connection_id');
 await db.batch([
  db.prepare('INSERT OR IGNORE INTO academic_users(id,created_at) VALUES (?,?)')
   .bind(userId,now),
  db.prepare(`INSERT INTO academic_connections
   (id,user_id,provider,profile_key,mode,status,last_attempt_at)
   VALUES (?,? ,?,'default','readonly','partial',?)`)
   .bind(connId,userId,provider,now)
 ]);
 return connId;
}

export async function runTrustedConnector(db,userId,adapter,{
 now=()=>new Date().toISOString(),id=()=>crypto.randomUUID(),maxPages=100
}={}){
 if(!safeId(userId)||!adapter||!PROVIDERS.has(adapter.provider)||
    typeof adapter.verifySession!=='function'||typeof adapter.readPage!=='function'||
    !Number.isInteger(maxPages)||maxPages<1||maxPages>100)fail('connector_not_configured');
 const provider=adapter.provider;
 const previous=await db.prepare(`SELECT id FROM academic_connections
   WHERE user_id=? AND provider=? AND profile_key='default' AND mode='readonly'`)
   .bind(userId,provider).first();
 let verified;
 try{verified=await adapter.verifySession();}
 catch{
  await markFailure(db,userId,previous?.id,'authorization_failed',now()).catch(()=>{});
  fail('authorization_failed');
 }
 if(!verified||verified.authorized!==true||verified.scope!=='self'||
    verified.provider!==provider){
  await markFailure(db,userId,previous?.id,'authorization_failed',now()).catch(()=>{});
  fail('authorization_failed');
 }
 let connectionId=previous?.id??null,activated=false,cursor=null,pages=0,received=0,inserted=0,updated=0,linked=0;
 const observed=new Set();
 try{
  for(let i=0;i<maxPages;i++){
   // A trusted adapter performs only approved, authorized read-only requests.
   // No caller-supplied arbitrary URLs or credential strings reach this runner.
   const page=await adapter.readPage({cursor,session:verified.session});
   if(!page||typeof page!=='object'||Array.isArray(page)||
      !Array.isArray(page.tasks)||page.tasks.length>20||
      typeof page.complete!=='boolean')fail('invalid_response');
   if(page.nextCursor!==null&&page.nextCursor!==undefined &&
      (!safeId(page.nextCursor)||page.complete===true))fail('invalid_response');
   if(page.tasks.length===0&&
      !(cursor===null&&page.complete===true&&page.verifiedEmpty===true))
      fail('invalid_response');
   const snapshot={format:'xiaoxu-task-batch-v1',source:provider,tasks:page.tasks};
   try{normalizeReadOnlySnapshot(snapshot);}catch{fail('invalid_response');}
   for(const task of page.tasks){
    if(observed.has(task.externalId))fail('invalid_response');
    observed.add(task.externalId);
   }
   if(!activated){
    connectionId=await ensureReadOnlyConnection(db,userId,provider,now(),id);
    activated=true;
   }
   // Complete may only be claimed when this is the last, verified final page.
   const coverage=page.complete?'complete':'partial';
   const result=await applyReadOnlySnapshot(db,userId,connectionId,snapshot,{
     coverage,now,id
   });
   pages++;received+=result.received;inserted+=result.inserted;
   updated+=result.updated;linked+=result.linked;
   if(page.nextCursor==null)return {
    provider,connectionId,pages,received,inserted,updated,linked,coverage
   };
   if(observed.has('__cursor__'+page.nextCursor))fail('invalid_response');
   observed.add('__cursor__'+page.nextCursor);
   cursor=page.nextCursor;
  }
  fail('invalid_response');
 }catch(e){
  // Preserve previously saved assignments on any failure. Never log the
  // adapter's raw exceptions, HTML, token, cookie, or signed request.
  const code=e instanceof ConnectorRunError?e.code:'upstream_unavailable';
  await markFailure(db,userId,connectionId,
    errorCodes.has(code)?code:'invalid_response',now()).catch(()=>{});
  fail(errorCodes.has(code)?code:'invalid_response');
 }
}
