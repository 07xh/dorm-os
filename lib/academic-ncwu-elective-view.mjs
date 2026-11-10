/**
 * Read-only filtering and timetable comparison for NCWU elective candidates.
 * This layer never fetches, submits, mutates a timetable, or asserts eligibility.
 */
import {normalizeNcwuElectiveCandidate} from './academic-ncwu-elective-contract.mjs';

const FILTERS=new Set(['keyword','teacher','campus','seats']);
const SEATS=new Set(['all','known_available','known_full','unknown']);
const fail=code=>{throw new Error('ncwu_elective_view_'+code);};

function text(value,max=120){
 if(typeof value!=='string'||value.length>max||/[\u0000-\u001f\u007f<>]/.test(value))
  fail('invalid_filter');
 return value.trim().normalize('NFKC').toLocaleLowerCase();
}
function normalizeFilters(filters){
 if(!filters||typeof filters!=='object'||Array.isArray(filters))fail('invalid_filter');
 for(const key of Object.keys(filters))if(!FILTERS.has(key))fail('invalid_filter');
 const seats=filters.seats??'all';
 if(!SEATS.has(seats))fail('invalid_seat_filter');
 return {keyword:text(filters.keyword??''),teacher:text(filters.teacher??''),
  campus:text(filters.campus??''),seats};
}
function matches(haystack,needle){
 return !needle||haystack.normalize('NFKC').toLocaleLowerCase().includes(needle);
}
export function filterNcwuElectiveClasses(raw,filters={}){
 const catalog=normalizeNcwuElectiveCandidate(raw);
 const f=normalizeFilters(filters);
 const classes=catalog.classes.filter(cls=>{
  if(!matches([cls.courseName,cls.courseCode,cls.classCode,cls.teacher]
    .join(' '),f.keyword))return false;
  if(!matches(cls.teacher,f.teacher)||!matches(cls.campus,f.campus))return false;
  if(f.seats==='known_available'&&!(cls.remaining!==null&&cls.remaining>0))
   return false;
  if(f.seats==='known_full'&&cls.remaining!==0)return false;
  if(f.seats==='unknown'&&cls.remaining!==null)return false;
  return true;
 }).sort((a,b)=>a.courseName.localeCompare(b.courseName,'zh-CN')||
  a.courseCode.localeCompare(b.courseCode)||a.classCode.localeCompare(b.classCode));
 return {...catalog,classes,filteredCount:classes.length,
  // Neither a filter result nor displayed seats establish personal eligibility.
  eligibilityVerified:false};
}
function verifiedMeetingLike(row){
 if(!row||typeof row!=='object'||Array.isArray(row)||
  Object.keys(row).some(k=>!['weekday','startSection','endSection','weeks'].includes(k)))
  fail('invalid_personal_meeting');
 if(!Number.isInteger(row.weekday)||row.weekday<1||row.weekday>7||
  !Number.isInteger(row.startSection)||row.startSection<1||row.startSection>24||
  !Number.isInteger(row.endSection)||row.endSection<row.startSection||
  row.endSection>24||!Array.isArray(row.weeks)||!row.weeks.length||
  row.weeks.length>35||row.weeks.some(w=>!Number.isInteger(w)||w<1||w>35)||
  new Set(row.weeks).size!==row.weeks.length)fail('invalid_personal_meeting');
 return {weekday:row.weekday,startSection:row.startSection,
  endSection:row.endSection,weeks:[...row.weeks]};
}
function overlap(a,b){
 return a.weekday===b.weekday&&
  a.startSection<=b.endSection&&b.startSection<=a.endSection&&
  a.weeks.some(w=>b.weeks.includes(w));
}
/**
 * personalMeetings are locally supplied known course meetings, never fetched.
 * The negative result is intentionally NOT "no conflict" or "eligible".
 * A single known positive overlap can be reported even with partial inputs.
 */
export function compareNcwuElectiveTimetables(raw,personalMeetings=[],{
 personalScheduleComplete=false
}={}){
 const catalog=normalizeNcwuElectiveCandidate(raw);
 if(!Array.isArray(personalMeetings)||personalMeetings.length>300||
  typeof personalScheduleComplete!=='boolean')fail('invalid_personal_schedule');
 const registered=personalMeetings.map(verifiedMeetingLike);
 const classes=catalog.classes.map(cls=>{
  const conflictingIndices=registered.flatMap((existing,i)=>
   cls.meetings.some(current=>overlap(current,existing))?[i]:[]);
  const conflictStatus=conflictingIndices.length?'conflict_detected':
   !cls.meetings.length||(!personalScheduleComplete&&!registered.length)?
   'insufficient_data':'no_known_conflict';
  return {courseCode:cls.courseCode,classCode:cls.classCode,
   conflictStatus,conflictingIndices,eligibility:'unknown'};
 });
 return {provider:'ncwu',term:catalog.term,observedAt:catalog.observedAt,
  verified:false,readOnly:true,classes};
}
