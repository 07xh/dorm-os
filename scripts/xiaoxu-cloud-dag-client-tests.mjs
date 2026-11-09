import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {webcrypto} from 'node:crypto';

const code=readFileSync(new URL('../public/xiaoxu-cloud-tasks.js',import.meta.url),'utf8');
const initial=[
 {id:'cloud-a',title:'先完成讲义',kind:'manual',local_status:'todo',
  revision:0,due_precision:'unknown',due_local_date:null,source_provider:'manual'},
 {id:'cloud-b',title:'后绘制视图',kind:'manual',local_status:'todo',
  revision:0,due_precision:'unknown',due_local_date:null,source_provider:'manual'}
];
let tasks=initial.map(x=>({...x})),edges=[],revision=0,reads=0,writes=0;
const json=(payload,status=200)=>new Response(JSON.stringify(payload),{
 status,headers:{'content-type':'application/json'}
});
function server(url,{method='GET',body}={}){
 if(url.startsWith('/api/v1/assignments?')){
  reads++;return json({items:tasks.map(x=>({...x}))});
 }
 if(url==='/api/v1/sources')return json({connections:[],userSnapshots:[]});
 if(url==='/api/v1/assignments/dependencies'){
  if(method==='GET')return json({revision,edges:edges.map(x=>({...x}))});
  if(method==='PUT'){
   const patch=JSON.parse(body),target=tasks.find(t=>t.id===patch.taskId);
   if(patch.graphRevision!==revision||patch.taskRevision!==target?.revision)
    return json({error:'revision_conflict'},409);
   if(target?.kind!=='manual')return json({error:'readonly'},403);
   edges=edges.filter(e=>e.taskId!==patch.taskId)
    .concat(patch.prerequisiteIds.map(p=>({taskId:patch.taskId,prerequisiteId:p})));
   revision++;target.revision++;writes++;
   return json({item:{taskId:target.id,revision,prerequisiteIds:patch.prerequisiteIds}});
  }
 }
 const match=url.match(/^\/api\/v1\/assignments\/(cloud-[ab])$/);
 if(match&&method==='PATCH'){
  const target=tasks.find(t=>t.id===match[1]),patch=JSON.parse(body);
  if(patch.revision!==target.revision)return json({error:'revision_conflict'},409);
  if(patch.localStatus==='done'&&edges.some(e=>
   e.taskId===target.id&&tasks.find(t=>t.id===e.prerequisiteId)?.local_status!=='done'))
   return json({error:'prerequisites_incomplete'},409);
  if(patch.title!==undefined)target.title=patch.title;
  if(patch.localStatus!==undefined)target.local_status=patch.localStatus;
  target.revision++;
  return json({item:{id:target.id,revision:target.revision}});
 }
 throw Error('unexpected request '+method+' '+url);
}
const client=()=>{
 const ctx={fetch:async(url,options)=>server(url,options),Date,Intl,Response,
 crypto:webcrypto,TextEncoder};
 runInNewContext(code,ctx);
 return ctx.XiaoxuCloudTasks;
};
const phone=client(),desktop=client();
assert.equal(reads,0,'no implicit account fetch');
assert.equal(phone.dependenciesAvailable(),false);
await phone.connect();await desktop.connect();
assert.equal(phone.dependenciesAvailable(),true);
assert.equal(phone.graphSnapshot().revision,0);
await phone.setPrerequisites('cloud-b',['cloud-a']);
assert.equal(writes,1);
assert.equal(phone.graphSnapshot().revision,1);
assert.deepEqual(Array.from(phone.displayRecords().find(t=>t.id==='cloud-b').prerequisite_waiting),
 ['先完成讲义']);
assert.equal(desktop.graphSnapshot().revision,0,'another device must refresh');
await desktop.refresh();
assert.deepEqual(Array.from(desktop.displayRecords().find(t=>t.id==='cloud-b').prerequisite_ids),
 ['cloud-a']);
assert.equal(desktop.displayRecords().find(t=>t.id==='cloud-b').dependency_ready,true);
const phoneProof=await phone.syncFingerprint();
const deskProof=await desktop.syncFingerprint();
assert.equal(phoneProof.code,deskProof.code,'same real API data produces same receipt');
assert.equal(phoneProof.taskCount,2);
assert.equal(phoneProof.edgeCount,1);
assert.equal(phoneProof.graphRevision,1);
const oldProof=phoneProof.code;
tasks[0].title='先完成讲义（已改）';
const updatedProof=await desktop.syncFingerprint();
assert.notEqual(updatedProof.code,oldProof,'renamed task changes cross-device fingerprint');
assert.equal((await phone.syncFingerprint()).code,updatedProof.code,
 'second device refresh must converge without fake local tasks');
tasks[0].title='先完成讲义';
const workspaceRoot={};
runInNewContext(readFileSync(new URL('../public/xiaoxu-workspace.js',import.meta.url),'utf8'),
 workspaceRoot);
const projection=workspaceRoot.XiaoxuWorkspace;
assert.deepEqual(Array.from(projection.tasks({cloud:desktop.displayRecords()},{filter:'ready'}),r=>r.id),
 ['cloud-a'],'cloud successor cannot enter the ready list before predecessors');
assert.deepEqual(Array.from(projection.tasks({cloud:desktop.displayRecords()},{filter:'waiting'}),r=>r.id),
 ['cloud-b'],'waiting cloud task remains visible and editable');
await phone.toggle('cloud-a','done');
await desktop.refresh();
assert.equal(desktop.displayRecords().find(t=>t.id==='cloud-b').prerequisite_waiting.length,0);
await desktop.toggle('cloud-b','done');
assert.equal(desktop.displayRecords().find(t=>t.id==='cloud-b').local_status,'done');
const priorWrites=writes;
await desktop.setPrerequisites('cloud-b',['cloud-a']);
assert.equal(writes,priorWrites,'idempotent same predecessor set');
// Revoke connection and ensure no stale private graph leaks to the UI.
desktop.disconnect();
assert.equal(desktop.graphSnapshot().ready,false);
assert.deepEqual(Array.from(desktop.graphSnapshot().edges),[]);
assert.deepEqual(Array.from(desktop.displayRecords()),[]);
await assert.rejects(()=>desktop.setPrerequisites('cloud-b',[]),/云端先后关系暂不可用/);
await assert.rejects(()=>desktop.syncFingerprint(),/先连接/);
// A stale browser gets a conflict, refreshes, and does not overwrite another edit.
await desktop.connect();
await phone.refresh();
await phone.setPrerequisites('cloud-b',[]);
await assert.rejects(()=>desktop.setPrerequisites('cloud-b',[]),/数据发生冲突/);
assert.equal(desktop.graphSnapshot().revision,2);
// A background refresh during asynchronous hashing must invalidate the
// receipt instead of pairing stale task bytes with the new graph revision.
let signalDigest,releaseDigest;
const digestEntered=new Promise(resolve=>{signalDigest=resolve;});
const digestUnblocked=new Promise(resolve=>{releaseDigest=resolve;});
const delayedCrypto={subtle:{digest:async(algorithm,bytes)=>{
 signalDigest();await digestUnblocked;
 return webcrypto.subtle.digest(algorithm,bytes);
}}};
const raceContext={fetch:async(url,options)=>server(url,options),Date,Intl,Response,
 crypto:delayedCrypto,TextEncoder};
runInNewContext(code,raceContext);
const raceClient=raceContext.XiaoxuCloudTasks;
await raceClient.connect();
const inflightReceipt=raceClient.syncFingerprint();
await digestEntered;
await raceClient.refresh();
releaseDigest();
await assert.rejects(inflightReceipt,/核对期间任务数据已刷新/,
 'a concurrent refresh invalidates a stale sync receipt');

console.log('Cloud DAG browser client: consent, two-device refresh, blocked/unlocked tasks, 409 and owner disconnect and fingerprint snapshot consistency PASS');
