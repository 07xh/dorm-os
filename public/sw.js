// CI stamps this placeholder with the exact deployed commit. A new deploy
// activates a new app-shell cache instead of retaining yesterday's files.
const CACHE='dorm-os-shell-__XIAOXU_RELEASE_SHA__';
const ASSETS=['/','/xiaoxu-base.css','/manifest.webmanifest','/icons/dorm-os.svg','/icons/icon-192.png','/icons/icon-512.png','/xiaoxu-academic.css','/xiaoxu-task-import.js','/xiaoxu-chaoxing-cli.js','/xiaoxu-chaoxing-homework.js','/xiaoxu-chaoxing-inbox.js','/xiaoxu-chaoxing-inbox.css','/xiaoxu-notices.js','/xiaoxu-academic-client.js','/xiaoxu-cloud-tasks.js','/xiaoxu-wechat-sync.js','/xiaoxu-deadline-reminders.js','/xiaoxu-reminders.js','/xiaoxu-owner-auth-ui.js','/xiaoxu-owner-auth.css?v=98-profile-styles','/xiaoxu-workspace.css','/xiaoxu-workspace.js'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(ASSETS)).then(()=>self.skipWaiting()));});
self.addEventListener('activate',e=>{e.waitUntil(Promise.all([caches.keys().then(keys=>Promise.all(keys.filter(k=>k.startsWith('dorm-os-shell-')&&k!==CACHE).map(k=>caches.delete(k)))),self.clients.claim()]));});
self.addEventListener('fetch',e=>{
  const r=e.request;
  if(r.method!=='GET')return;
  const u=new URL(r.url);
  if(u.origin!==self.location.origin)return;
  if(u.pathname==='/revision.json'){
    // Version probe must never be answered from a previous PWA shell.
    e.respondWith(fetch(r,{cache:'no-store'}));return;
  }
  if(r.mode==='navigate'){
    e.respondWith(fetch(r,{cache:'no-store'}).then(resp=>{if(resp.ok && u.pathname==='/'){const clone=resp.clone();e.waitUntil(caches.open(CACHE).then(c=>c.put('/',clone)));}return resp;}).catch(()=>caches.match('/')));
    return;
  }
  // Versionless academic scripts and styles are network-first with offline
  // cached fallback, preventing old deployed login code from persisting in PWAs.
  if(u.pathname==='/xiaoxu-owner-auth-ui.js' || u.pathname.endsWith('.css') ||
     (u.pathname.endsWith('.js')&&ASSETS.includes(u.pathname))){
    e.respondWith(fetch(r,{cache:'no-store'}).then(resp=>{
      if(resp.ok){const copy=resp.clone();e.waitUntil(caches.open(CACHE).then(c=>c.put(r,copy)));}
      return resp;
    }).catch(()=>caches.match(r)));
    return;
  }
  if(ASSETS.includes(u.pathname))e.respondWith(caches.match(r).then(hit=>hit||fetch(r)));
});


// Explicitly permitted, foreground-triggered notifications only. No push subscription.
self.addEventListener('notificationclick',event=>{
 event.notification.close();
 const destination=new URL('/#academic',self.location.origin).href;
 event.waitUntil((async()=>{
  const pages=await self.clients.matchAll({type:'window',includeUncontrolled:true});
  const tab=pages.find(p=>{try{return new URL(p.url).origin===self.location.origin;}catch{return false;}});
  if(tab){
   await tab.focus();
   if(tab.navigate)await tab.navigate(destination);
   return;
  }
  await self.clients.openWindow(destination);
 })());
});
