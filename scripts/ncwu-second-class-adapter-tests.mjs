import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {validateSnapshot} from '../lib/academic-contract.mjs';
const require=createRequire(import.meta.url);
const adapter=require('../integrations/ncwu-second-class-extension/adapter.js');
const {readSnapshot,AdapterError}=adapter;
const categories=[
 {code:'1',name:'思想政治和道德修养'},
 {code:'2',name:'社会实践与志愿服务'},
 {code:'3',name:'科学技术与创新创业'},
 {code:'4',name:'文化艺术与身心健康'},
 {code:'5',name:'学生团体与社会工作'},
 {code:'6',name:'技能培训'}
];
const scoreRow=(school_term_index,school_year)=>{
 const row={school_term_index,score_value:76.5,score_value_last:70,
  score_plus:12,score_plus2:0};
 if(school_year)Object.assign(row,{school_year,school_term:'1'});
 for(let i=1;i<=6;i++){
  row['activity_type'+i+'_score']=i*2;
  row['activity_type'+i+'_score_last']=i;
 }
 return row;
};
const activity=(id,verify='1',state='5',mode='0')=>({
 id:String(id),activity_id:'event-'+id,activity_name:'合成活动 '+id,
 activity_start:'2026-10-20 09:30:00',activity_end:'2026-10-20 11:00:00',
 school_year:'2026-2027',school_term:'1',activity_type_name:'社会实践',
 score_value:2.5,verify_state:verify,activity_state:state,activity_mode:mode,
 signup_state:'1',student_code:'SYNTHETIC-PII',student_name:'合成学生'
});
function response(value,status=200,extra={}) {
 return {ok:status>=200&&status<300,status,redirected:false,url:'https://dekt.ncwu.edu.cn/test',
  text:async()=>typeof value==='string'?value:JSON.stringify(value),...extra};
}
function fixtureFetch({activities=Array.from({length:51},(_,i)=>activity(i+1)),
 scoreRows=[scoreRow(1,'2026-2027'),scoreRow(100,null)],types=categories,alter={}}={}) {
 const calls=[];
 const fetchImpl=async(url,options)=>{
  const u=new URL(url),path=u.pathname;calls.push({path,options});
  if(path==='/sys/stu/score/types')return response(types);
  if(path==='/sys/stu/score/list')return response({data:scoreRows,startIndex:0,totalPage:0,totalSize:scoreRows.length});
  if(path==='/sys/stu/center/signupactivity/list'){
   const body=new URLSearchParams(options.body||'');
   const page=Number(body.get('page')),size=Number(body.get('pagesize'));
   const start=(page-1)*size;
   const totalPage=Math.ceil(activities.length/size);
   const data=activities.slice(start,start+size);
   const wrapper={data,startIndex:start+1,totalPage,totalSize:activities.length};
   if(alter.activityEnvelope)return response(alter.activityEnvelope(wrapper,page));
   return response(wrapper);
  }
  throw Error('unexpected endpoint');
 };
 return {fetchImpl,calls};
}
const base=fixtureFetch();
const result=await readSnapshot({fetchImpl:base.fetchImpl,now:()=> '2026-11-03T03:50:00.000Z'});
const checked=validateSnapshot(result.snapshot);
assert.equal(checked.kind,'second-class');
assert.equal(result.stats.activityCount,51);
assert.equal(result.stats.semesterCount,1);
assert.equal(result.stats.categoryCount,6);
assert.equal(result.stats.hasSummary,true);
assert.equal(checked.records.length,53,'all 51 activities plus semester and total score rows');
assert.deepEqual(base.calls.map(x=>x.path),[
 '/sys/stu/score/types','/sys/stu/score/list',
 '/sys/stu/center/signupactivity/list','/sys/stu/center/signupactivity/list'
]);
for(const call of base.calls){
 assert.equal(call.options.method,'POST');
 assert.equal(call.options.credentials,'same-origin');
 assert.equal(call.options.cache,'no-store');
 assert.equal('Cookie' in call.options.headers,false);
 assert.equal('Authorization' in call.options.headers,false);
}
const firstActivity=checked.records.find(r=>r.externalId==='activity:1');
assert.equal(firstActivity.title,'合成活动 1');
assert.equal(firstActivity.date,'2026-10-20');
assert.equal(firstActivity.term,'2026-2027-S1');
assert.equal(firstActivity.points,2.5);
assert.equal(firstActivity.activityStatus,'registered');
assert.equal(firstActivity.reviewStatus,'calculated');
assert.equal(Object.hasOwn(firstActivity,'student_code'),false);
assert.equal(Object.hasOwn(firstActivity,'student_name'),false);
const semester=checked.records.find(r=>r.recordType==='semester_score');
assert.equal(semester.title,'2026-2027 第1学期');
assert.equal(semester.points,76.5);
assert.equal(semester.previousPoints,70);
assert.equal(semester.hours,12);
assert.equal(semester.categoryScores.length,6);
assert.equal(semester.categoryScores[1].name,'社会实践与志愿服务');
assert.equal(semester.categoryScores[1].value,4);
const summary=checked.records.find(r=>r.recordType==='score_summary');
assert.equal(summary.term,'summary');
assert.equal(summary.title,'第二课堂合计');
assert.equal(summary.categoryScores.length,6);
assert.equal(result.snapshot.verifiedEmpty,undefined);

for(const [verify,state,mode,expected] of [
 ['1','4','0','not_calculated'],['1','6','0','awaiting_calculation'],
 ['2','4','0','calculated'],['0','0','3','verified'],['0','0','0','unverified']
]){
 const row=adapter.normalizeActivity(activity('status-'+verify+state+mode,verify,state,mode));
 assert.equal(row.reviewStatus,expected);
}
const empty=fixtureFetch({activities:[],scoreRows:[]});
const emptyResult=await readSnapshot({fetchImpl:empty.fetchImpl});
assert.equal(emptyResult.snapshot.verifiedEmpty,true);
assert.equal(validateSnapshot(emptyResult.snapshot).records.length,0);

async function rejectsWith(fetchImpl,code){
 await assert.rejects(()=>readSnapshot({fetchImpl}),e=>e instanceof AdapterError&&e.code===code);
}
await rejectsWith(async()=>response('<!doctype html><html>统一身份认证</html>'),'AUTH_EXPIRED');
await rejectsWith(async()=>response({data:[],totalSize:1,totalPage:1}),'INVALID_RESPONSE');
const duplicate=fixtureFetch({activities:[activity(1),activity(1)]});
await rejectsWith(duplicate.fetchImpl,'DUPLICATE_ACTIVITY');
const badTypes=fixtureFetch({types:categories.slice(0,5)});
await rejectsWith(badTypes.fetchImpl,'INVALID_RESPONSE');
const badSummary=fixtureFetch({scoreRows:[scoreRow(1,'2026-2027')]});
await rejectsWith(badSummary.fetchImpl,'INVALID_RESPONSE');
const malformedCategory=fixtureFetch({scoreRows:[{...scoreRow(1,'2026-2027'),activity_type4_score:'not-a-number'},scoreRow(100,null)]});
await rejectsWith(malformedCategory.fetchImpl,'INVALID_RESPONSE');
const tooMany=fixtureFetch({activities:Array.from({length:251},(_,i)=>activity(i+1))});
await rejectsWith(tooMany.fetchImpl,'TOO_MANY_RECORDS');
const mismatchedPages=fixtureFetch({alter:{activityEnvelope:(wrapper,page)=>page===2?{...wrapper,totalSize:52}:wrapper}});
await rejectsWith(mismatchedPages.fetchImpl,'INVALID_RESPONSE');
const duplicateAcrossPages=fixtureFetch({activities:Array.from({length:51},(_,i)=>activity(i===50?1:i+1))});
await rejectsWith(duplicateAcrossPages.fetchImpl,'DUPLICATE_ACTIVITY');
await rejectsWith(async()=>{throw Error('offline');},'NETWORK_ERROR');
console.log('NCWU HAR-verified local adapter: 51-row pagination, six categories, semester/summary rows, exact status mapping, PII minimization, same-origin credential handling and negative cases passed.');
