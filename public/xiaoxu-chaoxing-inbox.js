/* Chaoxing saved notices: one collapsible section in existing Tasks page.
 * This displays server-stored metadata only; a browser refresh is not a school sync.
 */
(function(root){
 'use strict';
 let rows=[],filter='all',loaded=false,loading=false,error='',epoch=0;
 const escapeHtml=value=>String(value??'').replace(/[&<>"']/g,c=>({
  '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
 }[c]));
 const title={assignment:'作业',assignment_reminder:'作业提醒',
  peer_review:'互评',exam:'考试',checkin:'签到',
  quiz:'测验',activity:'活动',notice:'课程通知'};
 const actionable=new Set(['assignment','assignment_reminder','peer_review','exam']);
 const isConnected=()=>root.XiaoxuCloudTasks?.isReady?.()===true;
 function message(row){
  if(!row||typeof row!=='object'||!title[row.category])return '';
  return '<article class="xu-inbox-entry">'+
   '<div class="xu-inbox-entry-title"><span class="xu-inbox-chip">'+escapeHtml(title[row.category])+'</span>'+
   '<strong>'+escapeHtml(row.title||'学习通消息')+'</strong></div>'+
   '<div class="small muted">'+escapeHtml(row.course||'未关联课程')+
   ' · '+escapeHtml((row.sentAt||'').slice(0,10))+
   (row.dueAt?' · 截止 '+escapeHtml(new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',
    year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'
   }).format(new Date(row.dueAt))):'')+'</div></article>';
 }
 function panel(){
  const logged=isConnected();
  const shown=rows.filter(r=>filter==='all'||
   (filter==='tasks'?actionable.has(r.category):!actionable.has(r.category)));
  return '<section class="card xu-inbox-panel" aria-label="学习通收件箱">'+
   '<details class="xu-inbox-details"><summary><strong>学习通收件箱</strong> '+
   '<span class="small muted">'+(logged?(loaded?rows.length+' 条已保存消息':'尚未读取'):'未连接校序云端')+'</span></summary>'+
   '<p class="small muted">这是校序保存的学习通消息，不代表已实时同步。发布/截止提醒不会自动判定作业是否提交。</p>'+
   (logged?'<div class="button-row"><button type="button" class="secondary" data-chaoxing-inbox-refresh '+
      (loading?'disabled':'')+'>'+(loading?'读取中…':'读取已保存消息')+'</button></div>':
    '<p class="small muted">先在「我的」完成校序登录，再查看已保存的消息。</p>')+
   (error?'<p class="small muted" role="status">'+escapeHtml(error)+'</p>':'')+
   (logged&&loaded?'<div class="list-controls">'+
    [['all','全部'],['tasks','任务相关'],['notices','其他通知']].map(([k,label])=>
    '<button type="button" class="filter-btn '+(k===filter?'active':'')+
    '" data-chaoxing-inbox-filter="'+k+'" aria-pressed="'+(k===filter)+'">'+label+'</button>').join('')+
    '</div>'+(shown.length?shown.map(message).join(''):
       '<div class="friendly-empty">当前筛选下没有已保存消息。尚不能据此断定官方收件箱为空。</div>'):'')+
   '</details></section>';
 }
 function update(){
  const host=document.getElementById('xuChaoxingInboxHost');
  if(host){const expanded=host.querySelector('.xu-inbox-details')?.open===true;
   host.innerHTML=panel();const details=host.querySelector('.xu-inbox-details');if(details)details.open=expanded;}
 }
 async function refresh(){
  if(!isConnected())return;
  if(loading)return;
  loading=true;error='';const requestEpoch=epoch;update();
  try{
   const response=await fetch('/api/v1/chaoxing-inbox?limit=100',{
    credentials:'same-origin',cache:'no-store',redirect:'error',headers:{accept:'application/json'}});
   if(requestEpoch!==epoch||!isConnected())return;
   if(!response.ok)throw Error(response.status===503?'消息数据库尚未准备好':'无法读取个人消息');
   const data=await response.json();
   if(data.schema!=='xiaoxu/chaoxing-inbox-v1'||!Array.isArray(data.messages))
    throw Error('消息格式不兼容');
   rows=data.messages.filter(r=>r&&typeof r.id==='string'&&typeof r.title==='string');
   loaded=true;
  }catch(e){
   if(requestEpoch===epoch){error=e.message==='消息数据库尚未准备好'?'消息数据库尚未准备好':'暂时无法读取保存的消息';rows=[];loaded=false;}
  }finally{
   if(requestEpoch===epoch){loading=false;update();}
  }
 }
 function disconnect(){epoch++;rows=[];loaded=false;loading=false;error='';filter='all';update();}
 document.addEventListener('click',event=>{
  const refreshButton=event.target.closest('[data-chaoxing-inbox-refresh]');
  if(refreshButton){event.preventDefault();refresh();return;}
  const f=event.target.closest('[data-chaoxing-inbox-filter]');
  if(f&&['all','tasks','notices'].includes(f.dataset.chaoxingInboxFilter)){
   filter=f.dataset.chaoxingInboxFilter;update();
  }
 });
 root.XiaoxuChaoxingInbox=Object.freeze({panel,refresh,disconnect});
})(window);
