import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
import {parseChaoxingCourseList,parseChaoxingWorkCredentials,
 parseChaoxingWorkList,safeChaoxingWorkUrl,classifyChaoxingWorkStatus} from '../lib/academic-chaoxing-work-discovery.mjs';
import {createChaoxingSchoolAdapter} from '../lib/academic-chaoxing-provider.mjs';
globalThis.crypto??=webcrypto;
const courseHtml='<a href="/visit/stucoursemiddle?courseid=123&clazzid=456&cpi=789"></a>'+
 '<span class="course-name" title="Python 基础"></span>';
const middle='<input id="workEnc" value="signedWork"/>'+
 '<input id="enc" value="signedPage"/><input id="openc" value="openKey"/>';
const taskUrl='https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task'+
 '?courseId=123&classId=456&workId=987&enc=signedTask';
const workHtml='<ul><li onclick="goTask(this);" data="'+taskUrl.replaceAll('&','&amp;')+'">'+
 '<p class="overHidden2 fl">循环练习</p><p class="status fl">未提交</p></li></ul>';
const questionHtml='<html><body><div>作答时间: 11-03 08:15 至 12-01 23:59</div><form id="submitForm">'+
 '<div class="questionLi"><h3 class="mark_name">单选题：列表索引从几开始？</h3>'+
 '<div class="num_option">A. 0</div><div class="num_option">B. 1</div>'+
 '<div class="answerBg">隐藏答案</div></div></form></body></html>';
assert.deepEqual(parseChaoxingCourseList(courseHtml),[
 {courseId:'123',classId:'456',cpi:'789',title:'Python 基础'}
]);
// Upstream query argument order can vary and entities are HTML-escaped.
// Old strict regex returned courses_unverified even with legitimate IDs.
const reordered='<a href="/visit/stucoursemiddle?cpi=789&amp;courseid=123&amp;clazzid=456"></a>'+ 
 '<span class="course-name" title="Python 基础"></span>';
assert.deepEqual(parseChaoxingCourseList(reordered),[
 {courseId:'123',classId:'456',cpi:'789',title:'Python 基础'}
]);
// Multiple links to the same official course should not create duplicates.
assert.equal(parseChaoxingCourseList(courseHtml+courseHtml).length,1);
assert.throws(()=>parseChaoxingCourseList(
 '<a href="/visit/stucoursemiddle?courseid=12&amp;courseid=13&amp;clazzid=4&amp;cpi=5"></a>'+ 
 '<span class="course-name" title="wrong"></span>'
),e=>e.code==='courses_unverified','ambiguous official IDs must be rejected');
assert.throws(()=>parseChaoxingCourseList(
 '<a href="/visit/stucoursemiddle?cpi=789&amp;courseid=abc&amp;clazzid=456"></a>'+ 
 '<span class="course-name" title="wrong"></span>'
),e=>e.code==='courses_unverified','nonnumeric course IDs are not accepted');
assert.deepEqual(parseChaoxingWorkCredentials(middle),
 {workEnc:'signedWork',pageEnc:'signedPage',openc:'openKey'});
assert.deepEqual(parseChaoxingWorkList(workHtml,{courseId:'123',classId:'456'}).items.map(x=>x.workId),['987']);
// A signed official work URL may be percent-encoded in its entirety.
const encodedWorkHtml=workHtml.replace(taskUrl.replaceAll('&','&amp;'),
 encodeURIComponent(taskUrl));
assert.deepEqual(parseChaoxingWorkList(encodedWorkHtml,{courseId:'123',classId:'456'}).items.map(x=>x.workId),['987']);
for(const untrusted of [
 encodeURIComponent('https://evil.example/mooc-ans/mooc2/work/task?courseId=123&classId=456&workId=987'),
 encodeURIComponent('http://mooc1.chaoxing.com/mooc-ans/mooc2/work/task?courseId=123&classId=456&workId=987'),
 encodeURIComponent(taskUrl.replace('courseId=123','courseId=999')),
 encodeURIComponent(taskUrl+'\n')
])assert.throws(()=>safeChaoxingWorkUrl(untrusted,{courseId:'123',classId:'456'}));
// Only the xuexitong-cli official work-page total, never an inferred total.
const countHtml=workHtml.replace('<ul>', '<div><div><span>1/1</span></div></div><ul>');
assert.deepEqual(parseChaoxingWorkList(countHtml,{courseId:'123',classId:'456'}).progress,
 {completed:1,total:1});
assert.equal(parseChaoxingWorkList(workHtml,{courseId:'123',classId:'456'}).progress,null);
for(const html of [
 countHtml.replace('<span>1/1</span>','<span>1/2</span>'),
 countHtml.replace('<span>1/1</span>','<span>1/1</span></div></div><div><div><span>0/2</span>'),
])assert.throws(()=>parseChaoxingWorkList(html,{courseId:'123',classId:'456'}),
 e=>e.code==='work_list_incomplete');
assert.throws(()=>parseChaoxingWorkList(countHtml.replace('1/1','2/1'),
 {courseId:'123',classId:'456'}),e=>e.code==='invalid_response');
assert.equal(parseChaoxingWorkList('<body>暂无作业</body>',{courseId:'123',classId:'456'}).verifiedEmpty,true);
assert.equal(parseChaoxingWorkList('<body>无数据</body>',{courseId:'123',classId:'456'}).verifiedEmpty,false);
for(const url of [
 'https://attacker.test/mooc-ans/mooc2/work/task?courseId=123&classId=456&workId=987',
 'http://mooc1.chaoxing.com/mooc-ans/mooc2/work/task?courseId=123&classId=456&workId=987',
 'https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task?courseId=999&classId=456&workId=987',
 'https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task?courseId=123&classId=456&workId=987#x'
])assert.throws(()=>safeChaoxingWorkUrl(url,{courseId:'123',classId:'456'}));
assert.throws(()=>parseChaoxingCourseList('<body>登录超星</body>'),e=>e.code==='courses_unverified');
const profile='<div aria-label="账号：虚构测试人"></div>'+
 '<div id="siteName" title="华北水利水电大学"></div><script>var uid = "1234567";var fid = "427";</script>';
const requests=[];
const response=s=>new Response(s,{status:200,headers:{'content-type':'text/html'}});
async function fakeFetch(raw,options){
 const u=new URL(raw),path=u.pathname;
 requests.push({path,host:u.hostname,method:options.method,
  cookie:options.headers.get('cookie'),query:u.searchParams});
 if(path==='/base')return response(profile);
 if(path==='/mooc2-ans/visit/courselistdata'){
  assert.equal(u.searchParams.get('courseType'),'1');
  assert.equal(u.searchParams.get('courseFid'),'427');
  assert.equal(u.searchParams.get('courseTeacherid'),'0');
  return response(courseHtml);
 }
 if(path==='/visit/stucoursemiddle')return response(middle);
 if(path==='/mooc2/work/list'){
  assert.equal(u.searchParams.get('enc'),'signedWork');
  assert.equal(u.searchParams.get('stuenc'),'signedPage');
  assert.equal(u.searchParams.get('courseId'),'123');
  return response(workHtml);
 }
 if(path==='/mooc-ans/mooc2/work/task')return response(questionHtml);
 throw Error('unexpected route '+path);
}
const adapter=createChaoxingSchoolAdapter({fetchImpl:fakeFetch,clock:()=>Date.now()});
const session={schema:'chaoxing-session-v1',uid:'1234567',
 jar:[{name:'cx_s',value:'sandbox-session',domain:'chaoxing.com',path:'/',hostOnly:false}]};
const read=await adapter.forSession({session,userId:'owner1'});
assert.equal((await read.verifySession()).principalBoundTo,'owner1');
const courses=await read.readCourses();
assert.equal(courses.length,1);
await assert.rejects(()=>read.readHomeworkList({courseId:'999',classId:'456',cpi:'789'}),
 e=>e.code==='authorization_failed');
const list=await read.readHomeworkList({courseId:'123',classId:'456',cpi:'789'});
assert.equal(list.items.length,1);
assert.equal(list.progress,null, 'do not fabricate a total when the official page omits it');
assert.equal(list.items[0].workId,'987');
assert.equal(list.items[0].statusCode,'todo');
assert.equal(list.items[0].platformVerified,true);
for(const [label,code] of [
 ['未提交','todo'],['待评阅','submitted'],['已提交','submitted'],
 ['已评分','graded'],['批阅完成','graded'],['退回重做','returned'],['','unknown'],['待开放','unknown']
])assert.equal(classifyChaoxingWorkStatus(label),code);
assert.ok(!JSON.stringify(list).includes('signedTask'));
const detail=await read.readHomeworkDetail({courseId:'123',classId:'456',cpi:'789',workId:'987'});
assert.equal(detail.questionsAvailable,true);
assert.equal(detail.statusCode,'todo');
assert.equal(detail.startTimeRaw,'11-03 08:15');
assert.equal(detail.deadlineRaw,'12-01 23:59');
assert.equal(Object.hasOwn(detail,'dueAt'),false,'no invented year or absolute deadline');
assert.equal(detail.questions.length,1);
assert.equal(detail.questions[0].options.length,2);
assert.ok(!JSON.stringify(detail).includes('隐藏答案'));
assert.ok(!JSON.stringify(detail).includes('signedTask'));
assert.ok(!JSON.stringify(detail).includes('sandbox-session'));
assert.ok(requests.every(x=>x.method==='GET'));
assert.ok(requests.every(x=>x.cookie.includes('cx_s=sandbox-session')));
console.log('Chaoxing direct homework: discovery, session ownership, signed-link isolation, question preview passed');

const missingFidProfile='<div aria-label="账号：虚构测试人"></div>'+
 '<div id="siteName" title="华北水利水电大学"></div><script>var uid = "1234567";</script>';
const noFid=createChaoxingSchoolAdapter({fetchImpl:async(url,options)=>
 new Response(missingFidProfile)});
const noFidReader=await noFid.forSession({session,userId:'owner1'});
await noFidReader.verifySession();
await assert.rejects(()=>noFidReader.readCourses(),e=>e.code==='courses_unverified',
 'missing school Fid must not return anonymous/wrong-school courses');


// Regression: a redirect to official login is session expiry, not missing homework.
const loginRedirect=()=>new Response('',{status:302,headers:{location:'https://passport2.chaoxing.com/login'}});
const sessionLost=failAt=>createChaoxingSchoolAdapter({fetchImpl:async raw=>{
 const u=new URL(raw);
 if(u.pathname==='/base')return response(profile);
 if(u.pathname==='/mooc2-ans/visit/courselistdata')return response(courseHtml);
 if(u.pathname==='/visit/stucoursemiddle')return failAt==='middle'?loginRedirect():response(middle);
 if(u.pathname==='/mooc2/work/list')return loginRedirect();
 if(u.hostname==='passport2.chaoxing.com')return response('<body>登录超星</body>');
 throw Error('unexpected route '+u.pathname);
}});
for(const failAt of ['middle','list']){
 const lost=await sessionLost(failAt).forSession({session,userId:'owner1'});
 await lost.verifySession();
 await assert.rejects(()=>lost.readHomeworkList({courseId:'123',classId:'456',cpi:'789'}),
  e=>e.code==='authorization_failed','login redirect at '+failAt+' means expired session');
}
const captchaTitle=workHtml.replace('循环练习','验证码识别实验');
assert.equal(parseChaoxingWorkList(captchaTitle,{courseId:'123',classId:'456'}).items.length,1);
assert.throws(()=>parseChaoxingWorkList('<body>请输入验证码</body>',{courseId:'123',classId:'456'}),
 e=>e.code==='interactive_login_required');

// Regression: completed assignments can be li[data] rows without goTask,
// and the upstream list is paginated. Do not mistake page one for full coverage.
const courseKeys={courseId:'123',classId:'456',cpi:'789'};
const row=(id,title,status)=>
 '<li data="https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task'+
 '?courseId=123&amp;classId=456&amp;workId='+id+'&amp;enc=signedTask">'+
 '<div class="tag icon-zy-g"></div><p class="overHidden2 fl">'+title+'</p>'+
 '<p class="status fl">'+status+'</p></li>';
const paginated=(number,html,total='2')=>
 '<div><div><span>1/'+total+'</span></div></div>'+
 '<div id="page"><li'+(number===1?' class="xl-active"':'')+'>1</li>'+
 '<li'+(number===2?' class="xl-active"':'')+'>2</li></div>'+
 '<ul>'+html+'</ul>';
const firstPage=paginated(1,row('910','第一次作业','未提交'));
const secondPage=paginated(2,row('911','第二次作业','已完成'));
const parsedFirst=parseChaoxingWorkList(firstPage,courseKeys);
assert.equal(parsedFirst.totalPages,2);
assert.equal(parsedFirst.progress.total,2);
assert.equal(parsedFirst.items.length,1,'first page is not the entire course');
assert.equal(parseChaoxingWorkList(secondPage,courseKeys,{pageNumber:2}).items[0].workId,'911');
const pagesFetched=[];
const pagedAdapter=createChaoxingSchoolAdapter({fetchImpl:async (raw)=>{
 const u=new URL(raw);
 if(u.pathname==='/base')return response(profile);
 if(u.pathname==='/mooc2-ans/visit/courselistdata')return response(courseHtml);
 if(u.pathname==='/visit/stucoursemiddle')return response(middle);
 if(u.pathname==='/mooc2/work/list'){
  const pageNum=u.searchParams.get('pageNum');
  pagesFetched.push(pageNum);
  return response(pageNum==='1'?firstPage:secondPage);
 }
 throw Error('unexpected route '+u.pathname);
}});
const pagedReader=await pagedAdapter.forSession({session,userId:'owner1'});
await pagedReader.verifySession();
const paged=await pagedReader.readHomeworkList(courseKeys);
assert.deepEqual(pagesFetched,['1','2']);
assert.deepEqual(paged.items.map(x=>[x.workId,x.statusCode]),[
 ['910','todo'],['911','submitted']
]);
assert.deepEqual(paged.progress,{completed:1,total:2});
assert.equal(paged.items.every(x=>x.platformVerified),true);
assert.ok(!JSON.stringify(paged).includes('signedTask'));
// Mutating/duplicated pagination must not emit a partial trusted list.
const duplicatedAdapter=createChaoxingSchoolAdapter({fetchImpl:async raw=>{
 const u=new URL(raw);
 if(u.pathname==='/base')return response(profile);
 if(u.pathname==='/mooc2-ans/visit/courselistdata')return response(courseHtml);
 if(u.pathname==='/visit/stucoursemiddle')return response(middle);
 if(u.pathname==='/mooc2/work/list')
  return response(u.searchParams.get('pageNum')==='1'?firstPage:
   paginated(2,row('910','重复作业','已完成')));
 throw Error('unexpected route '+u.pathname);
}});
const duplicatedReader=await duplicatedAdapter.forSession({session,userId:'owner1'});
await duplicatedReader.verifySession();
await assert.rejects(()=>duplicatedReader.readHomeworkList(courseKeys),
 e=>e.code==='work_list_incomplete');
// Total is checked across ALL official pages, not incorrectly on page one.
const missingWorkAdapter=createChaoxingSchoolAdapter({fetchImpl:async raw=>{
 const u=new URL(raw);
 if(u.pathname==='/base')return response(profile);
 if(u.pathname==='/mooc2-ans/visit/courselistdata')return response(courseHtml);
 if(u.pathname==='/visit/stucoursemiddle')return response(middle);
 if(u.pathname==='/mooc2/work/list'){
  return response(paginated(Number(u.searchParams.get('pageNum')),
   Number(u.searchParams.get('pageNum'))===1?
    row('910','作业1','未提交'):row('911','作业2','已完成'),'3'));
 }
 throw Error('unexpected route '+u.pathname);
}});
const missingWorkReader=await missingWorkAdapter.forSession({session,userId:'owner1'});
await missingWorkReader.verifySession();
await assert.rejects(()=>missingWorkReader.readHomeworkList(courseKeys),
 e=>e.code==='work_list_incomplete','partial pages cannot be persisted');
// The official status text, never the icon-zy-g CSS label, controls completion.
assert.equal(classifyChaoxingWorkStatus('已批改'),'graded');
assert.equal(classifyChaoxingWorkStatus('已互評'),'submitted');
assert.equal(classifyChaoxingWorkStatus('待批改'),'submitted');
assert.equal(classifyChaoxingWorkStatus('待互评'),'unknown');
assert.equal(classifyChaoxingWorkStatus('未交'),'todo');

// Regression: the official student-course page varies by course. Its HTML can
// expose workEnc without the unrelated #enc/#openc fields, or only an official
// redirected enc token. Do not confuse this with an expired owner session.
const workOnly='<input id="workEnc" value="signedWork"/>';
assert.deepEqual(parseChaoxingWorkCredentials(workOnly),
 {workEnc:'signedWork',openc:'',pageEnc:''});
const urlToken='a'.repeat(32);
const officialRedirect='https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/stu?enc='+urlToken;
assert.deepEqual(parseChaoxingWorkCredentials('<html>student course</html>',
 {finalUrl:officialRedirect}),{workEnc:urlToken,openc:'',pageEnc:''});
for(const untrusted of [
 'https://other.example/mooc2-ans/mycourse/stu?enc='+urlToken,
 'http://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/stu?enc='+urlToken,
 'https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/stu?enc='+urlToken+'&enc='+urlToken,
 'https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/other?enc='+urlToken
])assert.throws(()=>parseChaoxingWorkCredentials('<html>course</html>',
 {finalUrl:untrusted}),e=>e.code==='work_not_available');
assert.throws(()=>parseChaoxingWorkCredentials('<html>course</html>'),
 e=>e.code==='work_not_available');
// The normal verified owner read must still validate course ownership and
// never send absent optional credentials as empty query parameters.
const templates=[];
const templated=createChaoxingSchoolAdapter({fetchImpl:async raw=>{
 const u=new URL(raw);
 if(u.pathname==='/base')return response(profile);
 if(u.pathname==='/mooc2-ans/visit/courselistdata')return response(courseHtml);
 if(u.pathname==='/visit/stucoursemiddle')return response(workOnly);
 if(u.pathname==='/mooc2/work/list'){
  templates.push(u);
  assert.equal(u.searchParams.get('enc'),'signedWork');
  assert.equal(u.searchParams.has('openc'),false);
  assert.equal(u.searchParams.has('stuenc'),false);
  return response(workHtml);
 }
 throw Error('unexpected route '+u.pathname);
}});
const templateReader=await templated.forSession({session,userId:'owner1'});
await templateReader.verifySession();
assert.equal((await templateReader.readHomeworkList(
 {courseId:'123',classId:'456',cpi:'789'})).items.length,1);
assert.equal(templates.length,1);
const redirected=createChaoxingSchoolAdapter({fetchImpl:async raw=>{
 const u=new URL(raw);
 if(u.pathname==='/base')return response(profile);
 if(u.pathname==='/mooc2-ans/visit/courselistdata')return response(courseHtml);
 if(u.pathname==='/visit/stucoursemiddle')return new Response('',{
  status:302,headers:{location:officialRedirect}});
 if(u.pathname==='/mooc2-ans/mycourse/stu')return response('<body>student course</body>');
 if(u.pathname==='/mooc2/work/list'){
  assert.equal(u.searchParams.get('enc'),urlToken);
  assert.equal(u.searchParams.has('openc'),false);
  assert.equal(u.searchParams.has('stuenc'),false);
  return response(workHtml);
 }
 throw Error('unexpected route '+u.pathname);
}});
const redirectReader=await redirected.forSession({session,userId:'owner1'});
await redirectReader.verifySession();
assert.equal((await redirectReader.readHomeworkList(
 {courseId:'123',classId:'456',cpi:'789'})).items.length,1);

// Regression: a documented newer official work-list path can expose completed
// homework for a course whose legacy /mooc2/work/list page is malformed.
// Both requests MUST use the already verified owner and the same course IDs.
const officialAltPath='/mooc-ans/mooc2/work/list';
const alternateCalls=[];
const legacyBroken=createChaoxingSchoolAdapter({fetchImpl:async(raw,options)=>{
 const u=new URL(raw);
 if(u.pathname==='/base')return response(profile);
 if(u.pathname==='/mooc2-ans/visit/courselistdata')return response(courseHtml);
 if(u.pathname==='/visit/stucoursemiddle')return response(workOnly);
 if(u.pathname==='/mooc2/work/list'){
  alternateCalls.push('legacy');
  return response('<html><body>无数据</body></html>');
 }
 if(u.pathname===officialAltPath){
  alternateCalls.push('official');
  assert.equal(u.hostname,'mooc1.chaoxing.com');
  assert.equal(u.searchParams.get('courseId'),'123');
  assert.equal(u.searchParams.get('classId'),'456');
  assert.equal(u.searchParams.get('cpi'),'789');
  assert.equal(u.searchParams.get('enc'),'signedWork');
  assert.equal(options.method,'GET');
  return response('<ul>'+row('930','Python 实验报告一','已完成')+
    row('931','Python 实验报告二','待批阅')+'</ul>');
 }
 throw Error('unexpected route '+u.pathname);
}});
const legacyReader=await legacyBroken.forSession({session,userId:'owner1'});
await legacyReader.verifySession();
const recovered=await legacyReader.readHomeworkList(courseKeys);
assert.deepEqual(alternateCalls,['legacy','official']);
assert.deepEqual(recovered.items.map(x=>[x.workId,x.statusCode]),[
 ['930','submitted'],['931','submitted']]);
assert.ok(recovered.items.every(x=>x.platformVerified));
assert.ok(!JSON.stringify(recovered).includes('signedTask'));
assert.equal(recovered.verifiedEmpty,false);

// Some signed-in courses use the compact official /mooc-ans/ work-list
// query. Legacy-only ut/stuenc/openc/isdisplaytable/t may produce a shell.
// Retry the documented compact request once, never for identity/login failures.
const compactCalls=[];
const compactOnly=createChaoxingSchoolAdapter({fetchImpl:async raw=>{
 const u=new URL(raw),path=u.pathname;
 if(path==='/base')return response(profile);
 if(path==='/mooc2-ans/visit/courselistdata')return response(courseHtml);
 if(path==='/visit/stucoursemiddle')return response(middle);
 if(path==='/mooc2/work/list')return response('<body>无数据</body>');
 if(path===officialAltPath){
  const keys=[...u.searchParams.keys()].sort();
  compactCalls.push(keys);
  if(keys.join(',')!=='classId,courseId,cpi,enc,pageNum')
   return new Response('<body>旧参数不兼容</body>',{status:400});
  assert.equal(u.searchParams.get('courseId'),'123');
  assert.equal(u.searchParams.get('classId'),'456');
  assert.equal(u.searchParams.get('cpi'),'789');
  assert.equal(u.searchParams.get('enc'),'signedWork');
  return response('<ul>'+row('940','Python 作业一','待批阅')+
    row('941','Python 作业二','已完成')+'</ul>');
 }
 throw Error('unexpected route '+path);
}});
const compactReader=await compactOnly.forSession({session,userId:'owner1'});
await compactReader.verifySession();
const compactWorks=await compactReader.readHomeworkList(courseKeys);
assert.deepEqual(compactCalls.map(keys=>keys.includes('ut')), [true,false]);
assert.deepEqual(compactWorks.items.map(x=>[x.workId,x.statusCode]),
 [['940','submitted'],['941','submitted']]);
assert.ok(!JSON.stringify(compactWorks).includes('signedWork'));

// A broken response under BOTH query shapes must not create fake zero work,
// alter trusted statuses or downgrade an actual official authorization failure.
const compactBroken=createChaoxingSchoolAdapter({fetchImpl:async raw=>{
 const u=new URL(raw),path=u.pathname;
 if(path==='/base')return response(profile);
 if(path==='/mooc2-ans/visit/courselistdata')return response(courseHtml);
 if(path==='/visit/stucoursemiddle')return response(middle);
 if(path==='/mooc2/work/list')return response('<body>无数据</body>');
 if(path===officialAltPath)return response('<body>未验证的页面空壳</body>');
 throw Error('unexpected route '+path);
}});
const compactBrokenReader=await compactBroken.forSession({session,userId:'owner1'});
await compactBrokenReader.verifySession();
await assert.rejects(()=>compactBrokenReader.readHomeworkList(courseKeys),
 e=>e.code==='work_not_available'&&e.schoolReadStage==='list_official');

// A verified "no work" banner from the obsolete route is not proof that the
// newer official route also has no homework.
const obsoleteEmpty=createChaoxingSchoolAdapter({fetchImpl:async raw=>{
 const path=new URL(raw).pathname;
 if(path==='/base')return response(profile);
 if(path==='/mooc2-ans/visit/courselistdata')return response(courseHtml);
 if(path==='/visit/stucoursemiddle')return response(middle);
 if(path==='/mooc2/work/list')return response('<body>暂无作业</body>');
 if(path===officialAltPath)return response('<ul>'+row('932','报告','已批阅')+'</ul>');
 throw Error('unexpected route '+path);
}});
const emptyReader=await obsoleteEmpty.forSession({session,userId:'owner1'});
await emptyReader.verifySession();
assert.equal((await emptyReader.readHomeworkList(courseKeys)).items[0].statusCode,'graded');

// Old-route data must remain authoritative when fully verified, never union
// unrelated pages or double count a work ID in two endpoint responses.
let mustNotCallAlternate=false;
const legacyWorking=createChaoxingSchoolAdapter({fetchImpl:async raw=>{
 const path=new URL(raw).pathname;
 if(path==='/base')return response(profile);
 if(path==='/mooc2-ans/visit/courselistdata')return response(courseHtml);
 if(path==='/visit/stucoursemiddle')return response(middle);
 if(path==='/mooc2/work/list')return response(workHtml);
 if(path===officialAltPath){mustNotCallAlternate=true;throw Error('wrong route');}
 throw Error('unexpected route '+path);
}});
const workingReader=await legacyWorking.forSession({session,userId:'owner1'});
await workingReader.verifySession();
assert.equal((await workingReader.readHomeworkList(courseKeys)).items.length,1);
assert.equal(mustNotCallAlternate,false);

// A login redirect is an authorization failure and must never cause a second
// work-list probe under the same expired session, even if a fake fixture says
// a newer route would contain assignments.
let authAltRequested=0;
const expiredLegacy=createChaoxingSchoolAdapter({fetchImpl:async raw=>{
 const path=new URL(raw).pathname;
 if(path==='/base')return response(profile);
 if(path==='/mooc2-ans/visit/courselistdata')return response(courseHtml);
 if(path==='/visit/stucoursemiddle')return response(middle);
 if(path==='/mooc2/work/list')return loginRedirect();
 if(path===officialAltPath){authAltRequested++;return response(workHtml);}
 if(path==='/login')return response('<html>login</html>');
 throw Error('unexpected route '+path);
}});
const expiredReader=await expiredLegacy.forSession({session,userId:'owner1'});
await expiredReader.verifySession();
await assert.rejects(()=>expiredReader.readHomeworkList(courseKeys),
 e=>e.code==='authorization_failed');
assert.equal(authAltRequested,0);

// Both routes being unusable never creates an empty trusted sync response.
const bothMissing=createChaoxingSchoolAdapter({fetchImpl:async raw=>{
 const path=new URL(raw).pathname;
 if(path==='/base')return response(profile);
 if(path==='/mooc2-ans/visit/courselistdata')return response(courseHtml);
 if(path==='/visit/stucoursemiddle')return response(middle);
 if(path==='/mooc2/work/list'||path===officialAltPath)
  return response('<body>登录页面以外的空壳</body>');
 throw Error('unexpected route '+path);
}});
const missingReader=await bothMissing.forSession({session,userId:'owner1'});
await missingReader.verifySession();
await assert.rejects(()=>missingReader.readHomeworkList(courseKeys),
 e=>e.code==='work_not_available');

// Diagnostic stage codes identify failing official read phases; no token,
// raw HTML or URL may be echoed to the caller.
const stageMiddle=createChaoxingSchoolAdapter({fetchImpl:async raw=>{
 const path=new URL(raw).pathname;
 if(path==='/base')return response(profile);
 if(path==='/mooc2-ans/visit/courselistdata')return response(courseHtml);
 if(path==='/visit/stucoursemiddle')return response('<body>no workEnc</body>');
 throw Error('unexpected route '+path);
}});
const stageMiddleReader=await stageMiddle.forSession({session,userId:'owner1'});
await stageMiddleReader.verifySession();
await assert.rejects(()=>stageMiddleReader.readHomeworkList(courseKeys),e=>
 e.code==='work_not_available'&&e.schoolReadStage==='course_entry');

const stageBothMissing=createChaoxingSchoolAdapter({fetchImpl:async raw=>{
 const path=new URL(raw).pathname;
 if(path==='/base')return response(profile);
 if(path==='/mooc2-ans/visit/courselistdata')return response(courseHtml);
 if(path==='/visit/stucoursemiddle')return response(middle);
 if(path==='/mooc2/work/list'||path==='/mooc-ans/mooc2/work/list')
  return response('<body>无数据</body>');
 throw Error('unexpected route '+path);
}});
const stageBothReader=await stageBothMissing.forSession({session,userId:'owner1'});
await stageBothReader.verifySession();
await assert.rejects(()=>stageBothReader.readHomeworkList(courseKeys),e=>
 e.code==='work_not_available'&&e.schoolReadStage==='list_official');
const stageCount=createChaoxingSchoolAdapter({fetchImpl:async raw=>{
 const path=new URL(raw).pathname;
 if(path==='/base')return response(profile);
 if(path==='/mooc2-ans/visit/courselistdata')return response(courseHtml);
 if(path==='/visit/stucoursemiddle')return response(middle);
 if(path==='/mooc2/work/list'||path==='/mooc-ans/mooc2/work/list')
  return response('<div><div><span>1/2</span></div></div>'+workHtml);
 throw Error('unexpected route '+path);
}});
const stageCountReader=await stageCount.forSession({session,userId:'owner1'});
await stageCountReader.verifySession();
await assert.rejects(()=>stageCountReader.readHomeworkList(courseKeys),e=>
 e.code==='work_list_incomplete'&&e.schoolReadStage==='list_integrity');

// Reference mobile client's scanCourses reads .bottomList li[data] from the
// signed-in owner's course entry. This path does not require workEnc.
const embeddedHtml='<section class="bottomList"><ul>'+
 row('950','Python 程序实验报告一','已完成')+
 row('951','工程制图 第五次作业','待批阅')+
 '</ul></section>';
const embeddedParsed=parseChaoxingWorkList(
 '<li data="https://example.com/bad"><p class="overHidden2">其他页条目</p></li>'+
 embeddedHtml,courseKeys,{embedded:true});
assert.deepEqual(embeddedParsed.items.map(x=>x.workId),['950','951']);
assert.equal(embeddedParsed.totalPages,1);
let embeddedListRequests=0;
const entryOnly=createChaoxingSchoolAdapter({fetchImpl:async raw=>{
 const path=new URL(raw).pathname;
 if(path==='/base')return response(profile);
 if(path==='/mooc2-ans/visit/courselistdata')return response(courseHtml);
 if(path==='/visit/stucoursemiddle')return response(embeddedHtml);
 if(path.includes('/work/list')){embeddedListRequests++;throw Error('unexpected list fetch');}
 throw Error('unexpected route '+path);
}});
const entryOnlyReader=await entryOnly.forSession({session,userId:'owner1'});
await entryOnlyReader.verifySession();
const embeddedStatus=await entryOnlyReader.readHomeworkList(courseKeys);
assert.equal(embeddedStatus.items.length,2);
assert.deepEqual(embeddedStatus.items.map(x=>x.statusCode),['submitted','submitted']);
assert.equal(embeddedStatus.progress,null);
assert.equal(embeddedStatus.items.every(x=>x.platformVerified),true);
assert.equal(embeddedListRequests,0,'entry-page source needs no fabricated workEnc');

// With a signed list route available, prefer its verified result; the
// embedded panel is only a fallback when the official list is inaccessible.
const entryAndList=createChaoxingSchoolAdapter({fetchImpl:async raw=>{
 const path=new URL(raw).pathname;
 if(path==='/base')return response(profile);
 if(path==='/mooc2-ans/visit/courselistdata')return response(courseHtml);
 if(path==='/visit/stucoursemiddle')
  return response(middle+embeddedHtml);
 if(path==='/mooc2/work/list')return response(workHtml);
 if(path==='/mooc-ans/mooc2/work/list')throw Error('alternative route was not needed');
 throw Error('unexpected route '+path);
}});
const entryAndListReader=await entryAndList.forSession({session,userId:'owner1'});
await entryAndListReader.verifySession();
assert.deepEqual((await entryAndListReader.readHomeworkList(courseKeys))
 .items.map(x=>x.workId),['987']);

// No assignment in the owner's verified course panel must not be treated as
// evidence that a failed signed listing is empty.
const fakePanel=createChaoxingSchoolAdapter({fetchImpl:async raw=>{
 const path=new URL(raw).pathname;
 if(path==='/base')return response(profile);
 if(path==='/mooc2-ans/visit/courselistdata')return response(courseHtml);
 if(path==='/visit/stucoursemiddle')
  return response('<section class="bottomList"><p>课程目录</p></section>');
 throw Error('unexpected route '+path);
}});
const fakePanelReader=await fakePanel.forSession({session,userId:'owner1'});
await fakePanelReader.verifySession();
await assert.rejects(()=>fakePanelReader.readHomeworkList(courseKeys),
 e=>e.code==='work_not_available'&&e.schoolReadStage==='course_entry');
console.log('Chaoxing signed-in course-entry embedded homework fallback passed');

console.log('Chaoxing safe course-entry, list-endpoint and count-failure stages passed');

console.log('Chaoxing verified alternate work-list route and completed state fallback passed');

console.log('Chaoxing legacy/modern owner-verified course token variants passed');

console.log('Chaoxing completed-work variants, bounded pagination, strict totals and duplicate guard passed');

console.log('Chaoxing review: login-redirect and challenge-title regressions passed');

// Reference CourseEntry follows the actual official link and its signed query,
// rather than manufacturing a work/list request from workEnc alone.
const actualEntryPath='/mooc-ans/mooc2/work/list';
const actualEntryQuery='courseId=123&classId=456&cpi=789&enc=entryToken&t=1700000000123&customView=owner';
for(const tag of [
 '<a data-url="'+actualEntryPath+'?'+actualEntryQuery+'">作业</a>',
 '<iframe src="https://mooc1.chaoxing.com'+actualEntryPath+'?'+actualEntryQuery+'"></iframe>',
 '<a href="'+actualEntryPath+'?'+actualEntryQuery+'">作业</a>'
]) {
 const seen=[];
 const reader=await createChaoxingSchoolAdapter({fetchImpl:async raw=>{
  const u=new URL(raw);seen.push(u);
  if(u.pathname==='/base')return response(profile);
  if(u.pathname==='/mooc2-ans/visit/courselistdata')return response(courseHtml);
  if(u.pathname==='/visit/stucoursemiddle')return response(tag);
  if(u.pathname===actualEntryPath){
   assert.equal(u.searchParams.get('enc'),'entryToken');
   assert.equal(u.searchParams.get('t'),'1700000000123');
   assert.equal(u.searchParams.get('customView'),'owner');
   return response(workHtml);
  }
  throw Error('must use course-owned entry, not synthesized legacy path');
 }}).forSession({session:structuredClone(session),userId:'owner1'});
 await reader.verifySession();
 const result=await reader.readHomeworkList(courseKeys);
 assert.equal(result.items[0].workId,'987');
 assert.equal(seen.filter(u=>u.pathname.includes('/work/list')).length,1);
 assert.ok(!JSON.stringify(result).includes('entryToken'));
}
console.log('Chaoxing reference course-owned work entry regression passed');

const {parseChaoxingCourseWorkEntry}=await import('../lib/academic-chaoxing-work-discovery.mjs');
for(const url of [
 'https://evil.example/mooc2/work/list?'+actualEntryQuery,
 'https://mooc1.chaoxing.com:444/mooc2/work/list?'+actualEntryQuery,
 'https://mooc1.chaoxing.com/mooc2/work/list?'+actualEntryQuery+'&courseId=999',
 'https://mooc1.chaoxing.com/mooc2/work/list?'+actualEntryQuery.replace('classId=456','classId=999'),
 'https://mooc1.chaoxing.com/mooc2/work/list?'+actualEntryQuery+'&enc=other',
])assert.throws(()=>parseChaoxingCourseWorkEntry('<a data-url="'+url+'">作业</a>',courseKeys),
 e=>['invalid_response','authorization_failed'].includes(e.code));
const filledEntry=parseChaoxingCourseWorkEntry(middle+'<input id="t" value="1700000000123">'+
 '<a data-url="/mooc2/work/list?courseId=123&classId=456&cpi=789&enc=old">作业</a>',courseKeys);
assert.equal(new URL(filledEntry).searchParams.get('enc'),'signedWork');
assert.equal(new URL(filledEntry).searchParams.get('t'),'1700000000123');
assert.throws(()=>parseChaoxingCourseWorkEntry('<input id="courseid" value="999">'+
 '<a data-url="'+actualEntryPath+'?'+actualEntryQuery+'">作业</a>',courseKeys),
 e=>e.code==='authorization_failed');
console.log('Chaoxing actual-entry ownership, URL and hidden-identity guards passed');
