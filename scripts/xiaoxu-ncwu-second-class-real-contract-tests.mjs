/* Synthetic data shaped after the observed NCWU self-service HAR.
 * NO actual activity, score, school account, session, SSO URL or HAR bytes.
 */
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {parseNcwuSignupPage,parseNcwuScoreList,parseNcwuScoreTypes,
 makeNcwuSecondClassAdapter,NCWU_SECOND_CLASS_READS,NcwuSecondClassError}
 from '../lib/academic-ncwu-second-class.mjs';
import {runVerifiedNcwuSecondClassSync}
 from '../lib/academic-ncwu-second-class-sync.mjs';
import {normalizeRecord} from '../lib/academic-contract.mjs';

const schoolCode='SYNTHETIC_STUDENT';
const activity=(id,state='5',verified='1')=>({
 id,activity_id:'act-'+id,activity_name:'合成活动'+id,
 activity_start:'2026-11-03 09:00:00',
 activity_end:'2026-11-03 11:00:00',school_year:'2026-2027',
 school_term:'1',student_code:schoolCode,
 student_name:'SHOULD_NEVER_BE_PERSISTED',verify_type_json:'PRIVATE',
 signup_state:'1',activity_state:state,verify_state:verified,
 score_value:1.25
});
const page1={data:[activity('signup-1')],startIndex:1,totalPage:2,totalSize:2};
const page2={data:[activity('signup-2','4','1')],startIndex:2,totalPage:2,totalSize:2};
const first=parseNcwuSignupPage(page1,{page:1,pageSize:50});
assert.equal(first.complete,false);
assert.equal(first.nextCursor,'2');
assert.deepEqual(first.activities[0],{
 activityId:'signup-1',title:'合成活动signup-1',term:'2026-2027-1',
 date:'2026-11-03',activityStatus:'registered',reviewStatus:'unknown',
 settlementStatus:'settled',points:1.25
});
assert.equal(JSON.stringify(first.activities).includes('SHOULD_NEVER_BE_PERSISTED'),false);
assert.equal(JSON.stringify(first.activities).includes('PRIVATE'),false);
const last=parseNcwuSignupPage(page2,{page:2,pageSize:50});
assert.equal(last.activities[0].points,undefined,'unsettled points must not count');
assert.equal(last.activities[0].settlementStatus,'unsettled');
assert.equal(parseNcwuSignupPage({data:[activity('x','6','1')],startIndex:1,totalPage:1,totalSize:1}).activities[0].settlementStatus,'awaiting_settlement');
assert.equal(parseNcwuSignupPage({data:[activity('x','0','0')],startIndex:1,totalPage:1,totalSize:1}).activities[0].settlementStatus,'unverified');
assert.equal(parseNcwuSignupPage({data:[activity('x','0','2')],startIndex:1,totalPage:1,totalSize:1}).activities[0].settlementStatus,'settled');
assert.deepEqual(parseNcwuSignupPage({data:[],startIndex:0,totalPage:0,totalSize:0}).activities,[]);
const expectFail=(fn,code)=>assert.throws(fn,e=>e instanceof NcwuSecondClassError&&(!code||e.code===code));
expectFail(()=>parseNcwuSignupPage({data:[],totalPage:1,totalSize:1,startIndex:0}));
expectFail(()=>parseNcwuSignupPage({data:[activity('a')],totalPage:0,totalSize:1,startIndex:0}));
expectFail(()=>parseNcwuSignupPage({data:[activity('a')],totalPage:1,totalSize:1,startIndex:0},{page:2}));
expectFail(()=>parseNcwuSignupPage({data:[{...activity('a'),id:''}],totalPage:1,totalSize:1,startIndex:1}));
expectFail(()=>parseNcwuSignupPage({data:[{...activity('a'),score_value:-1}],totalPage:1,totalSize:1,startIndex:1}));
expectFail(()=>parseNcwuSignupPage({data:[{...activity('a'),activity_start:'not a date'}],totalPage:1,totalSize:1,startIndex:1}));
const types=Array.from({length:6},(_,i)=>({code:String(i+1),name:'分类'+(i+1),
 privateMetadata:'should_not_persist'}));
const cleanTypes=parseNcwuScoreTypes(types);
assert.deepEqual(cleanTypes[0],{code:'1',label:'分类1'});
const scoreRow=(term,quality,hours)=>({
 school_term_index:term,score_value:quality,score_plus:hours,
 score_plus2:99,student_code:schoolCode,student_name:'SHOULD_NEVER_BE_PERSISTED',
 ...Object.fromEntries(Array.from({length:6},(_,i)=>['activity_type'+(i+1)+'_score',i+0.25]))
});
const schoolScores={data:[scoreRow(1,4,2),scoreRow(100,4,2)],
 startIndex:0,totalPage:0,totalSize:2};
const score=parseNcwuScoreList(schoolScores,cleanTypes);
assert.equal(score.isSchoolReportedAggregate,true);
assert.equal(score.semesters.length,1);
assert.equal(score.aggregate.qualityScore,4);
assert.equal(score.aggregate.volunteerHours,2);
assert.equal(score.aggregate.categories.length,6);
assert.equal(score.units.qualityScore,'素质分');
assert.equal(score.units.volunteerHours,'志愿服务时长');
assert.equal(JSON.stringify(score).includes('SYNTHETIC_STUDENT'),false);
assert.equal(JSON.stringify(score).includes('SHOULD_NEVER_BE_PERSISTED'),false);
assert.equal(JSON.stringify(score).includes('score_plus2'),false);
expectFail(()=>parseNcwuScoreTypes([{code:'1',name:'A'},{code:'1',name:'B'}]));
expectFail(()=>parseNcwuScoreList({data:[scoreRow(100,-1,2)],startIndex:0,totalPage:0,totalSize:1},cleanTypes));
expectFail(()=>parseNcwuScoreList({data:[scoreRow(100,1,2),scoreRow(100,1,2)],startIndex:0,totalPage:0,totalSize:2},cleanTypes));
assert.equal(normalizeRecord('second-class',{
 externalId:'x',title:'X',settlementStatus:'settled'
}).settlementStatus,'settled');
assert.throws(()=>normalizeRecord('second-class',{
 externalId:'x',title:'X',settlementStatus:'fake'
}),/未知二课核算状态/);

const auditCalls=[];
const verified={authorized:true,provider:'second_class',school:'ncwu',scope:'self',
 identityBound:true,session:{studentCode:schoolCode}};
function adapter({badScore=false,badPage=false,verifiedAccount=verified}={}){
 return makeNcwuSecondClassAdapter({
  async verifySession(){return verifiedAccount;},
  async readPost({method,path,form,session}){
   auditCalls.push({method,path,form});
   assert.equal(method,'POST');
   assert.equal(session.studentCode,schoolCode);
   if(path===NCWU_SECOND_CLASS_READS.activities)
    return badPage?{...page1,totalSize:3}:
     form.page==='1'?page1:page2;
   if(path===NCWU_SECOND_CLASS_READS.scoreTypes)return types;
   if(path===NCWU_SECOND_CLASS_READS.scores)
    return badScore?{...schoolScores,data:[scoreRow(100,-9,2)]}:schoolScores;
   throw Error('unexpected remote path');
  }
 });
}
const a=adapter();
const proof=await a.verifySession();
const received1=await a.readPage({cursor:null,session:proof.session});
const received2=await a.readPage({cursor:'2',session:proof.session});
assert.equal(received1.activities.length,1);
assert.equal(received2.complete,true);
const totals=await a.readOfficialScores();
assert.equal(totals.aggregate.qualityScore,4);
assert.deepEqual(auditCalls.map(x=>x.path).slice(-4),[
 NCWU_SECOND_CLASS_READS.activities,NCWU_SECOND_CLASS_READS.activities,
 NCWU_SECOND_CLASS_READS.scoreTypes,NCWU_SECOND_CLASS_READS.scores
]);
assert.deepEqual(auditCalls[0].form,{page:'1',pagesize:'50'});
await assert.rejects(async()=>adapter({verifiedAccount:{...verified,identityBound:false}}).verifySession(),
 e=>e.code==='authorization_failed');
await assert.rejects(async()=>{
 const x=adapter();const proof=await x.verifySession();
 await x.readPage({cursor:null,session:{studentCode:'OTHER'}});
},e=>e.code==='authorization_failed');

const sql=new DatabaseSync(':memory:');
sql.exec('PRAGMA foreign_keys=ON');
for(const name of ['0001_academic_core.sql','0003_academic_verified_snapshots.sql',
 '0008_academic_second_class_source.sql','0009_ncwu_second_class_official_scores.sql'])
 sql.exec(readFileSync(new URL('../migrations/'+name,import.meta.url),'utf8'));
const db={
 prepare(statement){let args=[];return{
  bind(...values){args=values;return this;},
  first(){return sql.prepare(statement).get(...args)??null;},
  all(){return {results:sql.prepare(statement).all(...args)};},
  run(){return sql.prepare(statement).run(...args);}
 };},
 async batch(commands){
  sql.exec('BEGIN');
  try{const out=commands.map(x=>x.run());sql.exec('COMMIT');return out;}
  catch(e){sql.exec('ROLLBACK');throw e;}
 }
};
let counter=0;
const opts={id:()=> 'id-'+(++counter),now:()=>new Date().toISOString()};
const outcome=await runVerifiedNcwuSecondClassSync(db,'owner-a',adapter(),opts);
assert.equal(outcome.activityCount,2);
assert.equal(outcome.hasOfficialScoreAggregate,true);
const stored=sql.prepare("SELECT payload FROM academic_second_class_official_scores WHERE user_id='owner-a'").get();
assert.ok(stored);
assert.equal(JSON.parse(stored.payload).aggregate.qualityScore,4);
assert.equal(JSON.parse(stored.payload).aggregate.volunteerHours,2);
assert.equal(stored.payload.includes('student_code'),false);
assert.equal(stored.payload.includes('SHOULD_NEVER'),false);
const activityRecords=sql.prepare("SELECT payload FROM academic_verified_records WHERE user_id='owner-a'").all();
assert.equal(activityRecords.length,2);
assert.ok(activityRecords.every(x=>!x.payload.includes('student')));
assert.equal(sql.prepare("SELECT provenance FROM academic_verified_batches WHERE user_id='owner-a'").get().provenance,'trusted_readonly');
const previousBatch=sql.prepare("SELECT batch_id FROM academic_verified_batches WHERE user_id='owner-a'").get().batch_id;
await assert.rejects(()=>runVerifiedNcwuSecondClassSync(db,'owner-a',adapter({badScore:true}),opts));
assert.equal(sql.prepare("SELECT batch_id FROM academic_verified_batches WHERE user_id='owner-a'").get().batch_id,
 previousBatch,'bad official score must not overwrite last good activity or score batch');
assert.equal(sql.prepare("SELECT batch_id FROM academic_second_class_official_scores WHERE user_id='owner-a'").get().batch_id,previousBatch);
await assert.rejects(()=>runVerifiedNcwuSecondClassSync(db,'owner-b',adapter({badPage:true}),opts));
assert.equal(sql.prepare("SELECT COUNT(*) AS n FROM academic_users WHERE id='owner-b'").get().n,0);
assert.deepEqual(sql.prepare('PRAGMA foreign_key_check').all(),[]);
sql.close();
console.log('NCWU genuine HAR shape: read-only endpoint allowlist, settled points vs volunteer hours, 100=aggregate, identity, pagination, D1 atomicity passed');
