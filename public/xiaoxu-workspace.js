/* Read-only projections shared by the daily overview and Tasks.
 * No persistence, network calls, authorization, or platform-status mutation. */
(function(root){
 'use strict';
 const names={chaoxing:'学习通',ncwu:'青果教务',second_class:'第二课堂',wechat:'微信',manual:'个人'};
 const sourceKey=(provider,id)=>provider&&id?provider+'|'+id:'';
 function tasks({local=[],cloud=[],remote=[]}={}, {filter='all',source='all'}={}){
  const rows=[],keys=new Set(),localIds=new Set(local.map(r=>r.id));
  function add(row){
   const key=sourceKey(row.source,row.sourceId);
   if(key&&keys.has(key))return;
   if(key)keys.add(key);
   rows.push(row);
  }
  for(const r of local)add({id:r.id,origin:'local',title:r.title,course:r.course||r.category||'',
   source:r.source||'manual',sourceId:r.sourceId||'',done:r.done===true,
   due:r.due||'',dueAt:r.dueAt||'',platformStatus:r.sourceStatus||'unknown',raw:r});
  for(const r of cloud){
   if(r.legacy_id&&localIds.has(r.legacy_id))continue;
   add({id:r.id,origin:'cloud',title:r.title,course:r.source_course||'',
    source:r.source_provider||'manual',sourceId:r.source_external_id||'',done:r.local_status==='done',
    due:r.due_local_date||'',dueAt:r.due_precision==='exact'?r.due_at_utc||'':'',
    platformStatus:r.platform_status||'unknown',raw:r});
  }
  for(const r of remote){
   if(typeof r.externalId!=='string')continue;
   add({id:r.externalId,origin:'remote',title:r.title,course:r.course||'',source:'chaoxing',
    sourceId:r.externalId,done:false,due:'',dueAt:r.dueAt||'',platformStatus:r.platformStatus||'unknown',raw:r});
  }
  return rows.filter(r=>(filter==='open'?!r.done:filter==='done'?r.done:true)&&
   (source==='all'||r.source===source)).sort((a,b)=>Number(a.done)-Number(b.done)||
    (a.dueAt||a.due||'9999').localeCompare(b.dueAt||b.due||'9999')||String(a.id).localeCompare(String(b.id)));
 }
 function courses({classes=[],calendarEvents=[]}={},date,day){
  return [...classes.filter(r=>r.day===day).map(r=>({...r,origin:'local'})),
   ...calendarEvents.filter(r=>r.date===date).map(r=>({...r,day,origin:'calendar'}))]
   .sort((a,b)=>String(a.start).localeCompare(String(b.start)));
 }
 root.XiaoxuWorkspace=Object.freeze({tasks,courses,names});
})(globalThis);
