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
const questionHtml='<html><body><form id="submitForm">'+
 '<div class="questionLi"><h3 class="mark_name">单选题：列表索引从几开始？</h3>'+
 '<div class="num_option">A. 0</div><div class="num_option">B. 1</div>'+
 '<div class="answerBg">隐藏答案</div></div></form></body></html>';
assert.deepEqual(parseChaoxingCourseList(courseHtml),[
 {courseId:'123',classId:'456',cpi:'789',title:'Python 基础'}
]);
assert.deepEqual(parseChaoxingWorkCredentials(middle),
 {workEnc:'signedWork',pageEnc:'signedPage',openc:'openKey'});
assert.deepEqual(parseChaoxingWorkList(workHtml,{courseId:'123',classId:'456'}).items.map(x=>x.workId),['987']);
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
assert.equal(list.items[0].workId,'987');
assert.equal(list.items[0].statusCode,'todo');
assert.equal(list.items[0].platformVerified,true);
for(const [label,code] of [
 ['未提交','todo'],['待评阅','submitted'],['已提交','submitted'],
 ['已评分','graded'],['退回重做','returned'],['','unknown'],['待开放','unknown']
])assert.equal(classifyChaoxingWorkStatus(label),code);
assert.ok(!JSON.stringify(list).includes('signedTask'));
const detail=await read.readHomeworkDetail({courseId:'123',classId:'456',cpi:'789',workId:'987'});
assert.equal(detail.questionsAvailable,true);
assert.equal(detail.statusCode,'todo');
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
