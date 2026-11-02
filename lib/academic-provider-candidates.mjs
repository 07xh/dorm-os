/* Pre-auth candidate adapters. The caller must still prove source, account,
 * school, semester, row IDs and completeness from the real authorized site.
 * No guessed course IDs, grade points, exam clock times or second-class points.
 */
import {normalizeRecord} from './academic-contract.mjs';
import {normalizeKingoCourseRows} from './academic-upstream-bridge.mjs';
const ID=/^[A-Za-z0-9:_-]{1,120}$/;
const str=(value,max,name,required=true)=>{
 if(typeof value!=='string'||!value.trim()&&required||value.length>max)
  throw Error('missing_or_invalid_'+name);
 return value.trim();
};
const distinct=(items,getId)=>{
 const seen=new Set();
 for(const item of items){
  const key=getId(item);
  if(seen.has(key))throw Error('duplicate_source_id');
  seen.add(key);
 }
};
function list(rows){if(!Array.isArray(rows)||rows.length===0||rows.length>250)throw Error('source_rows_not_verified');}
export function candidateSchedule(rows,semester){
 const candidates=normalizeKingoCourseRows(rows,{semester});
 return {kind:'schedule',source:'ncwu',verified:false,complete:false,records:candidates};
}
export function candidateAcademic(kind,rows,{semester}={}){
 if(!['grades','exams'].includes(kind))throw Error('unsupported_kingo_kind');
 list(rows);const term=str(semester,40,'semester');
 const records=rows.map(r=>{
  if(!r||typeof r!=='object'||Array.isArray(r))throw Error('invalid_school_record');
  const externalId=str(r.externalId,120,'school_external_id');
  if(!ID.test(externalId))throw Error('invalid_school_record_id');
  const title=str(r.title,120,'title');
  const values={externalId,title,term};
  if(kind==='grades'){
   if(r.score!=null&&r.score!=='')values.score=str(r.score,40,'score');
   if(r.credit!=null)values.credit=r.credit;
   if(r.gradePoint!=null)values.gradePoint=r.gradePoint;
  }else{
   values.date=r.date;
   if(r.location!=null)values.location=str(r.location,120,'location',false);
   if(r.startTime!=null)values.startTime=str(r.startTime,5,'startTime');
   if(r.endTime!=null)values.endTime=str(r.endTime,5,'endTime');
  }
  return normalizeRecord(kind,values);
 });
 distinct(records,r=>r.term+'|'+r.externalId);
 return {kind,source:'ncwu',verified:false,complete:false,records};
}
export function candidateSecondClass(rows){
 list(rows);
 const records=rows.map(r=>{
  if(!r||typeof r!=='object'||Array.isArray(r))throw Error('invalid_activity_record');
  const externalId=str(r.activityId,120,'activity_id');
  if(!ID.test(externalId))throw Error('invalid_activity_id');
  const title=str(r.title,120,'title');
  const allowed=new Set(['pending','approved','rejected','attended','unknown']);
  if(!allowed.has(r.status))throw Error('invalid_activity_status');
  const fields={externalId,title,term:typeof r.term==='string'?str(r.term,40,'term',false):'',
   note:'审核状态：'+r.status};
  if(r.date!=null)fields.date=r.date;
  if(r.credit!=null)fields.credit=r.credit;
  const item=normalizeRecord('second-class',fields);
  // Preserve status as candidate-only metadata; do NOT treat as school-verified credits.
  return {...item,reviewStatus:r.status};
 });
 distinct(records,r=>r.externalId+'|'+r.term);
 return {kind:'second-class',source:'second_class',verified:false,complete:false,records};
}
