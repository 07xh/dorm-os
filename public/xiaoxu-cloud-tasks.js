/* 校序云端任务工作台：只由用户主动连接，保留本地任务与现有备份。
 * 账号认证由受保护的 Pages API 处理；从不保存会话凭据。
 */
(function(root){
 'use strict';
 const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 let online=false,items=[],health=null,error='',migration=null,busy=false;
 async function api(path,method='GET',data){
  const r=await fetch(path,{method,cache:'no-store',credentials:'same-origin',
   redirect:'error',headers:{'Accept':'application/json',...(data?{'Content-Type':'application/json'}:{})},
   ...(data?{body:JSON.stringify(data)}:{})});
  if(!(r.headers.get('content-type')||'').includes('application/json'))
   throw Error('学业接口未返回 JSON，请检查本人 Access 登录与 D1 配置');
  const body=await r.json();
  if(!r.ok){
   const message=r.status===401?'请先完成本人 Access 登录':
    r.status===503?'私有数据库或接口尚未配置':
    r.status===409?'数据发生冲突，请重新检查并重试':
    String(body.error||body.status||'数据请求失败');
   const e=Error(message);e.status=r.status;e.details=body;throw e;
  }
  return body;
 }
 function disconnect(){online=false;items=[];health=null;error='';migration=null;busy=false;}
 async function refresh(){
  if(!online)throw Error('请先连接本人的学业数据');
  const next=[];
  try{
   for(let page=0;page<50;page++){
    const data=await api('/api/v1/assignments?limit=100&offset='+page*100);
    if(!Array.isArray(data.items))throw Error('云端任务格式无效');
    for(const row of data.items){
     if(!row||typeof row.id!=='string'||typeof row.title!=='string'||
        !Number.isSafeInteger(row.revision))throw Error('云端任务字段不完整');
     next.push(row);
    }
    if(data.items.length<100)break;
    if(page===49)throw Error('任务超过单次安全加载上限，请使用分页管理');
   }
   const sources=await api('/api/v1/sources').catch(()=>null);
   items=next;health=sources;error='';return true;
  }catch(e){error=String(e.message||'刷新失败');throw e;}
 }
 async function connect(){online=true;try{await refresh();return true;}catch(e){disconnect();error=String(e.message);throw e;}}
 const isReady=()=>online;
 const taskCount=()=>items.length;
 function formatDue(r){
  if(r.due_precision==='exact'&&r.due_at_utc){
   const ms=Date.parse(r.due_at_utc);
   if(Number.isFinite(ms))return '截止 '+new Intl.DateTimeFormat('zh-CN',{
    timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'
   }).format(new Date(ms));
  }
  if(r.due_precision==='date'&&r.due_local_date)return '截止 '+r.due_local_date+'（时间未知）';
  return '未核实截止时间';
 }
 function visibleCloudTasks(localTasks=[]){
  if(!online)return [];
  const locals=Array.isArray(localTasks)?localTasks:[];
  const localIds=new Set(locals.map(x=>x.id));
  const sources=new Set(locals.filter(x=>typeof x.source==='string'&&
    typeof x.sourceId==='string'&&x.sourceId).map(x=>x.source+'|'+x.sourceId));
  return items.filter(r=>(!r.legacy_id||!localIds.has(r.legacy_id))&&
    (!r.source_provider||!r.source_external_id||
     !sources.has(r.source_provider+'|'+r.source_external_id)));
 }
 function snapshotKeys(){
  if(!online)return [];
  return items.filter(r=>typeof r.source_provider==='string'&&r.source_provider&&
    typeof r.source_external_id==='string'&&r.source_external_id)
   .map(r=>r.source_provider+'|'+r.source_external_id);
 }
 function taskRows(filter='all',localTasks=[]){
  if(!online)return '';
  return visibleCloudTasks(localTasks)
   .filter(r=>filter==='open'?r.local_status!=='done':filter==='done'?r.local_status==='done':true)
   .map(r=>{
    const done=r.local_status==='done';
    const platform=r.platform_status;
    const status=platform?({todo:'平台待交',submitted:'平台已提交',graded:'平台已批阅',returned:'平台已退回',unknown:'平台状态未核实'}[platform]||'平台未核实'):
      r.kind==='platform'?'历史平台记录 · 尚未验证':'云端个人任务';
    const action=done?'todo':'done';
    return '<div class="task-item xu-cloud-task" data-origin="cloud-workspace">'+
     '<div class="task-main '+(done?'done':'')+'"><div class="entry-title">'+esc(r.title)+'</div>'+
     '<div class="entry-sub">'+esc(formatDue(r))+' · '+esc(r.source_course||status)+
     (r.source_course?' · '+esc(status):'')+'</div></div>'+
     '<button type="button" class="secondary" data-action="cloud-toggle:'+esc(r.id)+':'+action+'">'+
     (done?'恢复待办':'标记完成')+'</button></div>';
   }).join('');
 }

 function deadlineSummary(localTasks=[],now=Date.now()){
  if(!online)return '';
  const parts=new Intl.DateTimeFormat('en-US',{timeZone:'Asia/Shanghai',
    year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date(now));
  const values=Object.fromEntries(parts.map(p=>[p.type,p.value]));
  const today=values.year+'-'+values.month+'-'+values.day;
  const dayIndex=date=>Date.parse(date+'T00:00:00Z')/86400000;
  const buckets={overdue:0,h6:0,h24:0,d3:0,later:0,unknown:0};
  for(const r of visibleCloudTasks(localTasks)){
   if(r.local_status==='done')continue;
   if(r.due_precision==='exact'&&Number.isFinite(Date.parse(r.due_at_utc))){
    const delta=Date.parse(r.due_at_utc)-now;
    if(delta<0)buckets.overdue++;
    else if(delta<=6*3600000)buckets.h6++;
    else if(delta<=24*3600000)buckets.h24++;
    else if(delta<=3*86400000)buckets.d3++;
    else buckets.later++;
   }else if(r.due_local_date&&/^\d{4}-\d{2}-\d{2}$/.test(r.due_local_date)){
    const days=dayIndex(r.due_local_date)-dayIndex(today);
    if(days<0)buckets.overdue++;
    else if(days===0)buckets.h24++;
    else if(days<=3)buckets.d3++;
    else buckets.later++;
   }else buckets.unknown++;
  }
  const row=[
   ['逾期',buckets.overdue],['6小时内',buckets.h6],['今天/24小时',buckets.h24],
   ['3天内',buckets.d3],['以后',buckets.later],['日期未知',buckets.unknown]
  ].map(([name,value])=>'<span><strong>'+value+'</strong> '+name+'</span>').join('');
  return '<section class="card xu-cloud-deadline" aria-label="云端作业截止日概览">'+
   '<div class="card-header"><span class="card-title">云端作业截止日</span></div>'+
   '<div class="xu-cloud-metrics">'+row+'</div>'+
   '<p class="tiny muted">只知道截止日期的作业按自然日分类，不推断精确截止时刻；历史平台记录不代表实时同步。</p></section>';
 }
 function sourcePanel(){
  if(!online)return '<p class="small muted">云端工作台尚未连接。请在上方完成本人授权读取；本地任务仍可独立使用。</p>';
  const records=(health?.connections||[]).map(s=>
   '<div class="config-row"><strong>'+esc(s.provider)+'</strong><span class="small">'+
   esc(s.status)+' · '+esc(s.last_success_at||'无成功同步记录')+'</span></div>').join('');
  const imported=(health?.userSnapshots||[]).map(s=>
   '<div class="config-row"><strong>'+esc(s.kind)+'</strong><span class="small">个人上传快照 · '+esc(s.lastReceivedAt||'未知')+'</span></div>').join('');
  return (records+imported)||'<p class="small muted">暂无已授权的平台连接或个人快照；云端任务可能仍为空。</p>';
 }
 function panel(localTasks=[]){
  const count=(Array.isArray(localTasks)?localTasks:[]).length;
  const migrationStatus=migration?
   '<p class="small" role="status">预览结果：新增 '+migration.toCreate+' 项，已有 '+migration.unchanged+
   ' 项，冲突 '+migration.conflicts.length+' 项。</p>':'';
  return '<section class="card xu-cloud-panel" aria-label="云端任务与本地迁移">'+
   '<div class="card-header"><span class="card-title">云端任务与旧数据迁移</span>'+
   '<span class="xu-private-status '+(online?'is-ready':'')+'">'+
   (online?'已载入 '+items.length+' 项':'未连接')+'</span></div>'+
   '<p class="small muted">云端任务、个人备份及平台原始状态分开保存；不会自动上传或删除本机任务。</p>'+
   (error?'<p class="small xu-private-error" role="alert">'+esc(error)+'</p>':'')+
   sourcePanel()+
   (online?'<div class="button-row"><button type="button" class="secondary" data-action="cloud-refresh" '+(busy?'disabled':'')+'>刷新云端任务</button>'+
    '<button type="button" class="secondary" data-action="cloud-preview" '+(busy||!count?'disabled':'')+'>预览迁移本机 '+count+' 项</button></div>':'')+
   migrationStatus+
   (migration&&online&&!migration.conflicts.length&&migration.toCreate>0?
    '<button type="button" class="primary" data-action="cloud-commit" '+(busy?'disabled':'')+'>确认上传到私人云端</button>':'')+
   '<p class="tiny muted">迁移每批不超过 20 项，可安全重试；如果本机数据在预览后修改，必须重新预览。迁移不代表已自动连接学习通。</p></section>';
 }
 function normalizeLegacyTask(t){
  const source=['chaoxing','ncwu','second_class'].includes(t.source)&&t.sourceId?''
   +t.source:'manual';
  return {
   id:t.id,title:t.title,category:t.category||'其他',due:t.due||'',dueAt:t.dueAt||'',
   done:t.done===true,createdAt:t.createdAt||'',source,
   sourceId:source==='manual'?'':t.sourceId,sourceStatus:t.sourceStatus||'unknown',
   course:t.course||''
  };
 }
 function batches(localTasks){
  if(!Array.isArray(localTasks))throw Error('本地任务状态异常');
  const rows=localTasks.map(normalizeLegacyTask);
  const result=[];for(let i=0;i<rows.length;i+=20)result.push(rows.slice(i,i+20));
  return result;
 }
 async function previewLegacy(localTasks){
  if(!online)throw Error('尚未授权云端任务');
  if(busy)throw Error('正在执行另一项操作');
  busy=true;migration=null;
  try{
   const chunks=batches(localTasks),plan={toCreate:0,unchanged:0,conflicts:[],
    key:JSON.stringify(chunks),chunks};
   for(const chunk of chunks){
    const response=await api('/api/v1/assignments/legacy-import','POST',{
     format:'xiaoxu-legacy-tasks-v1',mode:'preview',tasks:chunk
    });
    plan.toCreate+=response.toCreate||0;
    plan.unchanged+=response.unchanged||0;
    if(Array.isArray(response.conflicts))plan.conflicts.push(...response.conflicts);
   }
   migration=plan;error='';return {toCreate:plan.toCreate,unchanged:plan.unchanged,
    conflicts:[...plan.conflicts]};
  }catch(e){error=String(e.message);throw e;}finally{busy=false;}
 }
 async function commitLegacy(localTasks){
  if(!online||!migration||migration.conflicts.length)throw Error('请先完成无冲突迁移预览');
  if(busy)throw Error('上一项操作尚未结束');
  if(JSON.stringify(batches(localTasks))!==migration.key)
   throw Error('本地任务已变化，请重新预览');
  busy=true;let created=0;
  try{
   for(const chunk of migration.chunks){
    const r=await api('/api/v1/assignments/legacy-import','POST',{
     format:'xiaoxu-legacy-tasks-v1',mode:'commit',tasks:chunk
    });
    created+=r.created??0;
   }
   migration=null;await refresh();return {created};
  }catch(e){error='云端迁移未完整结束，已成功批次可幂等重试：'+String(e.message);throw e;}
  finally{busy=false;}
 }
 async function toggle(id,target){
  if(!online)throw Error('请先连接云端任务');
  const found=items.find(x=>x.id===id);
  if(!found||!['todo','done'].includes(target))throw Error('任务已变化，请刷新');
  try{
   await api('/api/v1/assignments/'+encodeURIComponent(id),'PATCH',{
    revision:found.revision,localStatus:target
   });
   await refresh();return true;
  }catch(e){if(e.status===409)await refresh().catch(()=>{});throw e;}
 }
 function reminderCandidates(localTasks=[]){
  if(!online)return [];
  return visibleCloudTasks(localTasks).map(x=>({
   id:'cloud:'+x.id,title:x.title,local_status:x.local_status,
   platform_status:x.platform_status,due_precision:x.due_precision,
   due_at_utc:x.due_at_utc,due_local_date:x.due_local_date
  }));
 }
 root.XiaoxuCloudTasks=Object.freeze({
  connect,disconnect,refresh,isReady,taskCount,taskRows,snapshotKeys,deadlineSummary,reminderCandidates,panel,previewLegacy,commitLegacy,toggle
 });
})(globalThis);
