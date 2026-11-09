/* Read-only, single-source task projections for the校序 web task center.
 * Local prerequisite evaluation follows NextTodo's MIT-licensed DAG/ready model:
 * https://github.com/JunieXD/NextTodo . No second account or sync backend.
 * Never turn a personal checkbox into a school submission.
 */
(function(root){
 'use strict';
 const names={chaoxing:'学习通',ncwu:'青果教务',second_class:'第二课堂',wechat:'微信',manual:'个人'};
 const sourceKey=(provider,id)=>provider&&id?provider+'|'+id:'';
 const chinaDate=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit'});
 const officialWorkId=id=>typeof id==='string'&&/^work:[^:]+:[^:]+:[^:]+$/.test(id);
 const officialFinished=row=>row.source==='chaoxing'&&officialWorkId(row.sourceId)&&
   ['submitted','graded'].includes(row.platformStatus);
 const officialUnfinished=row=>row.source==='chaoxing'&&officialWorkId(row.sourceId)&&
   ['todo','returned'].includes(row.platformStatus);

 // The edge A -> B means "A waits for B". Sanitize legacy backups by dropping
 // invalid/dangling/cyclic edges; do not mutate the caller's local task array.
 function sanitizePrerequisites(tasks){
  const rows=tasks.map(t=>({...t,prerequisiteIds:[]}));
  const ids=new Set(rows.map(t=>t.id));
  const byId=new Map(rows.map(t=>[t.id,t]));
  const reaches=(start,target)=>{
   const seen=new Set(),stack=[start];
   while(stack.length){
    const id=stack.pop();
    if(id===target)return true;
    if(seen.has(id))continue;
    seen.add(id);
    for(const before of byId.get(id)?.prerequisiteIds||[])stack.push(before);
   }
   return false;
  };
  rows.forEach((row,index)=>{
   const source=Array.isArray(tasks[index].prerequisiteIds)?tasks[index].prerequisiteIds:[];
   for(const before of source.slice(0,30)){
    if(typeof before!=='string'||!ids.has(before)||before===row.id||
       row.prerequisiteIds.includes(before)||reaches(before,row.id))continue;
    row.prerequisiteIds.push(before);
   }
  });
  return rows;
 }
 function canSetPrerequisites(tasks,id,beforeIds){
  if(typeof id!=='string'||!Array.isArray(beforeIds)||beforeIds.length>30||
     new Set(beforeIds).size!==beforeIds.length||
     !beforeIds.every(x=>typeof x==='string'&&x!==id))return false;
  const target=tasks.find(t=>t.id===id);
  if(!target)return false;
  const changed=tasks.map(t=>({...t,prerequisiteIds:t.id===id?beforeIds:t.prerequisiteIds}));
  const clean=sanitizePrerequisites(changed).find(t=>t.id===id)?.prerequisiteIds||[];
  return clean.length===beforeIds.length&&clean.every((x,i)=>x===beforeIds[i]);
 }
 function deadlineOrder(row){
  const instant=row.dueAt?Date.parse(row.dueAt):NaN;
  if(Number.isFinite(instant)){
   const parts=Object.fromEntries(chinaDate.formatToParts(new Date(instant)).map(p=>[p.type,p.value]));
   return {day:parts.year+'-'+parts.month+'-'+parts.day,instant};
  }
  return {day:/^\d{4}-\d{2}-\d{2}$/.test(row.due)?row.due:'9999',instant:null};
 }
 function tasks({local=[],cloud=[],remote=[]}={}, {filter='all',source='all'}={}){
  const rows=[],keys=new Map(),localIds=new Set(local.map(r=>r.id));
  const localCopy=sanitizePrerequisites(local);
  const localById=new Map(localCopy.map(t=>[t.id,t]));
  const add=(row,legacyId='')=>{
   const key=sourceKey(row.source,row.sourceId);
   const existingIndex=rows.findIndex(r=>(key&&sourceKey(r.source,r.sourceId)===key)||
     (legacyId&&r.origin==='local'&&r.id===legacyId));
   if(existingIndex>=0){
    // Prefer a verified official completed assignment over a stale local import.
    if(row.origin==='cloud'&&officialFinished(row)&&!officialFinished(rows[existingIndex]))
     rows[existingIndex]=row;
    return;
   }
   if(key)keys.set(key,true);
   rows.push(row);
  };
  for(const r of localCopy)add({id:r.id,origin:'local',title:r.title,course:r.course||r.category||'',
   source:r.source||'manual',sourceId:r.sourceId||'',personalDone:r.done===true,
   due:r.due||'',dueAt:r.dueAt||'',platformStatus:r.sourceStatus||'unknown',raw:r});
  for(const r of cloud){
   if(r.legacy_id&&localIds.has(r.legacy_id)&&!officialFinished({
    source:r.source_provider,sourceId:r.source_external_id,platformStatus:r.platform_status
   }))continue;
   add({id:r.id,origin:'cloud',title:r.title,course:r.source_course||'',
    source:r.source_provider||'manual',sourceId:r.source_external_id||'',
    personalDone:r.local_status==='done',due:r.due_local_date||'',
    dueAt:r.due_precision==='exact'?r.due_at_utc||'':'',
    platformStatus:r.platform_status||'unknown',raw:r},r.legacy_id);
  }
  for(const r of remote){
   if(typeof r.externalId!=='string')continue;
   add({id:r.externalId,origin:'remote',title:r.title,course:r.course||'',
    source:'chaoxing',sourceId:r.externalId,personalDone:false,
    due:'',dueAt:r.dueAt||'',platformStatus:r.platformStatus||'unknown',raw:r});
  }
  return rows.map(row=>{
   const done=row.personalDone||officialFinished(row);
   const needsVerification=row.source==='chaoxing'&&!done&&!officialUnfinished(row);
   const prerequisites=row.origin==='local'?row.raw.prerequisiteIds:[];
   const waitingOn=prerequisites.map(id=>localById.get(id)).filter(t=>!t||!t.done)
    .map(t=>t?.title||'前置任务已丢失');
   const status=done?'done':needsVerification?'verify':waitingOn.length?'waiting':'ready';
   return {...row,done,status,waitingOn,officialDone:officialFinished(row)};
  }).filter(r=>{
   const accepted=filter==='all'||filter==='open'&&['ready','waiting'].includes(r.status)||
    filter==='ready'&&r.status==='ready'||filter==='waiting'&&r.status==='waiting'||
    filter==='done'&&r.status==='done'||filter==='verify'&&r.status==='verify';
   return accepted&&(source==='all'||r.source===source);
  }).map(row=>({row,deadline:deadlineOrder(row)}))
   .sort((a,b)=>Number(a.row.done)-Number(b.row.done)||
     ({ready:0,waiting:1,verify:2,done:3}[a.row.status]-
      {ready:0,waiting:1,verify:2,done:3}[b.row.status])||
     a.deadline.day.localeCompare(b.deadline.day)||
     (a.deadline.instant===null?b.deadline.instant===null?0:-1:
      b.deadline.instant===null?1:a.deadline.instant-b.deadline.instant)||
     String(a.row.id).localeCompare(String(b.row.id))).map(entry=>entry.row);
 }
 function courses({classes=[],calendarEvents=[]}={},date,day){
  return [...classes.filter(r=>r.day===day).map(r=>({...r,origin:'local'})),
   ...calendarEvents.filter(r=>r.date===date).map(r=>({...r,day,origin:'calendar'}))]
   .sort((a,b)=>String(a.start).localeCompare(String(b.start)));
 }
 root.XiaoxuWorkspace=Object.freeze({tasks,courses,names,sanitizePrerequisites,canSetPrerequisites});
})(globalThis);
