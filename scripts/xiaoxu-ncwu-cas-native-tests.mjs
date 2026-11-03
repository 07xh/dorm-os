/* Synthetic CAS transcript for the school-controlled login path.
 * No real credentials, student records, HTTP requests or CAS tickets.
 */
import assert from 'node:assert/strict';
import {makeNcwuCasServerAdapter,inspectCasForm,NcwuCasError}
 from '../lib/academic-ncwu-cas-server.mjs';
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
assert.deepEqual(inspectCasForm(markup,casUrl).hidden,{execution:'e1s1',_eventId:'submit'});
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
const adapter=makeNcwuCasServerAdapter({fetcher:fakeSchool});
const preflight=await adapter.preflight();
assert.deepEqual(preflight,{loginSupported:true,mode:'password_form_verified'});
assert.equal(receivedCredential,false,'preflight must not collect or submit credentials');
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

const unsafe=makeNcwuCasServerAdapter({fetcher:async()=>response(302,'',{
 location:'https://outsider.example/capture?secret=SECRET'
})});
assert.deepEqual(await unsafe.preflight(),{loginSupported:false,mode:'interactive_login_required',
 reason:'unrecognized_auth_flow'});
await assert.rejects(()=>unsafe.login({username:'u',password:'p'}),
 e=>e.code==='interactive_login_required');
const challenge=makeNcwuCasServerAdapter({fetcher:async(url,opts)=>{
 const u=new URL(url);
 if(u.pathname==='/')return response(302,'',{location:'/rump_frontend/login/'});
 if(u.pathname==='/rump_frontend/login/')
  return response(200,'<a href="'+casUrl+'">认证</a>');
 if(u.hostname==='authserver.ncwu.edu.cn')return response(200,
  markup.replace('</form>','<input name="captcha" type="text"></form>'));
 throw Error('unexpected challenge flow');
}});
assert.deepEqual(await challenge.preflight(),{loginSupported:false,mode:'interactive_login_required',
 reason:'interactive_challenge_required'});
assert.throws(()=>inspectCasForm('<script>encryptPassword(raw)</script>'+markup,casUrl),
 e=>e.code==='interactive_login_required');
const domainScope=makeNcwuCasServerAdapter({fetcher:async(url,options)=>{
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
 const alternate=makeNcwuCasServerAdapter({fetcher:async(url,options)=>{
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
const rejectedHandoff=makeNcwuCasServerAdapter({fetcher:async(url,options)=>{
 const u=new URL(url);
 if(u.hostname==='dekt.ncwu.edu.cn'&&u.pathname==='/rump_frontend/login/')
  return response(200,'<meta http-equiv="refresh" content="0;url=https://outsider.example/capture?token=NOT_SENT">');
 return fakeSchool(url,options);
}});
assert.deepEqual(await rejectedHandoff.preflight(),{loginSupported:false,mode:'interactive_login_required',
 reason:'cas_handoff_unrecognized'});
const transforms=makeNcwuCasServerAdapter({fetcher:async(url,options)=>{
 if(new URL(url).hostname==='authserver.ncwu.edu.cn'&&options.method==='GET')
  return response(200,'<script>encryptPassword(raw)</script>'+markup);
 return fakeSchool(url,options);
}});
assert.deepEqual(await transforms.preflight(),{loginSupported:false,mode:'interactive_login_required',
 reason:'password_transform_required'});
const schoolDown=makeNcwuCasServerAdapter({fetcher:async()=>{throw Error('school failed');}});
assert.deepEqual(await schoolDown.preflight(),{loginSupported:false,mode:'upstream_unavailable',
 reason:'school_unreachable'});

let postAttempts=0;
const noReplay=makeNcwuCasServerAdapter({fetcher:async(url,options)=>{
 if(new URL(url).hostname==='authserver.ncwu.edu.cn'&&options.method==='POST'){
  postAttempts++;
  return response(307,'',{location:'https://dekt.ncwu.edu.cn/sys'});
 }
 return fakeSchool(url,options);
}});
await assert.rejects(()=>noReplay.login({username:'SYNTHETIC_ACCOUNT',
 password:'EPHEMERAL_PASSWORD'}), e=>e.code==='interactive_login_required');
assert.equal(postAttempts,1);
console.log('M23 NCWU CAS transport: synthetics, identity, cookie scope, preflight, read-only business endpoints, MFA and redirect refusal passed');
