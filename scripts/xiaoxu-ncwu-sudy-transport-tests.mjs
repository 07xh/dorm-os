import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {makeNcwuSecondClassServerAdapter} from '../lib/academic-ncwu-secondclass-server.mjs';
import {verifyNcwuSudySource} from '../lib/academic-ncwu-sudy-codec.mjs';
// Only public static JS is loaded. All account, CAS, cookies and business
// responses below are synthetic. Never execute the downloaded school code.
const source=process.argv[2]==='--live'?
 await (async()=>{
  const r=await fetch('https://authserver.ncwu.edu.cn/authserver/themes/sudy_ncwu/js/login.js?t=1',
   {redirect:'error',signal:AbortSignal.timeout(15000)});
  assert.equal(r.status,200);
  const s=await r.text();assert.ok(s.length<=300000);return s;
 })():readFileSync(process.argv[2],'utf8');
assert.equal(await verifyNcwuSudySource(source),true,'public school source changed; review before enabling login');
const service='https://sec.ncwu.edu.cn/rump_frontend/loginFromCas/';
const cas='https://authserver.ncwu.edu.cn/authserver/login?service='+encodeURIComponent(service);
const markup='<form id="fm1" method="post" action="/authserver/login" onsubmit="return doPcMobilesubmit();">'+
 '<input name="username"><input id="passwordShow" type="password">'+
 '<input name="password" id="password" type="hidden">'+
 '<input id="encrypted" name="encrypted" type="hidden" value="SYNTHETIC">'+
 '<input name="execution" type="hidden" value="SYNTHETIC">'+
 '<input name="_eventId" type="hidden" value="submit">'+
 '<input name="loginType" type="hidden" value="SYNTHETIC">'+
 '<input type="submit" onclick="return checkPassLogin();"></form>'+
 '<script src="/authserver/themes/sudy_ncwu/js/login.js?t=1"></script>';
const response=(status,body='',headers={})=>new Response(body,{status,headers});
function school({html=markup,script=source}={}){
 let posts=0;
 const fetcher=async(url,options)=>{
  const u=new URL(url);
  if(u.pathname==='/')return response(302,'',{location:cas});
  if(u.pathname.endsWith('/js/login.js')){
   assert.equal(options.headers.cookie,undefined);return response(200,script);
  }
  if(u.pathname==='/authserver/login'){
   if(options.method==='GET')return response(200,html);
   posts++;throw Error('retired CAS POST must never occur');
  }
  if(u.hostname==='sec.ncwu.edu.cn')return response(302,'',{location:'https://dekt.ncwu.edu.cn/sys'});
  if(u.pathname==='/sys')return response(200,'<main>Synthetic</main>');
  if(u.pathname==='/sys/stu/score/list')return response(200,JSON.stringify({data:[{student_code:'SYNTHETIC'}]}));
  throw Error('unexpected synthetic endpoint');
 };
 return {adapter:makeNcwuSecondClassServerAdapter({fetcher}),posts:()=>posts};
}
const good=school();
assert.equal((await good.adapter.preflight()).loginSupported,true);
assert.equal(good.posts(),0);
await assert.rejects(()=>good.adapter.login({username:'SYNTHETIC',password:'Synthetic_Pass123!'}),
 e=>e.reason==='official_app_authorization_required');
assert.equal(good.posts(),0);
const irrelevantDuplicate=school({html:markup+'<div class="a" class="b"></div>'});
assert.equal((await irrelevantDuplicate.adapter.preflight()).loginSupported,true,
 'unrelated page markup must not be parsed as a credential form');
for(const fixture of [
 {script:source+'\n/* modified */'},
 {html:markup.replace('</form>','<input id="authcode" name="authcode"></form>')},
 {html:markup+'<input id="authcode" name="authcode">'}
]){
 const denied=school(fixture);
 assert.equal((await denied.adapter.preflight()).loginSupported,false);
 await assert.rejects(()=>denied.adapter.login({username:'SYNTHETIC',password:'Synthetic_Pass123!'}));
 assert.equal(denied.posts(),0,'unverified source or mandatory challenge must prevent password POST');
}
console.log('Sudy transport: pinned public read-only source, retired password transport, tamper and CAPTCHA refusal passed');

