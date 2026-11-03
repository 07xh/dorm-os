// ==UserScript==
// @name         校序 · 华水第二课堂本人只读导出
// @namespace    https://dorm-os-dk9.pages.dev/
// @version      0.1.0
// @description  在第二课堂本人已登录页面读取活动与分数，脱敏后仅保存本机文件；不上传凭据
// @match        https://dekt.ncwu.edu.cn/*
// @run-at       document-idle
// @grant        none
// ==/UserScript==
/* No login automation, cookies, CAS tickets, cross-origin upload or write APIs.
 * A user must first sign in through the university itself and click export.
 * The result is a PERSONAL IMPORT, never an independently trusted connector.
 */
(function(root){
 'use strict';
 const P=Object.freeze({
  activities:'/sys/stu/center/signupactivity/list',
  types:'/sys/stu/score/types',
  scores:'/sys/stu/score/list'
 });
 const allowed=new Set(Object.values(P));
 const fail=msg=>{throw Error(msg);};
 const cleanText=(value,max=120)=>{
  if(typeof value!=='string'||!value.trim()||value.length>max)fail('学校数据文本格式异常');
  return value.trim();
 };
 const nonnegative=(value)=>{
  if(value===undefined||value===null||value==='')return null;
  if(typeof value!=='number'||!Number.isFinite(value)||value<0||value>100000)
   fail('学校积分数字异常');
  return value;
 };
 const sameOwner=(rows,student)=>{
  for(const row of rows){
   if(!row||typeof row!=='object'||typeof row.student_code!=='string'||
      !row.student_code||row.student_code!==student)fail('学校本人身份不一致，终止导出');
  }
 };
 function shapePage(data,page){
  if(!data||!Array.isArray(data.data)||data.data.length>50||
     !Number.isSafeInteger(data.totalPage)||data.totalPage<0||data.totalPage>25||
     !Number.isSafeInteger(data.totalSize)||data.totalSize<0||data.totalSize>250||
     !Number.isSafeInteger(data.startIndex)||data.startIndex<0)
   fail('学校分页返回格式不完整');
  if(data.totalSize===0&&data.totalPage===0&&data.data.length===0&&page===1)return true;
  if(data.totalPage<page||data.totalSize===0||!data.data.length)
   fail('学校分页不完整，不允许导出');
  return false;
 }
 function mapActivity(row){
  if(!row||typeof row!=='object')fail('活动数据无效');
  const id=cleanText(row.id),title=cleanText(row.activity_name);
  if(!/^[A-Za-z0-9:_-]{1,120}$/.test(id))fail('活动稳定 ID 无效');
  const year=row.school_year,term=row.school_term;
  if(year!==undefined&&year!==null&&
     (typeof year!=='string'||year.length>30))fail('学年异常');
  if(term!==undefined&&term!==null&&(!['1','2'].includes(term)))fail('学期异常');
  const status=row.verify_state==='2'||row.verify_state==='1'&&row.activity_state==='5'
   ?'settled':row.verify_state==='1'&&row.activity_state==='4'
   ?'unsettled':row.verify_state==='1'&&row.activity_state==='6'
   ?'awaiting_settlement':'unverified';
  const result={
   externalId:id,title,
   ...(year&&term?{term:year+'-'+term}:{}),
   activityStatus:row.signup_state==='1'?'registered':'unknown',
   reviewStatus:'unknown',settlementStatus:status
  };
  if(row.activity_start){
   if(typeof row.activity_start!=='string'||
      !/^20\d{2}-\d{2}-\d{2}(?: \d\d:\d\d:\d\d)?$/.test(row.activity_start))
    fail('活动日期异常');
   const date=row.activity_start.slice(0,10);
   if(new Date(date+'T00:00:00Z').toISOString().slice(0,10)!==date)fail('活动日期无效');
   result.date=date;
  }
  if(status==='settled'){
   const points=nonnegative(row.score_value);
   if(points!==null)result.points=points;
  }
  return result;
 }
 async function collect(readPost,now=()=>new Date().toISOString()){
  if(typeof readPost!=='function')fail('只读查询未配置');
  const categories=await readPost(P.types,{});
  if(!Array.isArray(categories)||!categories.length||categories.length>12||
    categories.some(row=>!row||typeof row.code!=='string'||
     !/^[1-9]$/.test(row.code)||typeof row.name!=='string'||!row.name.trim()))
   fail('素质分分类结构未识别');
  let student=null,count=0,total=null,records=[],seen=new Set();
  for(let page=1;page<=25;page++){
   const raw=await readPost(P.activities,{page:String(page),pagesize:'50'});
   const empty=shapePage(raw,page);
   if(total===null)total=raw.totalSize;
   if(total!==raw.totalSize)fail('活动总数在导出期间发生变化');
   for(const row of raw.data){
    if(typeof row.student_code!=='string'||!row.student_code)
     fail('学校返回未包含本人身份，终止导出');
    if(student!==null&&student!==row.student_code)
     fail('学校本人身份在分页间发生变化');
    student=row.student_code;
    const item=mapActivity(row),key=item.externalId+'|'+(item.term||'');
    if(seen.has(key))fail('发现重复活动 ID');
    seen.add(key);records.push(item);count++;
   }
   if(count>250)fail('活动数超过安全上限');
   if(empty||page===raw.totalPage){
    if(count!==raw.totalSize)fail('学校分页条数不完整');
    break;
   }
   if(page===25)fail('活动分页达到上限');
  }
  const scores=await readPost(P.scores,{});
  // The observed score/list response uses totalPage=0 even with data rows.
  // It is NOT the activity endpoint's page-count convention.
  if(!scores||!Array.isArray(scores.data)||scores.data.length>50||
     !Number.isSafeInteger(scores.totalSize)||scores.totalSize<0||
     !Number.isSafeInteger(scores.totalPage)||scores.totalPage<0||
     scores.totalPage>1||scores.data.length!==scores.totalSize)
   fail('学校素质分列表不完整');
  // Score rows must be associated with one student. No student ID is exported.
  if(scores.data.length){
   if(student===null)student=cleanText(scores.data[0].student_code,50);
   sameOwner(scores.data,student);
  }
  const overall=scores.data.find(row=>row.school_term_index===100);
  if(scores.data.filter(row=>row.school_term_index===100).length>1)
   fail('学校分数合计重复');
  const stamp=now();
  if(typeof stamp!=='string'||!Number.isFinite(Date.parse(stamp)))fail('导出时间无效');
  // The school-wide aggregate is a local preview only. Importing cannot
  // claim an official independently attested total in Academic OS.
  const schoolReportedPreview=overall?{
   qualityScore:nonnegative(overall.score_value),
   volunteerHours:nonnegative(overall.score_plus),
   origin:'unverified_local_browser_export'
  }:null;
  const snapshot={
   schema:'xiaoxu/academic-snapshot-v1',source:'second_class',kind:'second-class',
   verifiedSchool:'ncwu',capturedAt:stamp,complete:true,
   verifiedEmpty:records.length===0,records
  };
  return {snapshot,schoolReportedPreview};
 }
 async function readPost(path,form){
  if(!allowed.has(path))fail('拒绝非白名单接口');
  const response=await fetch(path,{
   method:'POST',credentials:'same-origin',cache:'no-store',redirect:'error',
   headers:{'accept':'application/json','content-type':'application/x-www-form-urlencoded;charset=UTF-8'},
   body:new URLSearchParams(form).toString()
  });
  if(!response.ok)fail('学校会话无效或只读请求失败（HTTP '+response.status+'）');
  const contentType=response.headers.get('content-type')||'';
  if(!contentType.toLowerCase().includes('json'))fail('学校返回非 JSON，请先在官网完成登录');
  return response.json();
 }
 function installButton(){
  if(root.location?.hostname!=='dekt.ncwu.edu.cn')return;
  if(document.getElementById('xu-second-class-export'))return;
  const button=document.createElement('button');
  button.id='xu-second-class-export';button.type='button';
  button.textContent='导出本人二课数据';
  button.style.cssText='position:fixed;right:16px;bottom:20px;z-index:2147483000;'+
   'background:#215ad5;color:white;border:0;border-radius:12px;padding:12px 16px;'+
   'box-shadow:0 6px 20px #0003;font:600 14px system-ui;cursor:pointer';
  button.addEventListener('click',async()=>{
   button.disabled=true;button.textContent='正在读取本人活动与积分…';
   try{
    const result=await collect(readPost);
    const blob=new Blob([JSON.stringify(result,null,2)],{type:'application/json'});
    const link=document.createElement('a');
    const url=URL.createObjectURL(blob);
    link.href=url;link.download='xiaoxu-second-class-personal-export.json';
    link.click();
    setTimeout(()=>URL.revokeObjectURL(url),1000);
    button.textContent='已保存本机文件 · 可导入校序';
   }catch(error){
    button.textContent='导出失败 · 再试一次';
    root.alert('无法完整读取本人数据：'+String(error.message||'未知错误').slice(0,120)+
     '\n请先确认已通过学校官网登录。未生成任何数据文件。');
   }finally{button.disabled=false;}
  });
  document.body.appendChild(button);
 }
 root.XiaoxuSecondClassLocalExport=Object.freeze({collect,paths:P});
 if(typeof document!=='undefined'){
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',installButton,{once:true});
  else installButton();
 }
})(globalThis);
