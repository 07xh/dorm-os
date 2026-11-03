/* Upload only normalized snapshots to the same-origin, Access-protected 校序 API.
 * Same-origin fetch lets the browser attach the user's existing Access session;
 * no Access JWT, school cookie, or credential is read by this extension.
 */
'use strict';
chrome.runtime.onMessage.addListener((message,sender,sendResponse)=>{
 if(message?.type!=='NCWU_UPLOAD_SNAPSHOT')return;
 if(location.origin!=='https://dorm-os-dk9.pages.dev'){
  sendResponse({ok:false,error:'PORTAL_ORIGIN_MISMATCH'});return;
 }
 (async()=>{
  const response=await fetch('/api/v1/verified/snapshot',{
   method:'POST',credentials:'same-origin',cache:'no-store',
   headers:{'content-type':'application/json','accept':'application/json'},
   body:JSON.stringify(message.snapshot)
  });
  const body=await response.json().catch(()=>null);
  if(response.status===401||response.status===403)
   return {ok:false,error:'PORTAL_AUTH_REQUIRED'};
  if(response.status===503&&body?.status==='database_not_configured')
   return {ok:false,error:'D1_NOT_CONFIGURED'};
  if(response.status===202&&body?.status==='accepted')
   return {ok:true,count:body.count,capturedAt:body.lastSuccessAt,provenance:body.provenance};
  return {ok:false,error:response.status===409?'OUTDATED_SNAPSHOT':'PORTAL_UPLOAD_FAILED'};
 })().then(sendResponse).catch(()=>sendResponse({ok:false,error:'PORTAL_NETWORK_ERROR'}));
 return true;
});
