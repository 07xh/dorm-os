/*
 * User-initiated, local-only HTTP metadata observer in the already opened
 * official second-class business page. Never reads bodies, form contents,
 * passwords, cookies, authorization, storage, query strings or fragments.
 *
 * Source evidence in user screenshot: POST /sys/stu/sys/stu/activity.
 * DO NOT "fix" the repeated /sys/stu/ path; retain the browser's actual
 * resolved same-origin pathname. HTTP status/MIME alone never establishes
 * a verified activity catalog or personal enrollment.
 */
(function installSecondClassCatalogObserver() {
  'use strict';
  const key='__xiaoxuSecondClassSourceObserverV1';
  const host='https://dekt.ncwu.edu.cn';
  if(location.origin!==host||
     typeof window.fetch!=='function'||
     typeof XMLHttpRequest==='undefined'||
     typeof XMLHttpRequest.prototype.open!=='function')
    return JSON.stringify({ok:false,reason:'official_page_required'});
  if(window[key])
    return JSON.stringify({ok:false,reason:'already_recording'});
  const savedFetch=window.fetch;
  const savedOpen=XMLHttpRequest.prototype.open;
  const seen=new Map();
  let overflow=false;
  const safeMime=value=>{
    if(typeof value!=='string')return 'unknown';
    const type=value.toLowerCase().split(';')[0].trim();
    return type==='application/json'||type.endsWith('+json')?'json':
      type==='text/html'||type==='application/xhtml+xml'?'html':
      type?'other':'unknown';
  };
  function record(input,method) {
    try {
      if(typeof method!=='string'||!/^(GET|POST)$/i.test(method))return null;
      const raw=typeof input==='string'?input:
        (input&&typeof input.url==='string'?input.url:null);
      if(!raw||raw.length>2048)return null;
      // The observed NCWU activity HTML declares <base href="http://dekt.ncwu.edu.cn/">.
      // Its relative Ajax path "sys/stu/activity" resolves under the SITE ROOT,
      // not relative to /sys/stu/activity. The browser upgrades the official
      // base to HTTPS; only normalize that exact known origin/root base.
      let requestBase=location.href;
      try {
        const declared=typeof document!=='undefined'&&document.baseURI
          ?new URL(document.baseURI):null;
        if(declared&&declared.hostname==='dekt.ncwu.edu.cn'&&
           (declared.protocol==='https:'||declared.protocol==='http:')&&
           declared.pathname==='/'&&!declared.username&&!declared.password&&
           !declared.search&&!declared.hash)
          requestBase=host+'/';
      }catch{}
      const url=new URL(raw,requestBase);
      if(url.origin!==host||url.username||url.password)return null;
      // Only static paths; no query string, student identifiers, tokens,
      // response data or arbitrary URLs are retained.
      const path=url.pathname;
      if(path.length>130||!/^\/sys\/(?:[A-Za-z_-]+\/){1,7}[A-Za-z_-]+\/?$/.test(path)||
         /(?:login|auth|ticket|token|password|captcha|secret|studentcode)/i.test(path))
        return null;
      const key=method.toUpperCase()+' '+path;
      if(!seen.has(key)) {
        if(seen.size>=25){overflow=true;return null;}
        seen.set(key,{method:method.toUpperCase(),path,count:0,outcomes:new Map()});
      }
      const row=seen.get(key);
      row.count=Math.min(99,row.count+1);
      return key;
    }catch{return null;}
  }
  function outcome(key,status,contentType) {
    try {
      const row=seen.get(key);
      if(!row||!Number.isInteger(status)||status<100||status>599)return;
      const type=safeMime(contentType);
      const label=status+':'+type;
      if(!row.outcomes.has(label)&&row.outcomes.size>=6){overflow=true;return;}
      row.outcomes.set(label,Math.min(99,(row.outcomes.get(label)||0)+1));
    }catch{}
  }
  function wrappedFetch(input,init) {
    const tracked=record(input,init&&init.method||
      (input&&typeof input.method==='string'?input.method:'GET'));
    const result=savedFetch.apply(this,arguments);
    // Attach only passive listeners. Return the original Promise unchanged.
    if(tracked&&result&&typeof result.then==='function')
      result.then(response=>{
        try{outcome(tracked,response.status,
          response.headers&&response.headers.get('content-type'));}catch{}
      },()=>{});
    return result;
  }
  function wrappedOpen(method,url) {
    const tracked=record(url,method);
    const result=savedOpen.apply(this,arguments);
    if(tracked&&typeof this.addEventListener==='function')
      try{
        this.addEventListener('loadend',()=>{
          try{outcome(tracked,this.status,this.getResponseHeader('content-type'));}catch{}
        },{once:true});
      }catch{}
    return result;
  }
  window.fetch=wrappedFetch;
  XMLHttpRequest.prototype.open=wrappedOpen;
  window[key]={
    stop:function(){
      if(window.fetch===wrappedFetch)window.fetch=savedFetch;
      if(XMLHttpRequest.prototype.open===wrappedOpen)
        XMLHttpRequest.prototype.open=savedOpen;
      delete window[key];
      return JSON.stringify({ok:true,observed:[...seen.values()].map(row=>({
        method:row.method,path:row.path,count:row.count,
        kind:row.path==='/sys/stu/center/signupactivity/list'||
          row.path==='/sys/stu/score/list'?'known_personal':'unverified_candidate',
        outcomes:[...row.outcomes].map(([key,count])=>{
          const [status,type]=key.split(':');
          return {status:Number(status),type,count};
        })
      })),overflow});
    }
  };
  return JSON.stringify({ok:true,mode:'recording'});
})()
