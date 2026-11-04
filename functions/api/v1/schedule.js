/* Read-only, owner-scoped verified NCWU timetable.
 * Never accepts school credentials or browser-supplied records.
 * Imported snapshots and invalid batches cannot be presented as real classes.
 */
import {authorize,json} from './assignments.js';
import {normalizeRecord} from '../../../lib/academic-contract.mjs';

export async function onRequestGet(context) {
  const auth=await authorize(context);
  if(auth.response)return auth.response;
  try {
    const batch=await auth.db.prepare(
      "SELECT batch_id,source,provenance,last_success_at FROM academic_verified_batches WHERE user_id=? AND kind='schedule'"
    ).bind(auth.userId).first();
    const base={schema:'xiaoxu/verified-schedule-v1',source:'ncwu',
      records:[],lastSuccessAt:null,provenance:null};
    if(!batch)return json({...base,status:'not_connected'});
    if(batch.source!=='ncwu'||batch.provenance!=='trusted_readonly'){
      return json({...base,status:'unverified_snapshot'});
    }
    const updated=Date.parse(batch.last_success_at);
    if(!Number.isFinite(updated)||updated>Date.now()+5*60_000){
      return json({...base,status:'invalid_snapshot'},503);
    }
    const rows=await auth.db.prepare(
      "SELECT payload FROM academic_verified_records WHERE user_id=? AND kind='schedule' AND source='ncwu' AND batch_id=? ORDER BY term,external_id LIMIT 251"
    ).bind(auth.userId,batch.batch_id).all();
    const records=rows?.results||[];
    if(records.length>250)return json({...base,status:'invalid_snapshot'},503);
    const seen=new Set();
    const mapped=records.map(row=>{
      const item=normalizeRecord('schedule',JSON.parse(row.payload));
      const key=item.term+'|'+item.externalId;
      if(seen.has(key))throw Error('duplicate_trusted_record');
      seen.add(key);
      return item;
    }).sort((a,b)=>a.weekday-b.weekday||a.startSection-b.startSection||
      a.title.localeCompare(b.title,'zh-CN'));
    return json({
      ...base,status:Date.now()-updated>36*3600_000?'stale':'ready',
      records:mapped,provenance:'trusted_readonly',lastSuccessAt:batch.last_success_at
    });
  }catch{
    return json({
      schema:'xiaoxu/verified-schedule-v1',
      source:'ncwu',status:'database_unavailable_or_invalid_snapshot',
      records:[],provenance:null,lastSuccessAt:null
    },503);
  }
}
