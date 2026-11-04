// Browser-independent smoke test of the existing Tasks page WeChat panel.
// Synthetic only: no school credentials, group messages or real device token.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

const handlers={};let calls=[],renderEvents=[],name='本人电脑';
const client='wxd_'+'a'.repeat(24);
const candidate='a'.repeat(20);
const token='wxu_'+'s'.repeat(43);
const record={
 id:candidate,title:'实验报告 <script>alert(1)</script>',
 kind:'homework',chatLabel:'测试群',sentAt:'2026-11-04T12:00:00+08:00',
 duePrecision:'date',dueLocalDate:'2026-11-10',reviewStatus:'needs_review'
};
let devices=[],pending=[record];
const json=data=>({ok:true,status:200,headers:{get:()=> 'application/json'},
 json:async()=>data});
const context={
 location:{origin:'https://academic.test'},navigator:{clipboard:{writeText:async()=>{}}},
 document:{
  getElementById:()=>null,
  querySelector:()=>({value:name}),
  addEventListener(type,fn){(handlers[type]??=[]).push(fn);}
 },
 fetch:async (url,options)=>{
  calls.push({url,options});
  if(url==='/api/v1/wechat/devices'&&options.method==='GET')
   return json({devices});
  if(url==='/api/v1/wechat/candidates?limit=50')
   return json({status:'personal_candidates',items:pending});
  if(url==='/api/v1/wechat/devices'&&options.method==='POST'){
   devices=[{id:client,name,lastSeenAt:null,revokedAt:null}];
   return json({device:{id:client,name},token});
  }
  if(url==='/api/v1/wechat/candidates/'+candidate&&options.method==='POST'){
   assert.equal(JSON.parse(options.body).decision,'accept');
   pending=[];return json({id:candidate,status:'accepted',assignmentId:'task-1'});
  }
  throw Error('unexpected API '+url+' '+options.method);
 },
 XiaoxuCloudTasks:{isReady:()=>true,refresh:async()=>{}},
 dispatchEvent:event=>{renderEvents.push(event.type);},
 Event:class Event{constructor(type){this.type=type;}},
 confirm:()=>true,
 console
};
context.globalThis=context;
vm.runInNewContext(readFileSync(new URL('../public/xiaoxu-wechat-sync.js',import.meta.url),'utf8'),
 context,{filename:'xiaoxu-wechat-sync.js'});
const ui=context.XiaoxuWechat;
assert.ok(ui&&typeof ui.panel==='function');
assert.equal(calls.length,0,'importing the UI must never poll personal data');
assert.match(ui.panel(),/微信待审核/);
assert.doesNotMatch(ui.panel(),/确认加入待办/);
const click=async attrs=>{
 const button={
  hasAttribute:k=>Object.hasOwn(attrs,k),
  getAttribute:k=>attrs[k]??null
 };
 for(const handler of handlers.click)
  handler({target:{closest:()=>button},preventDefault:()=>{}});
 // Event handlers intentionally start asynchronous work without awaiting it.
 for(let i=0;i<5;i++)await new Promise(resolve=>setImmediate(resolve));
};
await click({'data-xu-wc-refresh':''});
assert.equal(calls.length,2,'private requests require the user to request them');
assert.match(ui.panel(),/确认加入待办/);
assert.ok(ui.panel().includes('&lt;script&gt;'));
assert.ok(!ui.panel().includes('<script>'),'message titles must be escaped');
await click({'data-xu-wc-pair':''});
assert.ok(ui.panel().includes(token),'only newly created token appears in this memory view');
const pairing=calls.find(x=>x.options.method==='POST'&&x.url==='/api/v1/wechat/devices');
assert.equal(pairing.options.headers.origin,'https://academic.test');
await click({'data-xu-wc-review':candidate,'data-xu-wc-decision':'accept'});
assert.ok(renderEvents.includes('xiaoxu-wechat-task-reviewed'));
assert.doesNotMatch(ui.panel(),/实验报告/,'accepted candidate leaves the review list');
ui.disconnect();
assert.ok(!ui.panel().includes(token),'logout clears one-time secret');
assert.equal(calls.length,6,'logout does not trigger a new server request');
console.log('微信 App 面板：无静默私有访问、输入转义、显式绑定、本人审核、任务刷新事件、登出清密钥通过');
