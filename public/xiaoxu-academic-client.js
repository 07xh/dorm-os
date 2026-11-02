/* 校序私有学业只读客户端：无账号密码、无 Cookie 保存、无虚构数据。
 * 连接密钥只在当前页面 JS 内存保留，刷新页面即断开；原本地数据不变。
 */
(function(root){
 'use strict';
 const TYPES=['schedule','assignments','grades','exams','second-class'];
 const SOURCE={schedule:'青果课表',assignments:'学习通作业',grades:'青果成绩',exams:'青果考试','second-class':'第二课堂'};
 const escapeHtml=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 let connected=false,sources=[],error='';
 const feeds=Object.fromEntries(TYPES.map(kind=>[kind,{status:'not_connected',records:[],lastSuccessAt:null}]));
 const state=kind=>feeds[kind]||{status:'not_connected',records:[]};
 const hasData=kind=>connected&&['ready','stale'].includes(state(kind).status);
 const records=kind=>hasData(kind)?state(kind).records:[];
 const stamp=value=>value?new Date(value).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai'}):'未取得';
 const statusLabel=k=>{
  const s=state(k);
  return s.status==='ready'?'快照已获取 · '+stamp(s.lastSuccessAt):
   s.status==='stale'?'历史记录 · '+stamp(s.lastSuccessAt):
   s.status==='error'?'连接失败':s.status==='loading'?'正在读取':'未连接';
 };
 function disconnect(){
  connected=false;sources=[];error='';
  TYPES.forEach(kind=>feeds[kind]={status:'not_connected',records:[],lastSuccessAt:null});
 }
 async function get(kind){
  const resp=await fetch('/api/v1/verified/'+encodeURIComponent(kind),{
   headers:{accept:'application/json'},
   cache:'no-store',credentials:'same-origin',redirect:'error'
  });
  const body=await resp.json().catch(()=>null);
  if(!resp.ok)throw Error(body?.message||(
   resp.status===401?'请先通过 Cloudflare Access 完成本人授权':'个人学业服务尚未配置或不可用'));
  if(!body||typeof body!=='object')throw Error('授权登录尚未完成，服务返回了非 JSON 响应');
  return body;
 }
 async function refresh(){
  if(!connected)throw Error('尚未加载个人学业数据');
  const results=await Promise.allSettled(TYPES.map(kind=>get(kind)));
  if(!connected)return;
  for(let i=0;i<TYPES.length;i++){
   const kind=TYPES[i],result=results[i];
   if(result.status==='fulfilled'&&Array.isArray(result.value?.records)&&
       ['ready','stale','not_connected'].includes(result.value.status)){
     const v=result.value;
     feeds[kind]={status:v.status,records:v.records.slice(0,250),lastSuccessAt:v.lastSuccessAt||null};
   }else feeds[kind]={...feeds[kind],status:'error'};
  }
  error=results.some(v=>v.status==='rejected')?'部分个人数据源暂时不可用；不清空历史会话记录。':'';
  return true;
 }
 async function connect(){
  connected=true;
  try{
   const result=await get('connections');
   if(!Array.isArray(result.sources))throw Error('连接状态响应无效');
   sources=result.sources;
   await refresh();
   return true;
  }catch(err){disconnect();error=String(err.message||'个人学业连接失败');throw err;}
 }
 function connectionPanel(){
  const active=connected;
  const entries=TYPES.map(kind=>'<div class="xu-private-source"><span>'+escapeHtml(SOURCE[kind])+'</span><small>'+escapeHtml(statusLabel(kind))+'</small></div>').join('');
  return '<section class="card xu-private-panel" aria-label="个人学业数据连接">'+
   '<div class="card-header"><span class="card-title">个人学业数据 · 授权连接</span><span class="xu-private-status '+(active?'is-ready':'')+'">'+(active?'本页已加载本人数据':'尚未授权')+'</span></div>'+
   '<p class="small muted">成绩、课表、考试、学习通作业和二课共用一个受保护的后端。仅在本人授权并完成真实数据核验后展示，已有本地课表和任务不会被覆盖。</p>'+
   (!active?'<div class="xu-private-connect"><p class="small muted">通过 Cloudflare Access 登录本人账户后，读取已核验的个人学业快照；不需要在校序填写学校密码。</p><button type="button" class="primary" data-action="academic-connect">读取授权数据</button></div>':
   '<div class="button-row"><button type="button" class="secondary" data-action="academic-refresh">重新读取</button><button type="button" class="secondary" data-action="academic-disconnect">断开连接</button></div>')+
   (error?'<p class="small xu-private-error" role="alert">'+escapeHtml(error)+'</p>':'')+
   '<div class="xu-private-sources">'+entries+'</div>'+
   '<p class="tiny muted">本页刷新会清除已经加载的私有数据；访问身份由 Cloudflare Access 保护。没有完成学校数据授权和 D1 绑定时不会展示模拟成绩或作业。</p></section>';
 }
 const item=record=>{
  const date=record.date||record.dueAt||'';
  const detail=[record.course,record.teacher,record.location,date,record.platformStatus].filter(Boolean).map(escapeHtml).join(' · ');
  return '<div class="xu-private-item"><strong>'+escapeHtml(record.title)+'</strong><span>'+detail+'</span></div>';
 };
 function panel(kind){
  if(!hasData(kind))return '';
  const s=state(kind);
  const rows=s.records.length?s.records.map(record=>{
   if(kind==='schedule'){
    const weeks=Array.isArray(record.weeks)?record.weeks.join('、'):'';
    return '<div class="xu-private-item"><strong>'+escapeHtml(record.title)+'</strong><span>'+
     escapeHtml('周'+record.weekday+' · 第'+record.startSection+'–'+record.endSection+'节 · '+weeks+'周')+
     (record.teacher?' · '+escapeHtml(record.teacher):'')+
     (record.location?' · '+escapeHtml(record.location):'')+
     '</span></div>';
   }
   if(kind==='grades'){
    return '<div class="xu-private-item"><strong>'+escapeHtml(record.title)+'</strong><span>'+
     escapeHtml([record.score||'成绩未公布',record.credit!==undefined?'学分 '+record.credit:'',record.gradePoint!==undefined?'绩点 '+record.gradePoint:'',record.term].filter(Boolean).join(' · '))+
     '</span></div>';
   }
   return item(record);
  }).join(''):'<div class="friendly-empty">本次已核验的完整同步结果为空，没有编造记录。</div>';
  return '<section class="card xu-private-view" aria-label="'+escapeHtml(SOURCE[kind])+'"><div class="card-header"><span class="card-title">'+escapeHtml(SOURCE[kind])+'</span><span class="xu-private-status '+(s.status==='ready'?'is-ready':'')+'">'+(s.status==='stale'?'快照已过期':'已核验快照')+'</span></div>'+
   '<p class="tiny muted">来源：已核验的本人'+escapeHtml(SOURCE[kind])+'快照 · 获取于 '+escapeHtml(stamp(s.lastSuccessAt))+
   (s.status==='stale'?' · 请重新同步':'')+'</p><div class="xu-private-list">'+rows+'</div></section>';
 }
 function taskRows(filter='all'){
  if(!hasData('assignments')||filter==='done')return '';
  const list=records('assignments').filter(r=>filter!=='open'||['todo','returned','unknown'].includes(r.platformStatus));
  if(!list.length)return '';
  return list.map(r=>'<div class="task-item xu-private-task" data-origin="remote-readonly">'+
   '<span class="xu-private-mark" aria-hidden="true">●</span><div class="task-main"><div class="entry-title">'+escapeHtml(r.title)+'</div>'+
   '<div class="entry-sub">学习通 · '+escapeHtml(r.course||'')+
   (r.dueAt?' · 截止 '+escapeHtml(stamp(r.dueAt)):' · 截止时间未核实')+
   ' · '+escapeHtml(({todo:'未提交',submitted:'已提交',graded:'已批阅',returned:'已退回',unknown:'待核实'})[r.platformStatus]||'待核实')+
   '</div></div><span class="pill">只读同步</span></div>').join('');
 }
 root.XiaoxuAcademic=Object.freeze({TYPES,connect,disconnect,refresh,state,hasData,records,connectionPanel,panel,taskRows,statusLabel});
})(globalThis);
