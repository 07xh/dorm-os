import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
const code=readFileSync(new URL('../public/xiaoxu-cloud-tasks.js',import.meta.url),'utf8');
const html=readFileSync(new URL('../public/index.html',import.meta.url),'utf8');
const sw=readFileSync(new URL('../public/sw.js',import.meta.url),'utf8');
assert.ok(html.includes('<script src="/xiaoxu-cloud-tasks.js"></script>'));
assert.ok(html.includes('XiaoxuCloudTasks.previewLegacy(state.tasks)'));
assert.ok(html.includes('XiaoxuCloudTasks.commitLegacy(state.tasks)'));
assert.ok(sw.includes('/xiaoxu-cloud-tasks.js'));

const make=(value,status=200)=>new Response(JSON.stringify(value),{status,
 headers:{'content-type':'application/json;charset=utf-8'}});
const calls=[];
let remote=[{
 id:'cloud-1',title:'<img src=x onerror=alert(1)>',kind:'platform',
 due_precision:'date',due_local_date:'2026-11-22',due_at_utc:null,
 local_status:'todo',platform_status:'submitted',revision:2,
 legacy_id:null,source_course:'Python'
},{
 id:'cloud-legacy',title:'历史任务',kind:'manual',due_precision:'unknown',
 local_status:'done',revision:0,legacy_id:'local-1'
}];
let migrated=[];
const ctx={Response,Intl,Date,fetch:async(url,options={})=>{
 calls.push({url,options});
 if(url.startsWith('/api/v1/assignments?'))return make({items:remote});
 if(url==='/api/v1/sources')return make({connections:[],userSnapshots:[{
  kind:'assignments',source:'chaoxing',lastReceivedAt:'2026-11-03T10:00:00Z'
 }]});
 if(url==='/api/v1/assignments/legacy-import'){
  const input=JSON.parse(options.body);
  migrated.push(input);
  return make({mode:input.mode,total:input.tasks.length,
   toCreate:input.mode==='preview'?input.tasks.length:input.tasks.length,
   unchanged:0,conflicts:[],created:input.mode==='commit'?input.tasks.length:0},
   input.mode==='commit'?201:200);
 }
 if(url==='/api/v1/assignments/cloud-1'&&options.method==='PATCH'){
  const body=JSON.parse(options.body);
  assert.equal(body.revision,2,'must submit revision to prevent stale writes');
  assert.equal(body.localStatus,'done');
  remote[0]={...remote[0],local_status:'done',revision:3};
  return make({item:{id:'cloud-1',revision:3,unchanged:false}});
 }
 throw Error('unmocked '+url);
}};
runInNewContext(code,ctx);
const api=ctx.XiaoxuCloudTasks;
assert.equal(api.isReady(),false,'do not contact cloud on startup');
assert.equal(calls.length,0);
assert.ok(api.panel([]).includes('未连接'));
await api.connect();
assert.equal(api.isReady(),true);
assert.equal(api.taskCount(),2);
assert.equal(api.taskRows('all',[]).includes('平台已提交'),true);
assert.equal(api.taskRows('all',[]).includes('<img'),false,'HTML must be escaped');
assert.ok(api.taskRows('all',[]).includes('时间未知'));
assert.ok(api.taskRows('open',[{id:'local-1'}]).includes('平台已提交'),
  'open cloud tasks show the platform status independently');
assert.equal(api.taskRows('all',[{id:'local-1'}]).includes('历史任务'),false,
 'migrated record already present locally must not be shown twice');
assert.equal(api.taskRows('done',[]).includes('历史任务'),true);
assert.ok(api.panel([]).includes('个人上传快照'));
const deadline=api.deadlineSummary([],Date.parse('2026-11-22T00:00:00+08:00'));
assert.match(deadline,/<strong>1<\/strong> 今天\/24小时/,'date-only tasks counted by calendar day, not guessed 23:59');
assert.match(deadline,/<strong>0<\/strong> 6小时内/,'date-only task must not imply hour precision');
assert.ok(html.includes('XiaoxuCloudTasks.deadlineSummary(state.tasks)'));
const local=[{id:'local-1',title:'纸质习题',due:'2026-11-23',done:false,
 source:'manual',sourceId:'',sourceStatus:'unknown',category:'课程'}];
const preview=await api.previewLegacy(local);
assert.equal(preview.toCreate,1);
assert.equal(migrated[0].mode,'preview');
assert.equal(migrated[0].tasks[0].source,'manual');
await assert.rejects(()=>api.commitLegacy([{...local[0],title:'变化后的任务'}]),/重新预览/);
const result=await api.commitLegacy(local);
assert.equal(result.created,1);
assert.equal(migrated[1].mode,'commit');
assert.ok(!api.panel(local).includes('确认上传到私人云端'),
 'successful upload clears migration preview');
await api.toggle('cloud-1','done');
assert.ok(api.taskRows('done',[]).includes('平台已提交'),
 'local completion must not alter platform submission state');
assert.equal(remote[0].revision,3);
api.disconnect();
assert.equal(api.isReady(),false);
assert.equal(api.taskRows('all',[]),'');
assert.equal(api.taskCount(),0);
assert.equal(calls.filter(x=>x.url.startsWith('/api/v1/assignments?')).length>=3,true);
console.log('校序云端客户端：未授权零请求、用户主动迁移、HTML 防注入、迁移去重、版本 PATCH、断开清空均通过');
