/* Synthetic CAS transcript for the school-controlled login path.
 * No real credentials, student records, HTTP requests or CAS tickets.
 */
import assert from 'node:assert/strict';
import {makeNcwuSecondClassServerAdapter,inspectCasForm,NcwuCasError,observeOfficialWeChatEntry,summarizeSchoolSubmitFunctionScript,makeSecondClassSessionJar}
 from '../lib/academic-ncwu-secondclass-server.mjs';
import {NCWU_SECOND_CLASS_READS} from '../lib/academic-ncwu-second-class.mjs';
const school='https://dekt.ncwu.edu.cn';
const service='https://sec.ncwu.edu.cn/rump_frontend/loginFromCas/';
const casUrl='https://authserver.ncwu.edu.cn/authserver/login?service='+encodeURIComponent(service);
const markup='<form method="POST" action="'+casUrl+'">'+
 '<input name="username" type="text">'+
 '<input name="password" type="password">'+
 '<input name="execution" type="hidden" value="e1s1">'+
 '<input name="_eventId" type="hidden" value="submit"></form>';
const response=(status,body='',headers={})=>new Response(body,{status,headers});
const handler='doPcMobilesubmit';
const normalFunction='function doPcMobilesubmit() { document.forms[0].submit(); }';
const schoolFunction=summarizeSchoolSubmitFunctionScript(normalFunction,handler);
assert.equal(schoolFunction.definitionSeen,true,
 'the actual official handler name must be matched as a function declaration');
assert.equal(schoolFunction.formSubmissionSeen,true);
assert.equal(summarizeSchoolSubmitFunctionScript('doPcMobilesubmit();',handler).definitionSeen,false,
 'a bare call is not proof the handler is defined');
assert.equal(summarizeSchoolSubmitFunctionScript(
 'window.doPcMobilesubmit = function() { return true; }',handler).definitionSeen,true);
assert.equal(summarizeSchoolSubmitFunctionScript(
 'const doPcMobilesubmit = function() { return true; }',handler).definitionSeen,true);
assert.equal(summarizeSchoolSubmitFunctionScript(
 'function unrelatedHandler() { return true; }',handler).definitionSeen,false);

assert.deepEqual(inspectCasForm(markup,casUrl).hidden,{execution:'e1s1',_eventId:'submit'});
// The real CAS public probe returned hidden_length for a school-owned hidden
// token. Long opaque fields are accepted only within bounded per-field and
// total limits; no values are logged or returned by public diagnostics.
const longExecution='s'.repeat(4096);
assert.equal(inspectCasForm(markup.replace('value="e1s1"',
 'value="'+longExecution+'"'),casUrl).hidden.execution,longExecution);
assert.equal(inspectCasForm(markup.replace('value="e1s1"',
 'value="'+'s'.repeat(8192)+'"'),casUrl).hidden.execution.length,8192);
const oversizedAggregate=markup.replace('value="e1s1"',
 'value="'+'s'.repeat(8192)+'"').replace('</form>',
 '<input name="schoolToken" type="hidden" value="'+'t'.repeat(8192)+'"></form>');
for(const [html,stage] of [
 [markup.replace('name="username"','name="username" class="a" class="b"'),'input_attributes'],
 [markup.replace('value="e1s1"','value="'+'s'.repeat(8193)+'"'),'hidden_length'],
 [oversizedAggregate,'hidden_length'],
 [markup.replace('action="'+casUrl+'"','action="/authserver/login;jsessionid=SYNTHETIC"'),'action_url']
]){
 assert.throws(()=>inspectCasForm(html,casUrl),e=>e.failureStage===stage);
}
// The returned CAS form can omit the service query even though the login
// page was fetched with the exact trusted school-owned service.
const serviceLessForm=markup.replace('action="'+casUrl+'"','action="/authserver/login"');
assert.equal(inspectCasForm(serviceLessForm,casUrl).action,casUrl);
assert.throws(()=>inspectCasForm(serviceLessForm,
 'https://authserver.ncwu.edu.cn/authserver/login?service=https://untrusted.example/'),
 e=>e.code==='interactive_login_required'&&e.reason==='unexpected_cas_form_action');
assert.throws(()=>inspectCasForm(markup.replace('action="'+casUrl+'"',
 'action="/authserver/login?service=https%3A%2F%2Funtrusted.example%2F"'),casUrl),
 e=>e.code==='interactive_login_required'&&e.reason==='unexpected_cas_form_action');
// The official CAS page offers optional alternative authentication widgets.
// These must not veto an otherwise safe, self-contained ordinary password form.
const optionalMethods='<section aria-label="其他登录方式">短信登录 · 图形验证码 · 滑块验证 · 人脸登录'+
 '<input name="smsCode"></section><script>function showAlternateCaptcha() {}</script>';
assert.deepEqual(inspectCasForm(markup+optionalMethods,casUrl).hidden,
 {execution:'e1s1',_eventId:'submit'});
// A challenge INSIDE the submitted form is mandatory, and remains blocked.
assert.throws(()=>inspectCasForm(markup.replace('</form>',
 '<input name="geetest_challenge" type="hidden" value="required"></form>'),casUrl),
 e=>e.code==='interactive_login_required');
assert.throws(()=>inspectCasForm(markup.replace('</form>','<input name="captcha" type="text"></form>'),casUrl),
 e=>e instanceof NcwuCasError&&e.code==='interactive_login_required');
assert.throws(()=>inspectCasForm(markup.replace('sec.ncwu.edu.cn','thirdparty.invalid'),casUrl),
 e=>e.code==='interactive_login_required');
// The retired adapter must reject all credential inputs before any request.
let networkReads=0;
const retired=makeNcwuSecondClassServerAdapter({fetcher:async()=>{
 networkReads++;throw Error('must not contact school');
}});
for(const input of [{username:'SYNTHETIC_ACCOUNT',password:'DO_NOT_SEND'},{}])
 await assert.rejects(()=>retired.login(input),
  e=>e.code==='interactive_login_required'&&e.reason==='official_app_authorization_required');
assert.equal(networkReads,0);

// Existing encrypted sessions remain read-only and independently identity-bound.
const student='SYNTHETIC_STUDENT';
const original={studentCode:student,cookies:[{
 name:'school_ss',value:'synthetic-old',domain:'ncwu.edu.cn',path:'/',hostOnly:false
}]};
const calls=[];
async function readonlySchool(url,options){
 const u=new URL(url);calls.push({host:u.hostname,path:u.pathname,method:options.method});
 if(u.pathname==='/'&&options.method==='GET')return response(302,'',{location:casUrl});
 if(u.hostname==='authserver.ncwu.edu.cn'&&options.method==='GET')return response(200,markup);
 assert.equal(u.hostname,'dekt.ncwu.edu.cn');
 assert.equal(options.method,'POST');
 assert.ok(Object.values(NCWU_SECOND_CLASS_READS).includes(u.pathname));
 assert.equal(new URLSearchParams(options.body).has('password'),false);
 assert.ok(options.headers.cookie?.includes('school_ss='));
 if(u.pathname===NCWU_SECOND_CLASS_READS.scores)
  return response(200,JSON.stringify({data:[{student_code:student,
   school_term_index:100,score_value:4.5,score_plus:2,activity_type1_score:1.5}],
   startIndex:0,totalPage:0,totalSize:1}));
 if(u.pathname===NCWU_SECOND_CLASS_READS.scoreTypes)
  return response(200,JSON.stringify([{code:'1',name:'志愿服务'}]));
 if(u.pathname===NCWU_SECOND_CLASS_READS.activities)
  return response(200,JSON.stringify({data:[{id:'synthetic-1',activity_name:'合成活动',
   student_code:student,school_year:'2026-2027',school_term:'1',signup_state:'1',
   verify_state:'1',activity_state:'5',score_value:1.5}],totalPage:1,totalSize:1,startIndex:1}),
   {'set-cookie':'school_ss=synthetic-rotated; Domain=ncwu.edu.cn; Path=/; Secure'});
 throw Error('unexpected readonly endpoint');
}
const adapter=makeNcwuSecondClassServerAdapter({fetcher:readonlySchool});
const reader=await adapter.forSession({session:original,userId:'synthetic-owner'});
const verified=await reader.verifySession();
assert.equal(verified.authorized,true);
assert.equal(verified.principalBoundTo,'synthetic-owner');
const page=await reader.readPage({cursor:null,session:verified.session});
assert.equal(page.activities[0].points,1.5);
const scores=await reader.readOfficialScores();
assert.equal(scores.aggregate.qualityScore,4.5);
assert.equal(scores.aggregate.volunteerHours,2);
assert.equal(reader.getRefreshedSession().cookies.find(c=>c.name==='school_ss').value,'synthetic-rotated');
assert.equal(original.cookies[0].value,'synthetic-old','a read cannot mutate the stored input snapshot');
assert.ok(calls.every(c=>c.host==='dekt.ncwu.edu.cn'),'personal reads never POST credentials to CAS');
const beforeProbe=calls.length;
assert.equal((await adapter.preflight()).loginSupported,true,'public form inspection remains informational only');
assert.ok(calls.slice(beforeProbe).every(c=>c.method==='GET'));
await assert.rejects(()=>adapter.login({username:'SYNTHETIC_ACCOUNT',password:'DO_NOT_SEND'}),
 e=>e.code==='interactive_login_required');

const mismatched=await adapter.forSession({session:{...original,studentCode:'OTHER_SYNTHETIC'},userId:'synthetic-owner'});
await assert.rejects(()=>mismatched.verifySession(),e=>e.code==='authorization_failed');
const expired=makeNcwuSecondClassServerAdapter({fetcher:async()=>response(200,'<html>Login required</html>')});
const expiredReader=await expired.forSession({session:original,userId:'synthetic-owner'});
await assert.rejects(()=>expiredReader.verifySession(),e=>e.code==='authorization_failed');

// Header compatibility and host-only scope still protect saved read sessions.
const jar=makeSecondClassSessionJar();
const cookieLines=['SESSION=synthetic-cas; Path=/authserver/; HttpOnly',
 'school_locale=synthetic; Domain=ncwu.edu.cn; Path=/; Expires=Wed, 21 Oct 2037 07:28:00 GMT'];
jar.merge({headers:{getAll:()=>cookieLines,get:()=>cookieLines.join(', ')}},new URL(casUrl));
assert.ok(jar.header(new URL(casUrl)).includes('SESSION=synthetic-cas'));
assert.ok(jar.header(new URL(casUrl)).includes('school_locale=synthetic'));
assert.ok(!jar.header(new URL(service)).includes('SESSION='));
jar.merge(response(200,'',{'set-cookie':'spoof=x; Domain=authserver.ncwu.edu.cn; Path=/'}),new URL(school));
assert.ok(!jar.header(new URL(casUrl)).includes('spoof='));

for(const html of [markup.replace('</form>','<input name="captcha"></form>'),
 '<script>encryptPassword(raw)</script>'+markup]){
 const challenged=makeNcwuSecondClassServerAdapter({fetcher:async(url)=>
  new URL(url).hostname==='authserver.ncwu.edu.cn'?response(200,html):response(302,'',{location:casUrl})});
 assert.equal((await challenged.preflight()).loginSupported,false);
}
const unsafe=makeNcwuSecondClassServerAdapter({fetcher:async()=>response(302,'',{
 location:'https://outsider.example/'})});
assert.equal((await unsafe.preflight()).loginSupported,false);
console.log('Second-class official-only entry: password transport retired; saved-session identity, reads, cookie rotation and scope passed');
