/* Release recovery is deliberately independent of the main app and its modules.
 * It never clears localStorage, IndexedDB, cookies or account sessions. */
(function(root){
 'use strict';
 const STATE='xiaoxu-release-reload-state',LEGACY='xiaoxu-release-reload-target';
 const valid=value=>/^[a-f0-9]{40}$/.test(value||'');
 let busy=null,pending=null,reloading=false,registration=null;
 const dirtyForms=new Set();
 function built(){return document.querySelector('meta[name="xiaoxu-release"]')?.content||'';}
 function hasUnsavedWork(){
  if(document.querySelector('dialog[open]'))return true;
  for(const form of dirtyForms){
   const dialog=form.closest?.('dialog');
   if(!form.isConnected||dialog&&!dialog.open){dirtyForms.delete(form);continue;}
   return true;
  }
  return false;
 }
 function savedState(target){
  let stored=null;try{stored=JSON.parse(sessionStorage.getItem(STATE)||'null');}catch{}
  const url=new URL(location.href),fallback=url.searchParams.get('_xu_release')===target.slice(0,12)?Number(url.searchParams.get('_xu_attempts')):0;
  const attempts=stored?.target===target?Number(stored.attempts):0;
  const bounded=value=>Number.isSafeInteger(value)&&value>=0?Math.min(value,2):0;
  return {target,attempts:Math.max(bounded(attempts),bounded(fallback))};
 }
 function clearState(){
  try{sessionStorage.removeItem(STATE);sessionStorage.removeItem(LEGACY);}catch{}
  const url=new URL(location.href);
  if(url.searchParams.has('_xu_attempts')){url.searchParams.delete('_xu_attempts');root.history?.replaceState(null,'',url.pathname+url.search+url.hash);}
 }
 function notice(message,retry=false){
  if(!document.body)return;
  let box=document.getElementById('xuUpdateNotice');
  if(!box){box=document.createElement('section');box.id='xuUpdateNotice';box.className='xu-update-notice';box.setAttribute('role','status');box.setAttribute('aria-live','polite');document.body.append(box);}
  if(box.dataset?.message===message)return;
  if(box.dataset)box.dataset.message=message;
  const text=document.createElement('span');text.textContent=message;box.replaceChildren(text);
  if(retry){const button=document.createElement('button');button.type='button';button.textContent='重试更新';button.addEventListener('click',()=>{clearState();void checkPublishedRelease();});box.append(button);}
 }
 async function applyPending(){
  if(!pending||reloading||document.hidden||!navigator.onLine)return;
  if(hasUnsavedWork()){notice('新版本已就绪，保存或关闭编辑后会自动更新。');return;}
  const previous=savedState(pending),attempts=previous?.target===pending?Number(previous.attempts)||0:0;
  if(attempts>=2){notice('更新尚未完成，可重试获取新版本。',true);return;}
  reloading=true;
  try{
   // Unique navigation keys bypass an old worker without deleting the offline fallback.
   try{sessionStorage.setItem(STATE,JSON.stringify({target:pending,attempts:attempts+1}));}catch{}
   void registration?.update().catch(()=>{});
   const next=new URL(location.href);
   next.searchParams.set('_xu_release',pending.slice(0,12));
   next.searchParams.set('_xu_refresh',String(Date.now()));
   next.searchParams.set('_xu_attempts',String(attempts+1));
   location.replace(next.href);
  }catch{reloading=false;notice('暂时无法更新，请联网后重试。',true);}
 }
 function checkPublishedRelease(){
  if(busy)return busy;
  if(document.hidden||!navigator.onLine||!valid(built()))return Promise.resolve();
  busy=(async()=>{
   try{
    const response=await fetch('/revision.json?release_check='+Date.now(),{credentials:'same-origin',cache:'no-store',headers:{accept:'application/json'}});
    if(!response.ok)return;
    const latest=await response.json();
    if(!valid(latest?.commit))return;
    if(latest.commit===built()){
     pending=null;clearState();document.getElementById('xuUpdateNotice')?.remove();
     const url=new URL(location.href);
     if(url.searchParams.get('_xu_release')===latest.commit.slice(0,12)){
      url.searchParams.delete('_xu_release');url.searchParams.delete('_xu_refresh');url.searchParams.delete('_xu_attempts');
      root.history?.replaceState(null,'',url.pathname+url.search+url.hash);
     }
     return;
    }
    pending=latest.commit;await applyPending();
   }catch{/* Offline or upstream failure: retain the working page and its data. */}
  })().finally(()=>{busy=null;});
  return busy;
 }
 if('serviceWorker' in navigator&&(location.protocol==='https:'||['localhost','127.0.0.1'].includes(location.hostname))){
  navigator.serviceWorker.addEventListener('controllerchange',()=>{void checkPublishedRelease();});
  navigator.serviceWorker.addEventListener('message',event=>{
   if(event.data?.type==='XIAOXU_RELEASE_AVAILABLE')void checkPublishedRelease();
  });
  navigator.serviceWorker.register('/sw.js',{updateViaCache:'none'}).then(reg=>{
   registration=reg;void reg.update().catch(()=>{});
  }).catch(()=>{});
 }
 document.addEventListener('input',event=>{const form=event.target?.closest?.('form');if(form)dirtyForms.add(form);},true);
 document.addEventListener('reset',event=>{dirtyForms.delete(event.target);void applyPending();},true);
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)void checkPublishedRelease();});
 root.addEventListener('online',()=>{void checkPublishedRelease();});
 root.addEventListener('pageshow',event=>{if(event.persisted)reloading=false;void checkPublishedRelease();});
 if(root.MutationObserver)new MutationObserver(()=>{if(pending&&!reloading)void applyPending();}).observe(document,{subtree:true,childList:true,attributes:true,attributeFilter:['open']});
 if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',()=>{void checkPublishedRelease();},{once:true});
 else void checkPublishedRelease();
 setInterval(()=>{void checkPublishedRelease();},60000);
 root.XiaoxuUpdates=Object.freeze({checkPublishedRelease});
})(globalThis);
