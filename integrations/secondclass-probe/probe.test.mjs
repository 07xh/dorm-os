import test from 'node:test';
import assert from 'node:assert/strict';
import {run} from './probe.mjs';
const origin='https://dekt.ncwu.edu.cn';
const url=origin+'/sys/stu/score/list';
const student='SYNTHETIC_ACCOUNT_123';
const raw={data:[{student_code:student,school_term_index:100,score_value:0,score_plus:2}],totalPage:0,totalSize:1,startIndex:0};
function response(body=raw,status=200){
 const r=new Response(typeof body==='string'?body:JSON.stringify(body),{status});
 Object.defineProperty(r,'url',{value:url});return r;
}
test('one real-response-shaped score sample matches local account, including zero points',async()=>{
 let count=0;
 const result=await run({origin,expectedStudentCode:student,fetcher:async(actual,opts)=>{
  count++;assert.equal(actual,url);assert.equal(opts.method,'POST');
  assert.equal(opts.credentials,'same-origin');assert.equal(opts.redirect,'error');
  assert.equal(opts.body,'');assert.equal(opts.headers.cookie,undefined);
  return response();
 }});
 assert.equal(count,1);assert.equal(result.ok,true);
 assert.equal(result.identity.matchesLocalInput,true);
 assert.deepEqual(result.score,{school_term_index:100,score_value:0,score_plus:2});
 assert.equal(result.transport,'browser_same_origin');
 assert.equal(result.scope,'single_score_sample');
 assert.equal(JSON.stringify(result).includes(student),false);
});
test('wrong origin or missing local identity performs no request',async()=>{
 let calls=0;const fetcher=async()=>{calls++;return response();};
 for(const args of [{origin:'https://outsider.example',expectedStudentCode:student},{origin,expectedStudentCode:''}]){
  assert.equal((await run({...args,fetcher})).ok,false);
 }
 assert.equal(calls,0);
});
test('wrong or mixed account cannot produce success',async()=>{
 for(const data of [[{...raw.data[0],student_code:'OTHER'}],[raw.data[0],{...raw.data[0],student_code:'OTHER'}]]){
  const result=await run({origin,expectedStudentCode:student,fetcher:async()=>response({data})});
  assert.equal(result.ok,false);assert.equal(result.stage,'identity');
 }
});
test('login HTML, empty response, 5xx and invalid numeric score are explicit failures',async()=>{
 for(const value of [response('<html>login SECRET_TICKET</html>'),response({data:[]}),response({},503),response({data:[{...raw.data[0],score_value:'3'}]})]){
  const result=await run({origin,expectedStudentCode:student,fetcher:async()=>value});
  assert.equal(result.ok,false);assert.equal(JSON.stringify(result).includes('SECRET_TICKET'),false);
 }
});
test('network exceptions never echo credentials, URLs or arbitrary errors',async()=>{
 const result=await run({origin,expectedStudentCode:student,fetcher:async()=>{throw Error('COOKIE=SECRET_TICKET');}});
 assert.equal(result.ok,false);assert.equal(result.stage,'score_request');
 assert.equal(JSON.stringify(result).includes('SECRET_TICKET'),false);
});
