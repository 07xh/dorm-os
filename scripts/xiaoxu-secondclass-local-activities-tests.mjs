import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
const script=readFileSync('apps/xiaoxu-android/app/src/main/assets/ncwu_secondclass_local.js','utf8');
const account='SYNTHETIC_SELF';
const activity=(id='ACT1')=>({id,student_code:account,activity_name:'合成活动',activity_start:'2026-11-01 08:30:00',
  signup_state:'1',verify_state:'1',activity_state:'5',score_value:7.5});
async function read(pages,options={}){
 let requests=[];
 const location={origin:'https://dekt.ncwu.edu.cn',pathname:'/sys/stu/score'};
 const score={student_code:account,school_term_index:1,score_value:7.5,score_plus:0,
  ...Object.fromEntries(Array.from({length:6},(_,i)=>['activity_type'+(i+1)+'_score',0]))};
 const fetch=async(url,opts)=>{
  requests.push({url,body:opts.body});assert.equal(opts.method,'POST');assert.equal(opts.credentials,'same-origin');
  assert.equal(opts.redirect,'error');assert.equal(opts.headers.cookie,undefined);
  const isScore=url.endsWith('/score/list');
  if(!isScore)assert.equal(url,'https://dekt.ncwu.edu.cn/sys/stu/center/signupactivity/list');
  const page=Number(new URLSearchParams(opts.body).get('page'));
  if(!isScore&&options.throwPage===page)throw Error('SYNTHETIC_SECRET');
  const raw=isScore?{data:[score],totalSize:1,totalPage:0,startIndex:0}:pages[page-1];
  return {url,status:!isScore&&options.errorPage===page?503:200,headers:{get:()=> 'text/plain'},
   text:async()=>{if(!isScore&&options.navigate)location.pathname='/login';return JSON.stringify(raw);}};
 };
 const fn=runInNewContext(script,{location,document:{querySelectorAll:()=>[{innerText:'本人['+account+']',getClientRects:()=>[{}]}]},fetch,AbortController,setTimeout,clearTimeout});
 const result=await fn('',true);assert.equal(JSON.stringify(result).includes(account),false);
 return {result,requests};
}
const page=(data,totalPage=1,totalSize=data.length)=>({data,totalPage,totalSize,startIndex:0});
test('same-session paginated activity read returns complete personal records and known settlement',async()=>{
 const {result,requests}=await read([page([activity()],2,2),page([activity('ACT2')],2,2)]);
 assert.equal(result.ok,true);assert.equal(result.activitiesComplete,true);assert.equal(result.activities.length,2);
 assert.equal(result.activities[0].settlementStatus,'settled');assert.equal(result.activities[0].points,7.5);
 assert.equal(result.activities[0].startTime,'2026-11-01 08:30:00');
 assert.equal(requests[1].body,'page=1&pagesize=50');assert.equal(requests[2].body,'page=2&pagesize=50');
});
test('verified zero activities remains a complete result without invented items',async()=>{
 const {result}=await read([page([],0,0)]);assert.equal(result.ok,true);assert.equal(result.activitiesComplete,true);
 assert.equal(result.activities.length,0);
});
test('partial failure, mixed identity, duplicate IDs and changing pagination reject the entire snapshot',async()=>{
 for(const [pages,opts] of [
  [[page([activity()],2,2),page([activity('ACT2')],2,2)],{errorPage:2}],
  [[page([{...activity(),student_code:'OTHER'}])],{}],
  [[page([activity()],2,2),page([activity()],2,2)],{}],
  [[page([activity()],2,2),page([activity('ACT2')],2,3)],{}],
  [[page([activity()],2,2),page([],2,2)],{}],
  [[page([activity()])],{navigate:true}]
 ]){const {result}=await read(pages,opts);assert.equal(result.ok,false);assert.equal(result.rows,undefined);}
});
test('pending activities never count as settled credit and unknown review stays unknown',async()=>{
 const {result}=await read([page([{...activity(),activity_state:'6',verify_state:'1'}])]);
 assert.equal(result.activities[0].settlementStatus,'awaiting_settlement');
 assert.equal(result.activities[0].points,undefined);assert.equal(result.activities[0].reviewStatus,'unknown');
});
test('unknown and missing verification codes never imply a negative review or settlement',async()=>{
 for(const state of [null,undefined,'9',1,{value:'0'}]){
  const {result}=await read([page([{...activity(),verify_state:state}])]);
  assert.equal(result.activities[0].settlementStatus,'unknown');
 }
 const {result}=await read([page([{...activity(),activity_state:'NEW'}])]);
 assert.equal(result.activities[0].settlementStatus,'unknown');
});
test('failed later request has no fabricated previous HTTP status',async()=>{
 const {result}=await read([page([activity()],2,2),page([activity('ACT2')],2,2)],{throwPage:2});
 assert.equal(result.ok,false);assert.equal(result.stage,'activity_request');assert.equal(result.httpStatus,null);
 assert.equal(JSON.stringify(result).includes('SYNTHETIC_SECRET'),false);
});
