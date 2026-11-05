/*
 * Local-only, opt-in request-path observer for the student's own NCWU
 * second-class business page. Install only after an explicit UI tap.
 *
 * This instruments request INITIATION (fetch/XHR.open); it never reads
 * response bodies, form contents, headers, credentials, cookies or query
 * parameters, never issues a new network request, and never sends data
 * to Xiaoxu servers. The browser page owns the session.
 *
 * An observed path is ONLY a diagnostic candidate. It is not evidence that
 * the request returns the all-activities catalog or permits registration.
 */
(function installSecondClassCatalogObserver() {
  'use strict';
  const key='__xiaoxuSecondClassSourceObserverV1';
  const host='https://dekt.ncwu.edu.cn';
  if(location.origin!==host ||
     typeof window.fetch!=='function' ||
     typeof XMLHttpRequest==='undefined' ||
     typeof XMLHttpRequest.prototype.open!=='function')
    return JSON.stringify({ok:false,reason:'official_page_required'});
  if(window[key])
    return JSON.stringify({ok:false,reason:'already_recording'});
  const savedFetch=window.fetch;
  const savedOpen=XMLHttpRequest.prototype.open;
  const seen=new Map();
  let overflow=false;
  function record(input,method) {
    try {
      if(typeof method!=='string'||!/^(GET|POST)$/i.test(method))return;
      const raw=typeof input==='string'?input:
        (input&&typeof input.url==='string'?input.url:null);
      if(!raw||raw.length>2048)return;
      const url=new URL(raw,location.href);
      if(url.origin!==host || url.username || url.password)return;
      const path=url.pathname;
      // Suppress dynamic IDs, SSO tokens and filenames: only short, static
      // /sys/... routes are exported; URL queries and fragment never are.
      if(path.length>130||!/^\/sys\/(?:[A-Za-z_-]+\/){1,7}[A-Za-z_-]+\/?$/.test(path)||
         /(?:login|auth|ticket|token|password|captcha|secret|studentcode)/i.test(path))
        return;
      const normalized=method.toUpperCase()+' '+path;
      if(!seen.has(normalized) && seen.size>=25){overflow=true;return;}
      seen.set(normalized,Math.min(99,(seen.get(normalized)||0)+1));
    }catch{/* A malformed/unrelated request has no diagnostic value. */}
  }
  function wrappedFetch(input,init) {
    try{record(input,init&&init.method||
      (input&&typeof input.method==='string'?input.method:'GET'));}catch{}
    return savedFetch.apply(this,arguments);
  }
  function wrappedOpen(method,url) {
    try{record(url,method);}catch{}
    return savedOpen.apply(this,arguments);
  }
  window.fetch=wrappedFetch;
  XMLHttpRequest.prototype.open=wrappedOpen;
  window[key]={
    stop:function(){
      if(window.fetch===wrappedFetch)window.fetch=savedFetch;
      if(XMLHttpRequest.prototype.open===wrappedOpen)
        XMLHttpRequest.prototype.open=savedOpen;
      delete window[key];
      return JSON.stringify({ok:true,observed:[...seen].map(([entry,count])=>{
        const [method,...parts]=entry.split(' ');
        const path=parts.join(' ');
        return {method,path,count,
          kind:path==='/sys/stu/center/signupactivity/list'||
            path==='/sys/stu/score/list'?'known_personal':'unverified_candidate'};
      }),overflow});
    }
  };
  return JSON.stringify({ok:true,mode:'recording'});
})()
