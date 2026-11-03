/* 校序私有学业只读客户端：无账号密码、无 Cookie 保存、无虚构数据。
 * 连接密钥只在当前页面 JS 内存保留，刷新页面即断开；原本地数据不变。
 */
(function(root){
 'use strict';
 const TYPES=['schedule','assignments','grades','exams','second-class'];
 const SOURCE={schedule:'青果课表',assignments:'学习通作业',grades:'青果成绩',exams:'青果考试','second-class':'第二课堂'};
 const escapeHtml=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 let connected=false,sources=[],error='',serviceHealth=null,healthError='';
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
  connected=false;sources=[];error='';serviceHealth=null;healthError='';
  TYPES.forEach(kind=>feeds[kind]={status:'not_connected',records:[],lastSuccessAt:null});
 }
 async function get(kind){
  const path=kind==='connections'?'/api/v1/connections':kind==='second-class'?'/api/v1/second-class':'/api/v1/verified/'+encodeURIComponent(kind);
  const resp=await fetch(path,{
   headers:{accept:'application/json'},
   cache:'no-store',credentials:'same-origin',redirect:'error'
  });
  const body=await resp.json().catch(()=>null);
  if(!resp.ok)throw Error(body?.message||(
   resp.status===401?'请先登录校序':'个人学业服务尚未配置或不可用'));
  if(!body||typeof body!=='object')throw Error('授权登录尚未完成，服务返回了非 JSON 响应');
  return body;
 }
 async function refresh(){
  if(!connected)throw Error('尚未加载个人学业数据');
  const overview=await get('connections');
  if(overview?.schema!=='xiaoxu/connections-v1'||!Array.isArray(overview.platforms)||
     overview.platforms.length!==3)throw Error('数据连接状态格式无效');
  sources=overview.platforms;
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
   await refresh();
   return true;
  }catch(err){disconnect();error=String(err.message||'个人学业连接失败');throw err;}
 }
 async function diagnose(){
  if(!connected)throw Error('请先登录校序并读取个人连接状态');
  try{
   const response=await fetch('/api/v1/health',{credentials:'same-origin',
    cache:'no-store',redirect:'error',headers:{accept:'application/json'}});
   const data=await response.json().catch(()=>null);
   if(!data||!['database_ready','database_schema_incomplete',
      'database_unavailable','database_not_configured'].includes(data.status))
     throw Error(response.status===401?'登录已过期，请重新登录校序':'学业服务自检暂时不可用');
   serviceHealth={
    status:data.status,databaseReady:data.databaseReady===true,
    missingTables:Array.isArray(data.missingTables)?data.missingTables.slice(0,25):[],
    missingColumns:Array.isArray(data.missingColumns)?data.missingColumns.slice(0,30):[],
    loginAuthenticated:data.loginAuthenticated===true,
    schoolConnection:'not_verified'
   };
   healthError='';
   return serviceHealth;
  }catch(err){
   serviceHealth=null;healthError=String(err.message||'服务自检失败');
   throw err;
  }
 }
 const CONNECTION_PROVIDERS=Object.freeze([
  {id:'ncwu',name:'华水青果教务',url:'https://jwmis.ncwu.edu.cn/',what:'课表、成绩、考试'},
  {id:'chaoxing',name:'学习通',url:'https://ncwu.fanya.chaoxing.com/portal',what:'课程作业、截止日期、提交状态'},
  {id:'second_class',name:'第二课堂',url:'https://dekt.ncwu.edu.cn/',what:'活动、积分与审核记录'}
 ]);
 const CONNECTION_LABELS={connected:'曾完成只读同步',partial:'上次同步不完整',
   expired:'学校授权已过期',error:'上次同步失败',not_connected:'未连接'};
 function connectionPanel(){
  const active=connected;
  const entries=CONNECTION_PROVIDERS.map(provider=>{
   const record=sources.find(x=>x.id===provider.id)||null;
   const label=record?(record.sessionState==='expired'?'学校登录会话已过期':CONNECTION_LABELS[record.status]||'状态待确认'):'未读取连接状态';
   const trusted=Array.isArray(record?.trustedKinds)?record.trustedKinds.length:0;
   const imported=Array.isArray(record?.importedKinds)?record.importedKinds.length:0;
   const details=trusted?'有 '+trusted+' 类可信只读记录 · 当前学校授权仍需独立核验':
     imported?'有 '+imported+' 类个人导入记录 · 不是账号绑定':'尚无已验证的学校同步记录';
   const stampText=record?.lastSuccessAt?' · 上次记录 '+stamp(record.lastSuccessAt):'';
   const authorized=record?.accountAuthorization==='verified_session'&&record?.sessionState==='active';
   const canLogin=record?.loginMode==='password';
   const authPanel=canLogin?
    '<details class="xu-school-connect"><summary>'+ (authorized?'账号管理 / 重新连接':'在校序网页登录连接')+'</summary>'+
    '<form data-xu-school-login="'+escapeHtml(provider.id)+'" autocomplete="on">'+
    '<label>学校账号<input name="username" type="text" autocomplete="username" maxlength="128" required placeholder="学校系统登录账号"></label>'+
    '<label>学校密码<input name="password" type="password" autocomplete="current-password" maxlength="512" required placeholder="仅本次登录使用"></label>'+
    '<p class="tiny muted">通过校序 HTTPS 安全后端验证；不在本机保存学校密码，成功后仅保存加密的有效会话。</p>'+
    '<button type="submit" class="primary">登录并同步</button><p class="tiny" role="status" aria-live="polite" data-xu-school-result></p></form></details>':
    '<p class="tiny muted xu-school-pending">网页登录连接尚待学校实际适配验证，当前不会收集账号或密码。</p>';
   const sessionTools=authorized?
    '<div class="xu-school-session-tools"><button type="button" class="secondary" data-xu-school-sync="'+escapeHtml(provider.id)+'">同步数据</button>'+
    '<button type="button" class="secondary" data-xu-school-disconnect="'+escapeHtml(provider.id)+'">解除连接</button></div>':'';
   const guidance=provider.id==='ncwu'?
    '<details class="xu-kingo-guide"><summary>账号授权与同步说明</summary>'+
    '<p>华水教务可能先经过统一身份认证，再进入青果教务。校序仅在经过验证并启用安全后端时提供站内账号登录；否则请使用学校官方入口。</p>'+
    '<p>目前没有验证学校向第三方网站开放授权回调。打开官网不等于绑定成功，校序不能读取其他网站的登录 Cookie。</p>'+
    '<p>已准备课表、成绩、考试的可信只读接口与本人身份校验。取得学校许可的授权读取方式并完成真实对照后，才能启用自动同步。</p></details>':
    provider.id==='chaoxing'?
    '<details class="xu-provider-guide"><summary>学习通连接方式</summary>'+
    '<p>华水学习通可通过学校官方统一身份认证登录；登录学习通网站不会自动授权校序读取账号。</p>'+
    '<p>目前可以使用本人设备上的 xuexitong-cli 只读导出，再在下方「学校官网连通性诊断与手动导入」中导入作业 JSON。</p>'+
    '<p>本机导入属于部分覆盖，不代表已自动同步。学校账号的授权续期与完整作业接口尚未验证。</p></details>':
    '<details class="xu-provider-guide"><summary>第二课堂连接方式</summary>'+
    '<p>活动与素质分的本人只读响应结构已核验，现有连接器能够区分审核、核算状态和学校报告的分数合计。</p>'+
    '<p>仍须使用经本人授权且身份匹配的受信任会话；目前不能在校序内无感保持学校登录或自动刷新。</p>'+
    '<p>进入官方第二课堂只会打开学校页面，不等于账号已绑定。</p></details>';
   return '<div class="xu-connection-source"><div class="xu-connection-copy">'+
    '<strong>'+escapeHtml(provider.name)+'</strong><small>'+escapeHtml(provider.what)+'</small>'+
    '<span>'+escapeHtml(details+stampText)+'</span>'+authPanel+guidance+'</div>'+
    '<div class="xu-connection-actions"><span class="xu-private-status">'+escapeHtml(authorized?'已授权 · 可同步':label)+'</span>'+sessionTools+
    '<a class="secondary xu-connection-link" target="_blank" rel="noopener noreferrer" href="'+
    escapeHtml(provider.url)+'">打开学校官网</a></div></div>';
  }).join('');
  return '<section class="card xu-private-panel" aria-label="统一数据连接中心">'+
   '<div class="card-header"><span class="card-title">数据连接</span>'+
   '<span class="xu-private-status '+(active?'is-ready':'')+'">'+
   (active?'校序云端已读取':'校序云端未读取')+'</span></div>'+
   '<p class="small muted xu-connection-intro">校序与学校账号分开授权。只有学校登录适配器可用时，才会显示账号输入框。</p>'+
   '<div class="xu-connection-list">'+entries+'</div>'+
   '<div class="xu-connection-bottom">'+
   (!active?'<button type="button" class="primary" data-action="academic-connect">读取我的连接状态</button>':
    '<button type="button" class="secondary" data-action="academic-refresh">刷新连接状态与数据</button>'+
    '<button type="button" class="secondary" data-action="academic-disconnect">清除此页私人数据</button>')+
   '</div>'+(error?'<p class="small xu-private-error" role="alert">'+escapeHtml(error)+'</p>':'')+
   '<details class="xu-connection-diagnostics"><summary>校序数据服务自检</summary>'+
   '<p class="tiny muted">仅检测本人登录和 D1 学业表结构，不检查学校账号是否已连接，也不会修改数据。</p>'+
   (active?'<button type="button" class="secondary" data-action="academic-health">只读检查数据库</button>':
    '<p class="small muted">请先使用上方按钮读取本人连接状态。</p>')+
   (serviceHealth?'<p class="small" aria-live="polite">'+
     (serviceHealth.databaseReady?'校序数据库结构完整；当前校序登录已通过。':
      serviceHealth.status==='database_schema_incomplete'?'校序数据库结构不完整；需要核对生产迁移。':
      '校序数据服务暂不可用，暂时无法确认数据库结构。')+
      (serviceHealth.missingTables.length?' 缺少表：'+escapeHtml(serviceHealth.missingTables.join('、')):'')+
      (serviceHealth.missingColumns.length?' 缺少字段：'+escapeHtml(serviceHealth.missingColumns.join('、')):'')+
     '</p>':'')+
   (healthError?'<p class="small xu-private-error" role="alert">'+escapeHtml(healthError)+'</p>':'')+
   '</details>'+
   '<p class="tiny muted">“打开学校官网”仅用于本人正常登录，不会自动绑定账号。学校授权、在线同步和自动续期仍需在真实平台验证后逐项启用；过去保存的快照不会被误标为实时数据。</p></section>';
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
 const schoolMessage={invalid_credentials:'请填写有效学校账号和密码',authorization_failed:'学校登录或身份校验失败，请核对官方登录方式',interactive_login_required:'学校当前要求验证码或官方交互登录，暂不能通过校序直接登录',school_login_unavailable:'该平台网站登录尚未验证可用',session_expired:'学校会话已过期，请重新登录',sync_failed:'学校读取未完成，旧数据仍保留',too_many_attempts:'学校账号登录尝试过多，请稍后重试',not_configured:'服务端尚未配置连接能力',database_unavailable:'校序存储暂不可用'};
 async function schoolPost(provider,verb,payload={}){
  if(!['ncwu','chaoxing'].includes(provider))throw Error('未知学校平台');
  const response=await fetch('/api/v1/connections/'+provider+'/'+verb,{
   method:'POST',credentials:'same-origin',cache:'no-store',redirect:'error',
   headers:{'content-type':'application/json','accept':'application/json'},
   body:JSON.stringify(payload)});
  const result=await response.json().catch(()=>null);
  if(!response.ok)throw Error(schoolMessage[result?.error]||'连接操作失败');
  return result;
 }
 function announceUpdated(){
  root.dispatchEvent(new Event('xiaoxu-school-connection-updated'));
 }
 if(typeof document!=='undefined'){
  document.addEventListener('submit',async event=>{
   const form=event.target.closest?.('form[data-xu-school-login]');
   if(!form)return;
   event.preventDefault();
   const provider=form.dataset.xuSchoolLogin;
   const submit=form.querySelector('button[type="submit"]');
   const status=form.querySelector('[data-xu-school-result]');
   const username=form.elements.namedItem('username')?.value||'';
   const input=form.elements.namedItem('password'),password=input?.value||'';
   if(input)input.value='';
   submit.disabled=true;status.textContent='正在验证学校账号与读取数据…';
   try{
    await schoolPost(provider,'login',{username,password});
    await connect();
    announceUpdated();
   }catch(e){status.textContent=e.message||'学校账号连接失败';}
   finally{if(submit.isConnected)submit.disabled=false;}
  });
  document.addEventListener('click',async event=>{
   const button=event.target.closest?.('[data-xu-school-sync],[data-xu-school-disconnect]');
   if(!button)return;
   const provider=button.dataset.xuSchoolSync||button.dataset.xuSchoolDisconnect;
   const removing=button.hasAttribute('data-xu-school-disconnect');
   if(removing&&!confirm('解除该平台授权？已有学业记录会保留为历史数据。'))return;
   button.disabled=true;
   try{
    await schoolPost(provider,removing?'disconnect':'sync');
    await connect();announceUpdated();
   }catch(e){root.alert(e.message||'学校连接操作失败');}
   finally{if(button.isConnected)button.disabled=false;}
  });
 }
 root.XiaoxuAcademic=Object.freeze({TYPES,connect,disconnect,refresh,diagnose,state,hasData,records,connectionPanel,panel,taskRows,statusLabel});
})(globalThis);
