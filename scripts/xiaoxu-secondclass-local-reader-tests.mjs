import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {webcrypto} from 'node:crypto';
const script=readFileSync('apps/xiaoxu-android/app/src/main/assets/ncwu_secondclass_local.js','utf8');
const account='SYNTHETIC_SELF';
const term=()=>({student_code:account,school_term_index:1,score_value:7.5,score_plus:1.25,
  ...Object.fromEntries(Array.from({length:6},(_,i)=>['activity_type'+(i+1)+'_score',i?0:7.5]))});
const aggregate=()=>{const {student_code,...r}=term();return {...r,school_term_index:100};};
const body=()=>({data:[term(),aggregate()],totalSize:2,totalPage:0,startIndex:0});
async function read(options={}){
 let calls=0,identity=account;
 const location={origin:options.origin??'https://dekt.ncwu.edu.cn',pathname:options.path??'/sys/stu/score'};
 const fetch=async(url,opts)=>{
  calls++;assert.equal(url,'https://dekt.ncwu.edu.cn/sys/stu/score/list');
  assert.equal(opts.method,'POST');assert.equal(opts.credentials,'same-origin');
  assert.equal(opts.redirect,'error');assert.equal(opts.body,'');
  assert.equal(opts.headers.cookie,undefined);
  if(options.throw)throw Error('SECRET_COOKIE_TICKET');
  if(options.navigate)location.pathname='/sys/stu/activity';
  return {status:options.status??200,url,redirected:false,
   headers:{get:()=>options.contentType??'text/plain'},
   text:async()=>{if(options.switchAccount)identity='SYNTHETIC_OTHER';
    if(options.removeAccount)identity='';
    return options.text??JSON.stringify(options.body??body());}};
 };
 const document={querySelectorAll:()=>options.noIdentity?[]:[{
  innerText:identity?'本人['+identity+']':'登录',getClientRects:()=>[{}]}]};
 const fn=runInNewContext(script,{location,document,fetch,AbortController,setTimeout,clearTimeout,crypto:webcrypto,TextEncoder});
 const result=await fn(options.salt??'');
 assert.equal(JSON.stringify(result).includes(account),false);
 assert.equal(JSON.stringify(result).includes('SECRET_COOKIE_TICKET'),false);
 return {result,calls};
}
test('real contract shape: identity-bearing semester plus anonymous school aggregate',async()=>{
 const {result,calls}=await read();assert.equal(calls,1);assert.equal(result.ok,true);
 assert.equal(result.httpStatus,200);assert.equal(result.contentType,'text/plain');
 assert.equal(result.rows.length,2);assert.equal(result.rows[1].qualityScore,7.5);
 assert.equal(result.rows[1].volunteerHours,1.25);assert.equal(result.identityMatched,true);
 assert.equal(result.fullSyncVerified,false);assert.equal(result.nativeSessionHandoffVerified,false);
});
test('zero score remains valid',async()=>{
 const b=body();for(const r of b.data)r.score_value=0;
 assert.equal((await read({body:b})).result.rows[1].qualityScore,0);
});
test('wrong origin, login route and missing visible account never request',async()=>{
 for(const opts of [{origin:'https://attacker.example'},{path:'/login'},{noIdentity:true}]){
  const {result,calls}=await read(opts);assert.equal(result.ok,false);assert.equal(calls,0);
 }
});
test('anonymous semester, mixed account and anonymous-only response fail identity',async()=>{
 for(const rows of [[aggregate()],[{...term(),student_code:'OTHER'},aggregate()],
  [{...term(),student_code:undefined},aggregate()],
  [term(),{...aggregate(),student_code:'OTHER'}]]){
  assert.equal((await read({body:{...body(),data:rows,totalSize:rows.length}})).result.stage,'identity');
 }
});
test('duplicate terms, incomplete paging and invalid scores fail closed',async()=>{
 for(const b of [{...body(),data:[term(),term()]},{...body(),totalSize:3},
  {...body(),totalPage:2},{...body(),data:[{...term(),score_value:'3'},aggregate()]},
  {...body(),data:[{...term(),activity_type2_score:-1},aggregate()]}]){
  assert.equal((await read({body:b})).result.ok,false);
 }
});
test('actual HTTP status survives errors; HTML is never echoed',async()=>{
 for(const status of [401,403,429,503]){
  const {result}=await read({status});assert.equal(result.httpStatus,status);
  assert.equal(result.stage,'score_http');assert.equal(result.ok,false);
 }
 const {result}=await read({text:'<html>SECRET_COOKIE_TICKET</html>'});
 assert.equal(result.stage,'score_response');assert.equal(result.ok,false);
});
test('network exceptions, navigation and oversized body cannot produce a snapshot',async()=>{
 for(const opts of [{throw:true},{navigate:true},{text:'x'.repeat(300001)}])
  assert.equal((await read(opts)).result.ok,false);
});
test('account changed or disappeared while awaiting the response rejects the snapshot',async()=>{
 for(const opts of [{switchAccount:true},{removeAccount:true}]){
  const {result}=await read(opts);assert.equal(result.ok,false);assert.equal(result.stage,'identity');
 }
});
test('device-salted account binding is stable per device and contains no identifier',async()=>{
 const a=(await read({salt:'a'.repeat(32)})).result;
 const b=(await read({salt:'b'.repeat(32)})).result;
 assert.match(a.accountHash,/^[a-f0-9]{64}$/);
 assert.equal(a.accountHash,(await read({salt:'a'.repeat(32)})).result.accountHash);
 assert.notEqual(a.accountHash,b.accountHash);
});
