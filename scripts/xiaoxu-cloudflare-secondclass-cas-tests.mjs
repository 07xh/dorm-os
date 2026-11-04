import assert from 'node:assert/strict';
import {runCloudflareSecondClassCasProbe}
 from '../functions/api/academic/second-class-cas.js';

const service='https://sec.ncwu.edu.cn/rump_frontend/loginFromCas/';
const cas='https://authserver.ncwu.edu.cn/authserver/login?service='+encodeURIComponent(service);
const requests=[];
const response=(status,body='',headers={})=>new Response(body,{status,headers});
const fake=async(url,options)=>{
 const u=new URL(url);
 requests.push({host:u.hostname,method:options.method});
 assert.equal(options.method,'GET','CAS public probe must never send credentials');
 assert.ok(!options.headers.cookie,'no school cookie allowed in public login inspection');
 if(u.hostname==='dekt.ncwu.edu.cn'&&u.pathname==='/')
  return response(302,'',{location:'/rump_frontend/login/'});
 if(u.hostname==='dekt.ncwu.edu.cn'&&u.pathname==='/rump_frontend/login/')
  return response(200,'<a href="'+cas+'">登录</a>');
 if(u.hostname==='authserver.ncwu.edu.cn')
  return response(200,'<form method="post" action="/authserver/login">'+
   '<input name="username"><input type="password" name="password">'+
   '<input name="execution" value="a" type="hidden">'+
   '<input name="_eventId" value="submit" type="hidden"></form>');
 throw Error('Unexpected request');
};
const ok=await runCloudflareSecondClassCasProbe({fetcher:fake});
assert.equal(ok.environment,'cloudflare-pages-functions');
assert.equal(ok.officialEntryReachable,true);
assert.equal(ok.casLoginPageReachable,true);
assert.equal(ok.currentPasswordFormCompatible,true);
assert.equal(ok.mode,'password_form_verified');
assert.equal(ok.sessionLinked,false);
assert.equal(ok.personalRecordsRead,false);
assert.ok(requests.length>=3);

const timedOut=await runCloudflareSecondClassCasProbe({fetcher:async()=>{throw Error('timeout');}});
assert.equal(timedOut.officialEntryReachable,false);
assert.equal(timedOut.casLoginPageReachable,false);
assert.equal(timedOut.currentPasswordFormCompatible,false);
assert.equal(timedOut.mode,'upstream_unavailable');
assert.equal(JSON.stringify(timedOut).includes('timeout'),false);

const challenge=await runCloudflareSecondClassCasProbe({fetcher:async(url,options)=>{
 const u=new URL(url);
 if(u.hostname==='authserver.ncwu.edu.cn')return response(200,
  '<form method="post" action="/authserver/login">'+
  '<input name="username"><input name="password" type="password">'+
  '<input name="captcha"><input name="execution" type="hidden" value="a">'+
  '<input name="_eventId" type="hidden" value="submit"></form>');
 return fake(url,options);
}});
assert.equal(challenge.officialEntryReachable,true);
assert.equal(challenge.casLoginPageReachable,true);
assert.equal(challenge.currentPasswordFormCompatible,false);
assert.equal(challenge.reason,'interactive_challenge_required');
assert.deepEqual(challenge.challengeEvidence,{
 passwordFormSeen:true,challengeControlSeen:true,challengeMarkupSeen:true,
 challengeScriptSeen:false,passwordTransformationSeen:false,submitHandlerSeen:false,
 submitHandlerKind:'none',schoolScriptDefinitionSeen:false,sourceType:'unknown',
 passwordProcessingSeen:false,verificationSeen:false,formSubmissionSeen:false,
 asyncSubmissionSeen:false,
 inlineScriptCount:0,externalScriptTagsSeen:0,
 eligibleExternalScripts:0,fetchedExternalScripts:0,failedExternalScripts:0
});
assert.equal(challenge.sessionLinked,false);
const submitOnly=await runCloudflareSecondClassCasProbe({fetcher:async(url,options)=>{
 const u=new URL(url);
 if(u.hostname==='authserver.ncwu.edu.cn')return response(200,
  '<form method="post" action="/authserver/login" onsubmit="return checkLogin();">'+
  '<input name="username"><input name="password" type="password">'+
  '<input type="hidden" name="execution" value="opaque-test">'+
  '<input type="hidden" name="_eventId" value="submit"></form>');
 return fake(url,options);
}});
assert.equal(submitOnly.currentPasswordFormCompatible,false,
 'an unknown onsubmit handler must not be bypassed');
assert.deepEqual(submitOnly.challengeEvidence,{
 passwordFormSeen:true,challengeControlSeen:false,challengeMarkupSeen:false,
 challengeScriptSeen:false,passwordTransformationSeen:false,submitHandlerSeen:true,
 submitHandlerKind:'named_function_call',submitHandlerName:'checkLogin',
 schoolScriptDefinitionSeen:false,sourceType:'unknown',
 passwordProcessingSeen:false,verificationSeen:false,formSubmissionSeen:false,
 asyncSubmissionSeen:false,
 inlineScriptCount:0,externalScriptTagsSeen:0,
 eligibleExternalScripts:0,fetchedExternalScripts:0,failedExternalScripts:0
});
assert.equal(submitOnly.userAuthenticated,false);
assert.equal(submitOnly.sessionLinked,false);
// Read a bounded, same-origin school script as public evidence only.
// JS is inspected, never executed; no credential POST is authorized.
const observedScripts=[];
const scripted=await runCloudflareSecondClassCasProbe({fetcher:async(url,options)=>{
 const u=new URL(url);
 if(u.pathname==='/authserver/static/login.js'){
  observedScripts.push({host:u.hostname,method:options.method,
   credentialHeaders:!!options.headers.cookie});
  return response(200,'function checkLogin() { document.forms[0].submit(); }',
   {'content-type':'application/javascript'});
 }
 if(u.hostname==='authserver.ncwu.edu.cn')return response(200,
  '<form method="post" action="/authserver/login" onsubmit="return checkLogin();">'+
  '<input name="username"><input type="password" name="password">'+
  '<input type="hidden" name="execution" value="opaque-test">'+
  '<input type="hidden" name="_eventId" value="submit"></form>'+
  '<script src="/authserver/static/login.js"></script>');
 return fake(url,options);
}});
assert.equal(scripted.currentPasswordFormCompatible,false);
assert.equal(scripted.challengeEvidence?.schoolScriptDefinitionSeen,true);
assert.equal(scripted.challengeEvidence?.sourceType,'school_static_js');
assert.equal(scripted.challengeEvidence?.formSubmissionSeen,true);
assert.equal(scripted.challengeEvidence?.verificationSeen,false);
assert.equal(scripted.challengeEvidence?.externalScriptTagsSeen,1);
assert.equal(scripted.challengeEvidence?.eligibleExternalScripts,1);
assert.equal(scripted.challengeEvidence?.fetchedExternalScripts,1);
assert.equal(scripted.challengeEvidence?.failedExternalScripts,0);
assert.equal(scripted.sessionLinked,false);
assert.deepEqual(observedScripts,[{host:'authserver.ncwu.edu.cn',
 method:'GET',credentialHeaders:false}]);
console.log('Cloudflare CAS public-only edge probe: public redirect, service binding, challenge and no-session proof passed');
