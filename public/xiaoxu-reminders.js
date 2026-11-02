/* 校序个人云端提醒。必须用户主动连接及开启；不会请求浏览器系统通知权限。
 * 站内已读回执保存在本人 D1，下次同账号打开网站可恢复。
 */
(function(root){
 'use strict';
 const escapeHtml=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 let connected=false,payload=null,error='';
 async function api(method='GET',body){
  const response=await fetch('/api/v1/reminders',{
   method,cache:'no-store',credentials:'same-origin',redirect:'error',
   headers:{accept:'application/json',...(body?{'content-type':'application/json'}:{})},
   ...(body?{body:JSON.stringify(body)}:{})
  });
  if(!(response.headers.get('content-type')||'').includes('application/json'))
   throw Error('提醒接口尚未配置为私人 JSON API');
  const data=await response.json();
  if(!response.ok)throw Error(response.status===401?'请先完成本人 Access 授权':
   response.status===503?'云端提醒数据库尚未启用':data.status||'提醒请求失败');
  if(!data||data.delivery!=='in_app_only'||!Array.isArray(data.items))
   throw Error('私人提醒响应格式错误');
  return data;
 }
 function disconnect(){connected=false;payload=null;error='';}
 async function refresh(){
  if(!connected)throw Error('请先主动加载个人学业数据');
  try{const result=await api();if(!connected)return null;payload=result;error='';return payload;}
  catch(e){error=String(e.message||e);throw e;}
 }
 async function connect(){
  connected=true;
  try{return await refresh();}catch(e){disconnect();error=String(e.message||e);throw e;}
 }
 async function settings(enabled){
  if(!connected||!payload)throw Error('尚未连接提醒服务');
  const result=await api('PATCH',{operation:'settings',enabled,
   examLeadDays:payload.preferences.examLeadDays,taskLeadDays:payload.preferences.taskLeadDays});
  if(!connected)return null;payload=result;return payload;
 }
 async function mark(key,read=true){
  if(!connected||!payload)throw Error('尚未连接提醒服务');
  if(!payload.items.some(x=>x.key===key))throw Error('提醒不在当前收件箱');
  const result=await api('PATCH',{operation:'read',key,read});if(!connected)return null;payload=result;return payload;
 }
 function panel(context="settings"){
  if(context==="tasks"&&(!connected||!payload))return "";
  if(!connected||!payload)return '<section class="card xu-reminder-panel" aria-label="个人云端提醒">'+
   '<div class="card-header"><span class="card-title">个人云端提醒</span>'+
   '<span class="xu-private-status">未连接</span></div>'+
   '<p class="small muted">授权连接后可查看本人云端任务及已导入考试的到期提醒，并跨设备保留已读。当前不会在关闭网站后推送通知。</p>'+
   (error?'<p class="small xu-private-error" role="alert">'+escapeHtml(error)+'</p>':'')+'</section>';
  const prefs=payload.preferences;
  const active=prefs.enabled;
  const rows=payload.items.slice(0,8).map(event=>'<div class="xu-reminder-item">'+
   '<div class="xu-reminder-copy"><strong>'+escapeHtml(event.title)+'</strong>'+
   '<small>'+escapeHtml(event.when)+' · '+escapeHtml(event.subtitle)+'</small></div>'+
   '<button type="button" class="secondary" data-action="reminder-read:'+escapeHtml(event.key)+':'+(!event.read)+'" '+
   'aria-label="'+(event.read?'标为未读 ':'标为已读 ')+escapeHtml(event.title)+'">'+
   (event.read?'恢复未读':'标为已读')+'</button></div>').join('');
  return '<section class="card xu-reminder-panel" aria-label="个人云端提醒">'+
   '<div class="card-header"><span class="card-title">个人云端提醒</span>'+
   '<span class="xu-private-status '+(active?'is-ready':'')+'">'+
   (active?'已启用站内提醒':'未订阅站内提醒')+'</span></div>'+
   '<p class="small muted">提醒基于本人云端任务和个人导入考试快照，不代表学校平台已自动连接。已读在同一云端账号下同步；关闭网页后暂不提供系统推送。</p>'+
   (error?'<p role="alert" class="small xu-private-error">最近刷新失败；以下为上次加载的数据，请核对：'+escapeHtml(error)+'</p>':'')+
   '<div class="button-row"><button type="button" class="'+(active?'secondary':'primary')+'" data-action="'+
   (active?'reminder-disable':'reminder-enable')+'">'+(active?'关闭站内订阅':'开启站内订阅')+'</button>'+
   '<button type="button" class="secondary" data-action="reminder-refresh">刷新</button></div>'+
   '<p class="tiny muted">考试提前 '+prefs.examLeadDays+' 天，个人任务提前 '+prefs.taskLeadDays+
   ' 天。数据来自个人存储，不生成未验证的考试时间。</p>'+
   (payload.items.length?'<div class="xu-reminder-list">'+rows+'</div>'+
    (payload.items.length>8?'<p class="tiny muted">另有 '+(payload.items.length-8)+' 条提醒待查看。</p>':''):
    '<div class="empty">目前没有符合提前天数的真实个人事项。</div>')+
   '<p class="tiny muted">'+(active?'未读 '+payload.unread+' 条 · 仅网站打开时检查':'开启订阅后才显示站内未读计数')+
   (payload.source.exams==='personal_snapshot'?' · 考试仅为个人导入快照':' · 个人考试未连接')+'</p></section>';
 }
 root.XiaoxuReminders=Object.freeze({connect,disconnect,refresh,settings,mark,panel,isConnected:()=>connected});
})(globalThis);
