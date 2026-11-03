import assert from 'node:assert/strict';
import {parseNcwuTimetableGridHtml} from '../lib/academic-ncwu-grid-parser.mjs';
import {readNcwuTimetableCandidate} from '../lib/academic-ncwu-page-reader.mjs';

const semester='2026-2027-1';
const headers=['上课班级代码','课程','总学时','学分',
 '修读性质','任课教师','选课状态','上课时间地点'];
const entry='<html><iframe src="/hsjw/student/wsxk.xskcb10319.jsp?params=FAKE_DATA_ONLY"></iframe></html>';
const course='合成电工课程',teacher='虚构教师',room='虚构教室';
const safe=v=>String(v).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;');
function page({gridRoom=room,gridWeeks='1-8周[1-2]',
 columns=7,detailTime='1-8周 一[1-2] '+room}={}){
 const cells=Array.from({length:columns},(_,i)=>'<td class="td">'+
  (i===0?'<div style="padding-bottom:5px;">'+
    [course,teacher,gridWeeks,gridRoom].map(safe).join('<br>')+'</div>':'')+'</td>').join('');
 const detail='<table id="mytable"><tr>'+headers.map(v=>'<td>'+v+'</td>').join('')+'</tr>'+
  '<tr><td>FAKE-CLASS</td><td>'+course+'</td><td>32</td><td>2</td>'+
  '<td>必修</td><td>'+teacher+'</td><td>已选</td><td>'+safe(detailTime)+'</td></tr></table>';
 return '<html><input id="xn" value="2026"><input id="xq_m" value="1">'+
  '<table id="mytable"><tr>'+cells+'</tr></table>'+detail+'</html>';
}
function checkUnverified(result){
 assert.equal(result.verified,false);
 assert.equal(result.complete,false);
 assert.equal(result.trustedSyncAllowed,false);
 assert.equal(result.candidate?.verified,false);
 assert.equal(result.candidate?.complete,false);
}
const good=parseNcwuTimetableGridHtml(page(),{semester});
assert.equal(good.available,true);
assert.equal(good.gridItems,1);
assert.equal(good.parsedItems,1);
assert.deepEqual(good.blockers,[]);
assert.deepEqual(good.candidate.records[0].weeks,[1,2,3,4,5,6,7,8]);
assert.equal(good.candidate.records[0].weekday,1);
checkUnverified(good);

const badWeek=parseNcwuTimetableGridHtml(page({gridWeeks:'周次待定[1-2]'}),{semester});
assert.equal(badWeek.gridItems,1);
assert.equal(badWeek.parsedItems,0);
assert.deepEqual(badWeek.blockers.map(b=>b.code),['unsupported_grid_time']);
assert.equal(badWeek.candidate,null);
checkUnverified({...badWeek,candidate:{verified:false,complete:false}});

const missingDay=parseNcwuTimetableGridHtml(page({columns:6}),{semester});
assert.equal(missingDay.parsedItems,0);
assert.deepEqual(missingDay.blockers.map(b=>b.code),['ambiguous_grid_columns']);
const noGrid=parseNcwuTimetableGridHtml('<html><table><tr><td>合成</td></tr></table></html>',{semester});
assert.equal(noGrid.available,false);
assert.equal(noGrid.candidate,null);

const sameOrigin='https://jwmis.ncwu.edu.cn';
const read=html=>readNcwuTimetableCandidate({
 cookie:'SYNTHETIC_COOKIE_ONLY',semester,
 fetchImpl:async url=>new Response(url.includes('xkjg.wdkb.jsp')?entry:html,{
  status:200,headers:{'content-type':'text/html;charset=UTF-8'}
 })
});
const matched=await read(page());
checkUnverified(matched);
assert.equal(matched.gridCoverage.parsedItems,1);
assert.equal(matched.gridComparison.referenceBlocks,1);
assert.equal(matched.gridComparison.matchedBlocks,1);
assert.equal(matched.gridComparison.syntacticMatch,true);
assert.equal(matched.gridComparison.verified,false);
assert.equal(matched.gridComparison.trustedSyncAllowed,false);
assert.equal(matched.gridCandidate.records[0].externalId,null);

const mismatch=await read(page({gridRoom:'合成另一教室'}));
assert.equal(mismatch.gridComparison.syntacticMatch,false);
assert.equal(mismatch.gridComparison.missingBlocks,1);
assert.equal(mismatch.gridComparison.unexpectedBlocks,1);
assert.equal(mismatch.trustedSyncAllowed,false);
const partialDetail=await read(page({detailTime:'1-8周'}));
assert.equal(partialDetail.parseCoverage,'partial');
assert.equal(partialDetail.gridCoverage.parsedItems,1);
assert.equal(partialDetail.gridComparison.syntacticMatch,false);
assert.equal(partialDetail.gridComparison.reviewStatus,'requires_source_review');
assert.equal(partialDetail.trustedSyncAllowed,false);
const ambiguous=await read(page({columns:6}));
assert.equal(ambiguous.gridComparison,null);
assert.deepEqual(ambiguous.gridCoverage.blockerCodes,['ambiguous_grid_columns']);
assert.equal(ambiguous.trustedSyncAllowed,false);
assert.ok(!JSON.stringify(matched.gridCoverage).includes('COOKIE'));
assert.ok(!JSON.stringify(matched.gridComparison).includes(course));
assert.ok(!JSON.stringify(mismatch.gridComparison).includes('教室'));
console.log('NCWU weekday-grid crosscheck: dual HTML, missing cells, mismatch, no owner proof and fail-closed provenance passed');
