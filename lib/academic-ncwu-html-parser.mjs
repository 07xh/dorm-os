/**
 * NCWU Qingguo personal timetable HTML -> UNVERIFIED candidate geometry.
 *
 * Based on the non-secret structure of /hsjw/student/wsxk.xskcb10319.jsp:
 * - two tables may have the SAME id="mytable"; distinguish using headers;
 * - the detailed table has eight columns, last = 上课时间地点;
 * - term metadata is in hidden xn and xq_m inputs;
 * - schedule parts may be incomplete: NEVER infer missing time, place or weeks.
 *
 * No HTTP requests, user credentials, cookies, writes or live adapter registration.
 * Parsed rows MUST NOT be promoted to trusted_readonly by this module.
 */
import {load} from 'cheerio';
import {candidateSchedule} from './academic-provider-candidates.mjs';

const HEADERS=Object.freeze([
 '上课班级代码','课程','总学时','学分','修读性质','任课教师','选课状态','上课时间地点'
]);
const DAYS=Object.freeze({一:1,二:2,三:3,四:4,五:5,六:6,日:7,天:7});
const TIMES=/^([\d,\uFF0C\-—–~～至 ]+?)([单双])?周\s*(?:星期|周)?([一二三四五六日天])\s*[\[【](\d{1,2})[-~～至](\d{1,2})[\]】]\s*(.+)$/u;
const TERM=/^(\d{4})-(\d{4})-([1-3])$/;
const clean=v=>String(v??'').replace(/\s+/g,' ').trim();
const stripCode=v=>clean(v).replace(/^\[[^\]]{1,40}\]\s*/u,'').trim();
const validText=(v,max)=>v.length>0&&v.length<=max&&!/[\u0000-\u0008\u000e-\u001f]/.test(v);
export class NcwuTimetableHtmlError extends Error {
 constructor(code){super(code);this.name='NcwuTimetableHtmlError';this.code=code;}
}
const fail=code=>{throw new NcwuTimetableHtmlError(code);};

function weeksFromSpec(raw,parity){
 const result=new Set();
 const spec=String(raw).replace(/[，\s]/g,',').replace(/[—–～~至]/g,'-');
 const parts=spec.split(',').filter(Boolean);
 if(!parts.length||parts.length>35)return null;
 for(const part of parts){
  const match=/^(\d{1,2})(?:-(\d{1,2}))?$/.exec(part);
  if(!match)return null;
  const first=Number(match[1]),last=Number(match[2]??match[1]);
  if(first<1||last>35||last<first)return null;
  for(let i=first;i<=last;i++)if(!parity||(parity==='单'?i%2===1:i%2===0))result.add(i);
 }
 return result.size?[...result].sort((a,b)=>a-b):null;
}
function readSchedulePart(value,course){
 const match=TIMES.exec(clean(value));
 if(!match)return {error:'unsupported_schedule_notation'};
 const [,weeksSpec,parity,weekday,from,to,rawLocation]=match;
 const weeks=weeksFromSpec(weeksSpec,parity);
 if(!weeks)return {error:'invalid_or_ambiguous_weeks'};
 const startSection=Number(from),endSection=Number(to);
 if(startSection<1||endSection>24||startSection>endSection)
  return {error:'invalid_section_range'};
 const location=clean(rawLocation);
 if(!validText(location,130)||/^(待定|未安排|待通知|无|不详)$/u.test(location))
  return {error:'missing_or_unverified_location'};
 return {item:{
  name:course.name,teacher:course.teacher,position:location,
  day:DAYS[weekday],startSection,endSection,weeks
 }};
}

/**
 * Explicitly syntactic parse: semester is caller-provided AND checked against
 * the HTML hidden term markers. Even exact matching doesn't establish owner.
 * @returns {object} parseCoverage, blockers (row index + code only),
 *  and candidate, always verified=false / complete=false / trustedSyncAllowed=false.
 */
export function parseNcwuStudentTimetableHtml(html,{semester}={}){
 if(typeof html!=='string'||!html.trim()||html.length>500_000)
  fail('html_missing_or_oversized');
 const matched=TERM.exec(semester??'');
 if(!matched||Number(matched[2])!==Number(matched[1])+1)
  fail('semester_required');
 const $=load(html,{decodeEntities:true});
 const year=clean($('#xn').first().val()),term=clean($('#xq_m').first().val());
 if(year!==matched[1]||term!==matched[3])fail('semester_metadata_mismatch');

 // A second table may use the same ID: choose by verified header signature.
 const details=$('table').toArray().filter(table=>{
  const first=$(table).find('tr').first();
  const headings=first.children('th,td').toArray().map(el=>clean($(el).text()));
  return headings.length===HEADERS.length&&HEADERS.every((h,i)=>h===headings[i]);
 });
 if(details.length!==1)fail('detail_table_missing_or_ambiguous');
 const rows=$(details[0]).find('tr').slice(1).toArray();
 if(!rows.length||rows.length>250)fail('detail_rows_missing_or_oversized');
 const blocks=[],blockers=[],parsedRows=new Set(),blockedRows=new Set();
 let scheduleFragments=0;
 rows.forEach((row,i)=>{
  const cells=$(row).children('td,th').toArray();
  if(cells.length!==HEADERS.length){
   blockedRows.add(i+1);blockers.push({row:i+1,code:'unexpected_detail_columns'});return;
  }
  const texts=cells.map(el=>clean($(el).text()));
  const course={name:stripCode(texts[1]),teacher:stripCode(texts[5])};
  // The class-code field is not a proven immutable official course ID.
  if(!validText(course.name,120)||!validText(texts[0],90)||
     course.teacher.length>90){
   blockedRows.add(i+1);blockers.push({row:i+1,code:'missing_course_details'});return;
  }
  const parts=texts[7].split(/[；;]+/u).map(clean).filter(Boolean);
  if(!parts.length){blockedRows.add(i+1);blockers.push({row:i+1,code:'missing_schedule'});return;}
  scheduleFragments+=parts.length;
  for(const part of parts){
   const result=readSchedulePart(part,course);
   if(result.error){blockedRows.add(i+1);blockers.push({row:i+1,code:result.error});}
   else{parsedRows.add(i+1);blocks.push(result.item);}
  }
 });
 // Existing Shiguang-compatible normalizer verifies weeks, section ranges
 // and duplicate blocks, but it does not verify school account authorization.
 let candidate=null;
 if(blocks.length){
  try{candidate=candidateSchedule(blocks,semester);}
  catch{fail('candidate_validation_failed');}
 }
 return {
  source:'ncwu.student.wsxk.xskcb10319.jsp',
  semester,
  verified:false,
  complete:false,
  trustedSyncAllowed:false,
  parseCoverage:blockers.length||blocks.length===0?'partial':'syntactically_complete',
  courseRows:rows.length,scheduleFragments,parsedBlocks:blocks.length,
  parsedCourseRows:parsedRows.size,blockedCourseRows:blockedRows.size,
  blockers, // NO raw response, student ID, course content or session values
  candidate // ALWAYS unverified and incomplete, even if no syntax blockers
 };
}
