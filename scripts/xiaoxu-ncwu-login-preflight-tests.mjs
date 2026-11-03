import assert from 'node:assert/strict';
import {probeNcwuLogin,classifyNcwuLoginHtml,ncwuOfficialRedirect}
 from '../lib/academic-ncwu-login-probe.mjs';
import {resolveNcwuPreflight} from '../functions/api/v1/connections/[provider]/preflight.js';

const ENTRY='https://jwmis.ncwu.edu.cn/hsjw/caslogin';
const CAS='https://authserver.ncwu.edu.cn/authserver/login?service='+encodeURIComponent(ENTRY);
const form='<html><form method="POST" action="/authserver/login?service='+encodeURIComponent(ENTRY)+'">'+
 '<input name="execution" type="hidden" value="TEST_ONLY">'+
 '<input name="username" type="text">'+
 '<input name="password" type="password">'+
 '<button type="submit">登录</button></form></html>';
const headers={'content-type':'text/html; charset=utf-8'};
function fetcher(handler){
 const calls=[];
 const fetchImpl=async(url,opts)=>{
  calls.push({url,opts});
  assert.equal(opts.method,'GET');
  assert.equal(opts.redirect,'manual');
  assert.equal(opts.headers.Accept,'text/html,application/xhtml+xml');
  assert.equal(opts.headers.Cookie,undefined);
  assert.equal(opts.body,undefined);
  return handler(url);
 };
 return {calls,fetchImpl};
}
let network=fetcher(url=>url===ENTRY?
 new Response(null,{status:302,headers:{location:CAS}}):
 new Response(form,{status:200,headers}));
const valid=await probeNcwuLogin({fetchImpl:network.fetchImpl});
assert.deepEqual(valid,{loginSupported:true,mode:'ordinary_form_detected_not_authenticated'});
assert.deepEqual(network.calls.map(x=>x.url),[ENTRY,CAS]);

assert.equal(ncwuOfficialRedirect('https://attacker.test/capture'),null);
assert.equal(ncwuOfficialRedirect('http://authserver.ncwu.edu.cn/authserver/login'),null);
assert.equal(ncwuOfficialRedirect('https://authserver.ncwu.edu.cn/authserver/login?service=https%3A%2F%2Fattacker.test'),null);
assert.equal(ncwuOfficialRedirect('https://jwmis.ncwu.edu.cn/hsjw/student/xkjg.wdkb.jsp'),null);

const alteredForm=form.replace('method="POST"','method="GET"');
assert.equal(classifyNcwuLoginHtml(alteredForm,CAS).loginSupported,false);
const challenging=form.replace('</form>','<input name="captcha" value="1234"></form>');
assert.equal(classifyNcwuLoginHtml(challenging,CAS).mode,'interactive_login_required');
assert.equal(classifyNcwuLoginHtml(form+'<script src="/js/sm2encrypt.js"></script>',CAS).mode,'interactive_login_required');
network=fetcher(url=>url===ENTRY?
 new Response(null,{status:302,headers:{location:'https://example.test/login'}}):
 new Response(form,{status:200,headers}));
assert.equal((await probeNcwuLogin({fetchImpl:network.fetchImpl})).mode,'unexpected_school_redirect');
assert.equal(network.calls.length,1);
network=fetcher(()=>new Response(null,{status:307,headers:{location:CAS}}));
assert.equal((await probeNcwuLogin({fetchImpl:network.fetchImpl})).mode,'interactive_login_required');
network=fetcher(()=>new Response('{"token":"SECRET"}',{status:200,headers:{'content-type':'application/json'}}));
assert.equal((await probeNcwuLogin({fetchImpl:network.fetchImpl})).mode,'unexpected_school_page');
network=fetcher(()=>{throw new Error('network');});
assert.equal((await probeNcwuLogin({fetchImpl:network.fetchImpl})).mode,'upstream_unavailable');
for(const data of [valid,await probeNcwuLogin({fetchImpl:network.fetchImpl})]){
 const output=JSON.stringify(data);
 assert.ok(!output.includes('TEST_ONLY'));
 assert.ok(!output.includes('token'));
 assert.ok(!output.includes('Cookie'));
 assert.ok(!output.includes('SECRET'));
}
// The independent private CAS probe must not be blocked by a less-specific
// public HTML classifier. Neither result authenticates an account.
const falsePublic={loginSupported:false,mode:'login_form_not_found'};
const ready=await resolveNcwuPreflight(falsePublic,{
 preflight:async()=>({loginSupported:true,mode:'password_form_verified'})
},true);
assert.equal(ready.nativeLoginReady,true);
assert.equal(ready.loginSupported,true);
assert.equal(ready.officialFormDetected,false);
// An inspectable school webpage is not proof our own connector works.
const publicOnly=await resolveNcwuPreflight(
 {loginSupported:true,mode:'ordinary_form_detected_not_authenticated'},null,true);
assert.equal(publicOnly.officialFormDetected,true);
assert.equal(publicOnly.loginSupported,false);
assert.equal(publicOnly.nativeLoginReady,false);
const noVaultEvenIfNative=await resolveNcwuPreflight(falsePublic,{
 preflight:async()=>({loginSupported:true,mode:'password_form_verified'})
},false);
assert.equal(noVaultEvenIfNative.loginSupported,false);
assert.equal(noVaultEvenIfNative.status,'server_session_vault_unavailable');
assert.equal(ready.schoolAuthorized,false);
const needsHuman=await resolveNcwuPreflight(
 {loginSupported:true,mode:'ordinary_form_detected_not_authenticated'},
 {preflight:async()=>({loginSupported:false,mode:'interactive_login_required'})},true);
assert.equal(needsHuman.nativeLoginReady,false);
assert.equal(needsHuman.status,'interactive_login_required');
const noVault=await resolveNcwuPreflight(falsePublic,null,false);
assert.equal(noVault.status,'server_session_vault_unavailable');
assert.equal(noVault.nativeLoginReady,false);

console.log('NCWU public preflight: authentic official redirects, no credentials, safe blocking and privacy passed');
