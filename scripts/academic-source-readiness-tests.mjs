import assert from 'node:assert/strict';
import {SOURCES,checkSources} from '../functions/api/academic/status.js';

assert.deepEqual(SOURCES.map(s=>s.id),['kingo','chaoxing','second']);
assert.ok(SOURCES.every(s=>s.url.startsWith('https://')&&s.url.endsWith('/')||s.url.endsWith('/portal')));
const calls=[];
const check=await checkSources(async (url,options)=>{
 calls.push({url,method:options.method,redirect:options.redirect,credentials:options.credentials});
 if(url.includes('jwmis')) return new Response('',{status:302,headers:{location:'/login'}});
 if(url.includes('fanya')) return new Response('',{status:503});
 throw Error('Timed out');
});
assert.equal(check.schema,'xiaoxu/academic-readiness-v1');
assert.equal(check.personalData,false);
assert.equal(check.accountConnected,false);
assert.equal(check.fullyVerified,false);
assert.equal(check.sources.length,3);
assert.deepEqual(check.sources.map(s=>s.reachability),['responded','unavailable','unavailable']);
assert.deepEqual(check.sources.map(s=>s.accountStatus),['not-connected','not-connected','not-connected']);
assert.ok(calls.every(x=>x.method==='HEAD'&&x.redirect==='manual'&&x.credentials==='omit'));
assert.ok(calls.every(x=>SOURCES.some(s=>s.url===x.url)),'No user-provided fetch target');
assert.equal(check.sources[0].httpStatus,302);
assert.equal(check.sources[1].httpStatus,503);
const methods=[];
const fallback=await checkSources(async(url,options)=>{
 if(url.includes('jwmis')){methods.push(options.method);return new Response('unavailable',{status:options.method==='HEAD'?405:200});}
 return new Response('unavailable',{status:403});
});
assert.deepEqual(methods,['HEAD','GET'],'HEAD only retries once when source does not support it');
assert.equal(fallback.sources[0].reachability,'responded');
assert.equal(fallback.sources[1].reachability,'responded','403 proves an HTTP response, not login ability');
assert.equal(fallback.sources[1].accountStatus,'not-connected');
const moved=await checkSources(async url=>{
 return {status:200,url:'https://example.org/login',body:null};
});
assert.ok(moved.sources.every(s=>s.reachability==='unavailable'),'Redirected non-school origin must not be accepted');
console.log('Academic readiness: public origin allowlist, no auth/session data, HEAD/GET fallback and true login-state boundary passed');
