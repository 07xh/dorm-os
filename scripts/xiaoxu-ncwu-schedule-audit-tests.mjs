import assert from 'node:assert/strict';
import {auditNcwuTimetableAgainstReference}
 from '../lib/academic-ncwu-schedule-audit.mjs';
import {NcwuPageReadError} from '../lib/academic-ncwu-page-reader.mjs';

const ORIGIN='https://jwmis.ncwu.edu.cn';
const ENTRY=ORIGIN+'/hsjw/student/xkjg.wdkb.jsp';
const DETAIL=ORIGIN+'/hsjw/student/wsxk.xskcb10319.jsp?params=FAKE_TEST_VALUE';
const cookie='SYNTHETIC_SESSION=NOT_REAL';
const reference=[
 {title:'虚构工程图学',teacher:'合成教师甲',location:'虚构教室A101',
  weekday:1,startSection:1,endSection:2,weeks:[1,2,3,4]},
 {title:'虚构程序设计',teacher:'合成教师乙',location:'虚构实验室B202',
  weekday:4,startSection:5,endSection:6,weeks:[2,4,6,8]}
];
const headers=['上课班级代码','课程','总学时','学分','修读性质',
 '任课教师','选课状态','上课时间地点'];
const entry='<html><iframe src="/hsjw/student/wsxk.xskcb10319.jsp?params=FAKE_TEST_VALUE"></iframe></html>';
const escapeHtml=value=>String(value).replaceAll('&','&amp;')
 .replaceAll('<','&lt;').replaceAll('>','&gt;');
function detail({partial=false}={}){
 const rows=[
  ['FAKE-1',reference[0].title,'32','2.0','必修',reference[0].teacher,
   '已选','1-4周 一[1-2] '+reference[0].location],
  ['FAKE-2',reference[1].title,'32','2.0','必修',reference[1].teacher,
   '已选',partial?'9-10周':'2-8双周 四[5-6] '+reference[1].location]
 ];
 return '<html><input id="xn" value="2026"><input id="xq_m" value="1">'+
  '<table id="mytable"><tr><td>合成网格</td></tr></table>'+
  '<table id="mytable"><tr>'+headers.map(h=>'<th>'+h+'</th>').join('')+'</tr>'+
  rows.map(row=>'<tr>'+row.map(v=>'<td>'+escapeHtml(v)+'</td>').join('')+'</tr>').join('')+
  '</table></html>';
}
function transport({partial=false,expired=false}={}){
 const visited=[];
 const fetchImpl=async(url,options)=>{
  visited.push(url);
  assert.ok([ENTRY,DETAIL].includes(url),'must only request pinned official paths');
  assert.equal(options.method,'GET');
  assert.equal(options.redirect,'manual');
  assert.equal(options.headers.Cookie,cookie);
  if(expired)return new Response(null,{status:302,headers:{location:'/login'}});
  return new Response(url===ENTRY?entry:detail({partial}),{
   status:200,headers:{'content-type':'text/html; charset=utf-8'}
  });
 };
 return {visited,fetchImpl};
}
const fullTransport=transport();
const full=await auditNcwuTimetableAgainstReference({
 cookie,semester:'2026-2027-1',referenceRecords:reference,
 fetchImpl:fullTransport.fetchImpl
});
assert.deepEqual(fullTransport.visited,[ENTRY,DETAIL]);
assert.equal(full.reviewStatus,'structure_matched_not_authenticated');
assert.equal(full.matchedBlocks,2);
assert.equal(full.missingBlocks,0);
assert.equal(full.unexpectedBlocks,0);
assert.equal(full.syntaxComplete,true);
assert.equal(full.syntacticMatch,true);
assert.equal(full.verified,false);
assert.equal(full.trustedSyncAllowed,false);

const mismatch=await auditNcwuTimetableAgainstReference({
 cookie,semester:'2026-2027-1',
 referenceRecords:[reference[0],{...reference[1],location:'另一个虚构教室'}],
 fetchImpl:transport().fetchImpl
});
assert.equal(mismatch.reviewStatus,'mismatch_requires_review');
assert.equal(mismatch.missingBlocks,1);
assert.equal(mismatch.unexpectedBlocks,1);
assert.equal(mismatch.trustedSyncAllowed,false);

const incomplete=await auditNcwuTimetableAgainstReference({
 cookie,semester:'2026-2027-1',referenceRecords:reference,
 fetchImpl:transport({partial:true}).fetchImpl
});
assert.equal(incomplete.reviewStatus,'requires_source_review');
assert.equal(incomplete.syntaxComplete,false);
assert.equal(incomplete.missingBlocks,1);
assert.equal(incomplete.blockersByCode.unsupported_schedule_notation,1);
assert.equal(incomplete.verified,false);

await assert.rejects(
 ()=>auditNcwuTimetableAgainstReference({
  cookie,semester:'2026-2027-1',referenceRecords:reference,
  fetchImpl:transport({expired:true}).fetchImpl
 }),
 e=>e instanceof NcwuPageReadError&&e.code==='session_expired'
);
await assert.rejects(
 ()=>auditNcwuTimetableAgainstReference({
  cookie:'INVALID_'+String.fromCharCode(10),semester:'2026-2027-1',referenceRecords:reference,
  fetchImpl:transport().fetchImpl
 }),
 e=>e instanceof NcwuPageReadError&&e.code==='invalid_session_input'
);
const reports=JSON.stringify([full,mismatch,incomplete]);
for(const secret of [cookie,reference[0].title,reference[0].teacher,
 reference[0].location,reference[1].title,'另一个虚构教室',DETAIL]){
 assert.ok(!reports.includes(secret),'reports must contain aggregate metadata only');
}
assert.ok(!reports.includes('externalId'));
console.log('NCWU synthetic source audit: pinned fetch, official-page comparison, mismatch, partial coverage, expiration, no trust promotion or private report leakage passed');
