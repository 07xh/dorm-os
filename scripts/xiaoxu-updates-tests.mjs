import assert from 'node:assert/strict';
import {readFileSync,existsSync} from 'node:fs';
import vm from 'node:vm';
const file='public/xiaoxu-updates.js';
assert.ok(existsSync(file),'release recovery must run independently of application startup');
const code=readFileSync(file,'utf8');
const OLD='a'.repeat(40),NEW='b'.repeat(40);
function environment({built=OLD,blocked=false,online=true,manifest=NEW,reloadState=null,storageFails=false,url='https://test.invalid/?keep=1#tasks'}={}){
 const events={},docEvents={},swEvents={},session=new Map([['xiaoxu-release-reload-target',NEW]]),removed=[],navigations=[],requests=[];
 if(reloadState)session.set('xiaoxu-release-reload-state',JSON.stringify(reloadState));
 let notices=0,edited=blocked;
 const document={hidden:false,readyState:'complete',
  querySelector(selector){if(selector.startsWith('meta['))return {content:built};if(selector==='dialog[open]')return edited?{}:null;return null;},
  getElementById(){return null;},
  createElement(){return {id:'',setAttribute(){},replaceChildren(){},append(){},textContent:'',addEventListener(){}};},
  body:{append(){notices++;}},addEventListener(type,fn){docEvents[type]=fn;}};
 const context={document,navigator:{onLine:online,serviceWorker:{register:async()=>({update:async()=>{}}),getRegistration:async()=>({update:async()=>{}}),addEventListener(t,fn){swEvents[t]=fn;}}},
  location:{protocol:'https:',hostname:'test.invalid',href:url,replace(url){navigations.push(url);}},
  sessionStorage:{getItem:k=>{if(storageFails)throw Error('storage blocked');return session.get(k)||null;},setItem:(k,v)=>{if(storageFails)throw Error('quota exceeded');session.set(k,v);},removeItem:k=>session.delete(k)},
  caches:{keys:async()=>['dorm-os-shell-old','unrelated-cache'],delete:async key=>{removed.push(key);return true;}},
  fetch:async(url,options)=>{requests.push({url,options});return {ok:true,json:async()=>({commit:manifest})};},
  addEventListener(t,fn){events[t]=fn;},setInterval(){},setTimeout(fn){queueMicrotask(fn);},
  MutationObserver:class{observe(){}},URL,Date,Promise,Set,console};
 context.globalThis=context;vm.runInNewContext(code,context);
 return {context,navigations,removed,requests,session,events,docEvents,swEvents,get notices(){return notices;},unblock(){edited=false;}};
}
const settle=async()=>{for(let i=0;i<12;i++)await Promise.resolve();};
const first=environment();await settle();
assert.equal(first.navigations.length,1,'a previous failed reload target must not permanently lock this tab on old HTML');
assert.match(first.navigations[0],/_xu_release=bbbbbbbbbbbb/);
assert.ok(first.navigations[0].endsWith('#tasks'),'keep current page');
assert.ok(first.navigations[0].includes('keep=1'),'keep legitimate query values');
assert.equal(first.requests[0].options.cache,'no-store');
const edit=environment({blocked:true});await settle();assert.equal(edit.navigations.length,0,'never discard an open editor');
edit.unblock();await edit.context.XiaoxuUpdates.checkPublishedRelease();await settle();
assert.equal(edit.navigations.length,1,'save/close resumes the postponed update automatically');
const offline=environment({online:false});await settle();assert.equal(offline.navigations.length,0);assert.equal(offline.requests.length,0);
const bounded=environment({reloadState:{target:NEW,attempts:2}});await settle();assert.equal(bounded.navigations.length,0,'failed server delivery must not create an infinite reload loop');assert.ok(bounded.notices>0);
const noStorage=environment({storageFails:true});await settle();assert.equal(new URL(noStorage.navigations[0]).searchParams.get('_xu_attempts'),'1','blocked session storage needs a bounded URL retry guard');
const noStorageAgain=environment({storageFails:true,url:noStorage.navigations[0]});await settle();assert.equal(new URL(noStorageAgain.navigations[0]).searchParams.get('_xu_attempts'),'2');
const noStorageStop=environment({storageFails:true,url:noStorageAgain.navigations[0]});await settle();assert.equal(noStorageStop.navigations.length,0,'blocked storage must not cause endless stale-page reloads');
const malformed=environment({manifest:'not-a-revision'});await settle();assert.equal(malformed.navigations.length,0,'never navigate based on an invalid manifest');
const current=environment({built:NEW,reloadState:{target:NEW,attempts:2}});await settle();assert.equal(current.navigations.length,0,'current page does not reload-loop');
assert.equal(current.session.has('xiaoxu-release-reload-state'),false,'successful arrival resets the retry guard');
assert.equal(current.removed.length,0,'normal update detection must not wipe offline data');
console.log('Release recovery: failed-target retry, edited-page protection, automatic resume, offline retention and current-version stability passed');
