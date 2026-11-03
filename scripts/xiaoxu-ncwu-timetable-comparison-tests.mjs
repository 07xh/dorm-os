import assert from 'node:assert/strict';
import {parseNcwuStudentTimetableHtml} from '../lib/academic-ncwu-html-parser.mjs';
import {compareNcwuTimetableCandidate,NcwuTimetableCompareError}
 from '../lib/academic-ncwu-timetable-compare.mjs';

const HEADERS=['上课班级代码','课程','总学时','学分','修读性质','任课教师','选课状态','上课时间地点'];
const esc=s=>String(s).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;');
const course=(id,title,teacher,schedule)=>[id,title,'48','3','必修',teacher,'已选',schedule];
function syntheticHtml(rows){
 const detail='<table id="mytable"><tr>'+HEADERS.map(h=>'<td>'+h+'</td>').join('')+'</tr>'+
  rows.map(c=>'<tr>'+c.map(v=>'<td>'+esc(v)+'</td>').join('')+'</tr>').join('')+'</table>';
 return '<!doctype html><html><body><input id="xn" value="2026">'+
  '<input id="xq_m" value="1"><table id="mytable"><tr><td>星期一</td></tr></table>'+
  detail+'</body></html>';
}
const term={semester:'2026-2027-1'};
const partial=parseNcwuStudentTimetableHtml(syntheticHtml([
 course('TEST-01','虚构电路基础','合成教师A',
  '1-8周 二[3-4] [虚构]A201；11-13周；14-16周'),
 course('TEST-02','虚构工程图学','合成教师B',
  '2-10双周 三[5-6] [虚构]B202；15周'),
 course('TEST-03','虚构英语','合成教师C','17周；16周'),
 course('TEST-04','虚构实验','合成教师D','9周；10周；11-12周')
]),term);
assert.equal(partial.courseRows,4);
assert.equal(partial.scheduleFragments,10);
assert.equal(partial.parsedBlocks,2);
assert.equal(partial.blockers.length,8);
assert.equal(partial.parsedCourseRows,2);
assert.equal(partial.blockedCourseRows,4);
assert.equal(partial.parseCoverage,'partial');
assert.equal(partial.trustedSyncAllowed,false);
const reference=partial.candidate.records.map(r=>({...r,weeks:[...r.weeks]}));
const limited=compareNcwuTimetableCandidate(partial,reference);
assert.equal(limited.matchedBlocks,2);
assert.equal(limited.missingBlocks,0);
assert.equal(limited.unexpectedBlocks,0);
assert.equal(limited.syntaxComplete,false);
assert.equal(limited.syntacticMatch,false);
assert.equal(limited.reviewStatus,'requires_source_review');
assert.equal(limited.verified,false);
assert.equal(limited.trustedSyncAllowed,false);
assert.deepEqual(Object.keys(limited.blockersByCode),['unsupported_schedule_notation']);
assert.equal(limited.blockersByCode.unsupported_schedule_notation,8);

const full=parseNcwuStudentTimetableHtml(syntheticHtml([
 course('SYN-A','虚构高数','合成教师甲','1-8周 一[1-2] [虚构]A101'),
 course('SYN-B','虚构Python','合成教师乙','1-12单周 五[3-4] [虚构]C201')
]),term);
const cleanReference=full.candidate.records.map(row=>({...row,weeks:[...row.weeks]}));
const match=compareNcwuTimetableCandidate(full,cleanReference);
assert.equal(match.syntaxComplete,true);
assert.equal(match.syntacticMatch,true);
assert.equal(match.reviewStatus,'structure_matched_not_authenticated');
assert.equal(match.verified,false);
assert.equal(match.trustedSyncAllowed,false);
assert.equal(match.matchedBlocks,2);
assert.equal(match.blockedFragments,0);
const difference=compareNcwuTimetableCandidate(full,[
 {...cleanReference[0],location:'[虚构]不同教室'},cleanReference[1]
]);
assert.equal(difference.syntaxComplete,true);
assert.equal(difference.syntacticMatch,false);
assert.equal(difference.missingBlocks,1);
assert.equal(difference.unexpectedBlocks,1);
assert.equal(difference.reviewStatus,'mismatch_requires_review');

const duplicate=compareNcwuTimetableCandidate(full,[cleanReference[0],cleanReference[0]]);
assert.equal(duplicate.syntacticMatch,false);
assert.equal(duplicate.duplicateReferenceBlocks,1);
assert.equal(duplicate.missingBlocks,1);
assert.equal(duplicate.unexpectedBlocks,1);
function fails(fn,code){
 assert.throws(fn,e=>e instanceof NcwuTimetableCompareError&&e.code===code);
}
fails(()=>compareNcwuTimetableCandidate({...full,verified:true},cleanReference),'invalid_parser_result');
fails(()=>compareNcwuTimetableCandidate({...full,complete:true},cleanReference),'invalid_parser_result');
fails(()=>compareNcwuTimetableCandidate({...full,trustedSyncAllowed:true},cleanReference),'invalid_parser_result');
fails(()=>compareNcwuTimetableCandidate(full,[{title:'虚构高数'}]),'invalid_course_structure');
fails(()=>compareNcwuTimetableCandidate(full,[{...cleanReference[0],semester:'2025-2026-1'}]),'semester_mismatch');
fails(()=>compareNcwuTimetableCandidate(full,[]),'invalid_course_list');
fails(()=>compareNcwuTimetableCandidate({...full,scheduleFragments:-1},cleanReference),'invalid_parser_result');

const serialized=JSON.stringify(limited)+JSON.stringify(match)+JSON.stringify(difference);
for(const sensitiveSynthetic of ['虚构电路基础','合成教师A','[虚构]A201','虚构高数','[虚构]不同教室']){
 assert.ok(!serialized.includes(sensitiveSynthetic),'comparison report must not expose course or place text');
}
console.log('NCWU timetable comparison: complete / incomplete / altered / duplicate / fail-closed / privacy summary passed');
