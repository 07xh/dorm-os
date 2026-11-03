import assert from 'node:assert/strict';
import {readNcwuTimetableCandidate,findNcwuTimetableUrl,NcwuPageReadError}
 from '../lib/academic-ncwu-page-reader.mjs';

const ENTRY='https://jwmis.ncwu.edu.cn/hsjw/student/xkjg.wdkb.jsp';
const DETAIL='https://jwmis.ncwu.edu.cn/hsjw/student/wsxk.xskcb10319.jsp?params=SYNTHETIC_NO_STUDENT_ID';
const cookie='TEST_ONLY=fictional';
const entry='<html><body><iframe src="/hsjw/student/wsxk.xskcb10319.jsp?params=SYNTHETIC_NO_STUDENT_ID"></iframe></body></html>';
const headers={'content-type':'text/html; charset=utf-8'};
const page='<html><body><input id="xn" value="2026"><input id="xq_m" value="1">'+
 '<table id="mytable"><tr><td>星期一</td></tr></table>'+
 '<table id="mytable"><tr>'+
 ['上课班级代码','课程','总学时','学分','修读性质','任课教师','选课状态','上课时间地点']
 .map(s=>'<td>'+s+'</td>').join('')+
 '</tr><tr><td>SYNTHETIC-CLASS</td><td>虚构课程</td><td>32</td><td>2</td>'+
 '<td>必修</td><td>示例教师</td><td>已选</td>'+
 '<td>1-8周 一[1-2] 虚构教室A<br><div>2,4,6周 三[3-4] 虚构教室B</div></td>'+
 '</tr></table></body></html>';

const calls=[];
const read=async (url,options)=>{
 calls.push({url,options});
 assert.equal(options.method,'GET');
 assert.equal(options.redirect,'manual');
 assert.equal(options.headers.Cookie,cookie);
 if(url===ENTRY)return new Response(entry,{headers});
 assert.equal(url,DETAIL);
 return new Response(page,{headers});
};
const result=await readNcwuTimetableCandidate({cookie,semester:'2026-2027-1',fetchImpl:read});
assert.equal(result.courseRows,1);
assert.equal(result.parsedBlocks,2);
assert.equal(result.parseCoverage,'syntactically_complete');
assert.deepEqual(result.blockers,[]);
assert.equal(result.verified,false);
assert.equal(result.trustedSyncAllowed,false);
assert.equal(result.candidate.records.length,2);
assert.equal(result.candidate.records[0].externalId,null);
assert.deepEqual(calls.map(x=>x.url),[ENTRY,DETAIL]);

const rejects=async(fn,code)=>assert.rejects(fn,e=>e instanceof NcwuPageReadError&&e.code===code);
await rejects(()=>readNcwuTimetableCandidate({
 cookie:'INVALID\nCOOKIE',semester:'2026-2027-1',fetchImpl:read
}),'invalid_session_input');
await rejects(()=>readNcwuTimetableCandidate({
 cookie,semester:'2026-2027-1',
 fetchImpl:async()=>new Response(null,{status:302,headers:{location:'https://authserver.ncwu.edu.cn/login'}})
}),'session_expired');
await rejects(()=>readNcwuTimetableCandidate({
 cookie,semester:'2026-2027-1',
 fetchImpl:async()=>new Response('<html><input type="password"></html>',{headers})
}),'session_expired');
assert.throws(()=>findNcwuTimetableUrl(
 '<iframe src="https://example.org/hsjw/student/wsxk.xskcb10319.jsp?params=x"></iframe>'
),e=>e.code==='invalid_detail_url');
assert.throws(()=>findNcwuTimetableUrl(
 '<iframe src="/hsjw/student/wsxk.xskcb10319.jsp"></iframe>'
),e=>e.code==='timetable_parameter_missing');
assert.throws(()=>findNcwuTimetableUrl(
 '<iframe src="/hsjw/student/wsxk.xskcb10319.jsp?params=one"></iframe>'+
 '<iframe src="/hsjw/student/wsxk.xskcb10319.jsp?params=two"></iframe>'
),e=>e.code==='ambiguous_detail_url');
assert.throws(()=>findNcwuTimetableUrl('<html><body>not a timetable</body></html>'),
 e=>e.code==='detail_url_missing');
console.log('NCWU official-origin page reader: iframe isolation, multi-line HTML, session expiry and unverified provenance passed');
