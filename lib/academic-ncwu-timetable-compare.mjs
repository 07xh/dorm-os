/**
 * Privacy-safe, non-authoritative NCWU timetable comparison.
 *
 * Compares a syntactic HTML candidate to a SEPARATELY obtained schedule list.
 * This cannot authenticate an owner, validate an official session, establish
 * provenance, or grant trusted_readonly sync. All results are aggregated counts
 * without course names, locations, teachers, raw pages, URLs or credentials.
 */
const TERM=/^\d{4}-\d{4}-[1-3]$/;
const hasText=(value,max)=>typeof value==='string'&&value.trim().length>0&&
 value.length<=max;
const int=(value,min,max)=>Number.isInteger(value)&&value>=min&&value<=max;

export class NcwuTimetableCompareError extends Error {
 constructor(code){super(code);this.name='NcwuTimetableCompareError';this.code=code;}
}
const fail=code=>{throw new NcwuTimetableCompareError(code);};
function fingerprint(value,semester){
 if(!value||typeof value!=='object'||Array.isArray(value))fail('invalid_course_structure');
 const {title,teacher,location,weekday,startSection,endSection,weeks}=value;
 if(!hasText(title,120)||!hasText(teacher,90)||!hasText(location,130)||
    !int(weekday,1,7)||!int(startSection,1,24)||
    !int(endSection,startSection,24)||!Array.isArray(weeks)||
    weeks.length<1||weeks.length>35||
    !weeks.every(w=>int(w,1,35))||new Set(weeks).size!==weeks.length)
  fail('invalid_course_structure');
 if(value.semester!=null&&value.semester!==semester)fail('semester_mismatch');
 return JSON.stringify([semester,title.trim(),teacher.trim(),location.trim(),
  weekday,startSection,endSection,[...weeks].sort((a,b)=>a-b)]);
}
function aggregate(records,semester){
 if(!Array.isArray(records)||records.length<1||records.length>350)fail('invalid_course_list');
 const counts=new Map();
 for(const row of records){
  const key=fingerprint(row,semester);
  counts.set(key,(counts.get(key)||0)+1);
 }
 return counts;
}
const sortedBlockerCounts=blockers=>{
 const codes={};
 for(const b of blockers){
  if(!b||!int(b.row,1,250)||typeof b.code!=='string'||
     !/^[a-z_]{1,50}$/.test(b.code))fail('invalid_parse_diagnostics');
  codes[b.code]=(codes[b.code]||0)+1;
 }
 return Object.fromEntries(Object.entries(codes).sort(([a],[b])=>a.localeCompare(b)));
};
export function compareNcwuTimetableCandidate(parseResult,referenceRecords){
 if(!parseResult||typeof parseResult!=='object'||Array.isArray(parseResult)||
    parseResult.source!=='ncwu.student.wsxk.xskcb10319.jsp'||
    !TERM.test(parseResult.semester||'')||
    parseResult.verified!==false||parseResult.complete!==false||
    parseResult.trustedSyncAllowed!==false||!Array.isArray(parseResult.blockers)||
    !int(parseResult.courseRows,1,250)||
    !int(parseResult.scheduleFragments,0,5000)||
    !int(parseResult.parsedBlocks,0,350)||
    !int(parseResult.parsedCourseRows,0,parseResult.courseRows)||
    !int(parseResult.blockedCourseRows,0,parseResult.courseRows)||
    !['partial','syntactically_complete'].includes(parseResult.parseCoverage))
  fail('invalid_parser_result');
 const blockersByCode=sortedBlockerCounts(parseResult.blockers);
 const ref=aggregate(referenceRecords,parseResult.semester);
 const parsed=parseResult.candidate?.records;
 if(parsed!=null&&!Array.isArray(parsed))fail('invalid_parser_result');
 if((parsed?.length||0)!==parseResult.parsedBlocks)fail('invalid_parser_result');
 const parsedCounts=parsed?.length?aggregate(parsed,parseResult.semester):new Map();
 let matchedBlocks=0,missingBlocks=0,unexpectedBlocks=0;
 for(const [key,count] of ref){
  const actual=parsedCounts.get(key)||0;
  matchedBlocks+=Math.min(count,actual);
  missingBlocks+=Math.max(0,count-actual);
 }
 for(const [key,count] of parsedCounts){
  unexpectedBlocks+=Math.max(0,count-(ref.get(key)||0));
 }
 const duplicateReferenceBlocks=[...ref.values()].reduce((n,count)=>n+Math.max(0,count-1),0);
 const incomplete=parseResult.parseCoverage!=='syntactically_complete'||
    parseResult.blockers.length>0||parseResult.scheduleFragments!==parseResult.parsedBlocks||
    parseResult.parsedCourseRows!==parseResult.courseRows||
    parseResult.blockedCourseRows>0;
 const sameBlocks=missingBlocks===0&&unexpectedBlocks===0&&duplicateReferenceBlocks===0;
 // Syntactic match is NOT owner/session authentication, even for complete HTML.
 const syntacticMatch=!incomplete&&sameBlocks;
 return Object.freeze({
  source:'ncwu',
  semester:parseResult.semester,
  courseRows:parseResult.courseRows,
  scheduleFragments:parseResult.scheduleFragments,
  parsedBlocks:parseResult.parsedBlocks,
  blockedFragments:parseResult.blockers.length,
  blockedCourseRows:parseResult.blockedCourseRows,
  referenceBlocks:referenceRecords.length,
  matchedBlocks,missingBlocks,unexpectedBlocks,duplicateReferenceBlocks,
  blockersByCode,
  syntaxComplete:!incomplete,
  syntacticMatch,
  reviewStatus:incomplete?'requires_source_review':
    sameBlocks?'structure_matched_not_authenticated':'mismatch_requires_review',
  verified:false,
  trustedSyncAllowed:false
 });
}
