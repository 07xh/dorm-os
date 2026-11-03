// ==UserScript==
// @name         校序 · 第二课堂本人同步 + 只读导出
// @namespace    https://dorm-os-dk9.pages.dev/
// @version      0.2.0
// @description  在本人已登录的华水第二课堂同步脱敏活动到校序（需先自愿配对，支持本机导出）
// @match        https://dekt.ncwu.edu.cn/*
// @run-at       document-idle
// @grant        GM.getValue
// @grant        GM.setValue
// @grant        GM.deleteValue
// @grant        GM.xmlHttpRequest
// @connect      dorm-os-dk9.pages.dev
// @noframes
// ==/UserScript==
/* Explicit owner-approved pairing; no password, cookies, CAS tickets, or
 * university write APIs. Cross-origin upload is restricted to Xiaoxu's bridge.
 * Runs only while an authenticated official second-class page is open/active.
 * Every upload is user_import, NOT a server-side university trusted sync.
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

 const BRIDGE_URL='https://dorm-os-dk9.pages.dev/api/v1/second-class/bridge/ingest';
 const BRIDGE_TOKEN_KEY='xiaoxuSecondClassBrowserBridgeTokenV1';
 const BRIDGE_LAST_KEY='xiaoxuSecondClassBrowserBridgeLastSyncV1';
 const BRIDGE_CADENCE_MS=30*60_000;
 const BRIDGE_TOKEN=/^[A-Za-z0-9_-]{43}$/;
 let bridgeBusy=false,bridgeStatusNode=null;
 function showBridgeStatus(message){
  if(bridgeStatusNode)bridgeStatusNode.textContent=message;
 }
 function bridgeApiReady(){
  return typeof GM!=='undefined'&&typeof GM.getValue==='function'&&
   typeof GM.setValue==='function'&&typeof GM.deleteValue==='function'&&
   typeof GM.xmlHttpRequest==='function';
 }
 async function postToXiaoxu(snapshot,token){
  if(!BRIDGE_TOKEN.test(token))fail('校序配对密钥格式无效');
  const response=await GM.xmlHttpRequest({
   method:'POST',url:BRIDGE_URL,anonymous:true,timeout:25000,
   headers:{'content-type':'application/json','authorization':'Bearer '+token,
    'accept':'application/json'},
   data:JSON.stringify(snapshot),responseType:'json'
  });
  const status=response?.status;
  const body=typeof response?.response==='object'&&response.response!==null
   ?response.response:{};
  if(status===202&&body.provenance==='user_import'&&body.kind==='second-class')
   return {count:body.count};
  if(status===429)fail('校序限制十分钟内重复同步，请稍后再试');
  if(status===401||status===410) {
   await GM.deleteValue(BRIDGE_TOKEN_KEY);
   await GM.deleteValue(BRIDGE_LAST_KEY);
   fail('校序配对密钥失效，已清理本机配对，请重新生成');
  }
  if(status===409&&body.error==='trusted_source_readonly') {
   await GM.deleteValue(BRIDGE_TOKEN_KEY);
   fail('校序已有可信学校同步记录，已停止个人导入，避免覆盖');
  }
  fail('校序未接受此次同步（HTTP '+String(status||0)+'），未修改历史记录');
 }
 async function browserBridgeSync(force=false){
  if(bridgeBusy||!bridgeApiReady())return;
  const token=await GM.getValue(BRIDGE_TOKEN_KEY,'');
  if(!BRIDGE_TOKEN.test(token))return;
  const last=Number(await GM.getValue(BRIDGE_LAST_KEY,0))||0;
  if(!force&&Date.now()-last<BRIDGE_CADENCE_MS)return;
  bridgeBusy=true;showBridgeStatus('正在读取本人活动并同步…');
  try{
   const {snapshot}=await collect(readPost);
   const result=await postToXiaoxu(snapshot,token);
   await GM.setValue(BRIDGE_LAST_KEY,Date.now());
   showBridgeStatus('已同步 '+result.count+' 条活动 · 个人只读数据');
  }catch(error){
   showBridgeStatus('同步未完成：'+String(error?.message||'学校会话失效').slice(0,85));
  }finally{bridgeBusy=false;}
 }
 function installBridgeControls(){
  if(root.location?.hostname!=='dekt.ncwu.edu.cn'||!bridgeApiReady())return;
  if(document.getElementById('xu-second-class-sync-panel'))return;
  const container=document.createElement('section');
  container.id='xu-second-class-sync-panel';
  container.style.cssText='position:fixed;right:16px;bottom:78px;z-index:2147483000;'+
   'max-width:min(310px,calc(100vw - 32px));background:white;color:#23304a;'+
   'padding:10px;border:1px solid #d9e5f8;border-radius:12px;'+
   'box-shadow:0 6px 20px #0003;font:12px/1.5 system-ui';
  const heading=document.createElement('div');
  heading.textContent='校序 · 本人二课同步（网页运行时）';
  heading.style.cssText='font-weight:700;margin-bottom:6px';
  container.appendChild(heading);
  const controls=document.createElement('div');
  controls.style.cssText='display:flex;flex-wrap:wrap;gap:6px';
  function control(text,handler){
   const btn=document.createElement('button');btn.type='button';btn.textContent=text;
   btn.style.cssText='background:#e9f1ff;color:#1647a2;border:0;border-radius:8px;'+
    'padding:7px 9px;cursor:pointer;font:600 12px system-ui';
   btn.addEventListener('click',()=>{Promise.resolve(handler()).catch(e=>{
    showBridgeStatus('操作失败：'+String(e?.message||'请重试').slice(0,75));
   });});controls.appendChild(btn);
  }
  control('首次连接',async()=>{
   const token=root.prompt('到校序 → 我的 → 数据连接 → 第二课堂生成配对密钥。'+
    '\n只复制一次性配对密钥，绝不要填写学校密码或 Cookie。');
   if(token===null)return;
   if(!BRIDGE_TOKEN.test(token.trim())){
    showBridgeStatus('配对密钥格式无效，请在校序重新生成');return;
   }
   await GM.setValue(BRIDGE_TOKEN_KEY,token.trim());
   await GM.setValue(BRIDGE_LAST_KEY,0);
   showBridgeStatus('已保存校序配对，开始首次同步…');
   await browserBridgeSync(true);
  });
  control('立即同步',()=>browserBridgeSync(true));
  control('断开本机配对',async()=>{
   if(!root.confirm('删除本机校序配对密钥？校序中已有的个人活动数据会保留。'))return;
   await GM.deleteValue(BRIDGE_TOKEN_KEY);
   await GM.deleteValue(BRIDGE_LAST_KEY);
   showBridgeStatus('已清理本机密钥；如需彻底撤销，请到校序连接中心撤销');
  });
  container.appendChild(controls);
  const note=document.createElement('div');
  note.style.cssText='margin-top:6px;color:#64748b';
  note.textContent='只调用本人已登录网页的只读接口；页面关闭或会话失效无法持续同步。';
  container.appendChild(note);
  bridgeStatusNode=document.createElement('div');
  bridgeStatusNode.setAttribute('role','status');
  bridgeStatusNode.style.cssText='margin-top:6px;overflow-wrap:anywhere';
  bridgeStatusNode.textContent='未读取配对状态';
  container.appendChild(bridgeStatusNode);
  document.body.appendChild(container);
  GM.getValue(BRIDGE_TOKEN_KEY,'').then(token=>{
   showBridgeStatus(BRIDGE_TOKEN.test(token)?
    '已配对，正在检查本次同步…':'尚未配对，点击「首次连接」');
   if(BRIDGE_TOKEN.test(token))void browserBridgeSync();
  }).catch(()=>{showBridgeStatus('脚本存储权限不可用');});
  root.setInterval(()=>{void browserBridgeSync();},BRIDGE_CADENCE_MS);
  document.addEventListener('visibilitychange',()=>{
   if(!document.hidden)void browserBridgeSync();
  });
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
  const install=()=>{installButton();installBridgeControls();};
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',install,{once:true});
  else install();
 }
})(globalThis);
