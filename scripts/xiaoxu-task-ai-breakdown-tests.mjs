import assert from 'node:assert/strict';
import {test} from 'node:test';
import {
 parseTaskBreakdownInput,validateTaskBreakdownOutput,runTaskBreakdownAI,
 TASK_BREAKDOWN_MODEL
} from '../lib/academic-task-ai-breakdown.mjs';
import {handleBreakdownPost} from '../functions/api/v1/task-breakdown-ai.js';

test('only explicit title + notes are accepted, no model/key URL from caller',()=>{
 assert.deepEqual(parseTaskBreakdownInput({title:' 实验报告 ',notes:' 整理数据 '}),{
  title:'实验报告',notes:'整理数据'});
 for(const item of [null,{}, {title:'x',provider:'other'}, {title:'x',apiKey:'secret'},
  {title:'x'.repeat(121)}, {title:'x',notes:'y'.repeat(601)}])
  assert.equal(parseTaskBreakdownInput(item),null);
});
test('reject invalid, duplicated or fabricated result shapes',()=>{
 assert.deepEqual(validateTaskBreakdownOutput({steps:[' A ','B']}),['A','B']);
 for(const item of [{steps:['A']},{steps:['A','a']},{steps:['A','B\nC']},
  {steps:['A',42]}, {steps:['A','B'],saved:true}, {steps:Array(13).fill('x')}])
  if(item.steps.length!==2 || item.steps[0]==='A' && item.steps[1]==='B\nC' ||
    typeof item.steps[1]!=='string' || item.steps[0]===item.steps[1] ||
    item.steps[0]==='A' && item.steps[1]==='a')assert.equal(validateTaskBreakdownOutput(item),null);
 assert.equal(validateTaskBreakdownOutput({steps:['A','B']})?.length,2);
});
test('binding outputs are validated before returning to app',async()=>{
 let calls=0;
 const ai={run:async(model,arg)=>{calls++;assert.equal(model,TASK_BREAKDOWN_MODEL);
  assert.equal(arg.messages[1].content.includes('课程作业'),true);
  return {response:{steps:['检查要求','完成初稿','复核提交']}};}};
 assert.deepEqual(await runTaskBreakdownAI(ai,{title:'课程作业',notes:''}),
  ['检查要求','完成初稿','复核提交']);
 assert.equal(calls,1);
 await assert.rejects(()=>runTaskBreakdownAI({run:async()=>({response:{steps:['x']}})},
  {title:'x',notes:''}),/ai_invalid_response/);
});
const request=(body,origin='https://dorm-os-dk9.pages.dev')=>new Request(
 'https://dorm-os-dk9.pages.dev/api/v1/task-breakdown-ai',{
 method:'POST',headers:{origin,'content-type':'application/json'},
 body:JSON.stringify(body)
});
const owner=async()=>({ok:true,userId:'owner'});
test('auth, origin and disabled binding all fail closed without inference',async()=>{
 let calls=0;const env={AI:{run:async()=>{calls++;return {response:{steps:['A','B']}}}}}};
 const bad=await handleBreakdownPost({request:request({title:'x'}),env},{
  verify:async()=>({ok:false,error:'unauthorized',status:401})});
 assert.equal(bad.status,401);
 const denied=await handleBreakdownPost({request:request({title:'x'},'https://evil.example'),env},{verify:owner});
 assert.equal(denied.status,403);
 const disabled=await handleBreakdownPost({request:request({title:'x'}),env},{verify:owner});
 assert.equal(disabled.status,503);
 assert.equal(calls,0);
});
test('AI inference requires explicit feature flag and returns unsaved suggestions',async()=>{
 let calls=0;
 const env={TASK_BREAKDOWN_AI_ENABLED:'true',AI:{run:async()=>{calls++;
  return {response:{steps:['整理材料','编写初稿','最终检查']}};}}};
 const result=await handleBreakdownPost({request:request({title:'实验报告'}),env},{verify:owner});
 assert.equal(result.status,200);
 assert.deepEqual(await result.json(),{steps:['整理材料','编写初稿','最终检查'],model:'workers_ai',saved:false});
 assert.equal(calls,1);
 const invalid=await handleBreakdownPost({request:request({title:'x',apiKey:'x'}),env},{verify:owner});
 assert.equal(invalid.status,422);
 assert.equal(calls,1);
 const unavailable=await handleBreakdownPost({request:request({title:'x'}),env:{
  TASK_BREAKDOWN_AI_ENABLED:'true',AI:{run:async()=>{throw new Error('provider failed');}}
 }},{verify:owner});
 assert.equal(unavailable.status,503);
});
