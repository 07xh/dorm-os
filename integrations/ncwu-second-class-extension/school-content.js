/* Runs only on the official NCWU domain in the page's isolated content-script world. */
'use strict';
chrome.runtime.onMessage.addListener((message,sender,sendResponse)=>{
 if(message?.type!=='NCWU_READ_ONLY_SYNC')return;
 if(location.origin!=='https://dekt.ncwu.edu.cn'){
  sendResponse({ok:false,error:'AUTH_EXPIRED'});return;
 }
 if(!globalThis.NcwuSecondClassAdapter){
  sendResponse({ok:false,error:'ADAPTER_MISSING'});return;
 }
 globalThis.NcwuSecondClassAdapter.readSnapshot().then(result=>{
  sendResponse({ok:true,snapshot:result.snapshot,stats:result.stats});
 }).catch(error=>{
  const code=String(error?.code||'SYNC_FAILED');
  sendResponse({ok:false,error:code});
 });
 return true;
});
