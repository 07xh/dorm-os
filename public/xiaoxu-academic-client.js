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
 const isOfficial=kind=>state(kind).provenance==='trusted_readonly';
 const records=kind=>hasData(kind)?state(kind).records:[];
 const stamp=value=>value?new Date(value).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai'}):'未取得';
 const statusLabel=k=>{
  const s=state(k);
  return s.status==='ready'?(isOfficial(k)?'本人授权只读同步 · ':'个人快照 · ')+stamp(s.lastSuccessAt):
   s.status==='stale'?(isOfficial(k)?'过期只读同步 · ':'过期个人快照 · ')+stamp(s.lastSuccessAt):
   s.status==='error'?'连接失败':s.status==='loading'?'正在读取':'未连接';
 };
 function disconnect(){
  connected=false;sources=[];error='';
  TYPES.forEach(kind=>feeds[kind]={status:'not_connected',records:[],lastSuccessAt:null});
 }
 async function get(kind){
  const path=kind==='second-class'?'/api/v1/second-class':'/api/v1/verified/'+encodeURIComponent(kind);
  const resp=await fetch(path,{
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
     feeds[kind]={status:v.status,records:v.records.slice(0,250),lastSuccessAt:v.lastSuccessAt||null,provenance:v.provenance||'user_import',summary:v.summary||null,officialScore:v.officialScore||null,automaticSync:v.automaticSync===true};
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
   '<div class="card-header"><span class="card-title">个人学业数据 · 私人快照</span><span class="xu-private-status '+(active?'is-ready':'')+'">'+(active?'本页已加载本人数据':'尚未授权')+'</span></div>'+
   '<p class="small muted">成绩、课表、考试、作业和二课可保存到受保护的个人后端。手动上传的数据只表示个人快照，不证明学校平台已自动连接；原有本地数据不会被覆盖。</p>'+
   (!active?'<div class="xu-private-connect"><p class="small muted">通过 Cloudflare Access 登录后读取个人存储的学业快照；这不是学校自动同步，也不需要在校序填写学校密码。</p><button type="button" class="primary" data-action="academic-connect">读取授权数据</button></div>':
   '<div class="button-row"><button type="button" class="secondary" data-action="academic-refresh">重新读取</button><button type="button" class="secondary" data-action="academic-disconnect">断开连接</button></div>')+
   (error?'<p class="small xu-private-error" role="alert">'+escapeHtml(error)+'</p>':'')+
   '<div class="xu-private-sources">'+entries+'</div>'+
   '<p class="tiny muted">本页刷新会清除已经加载的私有数据；访问身份由 Cloudflare Access 保护。没有完成学校数据授权和 D1 绑定时不会展示模拟成绩或作业。</p></section>';
 }
 const item=(record,kind)=>{
  const date=record.date||record.dueAt||'';
  const fields=[record.course,record.teacher,record.location,date];
  if(kind==='exams'){
   fields.push(record.startTime?
    '考试开始 '+record.startTime+(record.endTime?'–'+record.endTime:''):'具体考试时刻未验证');
   if(!record.location)fields.push('考场未核实');
  }else if(kind==='second-class'){
   if(record.credit!==undefined)fields.push('学分 '+record.credit);
   if(record.hours!==undefined)fields.push('学时 '+record.hours);
   if(record.points!==undefined)fields.push((isOfficial(kind)?'已核算素质分 ':'积分 ')+record.points);
   const label={registered:'已报名',completed:'已完成',cancelled:'已取消',unknown:'活动状态未核实'};
   fields.push(label[record.activityStatus]||'活动状态未核实');
   const reviews={pending:'待审核',approved:'审核通过',rejected:'审核未通过',
    attended:'已签到记录',unknown:'审核状态未核实'};
   if(record.reviewStatus)fields.push(reviews[record.reviewStatus]||'审核状态未核实');
   const settled={settled:'已核算',unsettled:'未核算',awaiting_settlement:'等待核算',
    unverified:'未验证',unknown:'核算状态未核实'};
   if(record.settlementStatus&&record.settlementStatus!=='unknown')
     fields.push(settled[record.settlementStatus]||'核算状态未核实');
  }else if(record.platformStatus)fields.push(record.platformStatus);
  const detail=fields.filter(Boolean).map(escapeHtml).join(' · ');
  return '<div class="xu-private-item"><strong>'+escapeHtml(record.title)+'</strong><span>'+detail+'</span></div>';
 };
 function panel(kind){
  if(!hasData(kind))return '';
  const s=state(kind);
  const official=kind==='second-class'&&isOfficial(kind)&&s.officialScore?.aggregate?
  '<div class="xu-second-class-official" aria-label="学校原始分数合计">'+
  '<strong>学校显示合计</strong><div class="xu-second-class-summary">'+
  '<div><strong>'+escapeHtml(s.officialScore.aggregate.qualityScore??'未公布')+
  '</strong><small>素质分</small></div>'+
  '<div><strong>'+escapeHtml(s.officialScore.aggregate.volunteerHours??'未公布')+
  '</strong><small>志愿服务时长</small></div></div>'+
  '<p class="tiny muted">来自华水本人分数页的“合计”记录，未与各学期或活动逐条数据重复相加。</p></div>':'';
 const summary=kind==='second-class'&&s.summary?
   '<div class="xu-second-class-summary" aria-label="第二课堂已保存记录统计">'+
   '<div><strong>'+escapeHtml(s.summary.recordCount)+'</strong><small>已保存活动记录</small></div>'+
   ['recordedHours','recordedPoints'].map((key,i)=>{
     const v=s.summary[key]||{},name=i===0?'记录学时':'记录积分';
     return '<div><strong>'+escapeHtml(v.sum===null||v.sum===undefined?'未取得':v.sum)+
       '</strong><small>'+name+' · '+escapeHtml(v.knownCount||0)+' 条含该字段</small></div>';
   }).join('')+'</div><p class="tiny muted">以上仅为已保存活动记录中有数值字段的合计，不等于学校官方累计学时或积分；字段缺失时不按零计算。</p>':'';
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
   return item(record,kind);
  }).join(''):'<div class="friendly-empty">'+(kind==='second-class'?'当前没有已保存的第二课堂活动；这不证明学校平台无记录。':'本次保存的快照为空；不代表真实平台目前没有任务。')+'</div>';
  return '<section class="card xu-private-view" aria-label="'+escapeHtml(SOURCE[kind])+'"><div class="card-header"><span class="card-title">'+escapeHtml(SOURCE[kind])+'</span><span class="xu-private-status '+(s.status==='ready'?'is-ready':'')+'">'+(s.status==='stale'?'数据已过期':isOfficial(kind)?'授权只读同步':'个人快照')+'</span></div>'+
   '<p class="tiny muted">来源：'+(isOfficial(kind)?'本人授权的受信任只读连接器 · ':'本人保存的个人快照 · ')+escapeHtml(SOURCE[kind])+' · 获取于 '+escapeHtml(stamp(s.lastSuccessAt))+
   (s.status==='stale'?' · 请重新同步':'')+'</p>'+(kind==='second-class'?'<p class="tiny muted">校序尚未启动第二课堂无人值守自动更新；展示的是上一次保存的数据。</p>':'')+official+summary+'<div class="xu-private-list">'+rows+'</div></section>';
 }
 function taskRows(filter='all',localTasks=[]){
  if(!hasData('assignments')||filter==='done')return '';
  const localIds=new Set((Array.isArray(localTasks)?localTasks:[])
   .filter(t=>t.source==='chaoxing'&&typeof t.sourceId==='string'&&t.sourceId)
   .map(t=>t.sourceId));
  const cloudKeys=new Set(root.XiaoxuCloudTasks?.snapshotKeys?.()||[]);
  const list=records('assignments')
   .filter(r=>typeof r.externalId==='string'&&!localIds.has(r.externalId)&&
      !cloudKeys.has('chaoxing|'+r.externalId))
   .filter(r=>filter!=='open'||['todo','returned','unknown'].includes(r.platformStatus));
  if(!list.length)return '';
  return list.map(r=>'<div class="task-item xu-private-task" data-origin="remote-readonly">'+
   '<span class="xu-private-mark" aria-hidden="true">●</span><div class="task-main"><div class="entry-title">'+escapeHtml(r.title)+'</div>'+
   '<div class="entry-sub">学习通 · '+escapeHtml(r.course||'')+
   (r.dueAt?' · 截止 '+escapeHtml(stamp(r.dueAt)):' · 截止时间未核实')+
   ' · '+escapeHtml(({todo:'未提交',submitted:'已提交',graded:'已批阅',returned:'已退回',unknown:'待核实'})[r.platformStatus]||'待核实')+
   '</div></div><span class="pill">个人快照</span></div>').join('');
 }
 root.XiaoxuAcademic=Object.freeze({TYPES,connect,disconnect,refresh,state,hasData,records,connectionPanel,panel,taskRows,statusLabel});
})(globalThis);
