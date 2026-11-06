import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';

const script=readFileSync(
  'apps/xiaoxu-android/app/src/main/assets/ncwu_secondclass_source_observer.js','utf8');
function officialPage(origin='https://dekt.ncwu.edu.cn') {
  let fetchCalls=0,xhrCalls=0;
  const originalFetch=function () {
    fetchCalls++;
    return Promise.resolve({
      status:200,
      headers:{get:name=>name==='content-type'?'application/json; charset=utf-8':null},
      text:async()=>'{ "SECRET_PRIVATE_RESPONSE": true }'
    });
  };
  function XHR() {
    this.status=404;
    this.mime='text/html';
    this.listeners={};
    this.addEventListener=(name,callback)=>{
      (this.listeners[name]??=[]).push(callback);
    };
    this.getResponseHeader=name=>name==='content-type'?this.mime:null;
    this.fire=name=>(this.listeners[name]||[]).forEach(callback=>callback());
    this.responseText='{ "SECRET_PRIVATE_RESPONSE": true }';
  }
  const originalOpen=function () {xhrCalls++;return true;};
  XHR.prototype.open=originalOpen;
  const window={fetch:originalFetch};
  const location={origin,href:origin+'/sys/activity/page'};
  const context={window,location,XMLHttpRequest:XHR,URL,Map};
  return {context,window,XHR,originalFetch,originalOpen,
    calls:()=>({fetchCalls,xhrCalls}),
    start:()=>JSON.parse(runInNewContext(script,context))};
}
const stop=page=>JSON.parse(page.window.__xiaoxuSecondClassSourceObserverV1.stop());

test('user activation does not issue network requests; path/status/MIME only',async()=>{
  const page=officialPage();
  assert.deepEqual(page.start(),{ok:true,mode:'recording'});
  assert.deepEqual(page.calls(),{fetchCalls:0,xhrCalls:0});
  await page.window.fetch('/sys/stu/center/signupactivity/list?token=SECRET',
    {method:'POST',body:'student_code=SECRET'});
  const first=new page.XHR();
  first.open('GET','/sys/activity/catalog/list?session=SECRET');
  first.fire('loadend');
  const x=new page.XHR();
  x.open('POST','https://example.invalid/sys/activity/catalog/list');
  x.open('GET','/sys/student/876543/list?token=SECRET');
  x.open('GET','/sys/cas/auth/token');
  x.open('GET','/sys/static/bundle.js');
  const second=new page.XHR();
  second.status=201;second.mime='application/problem+json; charset=utf-8';
  second.open('POST','/sys/activity/catalog/list?my_private_query=SECRET');
  second.fire('loadend');
  assert.deepEqual(page.calls(),{fetchCalls:1,xhrCalls:6});
  const result=stop(page);
  assert.equal(result.ok,true);
  assert.deepEqual(result.observed,[
    {method:'POST',path:'/sys/stu/center/signupactivity/list',
      count:1,kind:'known_personal',
      outcomes:[{status:200,type:'json',count:1}]},
    {method:'GET',path:'/sys/activity/catalog/list',
      count:1,kind:'unverified_candidate',
      outcomes:[{status:404,type:'html',count:1}]},
    {method:'POST',path:'/sys/activity/catalog/list',
      count:1,kind:'unverified_candidate',
      outcomes:[{status:201,type:'json',count:1}]}
  ]);
  assert.equal(JSON.stringify(result).includes('SECRET'),false);
  assert.equal(page.window.fetch,page.originalFetch);
  assert.equal(page.XHR.prototype.open,page.originalOpen);
  assert.equal(page.window.__xiaoxuSecondClassSourceObserverV1,undefined);
});

test('screenshot candidate duplicated /sys/stu/ path is preserved, never silently corrected',async()=>{
  const page=officialPage();
  assert.equal(page.start().ok,true);
  for(let i=0;i<5;i++)
    await page.window.fetch('/sys/stu/sys/stu/activity?token=SECRET',{method:'POST'});
  const result=stop(page);
  assert.deepEqual(result.observed,[{
    method:'POST',path:'/sys/stu/sys/stu/activity',
    count:5,kind:'unverified_candidate',
    outcomes:[{status:200,type:'json',count:5}]
  }]);
  assert.equal(JSON.stringify(result).includes('SECRET'),false);
});

test('unrelated origin never instruments fetch or XHR',()=>{
  const page=officialPage('https://other.school.example');
  assert.deepEqual(page.start(),{ok:false,reason:'official_page_required'});
  assert.equal(page.window.fetch,page.originalFetch);
  assert.equal(page.XHR.prototype.open,page.originalOpen);
});

test('double start rejected, paths and outcome classes are bounded',()=>{
  const page=officialPage();
  assert.equal(page.start().ok,true);
  assert.deepEqual(page.start(),{ok:false,reason:'already_recording'});
  for(let i=0;i<32;i++) {
    const path='/sys/activity/'+String.fromCharCode(97+Math.floor(i/26))+
      String.fromCharCode(97+i%26)+'/list';
    page.window.fetch(path);
  }
  const result=stop(page);
  assert.equal(result.observed.length,25);
  assert.equal(result.overflow,true);
  assert.ok(result.observed.every(x=>x.method==='GET'&&
    x.path.startsWith('/sys/activity/')&&x.kind==='unverified_candidate'));
});

test('observation excludes bodies, secrets, queries, and unsupported methods',async()=>{
  const page=officialPage();
  assert.equal(page.start().ok,true);
  await page.window.fetch('/sys/activity/list',{method:'PUT',body:'password=SECRET'});
  await page.window.fetch('https://dekt.ncwu.edu.cn/sys/activity/list?secret=SECRET',{
    method:'POST',headers:{cookie:'SECRET'},body:'private=SECRET'
  });
  const result=stop(page);
  assert.deepEqual(result.observed,[{
    method:'POST',path:'/sys/activity/list',count:1,
    kind:'unverified_candidate',
    outcomes:[{status:200,type:'json',count:1}]
  }]);
  assert.equal(JSON.stringify(result).includes('SECRET'),false);
  assert.equal(JSON.stringify(result).includes('PRIVATE_RESPONSE'),false);
});
