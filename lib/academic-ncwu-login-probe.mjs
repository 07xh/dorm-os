/*
 * NCWU Qingguo real login-surface preflight; no credentials and no cookies.
 * This is NOT authentication or a trusted timetable adapter.
 * Fixed official entry and allowlisted HTTPS redirections only.
 */
import {load} from 'cheerio';
const ENTRY='https://jwmis.ncwu.edu.cn/hsjw/caslogin';
const SITE='jwmis.ncwu.edu.cn',CAS='authserver.ncwu.edu.cn';
const SERVICE='https://jwmis.ncwu.edu.cn/hsjw/caslogin';
const unsafe=/captcha|geetest|slider|slideverify|sm2encrypt|rsaencrypt|encryptpassword|图形验证码|滑块验证|滑动验证|短信验证码|人脸识别/i;
const fail=reason=>({loginSupported:false,mode:reason});
export function ncwuOfficialRedirect(target,base=ENTRY){
 let url;
 try{url=new URL(target,base);}catch{return null;}
 if(url.protocol!=='https:'||url.username||url.password||url.hash||url.port||url.href.length>3200)return null;
 if(url.hostname===SITE){
  if(!/^\/hsjw\/(?:caslogin|cas\/logon\.action|login(?:\.\w+)?|index(?:\.\w+)?)\/?$/.test(url.pathname))return null;
 }else if(url.hostname===CAS){
  if(url.pathname!=='/authserver/login'||url.searchParams.get('service')!==SERVICE)return null;
 }else return null;
 return url;
}
export function classifyNcwuLoginHtml(html,pageUrl){
 if(typeof html!=='string'||html.length>350000)return fail('unexpected_school_page');
 const page=ncwuOfficialRedirect(pageUrl);
 if(!page)return fail('unexpected_school_origin');
 const $=load(html);
 const candidates=$('form').toArray().filter(el=>$(el).find('input[type="password"],input[name="password"]').length);
 if(!candidates.length)return fail('login_form_not_found');
 if(candidates.length!==1)return fail('interactive_login_required');
 const form=$(candidates[0]),method=String(form.attr('method')||'').toLowerCase();
 if(method!=='post'||form.attr('onsubmit'))return fail('interactive_login_required');
 const action=ncwuOfficialRedirect(form.attr('action')||page.href,page.href);
 if(!action||action.hostname!==page.hostname||action.pathname!==page.pathname&&
    !(page.hostname===SITE&&action.pathname==='/hsjw/cas/logon.action'))return fail('interactive_login_required');
 const inputs=form.find('input').toArray();
 const names=inputs.map(el=>String($(el).attr('name')||'')).filter(Boolean);
 if(names.filter(name=>name==='username').length!==1||
    names.filter(name=>name==='password').length!==1)return fail('interactive_login_required');
 // A login form that depends on additional human checks or client-side password
 // transformations must be completed at the school's own website.
 const formHtml=$.html(form);
 if(unsafe.test(formHtml)||/on(?:click|change|input)\s*=/i.test(formHtml))return fail('interactive_login_required');
 if(inputs.some(el=>{const name=String($(el).attr('name')||'');
  return /^(?:captcha|validatecode|verifycode|smscode|otp|mfa|token_otp)$/i.test(name);
 }))return fail('interactive_login_required');
 // Look only at script *references* to avoid treating optional tabs as blockers.
 const scripts=$('script[src]').toArray().map(el=>$(el).attr('src')||'').join(' ');
 if(/(?:encryptpassword|sm2encrypt|rsaencrypt)/i.test(scripts))
  return fail('interactive_login_required');
 return {loginSupported:true,mode:'ordinary_form_detected_not_authenticated'};
}
export async function probeNcwuLogin({fetchImpl=fetch}={}){
 if(typeof fetchImpl!=='function')return fail('upstream_unavailable');
 let page=new URL(ENTRY);
 for(let step=0;step<6;step++){
  let response;
  try{
   response=await fetchImpl(page.href,{
    method:'GET',redirect:'manual',cache:'no-store',
    headers:{Accept:'text/html,application/xhtml+xml'},
    signal:AbortSignal.timeout(12000)
   });
  }catch{return fail('upstream_unavailable');}
  if(!response||!Number.isInteger(response.status))return fail('upstream_unavailable');
  if([301,302,303].includes(response.status)){
   const next=ncwuOfficialRedirect(response.headers?.get?.('location'),page.href);
   if(!next)return fail('unexpected_school_redirect');
   page=next;continue;
  }
  if([307,308,401,403].includes(response.status))return fail('interactive_login_required');
  if(response.status!==200)return fail('upstream_unavailable');
  const contentType=response.headers?.get?.('content-type')||'';
  if(contentType&&!/text\/html|application\/xhtml\+xml/i.test(contentType))
   return fail('unexpected_school_page');
  if(Number(response.headers?.get?.('content-length')||0)>350000)
   return fail('unexpected_school_page');
  let html;
  try{html=await response.text();}catch{return fail('upstream_unavailable');}
  return classifyNcwuLoginHtml(html,page.href);
 }
 return fail('redirect_limit_exceeded');
}
