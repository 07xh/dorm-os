import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import {validateSnapshot} from '../lib/academic-contract.mjs';
import {describeSecondClassAuthorization,NCWU_SECOND_CLASS_SSO}
 from '../lib/academic-ncwu-secondclass-sso.mjs';
import {onRequestPost as loginPost} from '../functions/api/v1/connections/[provider]/login.js';

assert.equal(NCWU_SECOND_CLASS_SSO.entryUrl,'https://dekt.ncwu.edu.cn/');
assert.equal(NCWU_SECOND_CLASS_SSO.schoolOwnedService,
 'https://sec.ncwu.edu.cn/rump_frontend/loginFromCas/');
assert.equal(NCWU_SECOND_CLASS_SSO.thirdPartyCallbackVerified,false);
const flow=describeSecondClassAuthorization({sessionState:'active',verifiedSession:false});
assert.equal(flow.loginMode,'official_sso');
assert.equal(flow.xiaoxuAuthorization,'not_verified',
 'university CAS login cannot silently grant Xiaoxu access');
assert.equal(describeSecondClassAuthorization({sessionState:'active',verifiedSession:true})
 .xiaoxuAuthorization,'verified_session');
assert.equal(describeSecondClassAuthorization({sessionState:'expired',verifiedSession:true})
 .xiaoxuAuthorization,'not_verified');

const script=readFileSync(new URL('../public/xiaoxu-second-class-export.user.js',import.meta.url),'utf8');
const sandbox={};
vm.runInNewContext(script,sandbox,{timeout:1000});
const collector=sandbox.XiaoxuSecondClassLocalExport;
assert.ok(collector);
assert.deepEqual(Object.values(collector.paths).sort().join(';'),
 ['/sys/stu/center/signupactivity/list','/sys/stu/score/list','/sys/stu/score/types'].sort().join(';'));
const student='SYNTHETIC_OWNER_CODE';
const rawActivities=[
 {id:'signup-1',activity_name:'合成活动一',activity_start:'2026-11-03 09:00:00',
  student_code:student,school_year:'2026-2027',school_term:'1',
  signup_state:'1',verify_state:'1',activity_state:'5',score_value:1.25,
  student_name:'PRIVATE_SHOULD_NOT_EXPORT',cookie:'COOKIE_SHOULD_NOT_EXPORT'},
 {id:'signup-2',activity_name:'合成活动二',activity_start:'2026-11-02 11:00:00',
  student_code:student,school_year:'2026-2027',school_term:'1',
  signup_state:'1',verify_state:'1',activity_state:'4',score_value:8}
];
const score=(studentCode=student)=>({
 data:[{school_term_index:100,student_code:studentCode,score_value:5.5,
  score_plus:2.25,student_name:'PRIVATE_SHOULD_NOT_EXPORT'}],
 totalPage:0,startIndex:0,totalSize:1
});
const calls=[];
function reader({otherScoreStudent=false,partial=false,switchedPage=false}={}){
 return async(path,form)=>{
  calls.push({path,form});
  if(path==='/sys/stu/score/types')return [{code:'1',name:'志愿服务'}];
  if(path==='/sys/stu/score/list')return score(otherScoreStudent?'DIFFERENT_STUDENT':student);
  if(path==='/sys/stu/center/signupactivity/list'){
   if(form.page==='1')return {data:[rawActivities[0]],startIndex:1,totalPage:2,
    totalSize:partial?3:2};
   return {data:[{...rawActivities[1],...(switchedPage?{student_code:'DIFFERENT_STUDENT'}:{})}],
    startIndex:2,totalPage:2,totalSize:partial?3:2};
  }
  throw Error('unexpected endpoint '+path);
 };
}
const at='2026-11-03T08:00:00.000Z';
const data=await collector.collect(reader(),()=>at);
const validated=validateSnapshot(data.snapshot,Date.parse(at));
assert.equal(validated.kind,'second-class');
assert.equal(validated.source,'second_class');
assert.equal(validated.records.length,2);
assert.equal(validated.records[0].settlementStatus,'settled');
assert.equal(validated.records[0].points,1.25);
assert.equal(validated.records[1].settlementStatus,'unsettled');
assert.ok(!Object.hasOwn(validated.records[1],'points'),'unsettled score cannot be included');
assert.equal(data.schoolReportedPreview.qualityScore,5.5);
assert.equal(data.schoolReportedPreview.volunteerHours,2.25);
assert.equal(data.schoolReportedPreview.origin,'unverified_local_browser_export');
const serial=JSON.stringify(data);
for(const sensitive of [student,'PRIVATE_SHOULD_NOT_EXPORT','COOKIE_SHOULD_NOT_EXPORT'])
 assert.ok(!serial.includes(sensitive),'export must never contain personal identity or credentials');
assert.deepEqual(calls.map(x=>x.path),[
 '/sys/stu/score/types','/sys/stu/center/signupactivity/list',
 '/sys/stu/center/signupactivity/list','/sys/stu/score/list'
]);
await assert.rejects(()=>collector.collect(reader({otherScoreStudent:true}),()=>at),
 /身份不一致/);
await assert.rejects(()=>collector.collect(reader({switchedPage:true}),()=>at),
 /身份在分页间发生变化/);
await assert.rejects(()=>collector.collect(reader({partial:true}),()=>at),
 /分页条数不完整/);
const client=readFileSync(new URL('../public/xiaoxu-academic-client.js',import.meta.url),'utf8');
assert.match(client,/school_cas_external/);
assert.match(client,/data-xu-second-class-import/);
assert.match(client,/\/api\/v1\/verified\/snapshot/);
assert.match(client,/个人导入.*不代表校序取得官方自动同步授权/);
assert.match(client,/body:JSON\.stringify\(snapshot\)/,
 'only whitelisted snapshot, not local score aggregate, must be uploaded');
const noAuth=await loginPost({request:new Request('https://test.example/api/v1/connections/second_class/login',{
 method:'POST',headers:{origin:'https://test.example','content-type':'application/json'},
 body:JSON.stringify({username:'SYNTHETIC_ACCOUNT',password:'DO_NOT_SEND'})}),
 env:{},params:{provider:'second_class'}});
assert.equal(noAuth.status,503,'school account endpoint must not bypass Academic OS authentication');
console.log('M19 official CAS + no-credential exporter: real endpoint shapes, no PII, incomplete data rejected, nondelegated SSO passed');
