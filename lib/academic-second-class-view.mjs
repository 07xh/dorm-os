/* Second-class record summary. Values are from stored, individual records,
 * NOT official aggregated school totals. Unknown fields stay unknown (null).
 */
import {normalizeRecord} from './academic-contract.mjs';
const round2=x=>Math.round((x+Number.EPSILON)*100)/100;
export function projectSecondClass(rows){
 if(!Array.isArray(rows)||rows.length>250)throw Error('invalid_second_class_records');
 const records=rows.map(raw=>normalizeRecord('second-class',raw));
 const counted=(key)=>{
  const values=records.map(r=>r[key]).filter(v=>typeof v==='number'&&Number.isFinite(v));
  return {knownCount:values.length,sum:values.length?round2(values.reduce((a,b)=>a+b,0)):null};
 };
 const activities={registered:0,completed:0,cancelled:0,unknown:0};
 const reviews={pending:0,approved:0,rejected:0,attended:0,unknown:0};
 for(const r of records){
  activities[r.activityStatus??'unknown']++;
  reviews[r.reviewStatus??'unknown']++;
 }
 return {records,summary:{
  recordCount:records.length,
  recordedHours:counted('hours'),
  recordedPoints:counted('points'),
  recordedCredits:counted('credit'),
  activityStatusCounts:activities,
  reviewStatusCounts:reviews,
  isOfficialAggregate:false
 }};
}
