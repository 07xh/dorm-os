/* Authenticated browser-side sync. One scoped bearer token, never a school
 * credential. The official website is queried by the user's own browser.
 * Imported data cannot masquerade as trusted automatic university access.
 */
import {validateSnapshot} from '../../../../../lib/academic-contract.mjs';
import {savePersonalSnapshot,PersonalSnapshotError} from '../../../../../lib/academic-personal-snapshot.mjs';
import {parsePersonalTotals,PersonalTotalsError} from '../../../../../lib/academic-secondclass-personal-totals.mjs';
import {bridgeTokenFromRequest,validateBrowserBridge,BridgeError}
 from '../../../../../lib/academic-secondclass-browser-bridge.mjs';
const ORIGIN='https://dekt.ncwu.edu.cn';
const CORS=Object.freeze({'access-control-allow-origin':ORIGIN,
 'access-control-allow-methods':'POST, OPTIONS',
 'access-control-allow-headers':'authorization, content-type',
 'access-control-max-age':'600','vary':'Origin'});
const HEADERS=Object.freeze({'content-type':'application/json; charset=utf-8',
 'cache-control':'private, no-store','x-content-type-options':'nosniff',
 'referrer-policy':'no-referrer',...CORS});
const respond=(body,status=200)=>new Response(JSON.stringify(body),{status,headers:HEADERS});
export function onRequestOptions(context){
 const origin=context.request.headers.get('origin');
 if(origin!==ORIGIN)return respond({error:'origin_not_allowed'},403);
 return new Response(null,{status:204,headers:CORS});
}
export async function onRequestPost(context){
 const req=context.request;
 if(new URL(req.url).protocol!=='https:')return respond({error:'https_required'},403);
 const origin=req.headers.get('origin');
 if(origin&&origin!==ORIGIN)return respond({error:'origin_not_allowed'},403);
 if(!context.env?.ACADEMIC_DB?.prepare||!context.env?.ACADEMIC_DB?.batch)
  return respond({error:'database_unavailable'},503);
 if(!(req.headers.get('content-type')||'').toLowerCase().startsWith('application/json'))
  return respond({error:'json_required'},415);
 if(Number(req.headers.get('content-length')||0)>120000)
  return respond({error:'payload_too_large'},413);
 try{
  const token=bridgeTokenFromRequest(req);
  const {userId,tokenHash}=await validateBrowserBridge(context.env.ACADEMIC_DB,token);
  const raw=await req.text();
  if(raw.length>120000)return respond({error:'payload_too_large'},413);
  let snapshot,personalTotals=null;
  try{
   const input=JSON.parse(raw);
   const wrapped=input&&typeof input==='object'&&!Array.isArray(input)&&
    Object.prototype.hasOwnProperty.call(input,'snapshot');
   if(wrapped&&Object.keys(input).some(key=>!['snapshot','schoolReportedPreview'].includes(key)))
    throw Error('unknown_upload_field');
   snapshot=validateSnapshot(wrapped?input.snapshot:input);
   personalTotals=wrapped?parsePersonalTotals(input.schoolReportedPreview):null;
  }catch(e){
   return respond({error:e instanceof PersonalTotalsError?'invalid_browser_totals':'invalid_snapshot'},422);
  }
  // Every token is pinned to second_class and can ONLY upload that section.
  if(snapshot.kind!=='second-class'||snapshot.source!=='second_class')
   return respond({error:'invalid_provider'},422);
  const stamp=new Date().toISOString();
  const db=context.env.ACADEMIC_DB;
  const checked= db.prepare(
    "UPDATE academic_secondclass_browser_bridges SET last_sync_at=?,last_status='synced',last_error_code=NULL WHERE user_id=? AND token_hash=? AND expires_at>?"
   ).bind(stamp,userId,tokenHash,stamp);
  try{
   const afterStatements=[checked];
   if(personalTotals){
    afterStatements.push(db.prepare(
     "INSERT INTO academic_secondclass_personal_totals(user_id,captured_at,quality_score,volunteer_hours,updated_at,source) "+
     "VALUES(?,?,?,?,?,'user_browser_export') ON CONFLICT(user_id) DO UPDATE SET "+
     "captured_at=excluded.captured_at,quality_score=excluded.quality_score,"+
     "volunteer_hours=excluded.volunteer_hours,updated_at=excluded.updated_at"
    ).bind(userId,snapshot.capturedAt,personalTotals.qualityScore,
     personalTotals.volunteerHours,stamp));
   }
   const saved=await savePersonalSnapshot(db,userId,snapshot,{afterStatements});
   return respond({...saved,syncMode:'user_browser_bridge'},202);
  }catch(e){
   if(e instanceof PersonalSnapshotError&&['outdated_snapshot','trusted_source_readonly'].includes(e.code))
    return respond({error:e.code},409);
   return respond({error:'database_unavailable'},503);
  }
 }catch(e){
  const code=e instanceof BridgeError?e.code:'bridge_unavailable';
  return respond({error:code},
   code==='sync_too_soon'?429:code==='bridge_expired'?410:
   code==='invalid_bridge_token'?401:503);
 }
}
