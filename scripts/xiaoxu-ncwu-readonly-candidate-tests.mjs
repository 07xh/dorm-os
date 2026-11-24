import assert from 'node:assert/strict';
import {readExamClassPath,parseExamRounds,examRoundBody,classifyExamCandidate,
 readEnrollmentYearCandidate,gradeQueryBody,classifyGradeCandidate} from '../lib/academic-ncwu-readonly-candidates.mjs';

const term='2026-2027-1',opaque='A'.repeat(240);
assert.equal(readExamClassPath('<root><examArrangeInfo>'+opaque+'</examArrangeInfo></root>'),opaque);
assert.throws(()=>readExamClassPath('<root><examArrangeInfo>x</examArrangeInfo></root>'));
assert.throws(()=>readExamClassPath('<!DOCTYPE root><root/>'));
assert.throws(()=>readExamClassPath('<root><examArrangeInfo>'+opaque+'</examArrangeInfo><examArrangeInfo>'+opaque+'</examArrangeInfo></root>'));
const item=(year,index,id,title)=>'<item><xn>'+year+'</xn><xq_m>'+index+'</xq_m><kslcdm>'+id+'</kslcdm><kslcmc>'+title+'</kslcmc></item>';
const xml=(items)=>'<info><xh>synthetic</xh>'+items+'</info>';
const a=item('2026','0','2','9-12周'),b=item('2025','1','6','19周');
assert.deepEqual(parseExamRounds(xml(a+b),term),[{id:'2',title:'9-12周'}]);
assert.deepEqual(parseExamRounds(xml(b),term),[]);
assert.throws(()=>parseExamRounds(xml(a+a),term));
assert.throws(()=>parseExamRounds('<html>凭证已失效</html>',term));
assert.throws(()=>parseExamRounds(xml(a),'2026-2028-1'));
assert.deepEqual(Object.fromEntries(new URLSearchParams(examRoundBody(term,{id:'2',title:'9-12周'}))),
 {xnxq:'20260',kslc:'2',xn:'2026',xq:'0',title:'9-12周'});
const empty='<h1>华北水利水电大学考试安排表</h1><p>2026-2027学年第一学期</p><p>没有检索到记录!</p>';
assert.equal(classifyExamCandidate(empty,term),'reported_empty');
assert.equal(classifyExamCandidate(empty,'2026-2027-2'),'unrecognized');
assert.equal(classifyExamCandidate(empty+'<table><tr><td>课程</td><td>时间</td></tr></table>',term),'schema_review_required');
assert.throws(()=>classifyExamCandidate('<p>凭证已失效</p>',term));
assert.equal(readEnrollmentYearCandidate('<p>年级：2026</p>'),'2026');
assert.throws(()=>readEnrollmentYearCandidate('<p>年级：2026</p><p>年级：2025</p>'));
assert.deepEqual(Object.fromEntries(new URLSearchParams(gradeQueryBody('2026'))),
 {sjxz:'sjxz1',ysyx:'yscj',zx:'1',fx:'0',wz:'0',rxnj:'2026',nj:'2026'});
assert.equal(classifyGradeCandidate('<p>你还有未评教的课程，不允许查看成绩！</p>'),'evaluation_required');
assert.equal(classifyGradeCandidate('<table><tr><td>合成成绩</td></tr></table>'),'schema_review_required');
assert.throws(()=>classifyGradeCandidate('<input type="password">'));
console.log('NCWU readonly candidate contracts passed');
