import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';

// Exercise the actual Service Worker rather than only inspecting source strings.
const sw=readFileSync(new URL('../public/sw.js',import.meta.url),'utf8');
const html=readFileSync(new URL('../public/index.html',import.meta.url),'utf8');
assert.ok(sw.includes('dorm-os-shell-__XIAOXU_RELEASE_SHA__'),
 'Every release must stamp a fresh PWA cache name');
assert.ok(html.includes('name="xiaoxu-release" content="__XIAOXU_RELEASE_SHA__"'),
 'The browser must know the exact HTML release it is executing');
assert.ok(html.includes("updateViaCache:'none'")&&html.includes('checkPublishedRelease()'),
 'Existing PWA tabs must recheck the live release on resume');
assert.ok(!html.includes('androidAutomationGuide')&&!html.includes('MacroDroid'),
 'Retired Android automation tutorial and handlers must be removed');
const literalAssets=sw.match(/const ASSETS=\[([\s\S]*?)\];/)?.[1];
assert.ok(literalAssets,'Expected explicit PWA app-shell asset list');
const assets=new Set([...literalAssets.matchAll(/'([^']+)'/g)]
 .map(m=>m[1].split('?')[0]));
const linked=[...html.matchAll(/<(?:script|link)\b[^>]*?(?:src|href)="(\/xiaoxu-[^"]+\.(?:js|css)(?:\?[^"]*)?)"/g)]
 .map(m=>m[1].split('?')[0]);
assert.ok(linked.length>=12,'Expected real page asset references');
for(const path of linked)assert.ok(assets.has(path),'Missing offline app-shell asset: '+path);

const handlers=new Map(),entries=new Map(),calls=[];
const keyOf=request=>typeof request==='string'?new URL(request,'https://test.invalid').href:request.url;
const appCache={
 async match(request){return entries.get(keyOf(request))?.clone()||undefined;},
 async put(request,response){entries.set(keyOf(request),response.clone());},
 async addAll(){}
};
const caches={
 open:async()=>appCache,
 match:request=>appCache.match(request),
 keys:async()=>[],
 delete:async()=>true
};
let offline=false;
const fetcher=async(request,options)=>{
 calls.push({url:keyOf(request),cache:options?.cache});
 if(offline)throw Error('offline');
 return new Response('current-deployed-script',{status:200});
};
const self={
 location:{origin:'https://test.invalid'},
 addEventListener:(type,listener)=>handlers.set(type,listener),
 skipWaiting:async()=>{},
 clients:{claim:async()=>{}}
};
runInNewContext(sw,{self,caches,fetch:fetcher,URL,Request,Response,Promise,Set});
const onFetch=handlers.get('fetch');
assert.equal(typeof onFetch,'function');
async function dispatch(url){
 let response,waits=[];
 onFetch({request:new Request(url),respondWith:p=>{response=p;},
  waitUntil:p=>{waits.push(p);}});
 if(!response)return null;
 const result=await response;
 await Promise.all(waits);
 return result.text();
}
const appScript='https://test.invalid/xiaoxu-academic-client.js';
entries.set(appScript,new Response('outdated-deployed-script'));
assert.equal(await dispatch(appScript),'current-deployed-script',
 'Online app should replace stale cached JS with current deployed code');
assert.equal(calls.at(-1)?.cache,'no-store',
 'PWA should bypass browser HTTP cache for versionless app scripts');
assert.equal(await(await appCache.match(new Request(appScript))).text(),
 'current-deployed-script','Successful network load should refresh offline cache');

offline=true;
assert.equal(await dispatch(appScript),'current-deployed-script',
 'Offline app should reuse last successful script');
// A navigation must bypass the HTTP cache even under an old active worker.
offline=false;
const waits=[];
let navigationResponse;
onFetch({request:{url:'https://test.invalid/',method:'GET',mode:'navigate'},
 respondWith:p=>{navigationResponse=p;},waitUntil:p=>waits.push(p)});
assert.equal(await (await navigationResponse).text(),'current-deployed-script');
await Promise.all(waits);
assert.equal(calls.at(-1)?.cache,'no-store');
const revisionText=await dispatch('https://test.invalid/revision.json');
assert.equal(revisionText,'current-deployed-script');
assert.equal(calls.at(-1)?.cache,'no-store',
 'Revision manifest must never be satisfied by stale browser cache');
const count=calls.length;
assert.equal(await dispatch('https://test.invalid/api/v1/connections'),null,
 'Private academic API must never be intercepted or cached by the static worker');
assert.equal(calls.length,count,'Private API stays outside SW fetch interception');
console.log('Academic PWA JS: complete asset precache, live updates, offline fallback and private API isolation passed');
