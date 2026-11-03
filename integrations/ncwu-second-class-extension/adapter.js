/* NCWU second-class adapter built from the user's redacted-in-memory HAR structure.
 * This file never reads Cookie/Authorization headers and never persists raw responses.
 * CommonJS export is for Node tests; global export is for the extension content script.
 */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 root.NcwuSecondClassAdapter=api;
})(globalThis,function(){
 'use strict';
 const BASE='https://dekt.ncwu.edu.cn';
 const PAGE_SIZE=50,MAX_ACTIVITY_ROWS=200,MAX_TOTAL_ROWS=250;
 const TYPES_PATH='/sys/stu/score/types';
 const SCORES_PATH='/sys/stu/score/list';
 const ACTIVITIES_PATH='/sys/stu/center/signupactivity/list';
 class AdapterError extends Error{
  constructor(code){super(code);this.name='AdapterError';this.code=code;}
 }
 const fail=code=>{throw new AdapterError(code);};
 const text=(v,max=240)=>typeof v==='string'&&v.trim()&&v.length<=max?v.trim():'';
 const number=(v,name,max=100000)=>{
  if(v===null||v===undefined||v==='')return null;
  if(typeof v!=='number'||!Number.isFinite(v)||v<0||v>max)fail('INVALID_RESPONSE');
  return v;
 };
 const integer=(v,name,min,max)=>{
  if(!Number.isInteger(v)||v<min||v>max)fail('INVALID_RESPONSE');
  return v;
 };
 const codeText=v=>v===null||v===undefined?'':String(v);
 function authRedirect(response,body){
  const url=typeof response.url==='string'?response.url:'';
  return response.status===401||response.status===403||response.redirected===true||
   /\/(?:login|sso|auth)(?:\/|$)/i.test(url)||
   (typeof body==='string'&&(/<html[\s>]/i.test(body)||/统一身份认证|请先登录|登录超时/.test(body)));
 }
 async function requestJson(fetchImpl,path,body){
  const options={method:'POST',credentials:'same-origin',cache:'no-store',redirect:'follow',
   headers:{Accept:'application/json, text/javascript, */*; q=0.01','X-Requested-With':'XMLHttpRequest'}};
  if(body!==undefined){
   options.headers['Content-Type']='application/x-www-form-urlencoded; charset=UTF-8';
   options.body=new URLSearchParams(body).toString();
  }
  let response;
  try{response=await fetchImpl(BASE+path,options);}
  catch{fail('NETWORK_ERROR');}
  let raw;
  try{raw=await response.text();}catch{fail('INVALID_RESPONSE');}
  if(authRedirect(response,raw))fail('AUTH_EXPIRED');
  if(!response.ok)fail('UPSTREAM_ERROR');
  let parsed;
  try{parsed=JSON.parse(raw);}catch{fail('INVALID_RESPONSE');}
  return parsed;
 }
 function pageEnvelope(value,maxRows){
  if(!value||typeof value!=='object'||Array.isArray(value)||!Array.isArray(value.data)||
   value.data.length>maxRows)fail('INVALID_RESPONSE');
  const totalSize=integer(value.totalSize,'totalSize',0,MAX_ACTIVITY_ROWS);
  const totalPage=integer(value.totalPage,'totalPage',0,100);
  if(totalSize===0){
   if(value.data.length!==0||totalPage!==0)fail('INVALID_RESPONSE');
  }else if(totalPage<1||value.data.length===0||totalPage!==Math.ceil(totalSize/PAGE_SIZE)){
   fail('INVALID_RESPONSE');
  }
  return {data:value.data,totalSize,totalPage};
 }
 function statusForActivity(row){
  const verify=codeText(row.verify_state),state=codeText(row.activity_state),mode=codeText(row.activity_mode);
  if(verify==='1'&&state==='5')return 'calculated';
  if(verify==='1'&&state==='4')return 'not_calculated';
  if(verify==='1'&&state==='6')return 'awaiting_calculation';
  if(verify==='2')return 'calculated';
  return mode==='3'?'verified':'unverified';
 }
 function dateOnly(value){
  if(typeof value!=='string')return '';
  const m=/^(20\d{2}-\d{2}-\d{2})(?:\s+\d{2}:\d{2}:\d{2})?$/.exec(value);
  if(!m)return '';
  const date=new Date(m[1]+'T00:00:00Z');
  return Number.isFinite(date.getTime())&&date.toISOString().slice(0,10)===m[1]?m[1]:'';
 }
 function normalizeActivity(row){
  if(!row||typeof row!=='object'||Array.isArray(row))fail('INVALID_RESPONSE');
  const id=text(row.id,120),title=text(row.activity_name,120);
  if(!/^[A-Za-z0-9:_-]{1,120}$/.test(id)||!title)fail('INVALID_RESPONSE');
  const year=text(row.school_year,12),semester=codeText(row.school_term);
  const term=/^20\d{2}-20\d{2}$/.test(year)&&['1','2'].includes(semester)?year+'-S'+semester:'';
  const record={recordType:'activity',externalId:'activity:'+id,term,title,
   activityStatus:codeText(row.signup_state)==='1'?'registered':'unknown',
   reviewStatus:statusForActivity(row)};
  const date=dateOnly(row.activity_start);if(date)record.date=date;
  const points=number(row.score_value,'score_value');if(points!==null)record.points=points;
  const hours=number(row.score_plus,'score_plus');if(hours!==null)record.hours=hours;
  const category=text(row.activity_type_name,80);if(category)record.category=category;
  return record;
 }
 function normalizeTypes(rows){
  if(!Array.isArray(rows)||rows.length!==6)fail('INVALID_RESPONSE');
  const map=new Map();
  for(const row of rows){
   const code=codeText(row?.code),name=text(row?.name,80);
   if(!/^[1-6]$/.test(code)||!name||map.has(code))fail('INVALID_RESPONSE');
   map.set(code,name);
  }
  for(let i=1;i<=6;i++)if(!map.has(String(i)))fail('INVALID_RESPONSE');
  return [...map.entries()].map(([code,name])=>({code,name}));
 }
 function normalizeScoreRow(row,categories){
  if(!row||typeof row!=='object'||Array.isArray(row))fail('INVALID_RESPONSE');
  const termIndex=integer(row.school_term_index,'school_term_index',1,100);
  const summary=termIndex===100;
  const year=text(row.school_year,12);
  if(!summary&&!/^20\d{2}-20\d{2}$/.test(year))fail('INVALID_RESPONSE');
  const term=summary?'summary':year+'-S'+termIndex;
  const categoryScores=categories.map(({code,name})=>({
   code,name,
   value:number(row['activity_type'+code+'_score'],'category score'),
   previousValue:number(row['activity_type'+code+'_score_last'],'previous category score')
  }));
  const record={recordType:summary?'score_summary':'semester_score',
   externalId:summary?'score:summary':'score:'+year+':'+termIndex,
   term,title:summary?'第二课堂合计':year+' 第'+termIndex+'学期',
   categoryScores};
  const points=number(row.score_value,'score_value');if(points!==null)record.points=points;
  const previousPoints=number(row.score_value_last,'score_value_last');if(previousPoints!==null)record.previousPoints=previousPoints;
  // The official score page labels score_plus as 志愿服务时长.
  const hours=number(row.score_plus,'score_plus');if(hours!==null)record.hours=hours;
  return record;
 }
 async function readSnapshot({fetchImpl=globalThis.fetch,now=()=>new Date().toISOString()}={}){
  if(typeof fetchImpl!=='function')fail('NETWORK_ERROR');
  const [typeResponse,scoreResponse,firstActivity]=await Promise.all([
   requestJson(fetchImpl,TYPES_PATH),
   requestJson(fetchImpl,SCORES_PATH),
   requestJson(fetchImpl,ACTIVITIES_PATH,{page:'1',pagesize:String(PAGE_SIZE)})
  ]);
  const categories=normalizeTypes(typeResponse);
  if(!scoreResponse||typeof scoreResponse!=='object'||Array.isArray(scoreResponse)||
   !Array.isArray(scoreResponse.data)||scoreResponse.data.length>50||
   !Number.isInteger(scoreResponse.totalSize)||scoreResponse.totalSize!==scoreResponse.data.length)
   fail('INVALID_RESPONSE');
  const scoreRecords=scoreResponse.data.map(row=>normalizeScoreRow(row,categories));
  const summaryRows=scoreRecords.filter(row=>row.recordType==='score_summary');
  if(scoreRecords.length>0&&summaryRows.length!==1)fail('INVALID_RESPONSE');
  const activityEnvelope=pageEnvelope(firstActivity,PAGE_SIZE);
  if(activityEnvelope.totalSize>MAX_ACTIVITY_ROWS)fail('TOO_MANY_RECORDS');
  const activityRows=[...activityEnvelope.data];
  for(let page=2;page<=activityEnvelope.totalPage;page++){
   const next=pageEnvelope(await requestJson(fetchImpl,ACTIVITIES_PATH,
    {page:String(page),pagesize:String(PAGE_SIZE)}),PAGE_SIZE);
   if(next.totalSize!==activityEnvelope.totalSize||next.totalPage!==activityEnvelope.totalPage||!next.data.length)
    fail('INVALID_RESPONSE');
   activityRows.push(...next.data);
   if(activityRows.length>MAX_ACTIVITY_ROWS)fail('TOO_MANY_RECORDS');
  }
  if(activityRows.length!==activityEnvelope.totalSize)fail('INVALID_RESPONSE');
  const activityRecords=activityRows.map(normalizeActivity);
  const ids=new Set();
  for(const record of activityRecords){
   if(ids.has(record.externalId))fail('DUPLICATE_ACTIVITY');
   ids.add(record.externalId);
  }
  const records=[...activityRecords,...scoreRecords];
  if(records.length>MAX_TOTAL_ROWS)fail('TOO_MANY_RECORDS');
  const capturedAt=now();
  if(typeof capturedAt!=='string'||!Number.isFinite(Date.parse(capturedAt)))fail('INVALID_RESPONSE');
  const snapshot={schema:'xiaoxu/academic-snapshot-v1',source:'second_class',
   kind:'second-class',verifiedSchool:'ncwu',capturedAt,complete:true,records};
  if(records.length===0)snapshot.verifiedEmpty=true;
  return {snapshot,stats:{activityCount:activityRecords.length,semesterCount:scoreRecords.filter(r=>r.recordType==='semester_score').length,
   hasSummary:summaryRows.length===1,categoryCount:categories.length}};
 }
 return Object.freeze({AdapterError,readSnapshot,normalizeActivity,normalizeScoreRow,normalizeTypes,statusForActivity});
});
