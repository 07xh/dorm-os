/**
 * NCWU elective course/class candidate contract, intentionally detached from
 * trusted academic snapshots, local timetable persistence and school mutation.
 * An adapter can supply read-only catalog rows; parsing them is NOT proof of
 * current availability, eligibility, authorization or successful enrollment.
 */
const ID=/^[A-Za-z0-9._:-]{1,96}$/;
const TERM=/^20\d{2}-20\d{2}-[1-3]$/;
const TIME=/^20\d{2}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?(?:Z|[+-]\d{2}:\d{2})$/;
const UNSAFE=/[\u0000-\u001f\u007f<>]/;
const TOP=new Set(['schema','provider','term','observedAt','classes']);
const CLASS=new Set(['courseCode','classCode','courseName','teacher','campus','capacity','remaining','meetings']);
const MEETING=new Set(['weekday','startSection','endSection','weeks']);
const fail=reason=>{throw new Error('ncwu_elective_candidate_'+reason);};
function object(value,keys){
 if(!value||typeof value!=='object'||Array.isArray(value))fail('invalid_object');
 for(const key of Object.keys(value))if(!keys.has(key))fail('unknown_field');
}
function id(value){
 if(typeof value!=='string'||!ID.test(value))fail('invalid_identifier');
 return value;
}
function label(value,required=false){
 if((value===null||value===undefined||value==='')&&!required)return '';
 if(typeof value!=='string'||value.length>120||UNSAFE.test(value)||!value.trim())
  fail('invalid_text');
 return value.trim();
}
function numberOrNull(value){
 if(value===undefined||value===null)return null;
 if(!Number.isInteger(value)||value<0||value>100000)fail('invalid_seat_count');
 return value;
}
function meeting(value){
 object(value,MEETING);
 const {weekday,startSection,endSection,weeks}=value;
 if(!Number.isInteger(weekday)||weekday<1||weekday>7||
    !Number.isInteger(startSection)||startSection<1||startSection>24||
    !Number.isInteger(endSection)||endSection<startSection||endSection>24||
    !Array.isArray(weeks)||!weeks.length||weeks.length>35||
    weeks.some(w=>!Number.isInteger(w)||w<1||w>35)||
    new Set(weeks).size!==weeks.length)fail('invalid_meeting');
 return {weekday,startSection,endSection,weeks:[...weeks].sort((a,b)=>a-b)};
}
function courseClass(value){
 object(value,CLASS);
 const courseCode=id(value.courseCode);
 const classCode=id(value.classCode);
 const courseName=label(value.courseName,true);
 const teacher=label(value.teacher);
 const campus=label(value.campus);
 const capacity=numberOrNull(value.capacity);
 const remaining=numberOrNull(value.remaining);
 if(capacity!==null&&remaining!==null&&remaining>capacity)fail('inconsistent_seat_count');
 if(value.meetings!==undefined&&(!Array.isArray(value.meetings)||value.meetings.length>24))
  fail('invalid_meetings');
 return {courseCode,classCode,courseName,teacher,campus,capacity,remaining,
  meetings:(value.meetings||[]).map(meeting),
  // Eligibility cannot be inferred from a displayed seat count.
  eligibility:'unknown'};
}
export function normalizeNcwuElectiveCandidate(value){
 object(value,TOP);
 if(value.schema!=='xiaoxu/ncwu-elective-candidate-v1'||value.provider!=='ncwu'||
    typeof value.term!=='string'||!TERM.test(value.term))fail('invalid_header');
 const [start,end]=value.term.split('-');
 if(Number(end)!==Number(start)+1)fail('invalid_term');
 if(typeof value.observedAt!=='string'||!TIME.test(value.observedAt)||
    !Number.isFinite(Date.parse(value.observedAt)))fail('invalid_observation_time');
 if(!Array.isArray(value.classes)||value.classes.length>300)
  fail('invalid_class_list');
 const classes=value.classes.map(courseClass);
 const seen=new Set();
 for(const entry of classes){
  const key=entry.courseCode+'|'+entry.classCode;
  if(seen.has(key))fail('duplicate_class');
  seen.add(key);
 }
 return {schema:'xiaoxu/ncwu-elective-candidate-v1',provider:'ncwu',
  term:value.term,observedAt:value.observedAt,classes,
  verified:false,sourceState:'unverified',readOnly:true,submissionAllowed:false};
}
