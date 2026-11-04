/* Synthetic CAS transcript for the school-controlled login path.
 * No real credentials, student records, HTTP requests or CAS tickets.
 */
import assert from 'node:assert/strict';
import {makeNcwuSecondClassServerAdapter,inspectCasForm,NcwuCasError,observeOfficialWeChatEntry,summarizeSchoolSubmitFunctionScript}
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
const calls=[];
const student='SYNTHETIC_STUDENT';
let receivedCredential=false,activityReads=0;
async function fakeSchool(url,options){
 const u=new URL(url),method=options.method;
 calls.push({host:u.hostname,path:u.pathname,method,hasCookie:!!options.headers.cookie});
 assert.equal(u.protocol,'https:');
 if(u.hostname==='dekt.ncwu.edu.cn'&&u.pathname==='/')
  return response(302,'',{location:'/rump_frontend/login/?next=%2Fsys',
   'set-cookie':'ncwu_synthetic_cookie=dkt-session; Domain=ncwu.edu.cn; Path=/; Secure'});
 if(u.hostname==='dekt.ncwu.edu.cn'&&u.pathname==='/rump_frontend/login/')
  return response(200,'<a href="'+casUrl.replace(/&/g,'&amp;')+'">学校认证</a>');
 if(u.hostname==='authserver.ncwu.edu.cn'&&u.pathname==='/authserver/login'&&method==='GET')
  return response(200,markup,{'set-cookie':'school_auth=auth-opaque; Path=/authserver/; Secure'});
 if(u.hostname==='authserver.ncwu.edu.cn'&&u.pathname==='/authserver/login'&&method==='POST'){
  assert.ok(options.headers.cookie?.includes('school_auth=auth-opaque'));
  const data=new URLSearchParams(options.body);
  assert.deepEqual(Object.fromEntries(data),{
   execution:'e1s1',_eventId:'submit',username:'SYNTHETIC_ACCOUNT',password:'EPHEMERAL_PASSWORD'
  });
  receivedCredential=true;
  return response(302,'',{location:service+'?ticket=ONLY_IN_SCHOOL_REQUEST'});
 }
 if(u.hostname==='sec.ncwu.edu.cn'&&u.pathname==='/rump_frontend/loginFromCas/')
  return response(302,'',{location:school+'/sys',
   'set-cookie':'school_ss=personal; Domain=ncwu.edu.cn; Path=/; Secure'});
 if(u.hostname==='dekt.ncwu.edu.cn'&&u.pathname==='/sys')
  return response(200,'<main>School Account</main>');
 if(u.hostname==='dekt.ncwu.edu.cn'&&method==='POST'){
  assert.ok(options.headers.cookie?.includes('school_ss=personal'));
  assert.equal(options.headers.origin,school);
  if(u.pathname===NCWU_SECOND_CLASS_READS.scores)
   return response(200,JSON.stringify({data:[{student_code:student,
    school_term_index:100,score_value:4.5,score_plus:2,
    activity_type1_score:1.5}],startIndex:0,totalPage:0,totalSize:1}));
  if(u.pathname===NCWU_SECOND_CLASS_READS.scoreTypes)
   return response(200,JSON.stringify([{code:'1',name:'志愿服务'}]));
  if(u.pathname===NCWU_SECOND_CLASS_READS.activities){
   activityReads++;
   const data=new URLSearchParams(options.body);
   assert.equal(data.get('page'),'1');
   return response(200,JSON.stringify({data:[{
    id:'synthetic-1',activity_name:'合成活动',student_code:student,
    school_year:'2026-2027',school_term:'1',
    signup_state:'1',verify_state:'1',activity_state:'5',
    score_value:1.5
   }],totalPage:1,totalSize:1,startIndex:1}));
  }
 }
 throw Error('unexpected request '+method+' '+url);
}
const adapter=makeNcwuSecondClassServerAdapter({fetcher:fakeSchool});
const preflight=await adapter.preflight();
assert.deepEqual(preflight,{loginSupported:true,mode:'password_form_verified'});
assert.equal(receivedCredential,false,'preflight must not collect or submit credentials');
assert.equal(observeOfficialWeChatEntry('<p>请使用微信扫码登录</p>'),true);
assert.equal(observeOfficialWeChatEntry('<script>请使用微信扫码登录</script>'),false,
 'script-only text does not prove a visible school QR option');
assert.equal(observeOfficialWeChatEntry('<div>学校官方密码登录</div>'),false);
// Discover the visible school option without QR token, secret, callback or cookies.
const qrEntry=makeNcwuSecondClassServerAdapter({fetcher:async(url,options)=>{
 if(new URL(url).hostname==='authserver.ncwu.edu.cn'&&options.method==='GET')
  return response(200,'<section>请使用微信扫码登录</section>'+markup);
 return fakeSchool(url,options);
}});
assert.deepEqual(await qrEntry.preflight(),{
 loginSupported:true,mode:'password_form_verified',officialWeChatEntryObserved:true
});
const qrWithChallenge=makeNcwuSecondClassServerAdapter({fetcher:async(url,options)=>{
 if(new URL(url).hostname==='authserver.ncwu.edu.cn'&&options.method==='GET')
  return response(200,'<p>请使用微信扫码登录</p>'+markup.replace('</form>',
   '<input name="captcha" type="text"></form>'));
 return fakeSchool(url,options);
}});
const qrChallengeStatus=await qrWithChallenge.preflight();
assert.equal(qrChallengeStatus.loginSupported,false);
assert.equal(qrChallengeStatus.mode,'interactive_login_required');
assert.equal(qrChallengeStatus.reason,'interactive_challenge_required');
assert.equal(qrChallengeStatus.officialWeChatEntryObserved,true);
assert.equal(qrChallengeStatus.challengeEvidence?.passwordFormSeen,true);
assert.equal(qrChallengeStatus.challengeEvidence?.challengeControlSeen,true);
const lateScript=makeNcwuSecondClassServerAdapter({fetcher:async(url,options)=>{
 const u=new URL(url);
 if(u.hostname==='authserver.ncwu.edu.cn'&&u.pathname==='/authserver/login')
  return response(200,markup.replace('<form ', '<form onsubmit="return doPcMobilesubmit()" ')+
   Array.from({length:10},(_,i)=>'<script src="/authserver/js/public-'+i+'.js"></script>').join(''));
 if(u.hostname==='authserver.ncwu.edu.cn'&&u.pathname.endsWith('.js')){
  assert.equal(options.headers.cookie,undefined,'public script inspection must not send session cookies');
  return response(200,u.pathname.endsWith('public-9.js')?normalFunction:'/* vendor */');
 }
 return fakeSchool(url,options);
}});
const lateEvidence=await lateScript.preflight({inspectScripts:true});
assert.equal(lateEvidence.challengeEvidence.schoolScriptDefinitionSeen,true,
 'handler in the tenth public script must not be silently excluded');
assert.equal(lateEvidence.loginSupported,false,'script discovery alone never authorizes login');


const connected=await adapter.login({username:'SYNTHETIC_ACCOUNT',password:'EPHEMERAL_PASSWORD'});
assert.equal(receivedCredential,true);
assert.equal(connected.session.studentCode,student);
assert.ok(connected.session.cookies?.length>0);
assert.ok(!JSON.stringify(connected.session).includes('EPHEMERAL_PASSWORD'));
assert.ok(!JSON.stringify(connected.session).includes('SYNTHETIC_ACCOUNT'));
const read=await adapter.forSession({session:connected.session,userId:'owner_synthetic'});
const verified=await read.verifySession();
assert.equal(verified.authorized,true);
assert.equal(verified.principalBoundTo,'owner_synthetic');
const page=await read.readPage({cursor:null,session:verified.session});
assert.equal(page.activities[0].points,1.5);
const score=await read.readOfficialScores();
assert.equal(score.aggregate.qualityScore,4.5);
assert.equal(score.aggregate.volunteerHours,2);
assert.ok(activityReads>0);
assert.ok(calls.every(x=>['dekt.ncwu.edu.cn','authserver.ncwu.edu.cn',
 'sec.ncwu.edu.cn'].includes(x.host)));
assert.ok(calls.some(x=>x.host==='dekt.ncwu.edu.cn'&&x.path===NCWU_SECOND_CLASS_READS.scores));

// A school-side Set-Cookie during a personal read must be returned to the
// encrypted owner session, never silently discarded after successful sync.
const rotating=makeNcwuSecondClassServerAdapter({fetcher:async(url,options)=>{
 const res=await fakeSchool(url,options);
 if(new URL(url).pathname===NCWU_SECOND_CLASS_READS.activities&&options.method==='POST')
  return response(200,await res.text(),{
   'set-cookie':'school_ss=post-read; Domain=ncwu.edu.cn; Path=/; Secure'
  });
 return res;
}});
const loginBeforeRotation=await rotating.login({
 username:'SYNTHETIC_ACCOUNT',password:'EPHEMERAL_PASSWORD'
});
const rotatingReader=await rotating.forSession({
 session:loginBeforeRotation.session,userId:'owner_synthetic'
});
const oldCookie=loginBeforeRotation.session.cookies.find(c=>c.name==='school_ss')?.value;
assert.equal(oldCookie,'personal');
await rotatingReader.verifySession();
await rotatingReader.readPage({cursor:null,session:{studentCode:student}});
const rotatedSession=rotatingReader.getRefreshedSession();
assert.equal(rotatedSession.studentCode,student,'rotation cannot alter the student principal');
assert.equal(rotatedSession.cookies.find(c=>c.name==='school_ss')?.value,'post-read');
assert.equal(loginBeforeRotation.session.cookies.find(c=>c.name==='school_ss')?.value,'personal',
 'the original input session remains unchanged');

const unsafe=makeNcwuSecondClassServerAdapter({fetcher:async()=>response(302,'',{
 location:'https://outsider.example/capture?secret=SECRET'
})});
assert.deepEqual(await unsafe.preflight(),{loginSupported:false,mode:'interactive_login_required',
 reason:'unrecognized_auth_flow'});
await assert.rejects(()=>unsafe.login({username:'u',password:'p'}),
 e=>e.code==='interactive_login_required');
const challenge=makeNcwuSecondClassServerAdapter({fetcher:async(url,opts)=>{
 const u=new URL(url);
 if(u.pathname==='/')return response(302,'',{location:'/rump_frontend/login/'});
 if(u.pathname==='/rump_frontend/login/')
  return response(200,'<a href="'+casUrl+'">认证</a>');
 if(u.hostname==='authserver.ncwu.edu.cn')return response(200,
  markup.replace('</form>','<input name="captcha" type="text"></form>'));
 throw Error('unexpected challenge flow');
}});
const challengeStatus=await challenge.preflight();
assert.equal(challengeStatus.loginSupported,false);
assert.equal(challengeStatus.mode,'interactive_login_required');
assert.equal(challengeStatus.reason,'interactive_challenge_required');
assert.equal(challengeStatus.challengeEvidence?.challengeControlSeen,true);
assert.throws(()=>inspectCasForm('<script>encryptPassword(raw)</script>'+markup,casUrl),
 e=>e.code==='interactive_login_required');
const domainScope=makeNcwuSecondClassServerAdapter({fetcher:async(url,options)=>{
 if(new URL(url).hostname==='dekt.ncwu.edu.cn'&&new URL(url).pathname==='/')
  return response(302,'',{location:'/rump_frontend/login/',
   'set-cookie':'school_auth=spoof; Domain=authserver.ncwu.edu.cn; Path=/; Secure'});
 if(new URL(url).hostname==='authserver.ncwu.edu.cn')
  assert.ok(!options.headers.cookie?.includes('school_auth=spoof'));
 return fakeSchool(url,options);
}});
assert.equal((await domainScope.preflight()).loginSupported,true);

// School service may expose a static redirect in HTML instead of an anchor.
// Follow only the already allowlisted CAS endpoint and exact service.
for(const mode of ['meta','literal_location']){
 let inspected=false;
 const alternate=makeNcwuSecondClassServerAdapter({fetcher:async(url,options)=>{
  const u=new URL(url);
  if(u.hostname==='dekt.ncwu.edu.cn'&&u.pathname==='/rump_frontend/login/'){
   inspected=true;
   return response(200,mode==='meta'?
    '<meta http-equiv="refresh" content="0;url='+casUrl.replace(/&/g,'&amp;')+'">':
    '<script>window.location.replace("'+casUrl+'");</script>');
  }
  return fakeSchool(url,options);
 }});
 assert.deepEqual(await alternate.preflight(),{loginSupported:true,mode:'password_form_verified'});
 assert.equal(inspected,true);
}
const rejectedHandoff=makeNcwuSecondClassServerAdapter({fetcher:async(url,options)=>{
 const u=new URL(url);
 if(u.hostname==='dekt.ncwu.edu.cn'&&u.pathname==='/rump_frontend/login/')
  return response(200,'<meta http-equiv="refresh" content="0;url=https://outsider.example/capture?token=NOT_SENT">');
 return fakeSchool(url,options);
}});
assert.deepEqual(await rejectedHandoff.preflight(),{loginSupported:false,mode:'interactive_login_required',
 reason:'cas_handoff_unrecognized'});
const transforms=makeNcwuSecondClassServerAdapter({fetcher:async(url,options)=>{
 if(new URL(url).hostname==='authserver.ncwu.edu.cn'&&options.method==='GET')
  return response(200,'<script>encryptPassword(raw)</script>'+markup);
 return fakeSchool(url,options);
}});
assert.deepEqual(await transforms.preflight(),{loginSupported:false,mode:'interactive_login_required',
 reason:'password_transform_required'});
const schoolDown=makeNcwuSecondClassServerAdapter({fetcher:async()=>{throw Error('school failed');}});
assert.deepEqual(await schoolDown.preflight(),{loginSupported:false,mode:'upstream_unavailable',
 reason:'school_unreachable'});

let postAttempts=0;
const noReplay=makeNcwuSecondClassServerAdapter({fetcher:async(url,options)=>{
 if(new URL(url).hostname==='authserver.ncwu.edu.cn'&&options.method==='POST'){
  postAttempts++;
  return response(307,'',{location:'https://dekt.ncwu.edu.cn/sys'});
 }
 return fakeSchool(url,options);
}});
await assert.rejects(()=>noReplay.login({username:'SYNTHETIC_ACCOUNT',
 password:'EPHEMERAL_PASSWORD'}), e=>e.code==='interactive_login_required');
assert.equal(postAttempts,1);
// The exact school-owned CAS callback can respond 200 HTML, not only 302.
let htmlCallbackPasswordPosts=0;
const casHtmlLanding=makeNcwuSecondClassServerAdapter({fetcher:async(url,options)=>{
 const u=new URL(url);
 if(u.hostname==='authserver.ncwu.edu.cn'&&options.method==='POST')
  htmlCallbackPasswordPosts++;
 if(u.hostname==='sec.ncwu.edu.cn'&&u.pathname==='/rump_frontend/loginFromCas/')
  return response(200,'<main>School-owned service</main>',{
   'set-cookie':'school_ss=personal; Domain=ncwu.edu.cn; Path=/; Secure'
  });
 if(u.hostname==='dekt.ncwu.edu.cn'&&u.pathname==='/'&&
    options.headers.cookie?.includes('school_ss=personal'))
  return response(302,'',{location:school+'/sys'});
 return fakeSchool(url,options);
}});
const verifiedHtmlCallback=await casHtmlLanding.login({
 username:'SYNTHETIC_ACCOUNT',password:'EPHEMERAL_PASSWORD'
});
assert.equal(verifiedHtmlCallback.session.studentCode,student);
assert.equal(htmlCallbackPasswordPosts,1,'password POST must never be replayed');
// A school-branded callback page WITHOUT a verified personal read is not
// permission to mark the school account connected.
const emptyCallback=makeNcwuSecondClassServerAdapter({fetcher:async(url,options)=>{
 const u=new URL(url);
 if(u.hostname==='sec.ncwu.edu.cn'&&u.pathname==='/rump_frontend/loginFromCas/')
  return response(200,'<main>School-owned service</main>');
 if(u.hostname==='dekt.ncwu.edu.cn'&&u.pathname===NCWU_SECOND_CLASS_READS.scores)
  return response(200,'<html>Login required</html>');
 return fakeSchool(url,options);
}});
await assert.rejects(()=>emptyCallback.login({
 username:'SYNTHETIC_ACCOUNT',password:'EPHEMERAL_PASSWORD'
}),e=>e.code==='authorization_failed'&&e.failureStage==='personal_data');
// Distinguish a school CAS login that never returns to the service,
// a rejected personal business endpoint, and a non-unique student identity.
// These synthetic tests never use school credentials or external requests.
const casNoReturn=makeNcwuSecondClassServerAdapter({fetcher:async(url,options)=>{
 if(new URL(url).hostname==='authserver.ncwu.edu.cn'&&options.method==='POST')
  return response(200,markup);
 return fakeSchool(url,options);
}});
await assert.rejects(()=>casNoReturn.login({username:'SYNTHETIC_ACCOUNT',
 password:'EPHEMERAL_PASSWORD'}),e=>e.code==='authorization_failed'&&e.failureStage==='cas_return');
const personalDataRejected=makeNcwuSecondClassServerAdapter({fetcher:async(url,options)=>{
 if(new URL(url).pathname===NCWU_SECOND_CLASS_READS.scores&&options.method==='POST')
  return response(200,'<html>School login required</html>');
 return fakeSchool(url,options);
}});
await assert.rejects(()=>personalDataRejected.login({username:'SYNTHETIC_ACCOUNT',
 password:'EPHEMERAL_PASSWORD'}),e=>e.code==='authorization_failed'&&e.failureStage==='personal_data');
const identityAmbiguous=makeNcwuSecondClassServerAdapter({fetcher:async(url,options)=>{
 if(new URL(url).pathname===NCWU_SECOND_CLASS_READS.scores&&options.method==='POST')
  return response(200,JSON.stringify({data:[
   {student_code:'SYNTHETIC_ONE'},{student_code:'SYNTHETIC_TWO'}]}));
 return fakeSchool(url,options);
}});
await assert.rejects(()=>identityAmbiguous.login({username:'SYNTHETIC_ACCOUNT',
 password:'EPHEMERAL_PASSWORD'}),e=>e.code==='authorization_failed'&&e.failureStage==='identity_evidence');
console.log('M23 NCWU CAS transport: synthetics, identity, cookie scope, preflight, read-only business endpoints, MFA and redirect refusal passed');

