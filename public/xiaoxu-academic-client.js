/* 校序私有学业客户端：只通过受保护的 API 读取本人数据。
 * 学校账号仅提交给可信服务端适配器，不在浏览器持久化密码或 Cookie。
 * 服务端加密会话独立于页面状态，刷新页面后重新读取授权信息。
 */
(function(root){
 'use strict';
 const TYPES=['schedule','assignments','grades','exams','second-class'];
 const SOURCE={schedule:'青果课表',assignments:'学习通作业',grades:'青果成绩',exams:'青果考试','second-class':'第二课堂'};
 const escapeHtml=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 let connected=false,sources=[],error='',serviceHealth=null,healthError='';
 let connectionStatusRead=false,connectionEpoch=0,connectionRequest=null;
 let schoolAutoSyncBusy=false,lastSchoolAutoSyncAttempt=0;
 let ncwuAutoSyncBusy=false,lastNcwuAutoSyncAttempt=0;
 const SCHOOL_AUTO_SYNC_INTERVAL=30*60*1000;
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
  connectionEpoch++;connectionStatusRead=false;connectionRequest=null;
  schoolAutoSyncBusy=false;ncwuAutoSyncBusy=false;lastNcwuAutoSyncAttempt=0;
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
  const epoch=connectionEpoch;
  const overview=await get('connections');
  if(!connected||epoch!==connectionEpoch)return false;
  if(overview?.schema!=='xiaoxu/connections-v1'||!Array.isArray(overview.platforms)||
     overview.platforms.length!==3)throw Error('数据连接状态格式无效');
  sources=overview.platforms;connectionStatusRead=true;
  const results=await Promise.allSettled(TYPES.map(kind=>get(kind)));
  if(!connected||epoch!==connectionEpoch)return false;
  for(let i=0;i<TYPES.length;i++){
   const kind=TYPES[i],result=results[i];
   if(result.status==='fulfilled'&&Array.isArray(result.value?.records)&&
       ['ready','stale','not_connected'].includes(result.value.status)){
     const v=result.value;
     feeds[kind]={status:v.status,records:v.records.slice(0,250),lastSuccessAt:v.lastSuccessAt||null,provenance:v.provenance||'user_import',summary:v.summary||null,officialScore:v.officialScore||null,personalScore:v.personalScore||null,automaticSync:v.automaticSync===true};
   }else feeds[kind]={...feeds[kind],status:'error'};
  }
  error=results.some(v=>v.status==='rejected')?'部分个人数据源暂时不可用；不清空历史会话记录。':'';
  return true;
 }

 // Read ONLY the connection summary on an already verified owner session.
 // No credential collection, school sign-in, trusted upstream sync, or cloud opt-in.
 function readConnectionStatus(){
  if(connected||connectionStatusRead)return Promise.resolve(true);
  if(connectionRequest)return connectionRequest;
  const epoch=connectionEpoch;
  const request=(async()=>{
   const overview=await get('connections');
   if(overview?.schema!=='xiaoxu/connections-v1'||!Array.isArray(overview.platforms)||
      overview.platforms.length!==3)throw Error('数据连接状态格式无效');
   if(epoch!==connectionEpoch)return false;
   sources=overview.platforms;
   connectionStatusRead=true;
   return true;
  })();
  connectionRequest=request;
  void request.finally(()=>{if(connectionRequest===request)connectionRequest=null;}).catch(()=>{});
  return request;
 }
 async function connect(){
  connected=true;
  try{
   const loaded=await refresh();
   if(!loaded||!connected)return false;
   // Uses only the owner's previously verified encrypted school session.
   void autoRefreshChaoxing();
   void autoRefreshNcwu();
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
  {id:'chaoxing',name:'学习通',url:'https://ncwu.fanya.chaoxing.com/portal',what:'收件箱、作业、互评、考试与提醒（提交状态待核实）'},
  {id:'second_class',name:'第二课堂',url:'https://dekt.ncwu.edu.cn/',what:'活动、积分与审核记录'}
 ]);
 const CONNECTION_LABELS={connected:'曾完成只读同步',partial:'上次同步不完整',
   expired:'学校授权已过期',error:'上次同步失败',not_connected:'未连接'};
 function connectionPanel(){
  const active=connected||connectionStatusRead;
  const entries=CONNECTION_PROVIDERS.map(provider=>{
   const record=sources.find(x=>x.id===provider.id)||null;
   const label=record?(record.sessionState==='expired'?'学校登录会话已过期':record.sessionState==='unreadable'?'学校会话无法验证，请重新连接':record.sessionState==='review_pending'?'本人课表等待核对':CONNECTION_LABELS[record.status]||'状态待确认'):'未读取连接状态';
   const trusted=Array.isArray(record?.trustedKinds)?record.trustedKinds.length:0;
   const imported=Array.isArray(record?.importedKinds)?record.importedKinds.length:0;
   const details=trusted?'有 '+trusted+' 类可信只读记录 · 当前学校授权仍需独立核验':
     imported?'有 '+imported+' 类个人导入记录 · 不是账号绑定':record?'尚无已验证的学校同步记录':'';
   const stampText=record?.lastSuccessAt?' · 上次记录 '+stamp(record.lastSuccessAt):'';
   const authorized=record?.accountAuthorization==='verified_session'&&record?.sessionState==='active';
   const canLogin=provider.id!=='second_class'&&record?.loginMode==='password';
   const chaoxingReadinessMessage={
    session_key_missing:'学校会话密钥尚未配置，无法安全连接学习通。',
    session_key_invalid:'学校会话密钥格式不符合要求，请检查生产 Secret（勿发送密码）。',
    database_unbound:'当前部署未绑定校序学业数据库。',
    school_session_table_missing:'学校账号加密会话表尚未准备好。',
    chaoxing_inbox_table_missing:'学习通收件箱数据表尚未准备好。',
    adapter_unavailable:'当前部署未正确注册学习通安全连接适配器。'
   };
   const chaoxingBlocked=provider.id==='chaoxing'&&!canLogin&&record?
    '<div class="xu-school-pending" role="status" data-school-unavailable="chaoxing">'+
    '<strong>此平台暂不支持站内登录：学习通连接入口未就绪</strong><p class="small">'+
    escapeHtml(chaoxingReadinessMessage[record.loginReadiness]||
     '连接诊断暂不可用，请重新读取连接状态。')+'</p>'+
    '<button type="button" class="secondary" data-action="academic-refresh">重新检测学习通连接</button>'+
    '<p class="tiny muted">第三方 APK 的本机会话不自动共享给校序。</p></div>':'';

   const officialCas=provider.id==='second_class';
   const nativeNcwu=provider.id==='ncwu'&&record?.nativeCasPreflight===true;
   // Second-class: school-owned SSO only. No password proxy or CAS preflight.
   const authPanel=authorized?'':officialCas?
    '<div class="xu-school-connect" data-xu-second-class-official-only>'+
    '<p class="tiny muted">第二课堂旧版站内账号密码直连已删除。请使用学校官方页面完成扫码、密码或其他必要验证；学校会话不会因此自动交给校序。</p>'+
    '<a class="secondary xu-connection-link" target="_blank" rel="noopener noreferrer" href="https://dekt.ncwu.edu.cn/">打开第二课堂官网</a>'+
    '<p class="tiny muted">校序尚未完成官方登录后的安全会话接入。目前只展示本人已保存的数据，不会假装登录成功或自动同步。</p></div>':
   nativeNcwu?
    '<div class="xu-school-connect"><p class="tiny muted">校序后端仅在校方提供安全的普通密码表单时尝试青果本人验证，不绕过验证码和二次认证。</p>'+
    '<button type="button" class="secondary" data-xu-cas-check="ncwu">尝试连接华水青果教务</button>'+
    '<div class="xu-school-cas-result" role="status" aria-live="polite" data-xu-ncwu-cas-result></div>'+
    '<a class="secondary xu-connection-link" target="_blank" rel="noopener noreferrer" href="https://jwmis.ncwu.edu.cn/">打开学校官网</a></div>':
    canLogin?
    (provider.id==='chaoxing'?
     '<div class="xu-school-connect">'+
     (record?.qrLoginAvailable===true?'<button type="button" class="primary" data-xu-chaoxing-qr-start>学习通 APP 扫码连接</button><div data-xu-chaoxing-qr-output role="status" aria-live="polite"></div>':'')+
     '<p class="tiny muted"><strong>华水学习通支持学校统一身份认证，但不能把校园密码直接当成超星独立密码。</strong>目前校序没有已验证的学校 CAS 授权回调；在学校官网登录并不会自动绑定校序。</p>'+
     '<a class="secondary xu-connection-link" target="_blank" rel="noopener noreferrer" href="https://authserver.ncwu.edu.cn/authserver/login?service=https%3A%2F%2Ffysso.chaoxing.com%2Fcassso%2Fncwu">打开华水学习通统一认证</a>'+
     '<details class="xu-chaoxing-direct"><summary>我另有超星独立账号密码（非校园密码）</summary>'+
     '<p class="tiny muted">只有单独设置过超星账号密码时才使用；这个入口是超星独立登录接口，不是华水统一认证。</p>'+
     '<form data-xu-school-login="chaoxing" autocomplete="on">'+
     '<label>超星独立账号<input name="username" type="text" autocomplete="username" maxlength="128" required placeholder="超星独立账号"></label>'+
     '<label>超星独立密码<input name="password" type="password" autocomplete="current-password" maxlength="512" required placeholder="超星独立密码（非校园密码）"></label>'+
     '<p class="tiny muted">仅本次向超星官方接口认证；校序不保存密码，验证本人身份与课程数据后才保存加密会话。</p>'+
     '<button type="submit" class="primary">使用超星独立账号连接</button><p class="tiny" role="status" aria-live="polite" data-xu-school-result></p>'+
     '</form></details></div>':
     '<details class="xu-school-connect"><summary>'+ (authorized?'账号管理 / 重新连接':'在校序网页登录连接')+'</summary>'+
     '<form data-xu-school-login="'+escapeHtml(provider.id)+'" autocomplete="on">'+
     '<label>学校账号<input name="username" type="text" autocomplete="username" maxlength="128" required placeholder="学校系统登录账号"></label>'+
     '<label>学校密码<input name="password" type="password" autocomplete="current-password" maxlength="512" required placeholder="仅本次登录使用"></label>'+
     '<p class="tiny muted">通过校序 HTTPS 安全后端验证；不在本机保存学校密码，成功后仅保存加密的有效会话。</p>'+
     '<button type="submit" class="primary">登录并同步</button><p class="tiny" role="status" aria-live="polite" data-xu-school-result></p></form></details>'):
    chaoxingBlocked||(active?'<p class="tiny muted xu-school-pending" data-school-unavailable="'+escapeHtml(provider.id)+'">此平台暂不支持站内登录，不接收学校账号或密码。</p>':'');
   const schoolScopeStatus=provider.id==='second_class'?'官方登录尚未接入':authorized&&provider.id==='ncwu'&&record.status==='partial'&&
    record.trustedKinds.includes('schedule')?'课表已授权 · 可同步；成绩、考试待接入':
    authorized?'已授权 · 可同步':label;
   const ncwuAutoHint=authorized&&provider.id==='ncwu'?
    '<p class="tiny muted">重开校序后，如课表已超过30分钟未更新且会话有效，将尝试只读同步。关闭网页时不采集数据。</p>':'';
   const sessionTools=authorized&&provider.id!=='second_class'?
    '<div class="xu-school-session-tools"><button type="button" class="secondary" data-xu-school-sync="'+escapeHtml(provider.id)+'">同步数据</button>'+
    '<button type="button" class="secondary" data-xu-school-disconnect="'+escapeHtml(provider.id)+'">解除连接</button></div>':'';
   const guidance=provider.id==='ncwu'?
    '<details class="xu-kingo-guide"><summary>账号授权与同步说明</summary>'+
    '<p>华水教务可能先经过统一身份认证，再进入青果教务。校序仅在经过验证并启用安全后端时提供站内账号登录；否则请使用学校官方入口。</p>'+
    '<p>目前没有验证学校向第三方网站开放授权回调。打开官网不等于绑定成功，校序不能读取其他网站的登录 Cookie。</p>'+
    '<p>已准备课表只读接口与本人身份检查；完整课表与来源键未验收时禁止自动同步。</p>'+
    (!nativeNcwu?'<button type="button" class="secondary" data-xu-ncwu-check>只读诊断华水教务入口</button>'+
    '<p class="tiny muted" role="status" aria-live="polite" data-xu-ncwu-result>仅检测学校登录页面，不收集账号或密码，也不表示绑定成功。</p>':'')+
    '</details>':
    provider.id==='chaoxing'?
    '<details class="xu-provider-guide"><summary>学习通连接方式</summary>'+
    '<p>华水学习通可通过学校官方统一身份认证登录；登录学习通网站不会自动授权校序读取账号。</p>'+
    '<p>学校统一认证和超星独立账号是两种不同的登录方式。仅已设置超星独立账号密码时，校序才可尝试直接读取；校园 CAS 登录不能作为本站授权凭据。</p>'+
    '<p>本人会话验证通过后，网站处于前台时每 30 分钟尝试更新收件箱；网页关闭后尚未启用后台推送。会话过期可在此重新连接，提交状态仍需另行核实。</p></details>':
    '<details class="xu-provider-guide"><summary>第二课堂连接状态</summary>'+
    '<p>活动、积分、志愿服务时长的只读解析与服务端会话框架已保留，但尚未通过本人真实学校登录验证；打开官网不等于账号已绑定，当前不会冒充已连接。</p>'+
    '<p>目前未完成学校官方登录后的会话授权链路；在校序中不要求输入第二课堂密码，也不采集校方 Cookie。只有合法有效的本人会话接入并验收后才会启用自动同步。</p></details>';
   return '<div class="xu-connection-source"><div class="xu-connection-copy">'+
    '<strong>'+escapeHtml(provider.name)+'</strong><small>'+escapeHtml(provider.what)+'</small>'+
    (details?'<span>'+escapeHtml(details+stampText)+'</span>':'')+authPanel+ncwuAutoHint+guidance+'</div>'+
    '<div class="xu-connection-actions"><span class="xu-private-status">'+escapeHtml(schoolScopeStatus)+'</span>'+sessionTools+
    ((officialCas||nativeNcwu||provider.id==='chaoxing')?'':'<a class="secondary xu-connection-link" target="_blank" rel="noopener noreferrer" href="'+
    escapeHtml(provider.url)+'">打开学校官网</a>')+'</div></div>';
  }).join('');
  return '<section class="card xu-private-panel" aria-label="统一数据连接中心">'+
   '<div class="card-header"><span class="card-title">数据连接</span>'+
   '<span class="xu-private-status '+(active?'is-ready':'')+'">'+
   (connected?'校序云端已读取':connectionStatusRead?'校序连接状态已读取':'校序云端未读取')+'</span></div>'+
   '<p class="small muted xu-connection-intro">校序登录与学校账号授权是两回事。只有学校登录适配器可用时，才会显示账号输入框。</p>'+

   '<div class="xu-connection-bottom xu-connection-primary-actions">'+
   (!active?'<button type="button" class="primary" data-action="academic-connect">读取我的连接状态</button>':
    !connected?'<button type="button" class="primary" data-action="academic-connect">读取我的学业数据</button>':
    '<button type="button" class="secondary" data-action="academic-refresh">刷新连接状态与数据</button>'+
    '<button type="button" class="secondary" data-action="academic-disconnect">清除此页私人数据</button>')+
   '</div>'+
   (!active?'<p class="tiny muted xu-connection-not-loaded" role="status">先读取校序连接状态；若提示未登录，请到「我的」登录校序。</p>':'')+
   '<div class="xu-connection-list">'+entries+'</div>'+(error?'<p class="small xu-private-error" role="alert">'+escapeHtml(error)+'</p>':'')+
   '<details class="xu-connection-diagnostics"><summary>校序数据服务自检</summary>'+
   '<p class="tiny muted">仅检测本人登录和 D1 学业表结构，不检查学校账号是否已连接，也不会修改数据。</p>'+
   (connected?'<button type="button" class="secondary" data-action="academic-health">只读检查数据库</button>':
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
   '<p class="tiny muted xu-connection-disclaimer">打开学校官网不会自动绑定校序；个人导入记录也不代表自动同步。学校站内登录需经真实适配验证。</p></section>';
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
 const browserTotals=kind==='second-class'&&!isOfficial(kind)&&s.personalScore?
   '<div class="xu-second-class-official" aria-label="学校网页本人同步的个人分数快照">'+
   '<strong>历史个人导入 · 分数合计</strong><div class="xu-second-class-summary">'+
   '<div><strong>'+escapeHtml(s.personalScore.qualityScore??'未公布')+
   '</strong><small>素质分</small></div>'+
   '<div><strong>'+escapeHtml(s.personalScore.volunteerHours??'未公布')+
   '</strong><small>志愿服务时长</small></div></div>'+
   '<p class="tiny muted">旧版个人快照留存；浏览器脚本同步已停用，历史数值未经校序服务端独立核验，请以学校官网为准。</p></div>':'';
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
   (s.status==='stale'?' · 请重新同步':'')+'</p>'+(kind==='second-class'?'<p class="tiny muted">校序尚未启动第二课堂无人值守自动更新；展示的是上一次保存的数据。</p>':'')+official+browserTotals+summary+'<div class="xu-private-list">'+rows+'</div></section>';
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
 // QR challenges are short-lived and sealed to the logged-in owner.
 let chaoxingQrAttempt=null;
 async function chaoxingQrPost(action,payload){
  const response=await fetch('/api/v1/connections/chaoxing/qr/'+action,{
   method:'POST',credentials:'same-origin',cache:'no-store',redirect:'error',
   headers:{'content-type':'application/json','accept':'application/json'},
   body:JSON.stringify(payload)});
  const result=await response.json().catch(()=>null);
  if(!response.ok)throw Error(schoolMessage[result?.error]||'扫码连接暂时不可用');
  return result;
 }
 async function checkChaoxingQr(){
  const attempt=chaoxingQrAttempt;
  if(!attempt||attempt.busy||document.hidden)return;
  const host=document.querySelector('[data-xu-chaoxing-qr-output]');
  if(!host){chaoxingQrAttempt=null;return;}
  if(Date.now()>=attempt.expiresAt){
   host.textContent='二维码已过期，请重新生成';chaoxingQrAttempt=null;return;
  }
  attempt.busy=true;
  try{
   const result=await chaoxingQrPost('status',{ticket:attempt.ticket});
   if(chaoxingQrAttempt!==attempt)return;
   if(result.status==='connected'){
    chaoxingQrAttempt=null;
    await connect();
    await Promise.allSettled([
     root.XiaoxuChaoxingInbox?.refresh?.()||Promise.resolve(),
     root.XiaoxuCloudTasks?.isReady?.()?root.XiaoxuCloudTasks.refresh():Promise.resolve()
    ]);
    announceUpdated();
    root.dispatchEvent(new Event('xiaoxu-school-authorization-connected'));
    return;
   }
   if(result.status==='cancelled'){
    host.textContent='已取消扫码连接';chaoxingQrAttempt=null;return;
   }
   if(!['pending','scanned'].includes(result.status)||typeof result.ticket!=='string')
    throw Error('扫码状态无效');
   attempt.ticket=result.ticket;
   const feedback=host.querySelector('[data-xu-qr-feedback]');
   if(feedback)feedback.textContent=result.status==='scanned'?
    '已扫码，请在学习通 APP 确认登录':'请使用学习通 APP 扫描二维码';
  }catch(error){
   if(chaoxingQrAttempt===attempt){
    host.textContent=error.message||'扫码连接失败';
    chaoxingQrAttempt=null;
   }
  }finally{
   attempt.busy=false;
   if(chaoxingQrAttempt===attempt&&!document.hidden)
    setTimeout(()=>{void checkChaoxingQr();},3000);
  }
 }
 const schoolMessage={upstream_unavailable:'学校平台当前不可访问，未建立连接',schedule_changed:'核对期间学校课表发生变化，请重新读取并确认',review_pending:'核对记录已失效，请重新登录查看课表',invalid_review:'请先确认已与学校官网核对',invalid_credentials:'账号或密码格式无效，请核对所选登录方式',authorization_failed:'平台身份校验失败或账号未关联学校，请核对所选登录方式',interactive_login_required:'学校当前要求验证码或官方交互登录，暂不能通过校序直接登录',school_login_unavailable:'该平台网站登录尚未验证可用',session_expired:'学校会话已过期，请重新登录',sync_failed:'学校读取未完成，旧数据仍保留',too_many_attempts:'学校账号登录尝试过多，请稍后重试',not_configured:'服务端尚未配置连接能力',database_unavailable:'校序存储暂不可用'};
 async function schoolPost(provider,verb,payload={}){
  if(!['ncwu','chaoxing'].includes(provider)||
     !['login','confirm','sync','disconnect'].includes(verb))
   throw Error('未知学校平台');
  const response=await fetch('/api/v1/connections/'+provider+'/'+verb,{
   method:'POST',credentials:'same-origin',cache:'no-store',redirect:'error',
   headers:{'content-type':'application/json','accept':'application/json'},
   body:JSON.stringify(payload)});
  const result=await response.json().catch(()=>null);
  if(!response.ok)throw Error(schoolMessage[result?.error]||'连接操作失败');
  return result;
 }
 // Reuse only a verified owner-bound encrypted NCWU session.
 // Foreground-only: closed tabs perform no school traffic.
 async function autoRefreshNcwu(){
  if(!connected||ncwuAutoSyncBusy||typeof document==='undefined'||
     document.visibilityState==='hidden')return false;
  const linked=sources.find(x=>x.id==='ncwu');
  if(linked?.accountAuthorization!=='verified_session'||
     linked.sessionState!=='active'||!Array.isArray(linked.trustedKinds)||
     !linked.trustedKinds.includes('schedule'))return false;
  const time=Date.now(),lastSuccess=Date.parse(linked.lastSuccessAt||'')||0;
  if(time-Math.max(lastSuccess,lastNcwuAutoSyncAttempt)<SCHOOL_AUTO_SYNC_INTERVAL)return false;
  ncwuAutoSyncBusy=true;lastNcwuAutoSyncAttempt=time;
  const epoch=connectionEpoch;
  try{
   await schoolPost('ncwu','sync');
   if(epoch!==connectionEpoch||!connected)return false;
   if(!await refresh())return false;
   if(epoch===connectionEpoch)announceUpdated();
   return true;
  }catch(e){
   if(epoch===connectionEpoch&&connected){
    await refresh().catch(()=>{});
    error=e.message||'青果课表自动同步未完成，旧数据仍保留';
    announceUpdated();
   }
   return false;
  }finally{ncwuAutoSyncBusy=false;}
 }
 async function autoRefreshChaoxing(){
  if(!connected||schoolAutoSyncBusy||typeof document==='undefined'||
     document.visibilityState==='hidden')return false;
  const linked=sources.find(x=>x.id==='chaoxing');
  if(linked?.loginMode!=='password'||linked?.sessionState!=='active')return false;
  const time=Date.now(),lastSuccess=Date.parse(linked.lastSuccessAt||'')||0;
  if(time-Math.max(lastSuccess,lastSchoolAutoSyncAttempt)<SCHOOL_AUTO_SYNC_INTERVAL)return false;
  schoolAutoSyncBusy=true;lastSchoolAutoSyncAttempt=time;
  const epoch=connectionEpoch;
  try{
   await schoolPost('chaoxing','sync');
   if(epoch!==connectionEpoch||!connected)return false;
   await refresh();
   await Promise.allSettled([
    root.XiaoxuCloudTasks?.isReady?.()?root.XiaoxuCloudTasks.refresh():Promise.resolve(),
    root.XiaoxuChaoxingInbox?.refresh?.()||Promise.resolve()
   ]);
   if(epoch===connectionEpoch)announceUpdated();
   return true;
  }catch(e){
   if(epoch===connectionEpoch&&connected){
    await refresh().catch(()=>{});
    error=e.message||'学习通自动同步未完成；之前的数据仍保留';
    announceUpdated();
   }
   return false;
  }finally{schoolAutoSyncBusy=false;}
 }
 if(typeof document!=='undefined'){
  document.addEventListener('visibilitychange',()=>{
   if(document.visibilityState==='visible'){void autoRefreshChaoxing();void autoRefreshNcwu();}
  });
  root.addEventListener?.('focus',()=>{void autoRefreshChaoxing();void autoRefreshNcwu();});
  // Foreground-only polling; this is NOT a background Cron subscription.
  // Reuse the existing minute tick for both sources.
 }
 function announceUpdated(){
  root.dispatchEvent(new Event('xiaoxu-school-connection-updated'));
 }
 if(typeof document!=='undefined'){
  document.addEventListener('click',async event=>{
   const button=event.target.closest?.('[data-xu-chaoxing-qr-start]');
   if(!button)return;
   button.disabled=true;chaoxingQrAttempt=null;
   const host=button.parentElement.querySelector('[data-xu-chaoxing-qr-output]');
   if(host)host.textContent='正在从学习通官网生成登录二维码…';
   try{
    const result=await chaoxingQrPost('start',{});
    const url=new URL(result.qrImageUrl);
    if(url.protocol!=='https:'||url.hostname!=='passport2.chaoxing.com'||
      url.pathname!=='/createqr'||typeof result.ticket!=='string'||
      !Number.isFinite(Date.parse(result.expiresAt)))throw Error('登录二维码响应无效');
    const img=document.createElement('img');
    img.src=url.href;img.alt='学习通官方登录二维码';img.width=190;img.height=190;
    img.referrerPolicy='no-referrer';
    const note=document.createElement('p');
    note.className='tiny muted';note.dataset.xuQrFeedback='';
    note.textContent='使用学习通 APP 扫描并确认；完成后校序自动读取本人数据。';
    host?.replaceChildren(img,note);
    chaoxingQrAttempt={ticket:result.ticket,expiresAt:Date.parse(result.expiresAt),busy:false};
    setTimeout(()=>{void checkChaoxingQr();},3000);
   }catch(error){
    if(host)host.textContent=error.message||'无法生成登录二维码';
   }finally{if(button.isConnected)button.disabled=false;}
  });
  document.addEventListener('visibilitychange',()=>{
   if(!document.hidden&&chaoxingQrAttempt)void checkChaoxingQr();
  });
  // Foreground-only checking; no hidden-tab school traffic.
  setInterval(()=>{
   if(!document.hidden){
    void autoRefreshChaoxing();
    void autoRefreshNcwu();
   }
  },60*1000);
  document.addEventListener('click',async event=>{
   const button=event.target.closest?.('[data-xu-ncwu-check]');
   if(!button)return;
   const result=button.parentElement?.querySelector('[data-xu-ncwu-result]');
   if(!result)return;
   button.disabled=true;
   result.textContent='正在从校序后端检测学校当前登录页面…';
   try{
    const response=await fetch('/api/v1/connections/ncwu/preflight',{
     method:'GET',credentials:'same-origin',cache:'no-store',redirect:'error',
     headers:{accept:'application/json'}
    });
    const info=await response.json().catch(()=>null);
    if(!response.ok){result.textContent='学校登录检测暂不可用，请检查校序本人登录和后端服务。';return;}
    if(info.loginSupported){
     result.textContent='已识别学校当前普通登录表单；'+
      (info.sessionVaultReady?'安全会话库已配置。':'服务端加密会话库仍未就绪。')+
      '目前尚未完成本人身份绑定与可信课表读取，不能把页面检测当成账号接入。';
    }else{
     result.textContent=info.status==='upstream_unavailable'?
      '校序服务器暂时不能访问学校登录服务。':
      info.status==='unexpected_school_redirect'?
      '学校入口跳转至尚未确认的地址，已安全停止。':
      '学校登录目前需要官方页面交互或表单尚未验证；请使用学校官网登录。';
    }
   }catch{result.textContent='检查失败，未访问账号和课表。';}
   finally{if(button.isConnected)button.disabled=false;}
  });
  document.addEventListener('click',async event=>{
   const button=event.target.closest?.('[data-xu-cas-check]');
   if(!button)return;
   const panel=button.closest('.xu-school-connect'),result=panel?.querySelector('[data-xu-ncwu-cas-result]');
   if(!result)return;
   const checkProvider='ncwu';
   
   button.disabled=true;result.textContent='正在向学校确认登录方式…';
   try{
    const response=await fetch('/api/v1/connections/'+checkProvider+'/preflight',{
     method:'GET',credentials:'same-origin',cache:'no-store',redirect:'error',
     headers:{accept:'application/json'}
    });
    const info=await response.json().catch(()=>null);
    if(!response.ok||(!info?.nativeLoginReady)){
     const qingguoBlockers={
      password_transform_required:'学校认证页面包含密码加密提交逻辑；校序当前尚不支持该方式的站内登录，不会要求输入密码。请在学校官网完成认证。',
      interactive_challenge_required:'学校当前要求验证码或交互式验证；需要在官方页面完成，校序不会绕过。',
      password_form_unavailable:'学校密码登录表单与当前适配器不一致，暂不接收账号密码。',
      unexpected_cas_form_action:'学校认证表单的提交地址未通过安全校验，已停止连接。'
     };
     result.textContent=info?.status==='server_session_vault_unavailable'?
      '校序服务端安全会话未就绪；没有读取或保存学校账号。':
      info?.status==='schedule_review_pending'?
      '学校普通登录表单已识别，但课表尚待本人核对；暂不收集密码。':
      info?.status==='upstream_unavailable'?
      '校序服务器暂不能访问学校登录服务（S01）；没有读取或保存学校账号。':
      qingguoBlockers[info?.reason]||
      '学校当前不支持此方式的安全自动登录（S08）；请通过学校官网正常登录，不会绕过身份验证。';
     return;
    }
    // The trusted backend has inspected a simple current CAS password form.
    // Only now does this page request school account credentials.
    const form=document.createElement('form');
    form.dataset.xuSchoolLogin='ncwu';
    form.autocomplete='on';
    const accountLabel=document.createElement('label');
    accountLabel.textContent='学校学工号';
    const account=document.createElement('input');
    account.name='username';account.type='text';account.required=true;
    account.maxLength=128;account.autocomplete='username';
    accountLabel.appendChild(account);
    const passwordLabel=document.createElement('label');
    passwordLabel.textContent='学校密码（仅本次登录）';
    const password=document.createElement('input');
    password.name='password';password.type='password';password.required=true;
    password.maxLength=512;password.autocomplete='current-password';
    passwordLabel.appendChild(password);
    const hint=document.createElement('p');
    hint.className='tiny muted';
    hint.textContent='先由安全后端读取本人课表，与学校官网核对后再启用同步；学校密码不保存。';
    if(checkProvider==='ncwu'){
     const extra=document.createElement('div');
     extra.className='xu-school-secondary';
     const extraUser=document.createElement('label');
     extraUser.textContent='教务独立账号（若学校二次登录要求）';
     const extraUserInput=document.createElement('input');
     extraUserInput.name='academicUsername';extraUserInput.maxLength=128;
     extraUserInput.autocomplete='off';extraUser.appendChild(extraUserInput);
     const extraPass=document.createElement('label');
     extraPass.textContent='教务独立密码（若学校二次登录要求）';
     const extraPassInput=document.createElement('input');
     extraPassInput.name='academicPassword';extraPassInput.type='password';
     extraPassInput.maxLength=512;extraPassInput.autocomplete='off';
     extraPass.appendChild(extraPassInput);
     extra.append(extraUser,extraPass);
     form.appendChild(extra);
    }
    const submit=document.createElement('button');
    submit.type='submit';submit.className='primary';
    submit.textContent='登录并查看待核对课表';
    const status=document.createElement('p');
    status.dataset.xuSchoolResult='';status.setAttribute('role','status');
    status.setAttribute('aria-live','polite');
    form.append(accountLabel,passwordLabel,hint,submit,status);
    result.replaceChildren(form);
   }catch{result.textContent='连接状态检查失败；没有获取学校账号信息。';}
   finally{if(button.isConnected)button.disabled=false;}
  });

  function showNcwuReview(form,preview){
   const host=form.closest('[data-xu-ncwu-cas-result]')||form.parentElement;
   const panel=document.createElement('section');
   panel.className='xu-school-review';panel.dataset.xuNcwuReview='';
   panel.dataset.reviewHash=preview.reviewHash;
   const heading=document.createElement('strong');
   heading.textContent='请与学校官网逐项核对课表';
   const desc=document.createElement('p');
   desc.className='small muted';
   desc.textContent='核对课程名称、星期、节次、周次、教师与教室。若不一致，请勿确认。';
   const count=document.createElement('p');
   count.className='small';
   count.textContent='共 '+preview.schedule.length+' 条安排 · 有效期至 '+new Date(preview.expiresAt).toLocaleTimeString();
   const details=document.createElement('details');
   details.open=true;
   const summary=document.createElement('summary');
   summary.textContent='所有待核对课程（尚未同步）';
   const list=document.createElement('ol');
   list.className='xu-review-schedule-list';
   for(const item of preview.schedule){
    const li=document.createElement('li');
    li.textContent=[item.title,'周'+('一二三四五六日'[item.weekday-1]||'?'),
     item.startSection+'–'+item.endSection+'节',
     Array.isArray(item.weeks)?'第'+item.weeks.join('、')+'周':'周次待核实',
     item.location||'地点未核实',item.teacher||''].filter(Boolean).join(' · ');
    list.appendChild(li);
   }
   details.append(summary,list);
   const official=document.createElement('a');
   official.className='secondary xu-connection-link';official.href='https://jwmis.ncwu.edu.cn/';
   official.target='_blank';official.rel='noopener noreferrer';
   official.textContent='打开华水教务官网核对';
   const label=document.createElement('label');
   const checkbox=document.createElement('input');
   checkbox.type='checkbox';checkbox.dataset.xuNcwuReviewed='';
   label.append(checkbox,document.createTextNode(' 已核对官网，全部课程一致'));
   const confirmButton=document.createElement('button');
   confirmButton.type='button';confirmButton.className='primary';
   confirmButton.dataset.xuNcwuConfirm='';
   confirmButton.textContent='确认并连接课表';confirmButton.disabled=true;
   checkbox.addEventListener('change',()=>{confirmButton.disabled=!checkbox.checked;});
   const status=document.createElement('p');
   status.className='tiny muted';status.setAttribute('role','status');
   status.dataset.xuNcwuReviewStatus='';
   panel.append(heading,desc,count,details,official,label,confirmButton,status);
   host.replaceChildren(panel);
  }
  document.addEventListener('click',async event=>{
   const button=event.target.closest?.('[data-xu-ncwu-confirm]');
   if(!button)return;
   const panel=button.closest('[data-xu-ncwu-review]');
   const status=panel?.querySelector('[data-xu-ncwu-review-status]');
   if(!panel||!status||!panel.querySelector('[data-xu-ncwu-reviewed]')?.checked)return;
   button.disabled=true;status.textContent='正在重新读取学校课表并对照核对版本…';
   try{
    await schoolPost('ncwu','confirm',{reviewed:true,reviewHash:panel.dataset.reviewHash});
    await connect();announceUpdated();
    root.dispatchEvent(new Event('xiaoxu-school-authorization-connected'));
    status.textContent='本人课表已连接并完成同步';
   }catch(e){
    status.textContent=e.message||'课表核对未完成';
    if(button.isConnected)button.disabled=false;
   }
  });
  document.addEventListener('submit',async event=>{
   const form=event.target.closest?.('form[data-xu-school-login]');
   if(!form)return;
   event.preventDefault();
   const provider=form.dataset.xuSchoolLogin;
   if(!['ncwu','chaoxing'].includes(provider))return;
   const submit=form.querySelector('button[type="submit"]');
   const status=form.querySelector('[data-xu-school-result]');
   const username=form.elements.namedItem('username')?.value||'';
   const input=form.elements.namedItem('password'),password=input?.value||'';
   const extraUser=form.elements.namedItem('academicUsername');
   const extraPass=form.elements.namedItem('academicPassword');
   const academicUsername=extraUser?.value?.trim()||'';
   const academicPassword=extraPass?.value||'';
   if(input)input.value='';
   if(extraPass)extraPass.value='';
   if(Boolean(academicUsername)!==Boolean(academicPassword)){
    status.textContent='教务独立账号与密码必须同时填写或同时留空';return;
   }
   submit.disabled=true;status.textContent='正在验证学校账号与读取数据…';
   try{
    const outcome=await schoolPost(provider,'login',{username,password,
     ...(provider==='ncwu'&&academicUsername?{academicUsername,academicPassword}:{})});
    if(provider==='ncwu'){
     if(outcome?.reviewRequired!==true||!Array.isArray(outcome.schedule)||
        !outcome.schedule.length||!/^[A-Za-z0-9_-]{43}$/.test(outcome.reviewHash||''))
      throw Error('课表预览格式无效；未启用同步');
     showNcwuReview(form,outcome);return;
    }
    if(provider==='chaoxing')lastSchoolAutoSyncAttempt=Date.now();
    await connect();
    if(provider==='chaoxing')await Promise.allSettled([
     root.XiaoxuChaoxingInbox?.refresh?.()||Promise.resolve(),
     root.XiaoxuCloudTasks?.isReady?.()?root.XiaoxuCloudTasks.refresh():Promise.resolve()
    ]);
    announceUpdated();
    root.dispatchEvent(new Event('xiaoxu-school-authorization-connected'));
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
    if(provider==='chaoxing')lastSchoolAutoSyncAttempt=Date.now();
    await connect();
    if(provider==='chaoxing'){
     if(removing)root.XiaoxuChaoxingInbox?.disconnect?.();
     else await Promise.allSettled([
      root.XiaoxuChaoxingInbox?.refresh?.()||Promise.resolve(),
      root.XiaoxuCloudTasks?.isReady?.()?root.XiaoxuCloudTasks.refresh():Promise.resolve()
     ]);
    }
    announceUpdated();
   }catch(e){root.alert(e.message||'学校连接操作失败');}
   finally{if(button.isConnected)button.disabled=false;}
  });
 }
 root.XiaoxuAcademic=Object.freeze({TYPES,connect,disconnect,refresh,readConnectionStatus,diagnose,isReady:()=>connected,hasVerifiedSchoolConnection:()=>connected&&sources.some(row=>row.accountAuthorization==='verified_session'&&row.sessionState==='active'),state,hasData,records,connectionPanel,panel,taskRows,statusLabel});
})(globalThis);
