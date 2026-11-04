/* Return from the existing first-party connection center to the native
 * Android app. This is NOT login/OAuth or a Cookie/ticket transfer.
 * It only returns an opaque, short-lived navigation nonce initiated by
 * the app; the Android owner session + server APIs independently verify data.
 */
(() => {
  'use strict';
  const KEY='xiaoxu.android.school-return.v1';
  const SOURCES=Object.freeze({
    chaoxing:'学习通',ncwu:'青果教务',second_class:'第二课堂'
  });
  const NONCE=/^[A-Za-z0-9_-]{43}$/;
  const TTL=10*60*1000;
  // A Custom Tab may reuse an already open first-party document. Refresh
  // only for a new app-originated hash so we can scrub and register it before
  // the normal SPA route handler sees an unknown route.
  window.addEventListener('hashchange',()=>{
    if(location.hash.startsWith('#settings?xu_app_source='))location.reload();
  });
  let pending=null;
  try {
    const prefix='#settings?';
    if (location.hash.startsWith(prefix)) {
      const args=new URLSearchParams(location.hash.slice(prefix.length));
      const provider=args.get('xu_app_source'),state=args.get('xu_app_state');
      // Scrub the opaque state from browser history BEFORE any site script
      // performs its own route parsing. Fragments are never HTTP referrers.
      history.replaceState(null,'',location.pathname+location.search+'#settings');
      if (args.size===2&&Object.hasOwn(SOURCES,provider)&&NONCE.test(state||'')) {
        pending={provider,state,created:Date.now()};
        sessionStorage.setItem(KEY,JSON.stringify(pending));
      }
    }
    if (!pending) {
      const old=JSON.parse(sessionStorage.getItem(KEY)||'null');
      if (old&&Object.hasOwn(SOURCES,old.provider)&&NONCE.test(old.state||'')&&
          Number.isFinite(old.created)&&Date.now()-old.created>=0&&
          Date.now()-old.created<=TTL) pending=old;
      else sessionStorage.removeItem(KEY);
    }
  } catch {
    try{sessionStorage.removeItem(KEY);}catch{}
  }
  if (!pending) return;
  // Do not label authorization as successful; the App must call the
  // existing owner-bound /auth/me and provider GET/POST endpoints on return.
  function draw() {
    if (!document.body || document.getElementById('xuAndroidSchoolReturn')) return;
    const panel=document.createElement('section');
    panel.id='xuAndroidSchoolReturn';
    panel.setAttribute('aria-label','校序 App 授权返回');
    panel.style.cssText='position:sticky;top:0;z-index:10010;display:flex;align-items:center;justify-content:center;gap:12px;flex-wrap:wrap;padding:10px 14px;background:#edf5ff;color:#173b6c;border-bottom:1px solid #cbdff8;font:14px system-ui';
    const label=document.createElement('span');
    label.textContent='正在从校序 App 连接'+SOURCES[pending.provider]+'。请先在本页完成本人授权；返回后 App 将核验实际数据。';
    const back=document.createElement('button');
    back.type='button';
    back.textContent='返回校序 App 并校验';
    back.style.cssText='border:0;border-radius:9px;background:#2563eb;color:white;padding:9px 13px;cursor:pointer';
    back.addEventListener('click',()=>{
      // User gesture only. The URI never contains credentials or a ticket.
      sessionStorage.removeItem(KEY);
      const uri='xiaoxu://school-auth-return?source='+pending.provider+
        '&state='+pending.state;
      location.assign(uri);
    });
    const cancel=document.createElement('button');
    cancel.type='button';cancel.textContent='关闭引导';
    cancel.style.cssText='border:0;background:transparent;color:#264e80;padding:9px;cursor:pointer';
    cancel.addEventListener('click',()=>{
      sessionStorage.removeItem(KEY);panel.remove();
    });
    panel.append(label,back,cancel);
    document.body.prepend(panel);
    // An App request names ONE provider. Open its existing connection
    // controls after the SPA renders them; never trigger credential POST.
    let focused=false;
    const locate=()=>{
      const rows=Array.from(document.querySelectorAll('.xu-connection-source'));
      const row=rows.find(el=>el.querySelector('strong')?.textContent?.trim()===
        SOURCES[pending.provider]);
      if (!row||focused)return;
      focused=true;
      const manage=row.querySelector('.xu-school-manage');
      if (manage)manage.open=true;
      row.scrollIntoView({block:'center',behavior:'smooth'});
      watcher.disconnect();
    };
    const watcher=new MutationObserver(locate);
    const app=document.getElementById('app');
    if (app)watcher.observe(app,{childList:true,subtree:true});
    locate();
  }
  if (document.readyState==='loading')document.addEventListener('DOMContentLoaded',draw,{once:true});
  else draw();
})();
