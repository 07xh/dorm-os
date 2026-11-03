/* Foreground-only second-class sync regression; synthetic owner session, no real school data. */
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';

const script=readFileSync(new URL('../public/xiaoxu-academic-client.js',import.meta.url),'utf8');
const minute=60*1000;
const response=(data,status=200)=>({ok:status>=200&&status<300,status,
 async json(){return data;}});

function makeBrowser({authorized=true,minutesOld=30,fail=false,hold=false}={}){
 const events=[],listeners=new Map(),intervals=[];
 let lastRead=new Date(Date.now()-minutesOld*minute).toISOString();
 let syncCount=0,releaseSync=null;
 const school=()=>({
  id:'second_class',name:'第二课堂',status:authorized?'connected':'not_connected',
  sessionState:authorized?'active':'not_connected',
  accountAuthorization:authorized?'verified_session':'not_verified',
  trustedKinds:authorized?['second-class']:[],
  lastSuccessAt:lastRead,authFlow:{method:'school_cas_external',
   officialLoginUrl:'https://dekt.ncwu.edu.cn/'},
  nativeCasPreflight:true
 });
 const http=async(url)=>{
  if(url==='/api/v1/connections')
   return response({schema:'xiaoxu/connections-v1',
    platforms:[{id:'ncwu',status:'not_connected'},{id:'chaoxing',status:'not_connected'},school()]});
  if(url==='/api/v1/second-class')
   return response({status:authorized?'ready':'not_connected',
    records:authorized?[{title:'合成活动'}]:[],
    lastSuccessAt:lastRead,provenance:authorized?'trusted_readonly':'user_import'});
  if(url.startsWith('/api/v1/verified/'))
   return response({status:'not_connected',records:[]});
  if(url==='/api/v1/connections/second_class/sync'){
   syncCount++;
   if(hold)return new Promise(resolve=>{releaseSync=()=>resolve(response({refreshed:true}));});
   if(fail)return response({error:'session_expired'},409);
   lastRead=new Date().toISOString();
   return response({refreshed:true});
  }
  throw Error('Unexpected request '+url);
 };
 const doc={hidden:false,addEventListener(type,cb){
  if(!listeners.has(type))listeners.set(type,[]);
  listeners.get(type).push(cb);
 }};
 const sandbox={
  document:doc,fetch:http,console,Date,Promise,
  setInterval:(cb,ms)=>{assert.equal(ms,60*1000);intervals.push(cb);return intervals.length;},
  Event:class{constructor(type){this.type=type;}},
  dispatchEvent:e=>events.push(e.type)
 };
 runInNewContext(script,sandbox,{filename:'xiaoxu-academic-client.js'});
 return {api:sandbox.XiaoxuAcademic,doc,events,listeners,intervals,
  syncCount:()=>syncCount,releaseSync:()=>releaseSync?.(),
  visibility(){for(const cb of listeners.get('visibilitychange')||[])cb();}};
}
async function settle(predicate){
 for(let n=0;n<100;n++){
  if(predicate())return;
  await new Promise(resolve=>setImmediate(resolve));
 }
 throw Error('Timed out waiting for foreground sync');
}
const stale=makeBrowser();
assert.equal(await stale.api.connect(),true);
await settle(()=>stale.syncCount()===1&&stale.events.length===1);
assert.equal(stale.api.state('second-class').records[0].title,'合成活动');
stale.visibility();
stale.intervals[0]();
await new Promise(resolve=>setImmediate(resolve));
assert.equal(stale.syncCount(),1,'duplicate visibility/interval events must not resync');

const fresh=makeBrowser({minutesOld:1});
await fresh.api.connect();
await new Promise(resolve=>setImmediate(resolve));
assert.equal(fresh.syncCount(),0,'recent trusted snapshot must not cause a network request');

const imported=makeBrowser({authorized:false});
await imported.api.connect();
imported.visibility();
await new Promise(resolve=>setImmediate(resolve));
assert.equal(imported.syncCount(),0,'missing school authorization must never sync');

const failure=makeBrowser({fail:true});
await failure.api.connect();
await settle(()=>failure.events.length===1);
assert.equal(failure.syncCount(),1);
assert.equal(failure.api.state('second-class').records[0].title,'合成活动',
 'temporary failure must preserve the last verified snapshot');
assert.ok(failure.api.connectionPanel().includes('第二课堂自动刷新失败'));

const logout=makeBrowser({hold:true});
await logout.api.connect();
await settle(()=>logout.syncCount()===1);
logout.api.disconnect();
logout.releaseSync();
await new Promise(resolve=>setImmediate(resolve));
await new Promise(resolve=>setImmediate(resolve));
assert.equal(logout.api.state('second-class').status,'not_connected');
assert.equal(logout.events.length,0,'stale pending network responses must not repopulate logged-out UI');
console.log('Second-class foreground sync: owner session, staleness, debounce, errors, logout isolation passed');
