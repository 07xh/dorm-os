import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {validateSecondClassMapping,mapSecondClassPage,makeSecondClassMappedAdapter,SecondClassMapError}
 from '../lib/academic-second-class-source-map.mjs';
import {describeSecondClassJson} from './xiaoxu-second-class-shape.mjs';
import {runAuthorizedSecondClassSync,collectAuthorizedSecondClass}
 from '../lib/academic-second-class-connector.mjs';

const mapping={
 schema:'xiaoxu/second-class-readonly-map-v1',
 listPath:'data.items',completePath:'data.finished',completeWhen:true,
 nextCursorPath:'data.nextCursor',verifiedEmptyPath:'data.emptyConfirmed',
 fields:{activityId:'id',title:'name',date:'activityDate',hours:'earned.hours',
  points:'earned.points',activityStatus:'participation',reviewStatus:'audit.result'},
 statusValues:{
  activityStatus:{enrolled:'registered',finished:'completed',cancel:'cancelled'},
  reviewStatus:{pass:'approved',waiting:'pending',deny:'rejected'}
 }
};
const rawFirst={data:{finished:false,nextCursor:'p2',items:[
 {id:1001,name:'课程外活动',activityDate:'2026-11-03',
  earned:{hours:'2.5',points:5},participation:'finished',audit:{result:'pass'},
  studentPhone:'SENSITIVE_SHOULD_NOT_PERSIST',session:'SECRET'},
 {id:1002,name:'尚未核准的活动',participation:'unmapped_code',audit:{result:'waiting'}}
]}};
const rawLast={data:{finished:true,nextCursor:null,items:[
 {id:'1003',name:'文化活动',earned:{hours:'1'},participation:'enrolled',audit:{result:'deny'}}
]}};
const safeShape=describeSecondClassJson(rawFirst);
assert.ok(safeShape.includes('data.items : array'));
assert.ok(safeShape.includes('data.items[].earned.hours : string'));
assert.equal(safeShape.includes('SENSITIVE_SHOULD_NOT_PERSIST'),false);
assert.equal(safeShape.includes('studentPhone'),false);
assert.equal(safeShape.includes('SECRET'),false);
assert.equal(safeShape.includes('1001'),false);
assert.throws(()=>describeSecondClassJson(rawFirst,{maxDepth:100}),/invalid/);
assert.equal(validateSecondClassMapping(mapping),mapping);
const first=mapSecondClassPage(rawFirst,mapping);
assert.equal(first.activities.length,2);
assert.equal(first.complete,false);
assert.equal(first.nextCursor,'p2');
assert.deepEqual(first.activities[0],{
 activityId:'1001',title:'课程外活动',date:'2026-11-03',
 hours:2.5,points:5,activityStatus:'completed',reviewStatus:'approved'
});
assert.equal(first.activities[1].activityStatus,'unknown');
assert.equal(JSON.stringify(first).includes('SENSITIVE_SHOULD_NOT_PERSIST'),false);
assert.equal(JSON.stringify(first).includes('SECRET'),false);
const last=mapSecondClassPage(rawLast,mapping);
assert.equal(last.complete,true);
assert.equal(last.nextCursor,null);
assert.equal(last.activities[0].hours,1);
assert.equal(mapSecondClassPage({data:{items:[],finished:true,nextCursor:null,emptyConfirmed:true}},mapping).verifiedEmpty,true);
const denied=fn=>assert.throws(fn,e=>e instanceof SecondClassMapError);
denied(()=>mapSecondClassPage({data:{items:[],finished:true,emptyConfirmed:false}},mapping));
denied(()=>mapSecondClassPage({data:{items:[rawFirst.data.items[0]],finished:true,nextCursor:'p3'}},mapping));
denied(()=>mapSecondClassPage({data:{items:[rawFirst.data.items[0]],finished:false,nextCursor:null}},mapping));
denied(()=>mapSecondClassPage({data:{items:[{id:'x',name:'非法时长',earned:{hours:'2小时'}}],finished:true}},mapping));
denied(()=>mapSecondClassPage({data:{items:[{id:'x',name:'越界学时',earned:{hours:-5}}],finished:true}},mapping));
denied(()=>mapSecondClassPage({data:{items:[{id:'x',name:'非法日期',activityDate:'2026-02-30'}],finished:true}},mapping));
denied(()=>validateSecondClassMapping({...mapping,fields:{...mapping.fields,studentId:'student.id'}}));
denied(()=>validateSecondClassMapping({...mapping,fields:{...mapping.fields,hours:'studentToken'}}));
denied(()=>validateSecondClassMapping({...mapping,fields:{...mapping.fields,title:'__proto__.x'}}));
denied(()=>validateSecondClassMapping({...mapping,statusValues:{...mapping.statusValues,activityStatus:{finished:'approved'}}}));
denied(()=>validateSecondClassMapping({...mapping,statusValues:{reviewStatus:{pass:'approved'}}}));
denied(()=>makeSecondClassMappedAdapter({mapping,verifySession:()=>true}));

const proof={authorized:true,scope:'self',provider:'second_class',school:'ncwu',
 identityBound:true,session:{ephemeral:true}};
const seen=[];
const adapter=makeSecondClassMappedAdapter({
 mapping,
 async verifySession(){return proof;},
 async fetchPage({cursor,session}){
  assert.equal(session,proof.session);seen.push(cursor);
  return cursor===null?rawFirst:rawLast;
 }
});
const collected=await collectAuthorizedSecondClass(adapter);
assert.equal(collected.records.length,3);
assert.deepEqual(seen,[null,'p2']);
assert.equal(collected.records[0].externalId,'1001');
assert.equal(collected.records[1].activityStatus,'unknown');
assert.equal(collected.records[2].reviewStatus,'rejected');
assert.equal(JSON.stringify(collected).includes('studentPhone'),false);

const sqlite=new DatabaseSync(':memory:');
sqlite.exec('PRAGMA foreign_keys=ON');
for(const n of ['0001_academic_core.sql','0003_academic_verified_snapshots.sql',
 '0008_academic_second_class_source.sql'])
 sqlite.exec(readFileSync(new URL('../migrations/'+n,import.meta.url),'utf8'));
const db={
 prepare(sql){let args=[];return{
  bind(...values){args=values;return this;},
  run(){return sqlite.prepare(sql).run(...args);}
 };},
 async batch(rows){
  sqlite.exec('BEGIN');
  try{const ret=rows.map(x=>x.run());sqlite.exec('COMMIT');return ret;}
  catch(e){sqlite.exec('ROLLBACK');throw e;}
 }
};
let idCount=0;
const opts={id:()=> 'sync-'+(++idCount),now:()=>new Date().toISOString()};
const stored=await runAuthorizedSecondClassSync(db,'owner-001',makeSecondClassMappedAdapter({
 mapping,verifySession:async()=>proof,
 fetchPage:async({cursor})=>cursor===null?rawFirst:rawLast
}),opts);
assert.equal(stored.count,3);
const data=sqlite.prepare("SELECT payload FROM academic_verified_records WHERE user_id='owner-001' ORDER BY external_id").all();
assert.equal(data.length,3);
assert.equal(JSON.stringify(data).includes('studentPhone'),false);
const oldId=sqlite.prepare("SELECT batch_id FROM academic_verified_batches WHERE user_id='owner-001'").get().batch_id;
await assert.rejects(()=>runAuthorizedSecondClassSync(db,'owner-001',
 makeSecondClassMappedAdapter({
  mapping,verifySession:async()=>proof,
  fetchPage:async()=>({data:{items:[{id:'x',name:'bad',earned:{hours:-3}}],finished:true}})
 }),opts),e=>e.code==='upstream_unavailable');
assert.equal(sqlite.prepare("SELECT batch_id FROM academic_verified_batches WHERE user_id='owner-001'").get().batch_id,
 oldId,'failed schema mapping must not replace prior verified data');
assert.equal(sqlite.prepare('PRAGMA integrity_check').get().integrity_check,'ok');
sqlite.close();
console.log('Second-class mapping: raw field paths, explicit status semantics, pagination, unknown fields, no PII persistence, authorized atomic write and rollback passed');
