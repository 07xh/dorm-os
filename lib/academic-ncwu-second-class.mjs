/* NCWU second-class personal query contract confirmed against a private HAR.
 * NEVER embed HAR data, session cookies, WeChat wxtoken, SSO redirects or
 * personal identifiers in source/repository. No credential/session creation.
 * Every request is a user-authorized read-only query (school uses POST).
 */
import {normalizeRecord} from './academic-contract.mjs';
import {collectAuthorizedSecondClass} from './academic-second-class-connector.mjs';

export const NCWU_SECOND_CLASS_READS=Object.freeze({
 activities:'/sys/stu/center/signupactivity/list',
 scores:'/sys/stu/score/list',
 scoreTypes:'/sys/stu/score/types',
 activityTypes:'/sys/activitytype/all'
});
export class NcwuSecondClassError extends Error {
 constructor(code){super(code);this.name='NcwuSecondClassError';this.code=code;}
}
const fail=code=>{throw new NcwuSecondClassError(code);};
const isObject=x=>x!==null&&typeof x==='object'&&!Array.isArray(x);
const stamp=/^20\d{2}-\d{2}-\d{2}(?:\s\d{2}:\d{2}:\d{2})?$/;
const ID=/^[A-Za-z0-9:_-]{1,120}$/;
const field=(v,n=120)=>typeof v==='string'&&v.trim()&&v.length<=n?v.trim():null;
const safeNum=(v,max=100000)=>{
 if(v===null||v===undefined||v==='')return null;
 if(typeof v!=='number'||!Number.isFinite(v)||v<0||v>max)fail('invalid_numeric_field');
 return v;
};
const pageMeta=(raw)=>{
 if(!isObject(raw)||!Array.isArray(raw.data)||raw.data.length>50||
    !Number.isSafeInteger(raw.totalPage)||raw.totalPage<0||raw.totalPage>1000||
    !Number.isSafeInteger(raw.totalSize)||raw.totalSize<0||raw.totalSize>100000||
    !Number.isSafeInteger(raw.startIndex)||raw.startIndex<0)fail('invalid_response');
 return raw;
};
function dateOnly(v){
 if(v===null||v===undefined||v==='')return undefined;
 if(typeof v!=='string'||!stamp.test(v))fail('invalid_activity_date');
 const date=v.slice(0,10);
 if(new Date(date+'T00:00:00Z').toISOString().slice(0,10)!==date)
  fail('invalid_activity_date');
 return date;
}
function settlement(row){
 if(row.verify_state==='2'||row.verify_state==='1'&&row.activity_state==='5')
  return 'settled';
 if(row.verify_state==='1'&&row.activity_state==='4')return 'unsettled';
 if(row.verify_state==='1'&&row.activity_state==='6')return 'awaiting_settlement';
 if(row.verify_state!==undefined)return 'unverified';
 return 'unknown';
}
export function parseNcwuSignupPage(raw,{page=1,pageSize=50}={}){
 pageMeta(raw);
 if(!Number.isInteger(page)||page<1||!Number.isInteger(pageSize)||pageSize<1||pageSize>50)
  fail('invalid_page');
 if(raw.data.length>pageSize)fail('invalid_page_size');
 const empty=raw.data.length===0&&raw.totalSize===0&&raw.totalPage===0;
 if(raw.data.length===0&&!empty)fail('unverified_empty_page');
 if(!empty&&(raw.totalPage<page||raw.totalSize<raw.data.length||
    raw.totalPage===0))fail('invalid_pagination');
 const activities=raw.data.map(row=>{
  if(!isObject(row)||!field(row.id)||!ID.test(row.id)||
   !field(row.activity_name))fail('invalid_activity_row');
  const state=settlement(row);
  const year=field(row.school_year,30),term=field(row.school_term,4);
  if(term!==null&&!/^[12]$/.test(term))fail('invalid_term');
  const title=row.activity_name.trim();
  const item={activityId:row.id,title,
   ...(year&&term?{term:year+'-'+term}:{}),
   ...(dateOnly(row.activity_start)?{date:dateOnly(row.activity_start)}:{}),
   activityStatus:row.signup_state==='1'?'registered':'unknown',
   reviewStatus:'unknown',settlementStatus:state
  };
  // On the real school page score_value is labelled 素质分; count only
  // settled records, never a pending activity as credited points.
  if(state==='settled'){
   const points=safeNum(row.score_value);
   if(points!==null)item.points=points;
  }
  try{normalizeRecord('second-class',{
   externalId:item.activityId,title:item.title,term:item.term,date:item.date,
   points:item.points,activityStatus:item.activityStatus,
   reviewStatus:item.reviewStatus,settlementStatus:item.settlementStatus
  });}catch{fail('invalid_activity_row');}
  return item;
 });
 const complete=empty||page>=raw.totalPage;
 return {activities,complete,
  nextCursor:complete?null:String(page+1),verifiedEmpty:empty,
  totalSize:raw.totalSize,totalPage:raw.totalPage};
}
export function parseNcwuScoreTypes(input){
 if(!Array.isArray(input)||input.length<1||input.length>12)fail('invalid_score_types');
 const seen=new Set();
 return input.map(row=>{
  if(!isObject(row)||!field(row.code,2)||!/^[1-9]$/.test(row.code)||
    !field(row.name,45)||seen.has(row.code))fail('invalid_score_types');
  seen.add(row.code);
  return {code:row.code,label:row.name.trim()};
 });
}
export function parseNcwuScoreList(raw,types){
 pageMeta(raw);
 if(!Array.isArray(types)||types.length<1||types.length>12||
   raw.data.length>50)fail('invalid_response');
 const observed=new Set();
 const rows=raw.data.map(row=>{
  if(!isObject(row)||!Number.isInteger(row.school_term_index)||
   row.school_term_index<1||row.school_term_index>100||
   observed.has(row.school_term_index))fail('invalid_score_row');
  observed.add(row.school_term_index);
  const r={
   termIndex:row.school_term_index,isAggregate:row.school_term_index===100,
   qualityScore:safeNum(row.score_value),volunteerHours:safeNum(row.score_plus),
   categories:types.map(t=>({code:t.code,label:t.label,
     qualityScore:safeNum(row['activity_type'+t.code+'_score'])}))
  };
  return r;
 });
 // The original page renders school_term_index=100 as "合计".
 // DO NOT add this row together with the per-semester entries.
 const aggregate=rows.find(row=>row.isAggregate)||null;
 return {school:'ncwu',units:{qualityScore:'素质分',volunteerHours:'志愿服务时长'},
  aggregate,semesters:rows.filter(row=>!row.isAggregate),
  isSchoolReportedAggregate:aggregate!==null};
}
export function makeNcwuSecondClassAdapter({verifySession,readPost,pageSize=50}={}){
 if(typeof verifySession!=='function'||typeof readPost!=='function'||
   !Number.isInteger(pageSize)||pageSize<1||pageSize>50)fail('connector_not_configured');
 let expectedTotal=null,received=0,studentRef=null,expectedAccount=null;
 async function verify(){
  let proof;
  try{proof=await verifySession();}
  catch{fail('authorization_failed');}
  if(!proof||proof.authorized!==true||proof.scope!=='self'||
    proof.school!=='ncwu'||proof.provider!=='second_class'||
    proof.identityBound!==true)fail('authorization_failed');
  const studentCode=field(proof.session?.studentCode,50);
  // HAR confirms the same personal account code appears in both read APIs.
  // The trusted verifier must bind it to the signed-in Cloudflare owner.
  if(!studentCode)fail('authorization_failed');
  if(expectedAccount!==null&&expectedAccount!==studentCode)
   fail('school_identity_changed');
  expectedAccount=studentCode;
  return proof;
 }
 const post=async (path,form,session)=>{
  // Only predefined, observed, personal read endpoints. No arbitrary URL.
  if(!Object.values(NCWU_SECOND_CLASS_READS).includes(path))
   fail('read_path_not_allowed');
  return readPost({method:'POST',path,form,session});
 };
 return Object.freeze({
  provider:'second_class',
  async verifySession(){
   expectedTotal=null;received=0;studentRef=null;expectedAccount=null;
   return verify();
  },
  async readPage({cursor,session}){
   const page=cursor===null?1:Number(cursor);
   if(!Number.isInteger(page)||page<1||page>25)fail('invalid_page');
   const raw=await post(NCWU_SECOND_CLASS_READS.activities,
     {page:String(page),pagesize:String(pageSize)},session);
   const mapped=parseNcwuSignupPage(raw,{page,pageSize});
   if(expectedTotal===null)expectedTotal=mapped.totalSize;
   if(expectedTotal!==mapped.totalSize)fail('inconsistent_total');
   for(const row of raw.data){
    const student=field(row.student_code,50);
    if(student===null||student!==expectedAccount)fail('school_identity_changed');
    if(studentRef!==null&&student!==studentRef)fail('school_identity_changed');
    if(student!==null)studentRef=student;
    const expected=field(session?.studentCode,50);
    if(expected!==student)fail('authorization_failed');
   }
   received+=mapped.activities.length;
   if(received>expectedTotal||mapped.complete&&received!==expectedTotal)
    fail('incomplete_pagination');
   // The collector persists only the pre-filtered record fields.
   const {totalSize,totalPage,...pageResult}=mapped;
   return pageResult;
  },
  async readOfficialScores(){
   const proof=await verify();
   const rawTypes=await post(NCWU_SECOND_CLASS_READS.scoreTypes,{},proof.session);
   const rawScores=await post(NCWU_SECOND_CLASS_READS.scores,{},proof.session);
   const types=parseNcwuScoreTypes(rawTypes);
   for(const row of rawScores?.data||[]){
    const expected=field(proof.session?.studentCode,50);
    const student=field(row.student_code,50);
    if(expected===null||student===null||expected!==student)fail('authorization_failed');
   }
   return parseNcwuScoreList(rawScores,types);
  }
 });
}
export {collectAuthorizedSecondClass};
