import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';

const script=readFileSync('apps/xiaoxu-android/app/src/main/assets/ncwu_secondclass_source_observer.js','utf8');
function officialPage(origin='https://dekt.ncwu.edu.cn') {
  let fetchCalls=0, xhrCalls=0;
  const originalFetch=function () {fetchCalls++;return Promise.resolve({ok:true});};
  function XHR(){}
  const originalOpen=function () {xhrCalls++;return true;};
  XHR.prototype.open=originalOpen;
  const window={fetch:originalFetch};
  const location={origin,href:origin+'/sys/activity/page'};
  const context={window,location,XMLHttpRequest:XHR,URL,Map};
  return {context,window,XHR,originalFetch,originalOpen,
    calls:()=>({fetchCalls,xhrCalls}),start:()=>JSON.parse(runInNewContext(script,context))};
}
function stop(page){
  return JSON.parse(page.window.__xiaoxuSecondClassSourceObserverV1.stop());
}

test('manual install does not send requests, and returns only static official same-origin paths',async()=>{
  const page=officialPage();
  assert.deepEqual(page.start(),{ok:true,mode:'recording'});
  assert.deepEqual(page.calls(),{fetchCalls:0,xhrCalls:0},
    'observer cannot initiate a school network request');
  await page.window.fetch('/sys/stu/center/signupactivity/list?token=SECRET',
    {method:'POST',body:'student_code=SECRET'});
  const xhr=new page.XHR();
  xhr.open('GET','/sys/activity/catalog/list?session=SECRET');
  xhr.open('POST','https://example.invalid/sys/activity/catalog/list');
  xhr.open('GET','/sys/student/876543/list?token=SECRET');
  xhr.open('GET','/sys/cas/auth/token');
  xhr.open('GET','/sys/static/bundle.js');
  xhr.open('POST','/sys/activity/catalog/list?my_private_query=SECRET');
  assert.deepEqual(page.calls(),{fetchCalls:1,xhrCalls:6},
    'wrappers delegate without modifying the original request');
  const result=stop(page);
  assert.equal(result.ok,true);
  assert.deepEqual(result.observed,[
    {method:'POST',path:'/sys/stu/center/signupactivity/list',
      count:1,kind:'known_personal'},
    {method:'GET',path:'/sys/activity/catalog/list',
      count:1,kind:'unverified_candidate'},
    {method:'POST',path:'/sys/activity/catalog/list',
      count:1,kind:'unverified_candidate'}
  ]);
  assert.equal(JSON.stringify(result).includes('SECRET'),false);
  assert.equal(page.window.fetch,page.originalFetch,'must restore official page fetch');
  assert.equal(page.XHR.prototype.open,page.originalOpen,'must restore XHR.open');
  assert.equal(page.window.__xiaoxuSecondClassSourceObserverV1,undefined);
});

test('unrelated origins are excluded and never instrumented',()=>{
  const page=officialPage('https://other.school.example');
  assert.deepEqual(page.start(),{ok:false,reason:'official_page_required'});
  assert.equal(page.window.fetch,page.originalFetch);
  assert.equal(page.XHR.prototype.open,page.originalOpen);
  assert.deepEqual(page.calls(),{fetchCalls:0,xhrCalls:0});
});

test('double-start is rejected, unknown URLs suppressed, and output bounded',()=>{
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

test('observer never captures response bodies, passwords or non-route URLs',async()=>{
  const page=officialPage();
  assert.equal(page.start().ok,true);
  await page.window.fetch('/sys/activity/list', {method:'PUT',body:'password=SECRET'});
  await page.window.fetch('https://dekt.ncwu.edu.cn/sys/activity/list?secret=SECRET', {
    method:'POST',headers:{cookie:'SECRET'},body:'private=SECRET'
  });
  const result=stop(page);
  assert.deepEqual(result.observed,[
    {method:'POST',path:'/sys/activity/list',count:1,kind:'unverified_candidate'}
  ]);
  assert.equal(JSON.stringify(result).includes('SECRET'),false);
});
