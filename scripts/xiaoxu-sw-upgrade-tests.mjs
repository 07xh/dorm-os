import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
const code=readFileSync('public/sw.js','utf8'),handlers={};
let skipped=false;const cached=new Map();
const cache={async addAll(requests){
 if(requests.some(r=>String(typeof r==='string'?r:r.url).includes('xiaoxu-workspace.css')))throw Error('synthetic asset outage');
 for(const r of requests){const url=new URL(typeof r==='string'?r:r.url,'https://test.invalid').href;cached.set(url,new Response('fresh',{headers:{'content-type':'text/plain'}}));}
},async put(r,v){cached.set(typeof r==='string'?new URL(r,'https://test.invalid').href:r.url,v);},async match(r,options){const url=new URL(typeof r==='string'?r:r.url,'https://test.invalid');for(const [key,value] of cached){const candidate=new URL(key);if(options?.ignoreSearch?candidate.pathname===url.pathname:key===url.href)return value.clone();}}};
const self={location:{origin:'https://test.invalid'},addEventListener:(t,f)=>handlers[t]=f,skipWaiting:async()=>{skipped=true;},clients:{claim:async()=>{},matchAll:async()=>[]}};
const caches={open:async()=>cache,keys:async()=>[],delete:async()=>true};
vm.runInNewContext(code,{self,caches,URL,Request,Response,Promise,fetch:async()=>{throw Error('offline');}});
let installing;handlers.install({waitUntil:p=>installing=p});
await assert.doesNotReject(installing,'one optional asset outage must not keep the old worker active forever');
assert.equal(skipped,true);
cached.set('https://test.invalid/xiaoxu-workspace.js',new Response('current-offline-asset'));
let responding;handlers.fetch({request:new Request('https://test.invalid/xiaoxu-workspace.js?_xu_asset=bbbbbbbbbbbb'),respondWith:p=>responding=p,waitUntil(){}});
assert.equal(await(await responding).text(),'current-offline-asset','release-tagged HTML must find the canonical cached asset offline');
console.log('SW upgrade: optional asset failure does not strand old worker; versioned assets retain offline fallback');
