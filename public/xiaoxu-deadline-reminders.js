/* Foreground-only personal deadline reminder: no notification permission, no push.
 * Acknowledgements contain an opaque local fingerprint, never task text or URL.
 */
(function(root){
 'use strict';
 const KEY='xiaoxu-deadline-ack-v1';
 const esc=v=>String(v??'').replace(/[&<>"']/g,x=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[x]));
 const dayInCN=ms=>new Date(ms+8*3600_000).toISOString().slice(0,10);
 const ordinal=iso=>Date.parse(iso+'T00:00:00Z')/86400000;
 const finger=value=>{let h=2166136261;for(const c of value){h=Math.imul(h^c.charCodeAt(0),16777619)>>>0;}return h.toString(16);};
 const validDay=d=>typeof d==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(d)&&
  Number.isFinite(Date.parse(d))&&new Date(d+'T00:00:00Z').toISOString().slice(0,10)===d;
 function plan(rows,now=Date.now()){
  if(!Array.isArray(rows)||!Number.isFinite(now))return [];
  const today=dayInCN(now),out=[],seen=new Set();
  for(const r of rows.slice(0,600)){
   if(!r||typeof r.id!=='string'||!r.id||r.done===true||r.local_status==='done'||
      ['submitted','graded'].includes(r.sourceStatus||r.platform_status))continue;
   const title=typeof r.title==='string'?r.title.slice(0,100):'';
   if(!title)continue;
   const exact=r.dueAt||r.due_at_utc||'';
   const date=r.due||r.due_local_date||'';
   let band='',label='';
   if(exact&&Number.isFinite(Date.parse(exact))&&
      (r.due_precision==='exact'||r.dueAt)){
    const delta=Date.parse(exact)-now;
    if(delta< -7*86400_000||delta>3*86400_000)continue;
    band=delta<0?'overdue':delta<=24*3600_000?'today':'soon';
    label=delta<0?'已过截止时间':delta<=24*3600_000?'24 小时内截止':'3 天内截止';
   }else if(validDay(date)){
    const days=ordinal(date)-ordinal(today);
    if(days< -7||days>3)continue;
    band=days<0?'overdue':days===0?'today':'soon';
    label=days<0?'截止日期已过':days===0?'今天截止（时刻未知）':'未来 3 天截止（时刻未知）';
   }else continue;
   const id=finger(r.id+'|'+(exact||date)+'|'+band);
   if(seen.has(id))continue;seen.add(id);
   out.push({id,title,label,band});
  }
  return out.sort((a,b)=>({overdue:0,today:1,soon:2}[a.band]-{overdue:0,today:1,soon:2}[b.band])).slice(0,20);
 }
 const stored=()=>{try{const value=JSON.parse(root.localStorage.getItem(KEY)||'{}');return value&&typeof value==='object'&&!Array.isArray(value)?value:{};}catch{return {};}};
 function ack(id){
  if(typeof id!=='string'||!/^[a-f0-9]{1,8}$/.test(id))return false;
  try{
   const known=stored(),today=dayInCN(Date.now());
   const data=Object.fromEntries(Object.entries(known).filter(([,date])=>validDay(date)&&ordinal(today)-ordinal(date)<=14).slice(-120));
   data[id]=today;root.localStorage.setItem(KEY,JSON.stringify(data));return true;
  }catch{return false;}
 }
 function panel(rows,now=Date.now()){
  const notices=plan(rows,now).filter(n=>!Object.hasOwn(stored(),n.id));
  if(!notices.length)return '';
  const items=notices.slice(0,5).map(n=>'<div class="xu-deadline-alert-item"><span><strong>'+esc(n.title)+
   '</strong><small>'+esc(n.label)+'</small></span><button type="button" class="secondary" data-action="deadline-ack:'+n.id+
   '">知道了</button></div>').join('');
  return '<section class="card xu-deadline-alerts" aria-label="个人作业截止提醒"><div class="card-header">'+
   '<span class="card-title">近期截止提醒</span><span class="tiny muted">'+notices.length+' 项</span></div>'+
   items+'<p class="tiny muted">仅在打开校序时显示；“知道了”只隐藏该次提醒，不改变作业完成/提交状态。日期未知时不猜测具体时刻。</p></section>';
 }
 root.XiaoxuDeadlineReminders=Object.freeze({plan,ack,panel});
})(globalThis);
