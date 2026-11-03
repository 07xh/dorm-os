import assert from 'node:assert/strict';
import {
 parseNcwuStudentTimetableHtml,NcwuTimetableHtmlError
} from '../lib/academic-ncwu-html-parser.mjs';

const HEADERS=['上课班级代码','课程','总学时','学分','修读性质','任课教师','选课状态','上课时间地点'];
const row=(code,title,teacher,times)=>[
 code,'[COURSE-SYNTH]'+title,'32','2.0','必修','[TEACHER-SYNTH]'+teacher,'已选',times
];
function html(rows,{year='2026',term='1',duplicateDetail=false,missingTable=false}={}){
 const esc=s=>String(s).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;');
 const data='<table id="mytable"><tr>'+HEADERS.map(x=>'<td>'+x+'</td>').join('')+'</tr>'+
  rows.map(r=>'<tr>'+r.map(v=>'<td>'+esc(v)+'</td>').join('')+'</tr>').join('')+'</table>';
 // Source uses repeated id=mytable: first one is a grid, the later one details.
 return '<!doctype html><html><body>'+
  '<input type="hidden" id="xn" value="'+year+'">'+
  '<input type="hidden" id="xq_m" value="'+term+'">'+
  '<table id="mytable"><tr><td colspan="2"></td><td>星期一</td></tr>'+
  '<tr><td>上午</td><td>1</td><td>合成示例</td></tr></table>'+
  (missingTable?'':data)+(duplicateDetail?data:'')+'</body></html>';
}
const example=html([
 row('SYN-01','合成电路基础','示例教师甲',
     '1-8周 一[1-2] [示例]教学楼A201；2,4,6周 三[3-4] [示例]实验楼B102'),
 row('SYN-02','合成程序设计','示例教师乙',
     '1-12双周 五[7-8] [示例]计算机教室C301')
]);
const result=parseNcwuStudentTimetableHtml(example,{semester:'2026-2027-1'});
assert.equal(result.source,'ncwu.student.wsxk.xskcb10319.jsp');
assert.equal(result.courseRows,2);
assert.equal(result.parsedBlocks,3);
assert.deepEqual(result.blockers,[]);
assert.equal(result.parseCoverage,'syntactically_complete');
assert.equal(result.verified,false);
assert.equal(result.complete,false);
assert.equal(result.trustedSyncAllowed,false);
assert.equal(result.candidate.verified,false);
assert.equal(result.candidate.complete,false);
assert.deepEqual(result.candidate.records.map(x=>x.weekday),[1,3,5]);
assert.deepEqual(result.candidate.records[0].weeks,[1,2,3,4,5,6,7,8]);
assert.deepEqual(result.candidate.records[1].weeks,[2,4,6]);
assert.deepEqual(result.candidate.records[2].weeks,[2,4,6,8,10,12]);
assert.equal(result.candidate.records[0].externalId,null,'do not invent school course IDs');
assert.equal(result.candidate.records[0].startTime,null,'no official bell schedule');
assert.equal(result.candidate.records[0].syncState,'unverified');

// HTML <br> and nested blocks must not be merged into one unreadable fragment.
const withBr=example.replace(
 '1-8周 一[1-2] [示例]教学楼A201；2,4,6周 三[3-4] [示例]实验楼B102',
 '1-8周 一[1-2] [示例]教学楼A201<br><div>2,4,6周 三[3-4] [示例]实验楼B102</div>'
);
const withBrParsed=parseNcwuStudentTimetableHtml(withBr,{semester:'2026-2027-1'});
assert.equal(withBrParsed.parsedBlocks,3);
assert.deepEqual(withBrParsed.blockers,[]);
assert.equal(withBrParsed.trustedSyncAllowed,false);

const partial=parseNcwuStudentTimetableHtml(html([
 row('SYN-01','合成高等数学','示例教师甲',
     '1-17周 二[1-2] [示例]教学楼A201；18-20周'),
 row('SYN-02','合成英语','示例教师乙','待通知')
]),{semester:'2026-2027-1'});
assert.equal(partial.courseRows,2);
assert.equal(partial.parsedBlocks,1);
assert.equal(partial.parseCoverage,'partial');
assert.equal(partial.trustedSyncAllowed,false);
assert.deepEqual(partial.blockers.map(x=>x.code),
 ['unsupported_schedule_notation','unsupported_schedule_notation']);
assert.deepEqual(Object.keys(partial.blockers[0]),['row','code'],'no private HTML in diagnostics');
assert.equal(partial.candidate.records.length,1);

const unsupported=parseNcwuStudentTimetableHtml(html([
 row('SYN-03','合成机械基础','示例教师','1-17周'),
]),{semester:'2026-2027-1'});
assert.equal(unsupported.parsedBlocks,0);
assert.equal(unsupported.candidate,null);
assert.equal(unsupported.parseCoverage,'partial');

const invalidWeeks=parseNcwuStudentTimetableHtml(html([
 row('SYN-04','合成英语','示例教师','0-18周 一[1-2] [示例]A201')
]),{semester:'2026-2027-1'});
assert.deepEqual(invalidWeeks.blockers.map(x=>x.code),['invalid_or_ambiguous_weeks']);
const badSection=parseNcwuStudentTimetableHtml(html([
 row('SYN-05','合成设计','示例教师','1-15周 三[9-3] [示例]A202')
]),{semester:'2026-2027-1'});
assert.deepEqual(badSection.blockers.map(x=>x.code),['invalid_section_range']);

function fails(fn,code){
 assert.throws(fn,e=>e instanceof NcwuTimetableHtmlError&&e.code===code);
}
fails(()=>parseNcwuStudentTimetableHtml(html([row('X','合成','教师','1-10周 一[1-2] [示例]A101')],{year:'2025'}),{semester:'2026-2027-1'}),'semester_metadata_mismatch');
fails(()=>parseNcwuStudentTimetableHtml(html([row('X','合成','教师','1-10周 一[1-2] [示例]A101')],{term:'2'}),{semester:'2026-2027-1'}),'semester_metadata_mismatch');
fails(()=>parseNcwuStudentTimetableHtml(html([row('X','合成','教师','1-10周 一[1-2] [示例]A101')],{missingTable:true}),{semester:'2026-2027-1'}),'detail_table_missing_or_ambiguous');
fails(()=>parseNcwuStudentTimetableHtml(html([row('X','合成','教师','1-10周 一[1-2] [示例]A101')],{duplicateDetail:true}),{semester:'2026-2027-1'}),'detail_table_missing_or_ambiguous');
fails(()=>parseNcwuStudentTimetableHtml(example,{semester:'2026-2027-2'}),'semester_metadata_mismatch');
fails(()=>parseNcwuStudentTimetableHtml('<html>请登录</html>',{semester:'2026-2027-1'}),'semester_metadata_mismatch');
fails(()=>parseNcwuStudentTimetableHtml('',{semester:'2026-2027-1'}),'html_missing_or_oversized');
fails(()=>parseNcwuStudentTimetableHtml('x'.repeat(500001),{semester:'2026-2027-1'}),'html_missing_or_oversized');
fails(()=>parseNcwuStudentTimetableHtml(example,{semester:'2026-2026-1'}),'semester_required');

const duplicate=html([
 row('SYN-1','合成课程','老师','1-3周 一[1-2] [示例]A201'),
 row('SYN-1','合成课程','老师','1-3周 一[1-2] [示例]A201')
]);
fails(()=>parseNcwuStudentTimetableHtml(duplicate,{semester:'2026-2027-1'}),'candidate_validation_failed');

console.log('NCWU HTML timetable parser: synthetic 8-column rows, repeated table IDs, weeks and strict fail-closed coverage passed');
