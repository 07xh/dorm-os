/* Fixed-source Cloudflare-edge CAS reachability probe. Unauthenticated public
 * login page ONLY: no credentials, sessions, QR tickets, private APIs or
 * caller-supplied URLs. A successful probe is NOT a school account connection.
 */
import {makeNcwuSecondClassServerAdapter}
 from '../../../lib/academic-ncwu-secondclass-server.mjs';

const HOSTS=new Set(['dekt.ncwu.edu.cn','authserver.ncwu.edu.cn','sec.ncwu.edu.cn']);
const REASONS=new Set(['school_unreachable','cas_handoff_unrecognized',
 'cas_login_page_unavailable','password_form_unavailable',
 'interactive_challenge_required','password_transform_required',
 'unexpected_cas_form_action','unrecognized_auth_flow']);
const STATUSES=new Set(['password_form_verified','interactive_login_required',
 'upstream_unavailable']);
const HEADERS={'content-type':'application/json; charset=utf-8',
 'cache-control':'public, max-age=120, s-maxage=600',
 'x-content-type-options':'nosniff','referrer-policy':'no-referrer'};

export async function runCloudflareSecondClassCasProbe({fetcher=fetch}={}){
 let entryReached=false,casPageReached=false;
 const restricted=async(url,options={})=>{
  const parsed=new URL(url);
  if(parsed.protocol!=='https:'||!HOSTS.has(parsed.hostname)||
     (options.method||'GET')!=='GET')throw Error('unsupported_probe_request');
  const response=await fetcher(url,options);
  // A 2xx/3xx/4xx from the university confirms a response, not authorization.
  if(parsed.hostname==='dekt.ncwu.edu.cn'&&response.status>=200&&response.status<500)
   entryReached=true;
  if(parsed.hostname==='authserver.ncwu.edu.cn'&&response.status===200)
   casPageReached=true;
  return response;
 };
 let state;
 try{state=await makeNcwuSecondClassServerAdapter({fetcher:restricted}).preflight({inspectScripts:true});}
 catch{state={mode:'upstream_unavailable',reason:'school_unreachable',loginSupported:false};}
 return {
  schema:'xiaoxu/cloudflare-second-class-cas-v1',
  environment:'cloudflare-pages-functions',
  checkedAt:new Date().toISOString(),
  officialEntryReachable:entryReached,
  casLoginPageReachable:casPageReached,
  currentPasswordFormCompatible:casPageReached&&state.loginSupported===true,
  mode:STATUSES.has(state.mode)?state.mode:'upstream_unavailable',
  reason:REASONS.has(state.reason)?state.reason:null,
  ...(state.reason==='interactive_challenge_required'&&
   state.challengeEvidence?.passwordFormSeen===true?
   {challengeEvidence:{
    passwordFormSeen:true,
    challengeControlSeen:state.challengeEvidence.challengeControlSeen===true,
    challengeMarkupSeen:state.challengeEvidence.challengeMarkupSeen===true,
    challengeScriptSeen:state.challengeEvidence.challengeScriptSeen===true,
    passwordTransformationSeen:state.challengeEvidence.passwordTransformationSeen===true,
    submitHandlerSeen:state.challengeEvidence.submitHandlerSeen===true,
    submitHandlerKind:['none','constant_true','constant_false',
     'named_function_call','complex_inline'].includes(state.challengeEvidence.submitHandlerKind)?
     state.challengeEvidence.submitHandlerKind:'complex_inline',
    ...(typeof state.challengeEvidence.submitHandlerName==='string'&&
       /^[A-Za-z_$][\w$]{0,60}$/.test(state.challengeEvidence.submitHandlerName)?
       {submitHandlerName:state.challengeEvidence.submitHandlerName}:{}),
    schoolScriptDefinitionSeen:state.challengeEvidence.schoolScriptDefinitionSeen===true,
    sourceType:['unknown','inline_school_js','school_static_js'].includes(
     state.challengeEvidence.sourceType)?state.challengeEvidence.sourceType:'unknown',
    passwordProcessingSeen:state.challengeEvidence.passwordProcessingSeen===true,
    verificationSeen:state.challengeEvidence.verificationSeen===true,
    formSubmissionSeen:state.challengeEvidence.formSubmissionSeen===true,
    asyncSubmissionSeen:state.challengeEvidence.asyncSubmissionSeen===true
   }}:{}),
  // Reaching CAS does not confer access to an owner's school account.
  userAuthenticated:false,sessionLinked:false,personalRecordsRead:false
 };
}

export async function onRequestGet({request,waitUntil}){
 const cache=globalThis.caches?.default;
 const key=new Request(new URL('/api/academic/second-class-cas',request.url).href);
 if(cache){
  try{const hit=await cache.match(key);if(hit)return hit;}catch{}
 }
 const payload=await runCloudflareSecondClassCasProbe();
 const result=new Response(JSON.stringify(payload),{status:200,headers:HEADERS});
 if(cache&&waitUntil)waitUntil(cache.put(key,result.clone()).catch(()=>{}));
 return result;
}
