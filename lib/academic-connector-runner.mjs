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
 now=()=>new Date().toISOString(),id=()=>crypto.randomUUID(),
 maxPages=100,maxRecords=250,additionalStatements=[]
}={}){
 if(!safeId(userId)||!adapter||!PROVIDERS.has(adapter.provider)||
    typeof adapter.verifySession!=='function'||typeof adapter.readPage!=='function'||
    !Number.isInteger(maxPages)||maxPages<1||maxPages>100||
    !Number.isInteger(maxRecords)||maxRecords<1||maxRecords>250)fail('connector_not_configured');
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
 // A school's valid login is insufficient unless the *same* principal was
 // independently bound by this trusted backend adapter to the current owner.
 if(!verified||verified.authorized!==true||verified.scope!=='self'||
    verified.school!=='ncwu'||verified.identityBound!==true||
    verified.principalBoundTo!==userId||verified.provider!==provider||
    !verified.session){
  await markFailure(db,userId,previous?.id,'authorization_failed',now()).catch(()=>{});
  fail('authorization_failed');
 }
 let cursor=null,pages=0,complete=false;
 const observed=new Set(),allTasks=[];
 try{
  // Phase one: verify every page, global IDs and completeness without D1
  // mutations. A later upstream failure must not leave a half-new task set.
  for(let i=0;i<maxPages;i++){
   const page=await adapter.readPage({cursor,session:verified.session});
   if(!page||typeof page!=='object'||Array.isArray(page)||
      !Array.isArray(page.tasks)||page.tasks.length>20||
      typeof page.complete!=='boolean')fail('invalid_response');
   const next=page.nextCursor??null;
   if(page.complete!==(next===null))fail('invalid_response');
   if(next!==null&&!safeId(next))fail('invalid_response');
   if(!page.tasks.length &&
     !(i===0&&page.complete===true&&page.verifiedEmpty===true))
     fail('invalid_response');
   const batch={format:'xiaoxu-task-batch-v1',source:provider,tasks:page.tasks};
   let validated;
   try{validated=normalizeReadOnlySnapshot(batch).tasks;}
   catch{fail('invalid_response');}
   for(let n=0;n<validated.length;n++){
    const task=page.tasks[n],key=validated[n].externalId;
    if(observed.has(key))fail('invalid_response');
    observed.add(key);allTasks.push(task);
   }
   if(allTasks.length>maxRecords)fail('invalid_response');
   pages++;
   if(page.complete){complete=true;break;}
   if(observed.has('__cursor__'+next))fail('invalid_response');
   observed.add('__cursor__'+next);
   cursor=next;
  }
  if(!complete)fail('invalid_response');
  // Phase two: exactly one atomic, owner-scoped D1 batch. The default
  // 20-task limit remains in force for all other importer callers.
  const connectionId=await ensureReadOnlyConnection(db,userId,provider,now(),id);
  const result=await applyReadOnlySnapshot(db,userId,connectionId,{
    format:'xiaoxu-task-batch-v1',source:provider,tasks:allTasks
  },{coverage:'complete',maxTasks:maxRecords,now,id,additionalStatements});
  return {provider,connectionId,pages,received:result.received,
    inserted:result.inserted,updated:result.updated,linked:result.linked,
    coverage:'complete'};
 }catch(e){
  // Preserve all pre-existing assignments on verification/query failure.
  // Never log upstream raw error, URL, session, cookie, or response.
  const code=e instanceof ConnectorRunError?e.code:'upstream_unavailable';
  await markFailure(db,userId,previous?.id,
    errorCodes.has(code)?code:'invalid_response',now()).catch(()=>{});
  fail(errorCodes.has(code)?code:'invalid_response');
 }
}
