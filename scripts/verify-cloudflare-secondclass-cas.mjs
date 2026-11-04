/* Execute after Pages deploy to ask the DEPLOYED Cloudflare Function,
 * not the GitHub runner, whether the school's public CAS chain is reachable.
 * No school accounts, passwords, tickets, cookies or personal rows involved.
 */
const base=process.argv[2];
if(!base||!/^https:\/\/[a-z0-9-]+\.dorm-os-dk9\.pages\.dev\/?$/.test(base))
 throw Error('expected deployment-specific Cloudflare Pages URL');
const url=new URL('/api/academic/second-class-cas',base);
const response=await fetch(url,{method:'GET',cache:'no-store',
 headers:{accept:'application/json'},signal:AbortSignal.timeout(45000)});
if(!response.ok)throw Error('Cloudflare CAS diagnostic endpoint returned HTTP '+response.status);
const data=await response.json();
if(data?.schema!=='xiaoxu/cloudflare-second-class-cas-v1'||
 data.environment!=='cloudflare-pages-functions'||
 typeof data.officialEntryReachable!=='boolean'||
 typeof data.casLoginPageReachable!=='boolean'||
 typeof data.currentPasswordFormCompatible!=='boolean'||
 data.userAuthenticated!==false||data.sessionLinked!==false||
 data.personalRecordsRead!==false)
 throw Error('Invalid Cloudflare CAS public probe contract');
const evidence={
 origin:'deployed_cloudflare_pages_function',
 officialEntryReachable:data.officialEntryReachable,
 casLoginPageReachable:data.casLoginPageReachable,
 currentPasswordFormCompatible:data.currentPasswordFormCompatible,
 mode:data.mode,reason:data.reason,
 ...(typeof data.failureStage==='string'?{failureStage:data.failureStage}:{}),
 ...(typeof data.actionHasPathParameters==='boolean'?{actionHasPathParameters:data.actionHasPathParameters}:{}),
 ...(data.reason==='interactive_challenge_required'&&data.challengeEvidence?
 {challengeEvidence:{
  passwordFormSeen:data.challengeEvidence.passwordFormSeen===true,
  challengeControlSeen:data.challengeEvidence.challengeControlSeen===true,
  challengeMarkupSeen:data.challengeEvidence.challengeMarkupSeen===true,
  challengeScriptSeen:data.challengeEvidence.challengeScriptSeen===true,
  passwordTransformationSeen:data.challengeEvidence.passwordTransformationSeen===true,
  submitHandlerSeen:data.challengeEvidence.submitHandlerSeen===true,
  submitHandlerKind:data.challengeEvidence.submitHandlerKind,
  ...(typeof data.challengeEvidence.submitHandlerName==='string'?
   {submitHandlerName:data.challengeEvidence.submitHandlerName}:{}),
  schoolScriptDefinitionSeen:data.challengeEvidence.schoolScriptDefinitionSeen===true,
  sourceType:data.challengeEvidence.sourceType,
  passwordProcessingSeen:data.challengeEvidence.passwordProcessingSeen===true,
  verificationSeen:data.challengeEvidence.verificationSeen===true,
  formSubmissionSeen:data.challengeEvidence.formSubmissionSeen===true,
  asyncSubmissionSeen:data.challengeEvidence.asyncSubmissionSeen===true,
  ...Object.fromEntries(['inlineScriptCount','externalScriptTagsSeen',
   'eligibleExternalScripts','fetchedExternalScripts','failedExternalScripts']
   .map(k=>[k,data.challengeEvidence[k]]))
 }}:{}),
 personalAccountLinked:false
};
console.log('Cloudflare second-class CAS public check: '+JSON.stringify(evidence));
if(!data.casLoginPageReachable)
 console.log('::warning::Second-class CAS login page was not reached from Cloudflare; personal authentication remains unavailable.');
else if(!data.currentPasswordFormCompatible)
 console.log('::warning::Second-class CAS was reachable but current native password form was not verified.');
else
 console.log('CAS public login form can be read by Cloudflare. NO personal account or session has been verified.');
