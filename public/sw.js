const CACHE='dorm-os-shell-v3.4-20261031';
const ASSETS=['/','/manifest.webmanifest','/icons/dorm-os.svg'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(ASSETS)).then(()=>self.skipWaiting()));});
self.addEventListener('activate',e=>{e.waitUntil(Promise.all([caches.keys().then(keys=>Promise.all(keys.filter(k=>k.startsWith('dorm-os-shell-')&&k!==CACHE).map(k=>caches.delete(k)))),self.clients.claim()]));});
self.addEventListener('fetch',e=>{
  const r=e.request;
  if(r.method!=='GET')return;
  const u=new URL(r.url);
  if(u.origin!==self.location.origin)return;
  if(r.mode==='navigate'){
    e.respondWith(fetch(r).then(resp=>{if(resp.ok){const clone=resp.clone();e.waitUntil(caches.open(CACHE).then(c=>c.put('/',clone)));}return resp;}).catch(()=>caches.match('/')));
    return;
  }
  if(ASSETS.includes(u.pathname))e.respondWith(caches.match(r).then(hit=>hit||fetch(r)));
});
