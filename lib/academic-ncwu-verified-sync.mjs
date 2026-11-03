/* NCWU KINGO trusted read-only bridge (server-side only).
 * No public POST handler invokes this module. The school browser login is NOT
 * delegated OAuth; credentials/cookies must never be sent here by the client.
 * A separately verified, owner-bound adapter is required before any write.
 */
import {normalizeRecord} from './academic-contract.mjs';

const KINDS=Object.freeze(['schedule','grades','exams']);
const ERROR_CODES=new Set(['authorization_failed','invalid_response','upstream_unavailable']);
const ID=/^[A-Za-z0-9:_-]{1,120}$/;
const fail=code=>{throw new NcwuSyncError(code);};

export class NcwuSyncError extends Error{
 constructor(code){super(code);this.name='NcwuSyncError';this.code=code;}
}
function normalizeSection(raw,kind,limit){
 if(!raw||typeof raw!=='object'||Array.isArray(raw)||
    raw.kind!==kind||raw.complete!==true||
    !Array.isArray(raw.records)||raw.records.length>limit||
    (!raw.records.length&&raw.verifiedEmpty!==true))fail('invalid_response');
 const seen=new Set();
 return raw.records.map(item=>{
   let record;
   try{record=normalizeRecord(kind,item);}catch{fail('invalid_response');}
   const key=record.term+'|'+record.externalId;
   if(seen.has(key))fail('invalid_response');
   seen.add(key);
   return record;
 });
}
async function markFailure(db,userId,code,at){
 if(!ERROR_CODES.has(code))return;
 try{
  await db.prepare("UPDATE academic_connections SET status=?,last_attempt_at=?,last_error_code=? WHERE user_id=? AND provider='ncwu' AND profile_key='default' AND mode='readonly'")
   .bind(code==='authorization_failed'?'expired':'error',at,code,userId).run();
 }catch{/* never overwrite or expose the last valid snapshot on failure */}
}
export async function collectVerifiedNcwu(adapter,userId,{maxRecords=150}={}){
 if(!ID.test(userId||'')||!adapter||adapter.provider!=='ncwu'||
    typeof adapter.verifySession!=='function'||typeof adapter.readSection!=='function'||
    !Number.isInteger(maxRecords)||maxRecords<1||maxRecords>250)
   fail('connector_not_configured');
 let proof;
 try{proof=await adapter.verifySession();}catch{fail('authorization_failed');}
 // These assertions are accepted ONLY from the private trusted adapter,
 // not from browser JSON, user supplied headers, or public probe results.
 if(!proof||proof.authorized!==true||proof.scope!=='self'||
    proof.school!=='ncwu'||proof.provider!=='ncwu'||
    proof.identityBound!==true||proof.principalBoundTo!==userId||
    proof.officialOrigin!=='https://jwmis.ncwu.edu.cn'||!proof.session)
   fail('authorization_failed');
 const data={};
 for(const kind of KINDS){
  let raw;
  try{raw=await adapter.readSection({kind,session:proof.session});}
  catch{fail('upstream_unavailable');}
  data[kind]=normalizeSection(raw,kind,maxRecords);
 }
 return {source:'ncwu',kinds:KINDS,records:data,provenance:'trusted_readonly'};
}

export async function runVerifiedNcwuSync(db,userId,adapter,{
 now=()=>new Date().toISOString(),id=()=>crypto.randomUUID(),maxRecords=150
}={}){
 if(!db?.prepare||typeof db.batch!=='function'||!ID.test(userId||''))
  fail('connector_not_configured');
 let collected;
 try{collected=await collectVerifiedNcwu(adapter,userId,{maxRecords});}
 catch(error){
  const code=error instanceof NcwuSyncError?error.code:'upstream_unavailable';
  await markFailure(db,userId,code,now());
  fail(code);
 }
 const at=now();
 if(typeof at!=='string'||!Number.isFinite(Date.parse(at)))fail('connector_not_configured');
 const conn=await db.prepare("SELECT id,mode FROM academic_connections WHERE user_id=? AND provider='ncwu' AND profile_key='default'")
  .bind(userId).first();
 if(conn&&conn.mode!=='readonly')fail('source_mode_conflict');
 const connectionId=conn?.id??id();
 if(!ID.test(connectionId))fail('connector_not_configured');
 const statements=[db.prepare('INSERT OR IGNORE INTO academic_users(id,created_at) VALUES (?,?)')
  .bind(userId,at)];
 if(conn){
  statements.push(db.prepare("UPDATE academic_connections SET status='connected',last_attempt_at=?,last_success_at=?,last_error_code=NULL WHERE id=? AND user_id=? AND mode='readonly'")
   .bind(at,at,connectionId,userId));
 }else{
  statements.push(db.prepare("INSERT INTO academic_connections(id,user_id,provider,profile_key,mode,status,last_attempt_at,last_success_at) VALUES (?,?,'ncwu','default','readonly','connected',?,?)")
   .bind(connectionId,userId,at,at));
 }
 const counts={};
 for(const kind of KINDS){
  const batchId=id();
  if(!ID.test(batchId))fail('connector_not_configured');
  const records=collected.records[kind];
  counts[kind]=records.length;
  for(const row of records){
   statements.push(db.prepare(
    'INSERT INTO academic_verified_records(user_id,kind,source,external_id,term,payload,batch_id,updated_at) VALUES(?,?,?,?,?,?,?,?) '+
    'ON CONFLICT(user_id,kind,source,external_id,term) DO UPDATE SET payload=excluded.payload,batch_id=excluded.batch_id,updated_at=excluded.updated_at'
   ).bind(userId,kind,'ncwu',row.externalId,row.term,JSON.stringify(row),batchId,at));
  }
  statements.push(db.prepare(
   'INSERT INTO academic_verified_batches(user_id,kind,source,last_success_at,batch_id,updated_at,provenance) VALUES(?,?,?,?,?,?,?) '+
   'ON CONFLICT(user_id,kind) DO UPDATE SET source=excluded.source,last_success_at=excluded.last_success_at,batch_id=excluded.batch_id,updated_at=excluded.updated_at,provenance=excluded.provenance'
  ).bind(userId,kind,'ncwu',at,batchId,at,'trusted_readonly'));
 }
 try{await db.batch(statements);}
 catch{fail('database_unavailable');}
 return {provider:'ncwu',connectionId,counts,lastSuccessAt:at,provenance:'trusted_readonly'};
}
