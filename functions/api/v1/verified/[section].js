/* 校序 Academic OS verified snapshot API. Extends the existing Cloudflare
 * Access JWT + per-user D1 foundation from M1-A; never trusts a browser user ID.
 */
import {verifyAcademicAccess} from '../../../../lib/academic-access.mjs';
import {KINDS,KIND_SOURCES,validateSnapshot} from '../../../../lib/academic-contract.mjs';
const HEADERS={'content-type':'application/json; charset=utf-8','cache-control':'no-store, private',
 'x-content-type-options':'nosniff','referrer-policy':'no-referrer','vary':'Cf-Access-Jwt-Assertion'};
const respond=(body,status=200)=>new Response(JSON.stringify(body),{status,headers:HEADERS});
async function gate(context){
 const verified=await verifyAcademicAccess(context.request,context.env);
 if(!verified.ok)return {response:respond({status:verified.error},verified.status)};
 if(!context.env?.ACADEMIC_DB?.prepare||!context.env?.ACADEMIC_DB?.batch)
  return {response:respond({status:'database_not_configured'},503)};
 return {db:context.env.ACADEMIC_DB,userId:verified.userId};
}
const kindOf=c=>String(c.params?.section||'');
export async function onRequestGet(context){
 const kind=kindOf(context);
 if(kind!=='connections'&&!KINDS.includes(kind))return respond({status:'not_found'},404);
 const auth=await gate(context);if(auth.response)return auth.response;
 const {db,userId}=auth;
 try{
  if(kind==='connections'){
   const {results}=await db.prepare('SELECT kind,source,last_success_at,provenance FROM academic_verified_batches WHERE user_id=?')
    .bind(userId).all();
   const map=new Map((results||[]).map(r=>[r.kind,r]));
   return respond({status:'ready',sources:KINDS.map(k=>({
    kind:k,source:KIND_SOURCES[k],state:map.has(k)?(map.get(k).provenance==='trusted_readonly'?'trusted_readonly':'personal_snapshot'):'not_connected',
    lastSuccessAt:map.get(k)?.last_success_at||null
   }))});
  }
  const batch=await db.prepare('SELECT source,last_success_at,batch_id,provenance FROM academic_verified_batches WHERE user_id=? AND kind=?')
   .bind(userId,kind).first();
  if(!batch)return respond({kind,source:KIND_SOURCES[kind],status:'not_connected',records:[],lastSuccessAt:null});
  const {results}=await db.prepare('SELECT payload FROM academic_verified_records WHERE user_id=? AND kind=? AND batch_id=? LIMIT 250')
   .bind(userId,kind,batch.batch_id).all();
  const stale=Date.now()-Date.parse(batch.last_success_at)>36*3600_000;
  return respond({kind,source:batch.source,status:stale?'stale':'ready',
   records:(results||[]).map(r=>JSON.parse(r.payload)),
   lastSuccessAt:batch.last_success_at,provenance:batch.provenance==='trusted_readonly'?'trusted_readonly':'user_import'});
 }catch{return respond({status:'database_unavailable'},503);}
}
export async function onRequestPost(context){
 if(kindOf(context)!=='snapshot')return respond({status:'not_found'},404);
 const origin=context.request.headers.get('origin');
 if((origin&&origin!==new URL(context.request.url).origin)||
  context.request.headers.get('sec-fetch-site')==='cross-site')return respond({status:'forbidden_origin'},403);
 const auth=await gate(context);if(auth.response)return auth.response;
 const type=(context.request.headers.get('content-type')||'').toLowerCase();
 if(!type.startsWith('application/json'))return respond({status:'json_required'},415);
 if(Number(context.request.headers.get('content-length')||0)>250000)return respond({status:'payload_too_large'},413);
 let batch;
 try{
  const raw=await context.request.text();
  if(raw.length>250000)return respond({status:'payload_too_large'},413);
  batch=validateSnapshot(JSON.parse(raw));
 }catch(e){return respond({status:'invalid_snapshot',message:String(e.message).slice(0,120)},422);}
 const {db,userId}=auth;
 try{
  const latest=await db.prepare('SELECT last_success_at FROM academic_verified_batches WHERE user_id=? AND kind=?')
   .bind(userId,batch.kind).first();
  if(latest&&Date.parse(latest.last_success_at)>Date.parse(batch.capturedAt))
   return respond({status:'outdated_snapshot'},409);
  const now=new Date().toISOString(),batchId=crypto.randomUUID();
  const statements=[db.prepare('INSERT OR IGNORE INTO academic_users(id,created_at) VALUES (?,?)').bind(userId,now)];
  statements.push(...batch.records.map(r=>db.prepare(
   'INSERT INTO academic_verified_records(user_id,kind,source,external_id,term,payload,batch_id,updated_at) VALUES(?,?,?,?,?,?,?,?) '+
   'ON CONFLICT(user_id,kind,source,external_id,term) DO UPDATE SET payload=excluded.payload,batch_id=excluded.batch_id,updated_at=excluded.updated_at')
     .bind(userId,batch.kind,batch.source,r.externalId,r.term,JSON.stringify(r),batchId,batch.capturedAt)));
  statements.push(db.prepare('INSERT INTO academic_verified_batches(user_id,kind,source,last_success_at,batch_id,updated_at,provenance) VALUES(?,?,?,?,?,?,?) '+
   'ON CONFLICT(user_id,kind) DO UPDATE SET source=excluded.source,last_success_at=excluded.last_success_at,batch_id=excluded.batch_id,updated_at=excluded.updated_at,provenance=excluded.provenance')
    .bind(userId,batch.kind,batch.source,batch.capturedAt,batchId,now,'user_import'));
  statements.push(db.prepare('INSERT INTO academic_verified_ingests(id,user_id,kind,source,captured_at,accepted_at,count) VALUES(?,?,?,?,?,?,?)')
    .bind(crypto.randomUUID(),userId,batch.kind,batch.source,batch.capturedAt,now,batch.records.length));
  await db.batch(statements);
  return respond({status:'accepted',kind:batch.kind,count:batch.records.length,lastSuccessAt:batch.capturedAt,provenance:'user_import'},202);
 }catch{return respond({status:'database_unavailable'},503);}
}
