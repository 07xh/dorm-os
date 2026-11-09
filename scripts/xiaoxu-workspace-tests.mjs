import assert from 'node:assert/strict';
import { readFileSync,existsSync } from 'node:fs';
import vm from 'node:vm';
assert.ok(existsSync('public/xiaoxu-workspace.js'),'shared workspace presentation model is missing');
const box={};vm.runInNewContext(readFileSync('public/xiaoxu-workspace.js','utf8'),box);
const model=box.XiaoxuWorkspace;
const input={local:[{id:'l',title:'Local',source:'chaoxing',sourceId:'same',due:'2026-11-05',done:false},{id:'m',title:'Unknown',done:true}],cloud:[{id:'c',title:'Duplicate',source_provider:'chaoxing',source_external_id:'same',local_status:'todo'},{id:'w',title:'WeChat',source_provider:'wechat',source_external_id:'same',local_status:'done',due_precision:'exact',due_at_utc:'2026-11-04T12:00:00Z'}],remote:[{externalId:'same',title:'Snapshot duplicate',platformStatus:'submitted'},{externalId:'r',title:'Read only',platformStatus:'submitted',dueAt:null}]};
const original=JSON.stringify(input);const rows=model.tasks(input);
assert.equal(rows.length,4,'cross-store duplicates collapse but other providers remain');
assert.equal(rows[0].id,'l');
assert.equal(rows.find(r=>r.id==='w').platformStatus,'unknown');
assert.equal(model.tasks(input,{filter:'open'}).length,0,'legacy records without official identifiers are not actionable');
assert.equal(model.tasks(input,{filter:'verify'}).length,2,'unverified Chaoxing records remain visible for review');
assert.equal(model.tasks(input,{filter:'done'}).length,2);
assert.equal(model.tasks(input,{source:'wechat'}).length,1);
assert.equal(JSON.stringify(input),original,'projection must not mutate records');
assert.equal(model.tasks({cloud:[{id:'x',title:'date',due_precision:'date',due_local_date:'2026-11-06'}]})[0].dueAt,'','date-only does not acquire a fabricated exact time');
const courses=model.courses({classes:[{id:'a',day:3,start:'08:00',end:'09:40',subject:'Weekly'}],calendarEvents:[{id:'b',date:'2026-11-04',start:'10:00',end:'11:40',subject:'Dated'}]},'2026-11-04',3);
assert.equal(courses.length,2,'overview includes date-specific imported classes');
assert.equal(model.courses({classes:[],calendarEvents:[{date:'2026-11-05',subject:'Tomorrow'}]},'2026-11-04',3).length,0);
console.log('Workspace projection: dedup, source filters, personal/platform separation, deadline precision and dated courses passed');

const mixed=model.tasks({
 local:[{id:'local-early',title:'北京时间八点',dueAt:'2026-11-04T08:00:00+08:00',done:false}],
 cloud:[{id:'cloud-later',title:'北京时间九点',due_precision:'exact',due_at_utc:'2026-11-04T01:00:00Z',local_status:'todo'}]
});
assert.deepEqual(Array.from(mixed,r=>r.id),['local-early','cloud-later'],'compare instants across local +08 and cloud UTC');
const boundary=model.tasks({
 local:[{id:'local-next',title:'次日凌晨',dueAt:'2026-11-05T00:30:00+08:00',done:false}],
 cloud:[{id:'cloud-first',title:'当天夜间',due_precision:'exact',due_at_utc:'2026-11-04T15:00:00Z',local_status:'todo'}]
});
assert.deepEqual(Array.from(boundary,r=>r.id),['cloud-first','local-next'],'UTC date boundaries must not reverse deadlines');
const dateOnly=model.tasks({local:[{id:'date-only',title:'当日时间未知',due:'2026-11-04'}],cloud:[{id:'exact',title:'当日精确时间',due_precision:'exact',due_at_utc:'2026-11-03T18:00:00Z'}]});
assert.deepEqual(Array.from(dateOnly,r=>r.id),['date-only','exact'],'unknown time groups by the Beijing calendar date, before timed tasks on that date');
assert.equal(dateOnly[0].dueAt,'','sorting must not invent a precise deadline');
console.log('Workspace deadline ordering: UTC/+08, midnight boundaries and unknown time passed');

const dependencies=[
 {id:'step-a',title:'先完成讲义',done:false,source:'manual'},
 {id:'step-b',title:'再画主视图',done:false,source:'manual',prerequisiteIds:['step-a']},
 {id:'step-c',title:'最后核对尺寸',done:false,source:'manual',prerequisiteIds:['step-a','step-b']}
];
assert.equal(model.canSetPrerequisites(dependencies,'step-a',['step-c']),false,'reject indirect cycle');
assert.equal(model.canSetPrerequisites(dependencies,'step-a',['step-a']),false,'reject self edge');
assert.equal(model.canSetPrerequisites(dependencies,'step-b',['missing']),false,'reject missing predecessor');
assert.equal(model.canSetPrerequisites(dependencies,'step-c',['step-a','step-b']),true);
let ready=model.tasks({local:dependencies},{filter:'ready'});
assert.deepEqual(Array.from(ready,r=>r.id),['step-a']);
assert.deepEqual(Array.from(model.tasks({local:dependencies},{filter:'waiting'}),r=>r.id),['step-b','step-c']);
const afterA=dependencies.map(r=>r.id==='step-a'?{...r,done:true}:r);
assert.deepEqual(Array.from(model.tasks({local:afterA},{filter:'ready'}),r=>r.id),['step-b']);
const afterB=afterA.map(r=>r.id==='step-b'?{...r,done:true}:r);
assert.deepEqual(Array.from(model.tasks({local:afterB},{filter:'ready'}),r=>r.id),['step-c']);
const cyclic=model.sanitizePrerequisites([
 {id:'a',title:'A',prerequisiteIds:['b']},
 {id:'b',title:'B',prerequisiteIds:['a','deleted']}
]);
assert.deepEqual(Array.from(cyclic[1].prerequisiteIds),[],'backups lose cyclic/dangling edges without inventing tasks');
const school={
 cloud:[
  {id:'verified-done',kind:'platform',title:'已交实验报告',source_provider:'chaoxing',
   source_external_id:'work:12:34:56',platform_status:'submitted',local_status:'todo'},
  {id:'verified-open',kind:'platform',title:'待交实验报告',source_provider:'chaoxing',
   source_external_id:'work:12:34:57',platform_status:'todo',local_status:'todo'},
  {id:'notice-unknown',kind:'platform',title:'旧通知',source_provider:'chaoxing',
   source_external_id:'cxn:older',platform_status:'unknown',local_status:'todo'}
 ]
};
assert.deepEqual(Array.from(model.tasks(school,{filter:'ready'}),r=>r.id),['verified-open']);
assert.deepEqual(Array.from(model.tasks(school,{filter:'done'}),r=>r.id),['verified-done']);
assert.deepEqual(Array.from(model.tasks(school,{filter:'verify'}),r=>r.id),['notice-unknown']);
assert.deepEqual(Array.from(model.tasks(school,{filter:'open'}),r=>r.id),['verified-open']);
const duplicate=model.tasks({local:[
 {id:'old',title:'旧本机重复',source:'chaoxing',sourceId:'work:12:34:56',done:false}
],cloud:school.cloud});
assert.equal(duplicate.filter(r=>r.sourceId==='work:12:34:56').length,1,
 'same official source key cannot create a second visible task');
assert.equal(duplicate.find(r=>r.sourceId==='work:12:34:56').status,'done',
 'trusted cloud completion must win over stale local duplicate');
console.log('NextTodo web readiness: dependencies, DAG, unlock, verified school status and duplicate safety passed');
