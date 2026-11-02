// Academic OS personal workspace: only the authenticated owner may edit.
// Platform fields are immutable here; only a verified connector changes them.
import {normalizeManualAssignment,AcademicInputError} from './academic-assignments.mjs';
export class WorkspaceError extends Error {
  constructor(code,status=422){super(code);this.name='WorkspaceError';this.code=code;this.status=status;}
}
const err=(code,status)=>{throw new WorkspaceError(code,status);};
const taskIdValid=id=>typeof id==='string'&&/^[A-Za-z0-9_-]{1,100}$/.test(id);
const allowed=new Set(['revision','localStatus','title','deadline','personalTargetAt']);
const localStates=new Set(['todo','in_progress','done']);

export async function patchPersonalTask(db,userId,taskId,patch,{
  id=()=>crypto.randomUUID(),now=()=>new Date().toISOString()
}={}){
  if(!taskIdValid(taskId)||!userId)err('invalid_id',400);
  if(!patch||typeof patch!=='object'||Array.isArray(patch)||
     Object.keys(patch).some(k=>!allowed.has(k)))err('invalid_patch');
  if(!Number.isSafeInteger(patch.revision)||patch.revision<0)err('revision_required');
  const row=await db.prepare(`SELECT id,user_id,title,kind,due_at_utc,due_local_date,due_precision,
   personal_target_at,local_status,revision,updated_at
   FROM academic_assignments WHERE id=? AND user_id=?`).bind(taskId,userId).first();
  if(!row)err('not_found',404);
  if(row.revision!==patch.revision)err('revision_conflict',409);
  const changes=[],events=[];
  const event=(type,oldValue,newValue)=>events.push({type,oldValue,newValue});
  const set=(column,value)=>changes.push({column,value});
  if(Object.hasOwn(patch,'localStatus')){
    if(!localStates.has(patch.localStatus))err('invalid_local_status');
    if(row.local_status!==patch.localStatus){
      set('local_status',patch.localStatus);
      event('personal_status_changed',row.local_status,patch.localStatus);
    }
  }
  if(Object.hasOwn(patch,'personalTargetAt')){
    let target=null;
    try{
      target=normalizeManualAssignment({title:row.title,
        personalTargetAt:patch.personalTargetAt}).personalTargetAt;
    }catch(e){if(e instanceof AcademicInputError)err('invalid_personal_target');throw e;}
    if(row.personal_target_at!==target){
      set('personal_target_at',target);
      event('personal_target_changed',row.personal_target_at,target);
    }
  }
  if(Object.hasOwn(patch,'title')||Object.hasOwn(patch,'deadline')){
    if(row.kind!=='manual')err('source_fields_readonly',403);
    let norm;
    try{
      norm=normalizeManualAssignment({
        title:Object.hasOwn(patch,'title')?patch.title:row.title,
        deadline:Object.hasOwn(patch,'deadline')?patch.deadline:{precision:'unknown'}
      });
    }catch(e){if(e instanceof AcademicInputError)err('invalid_manual_fields');throw e;}
    if(Object.hasOwn(patch,'title')&&row.title!==norm.title){
      set('title',norm.title);event('title_changed',row.title,norm.title);
    }
    if(Object.hasOwn(patch,'deadline')){
      const old={precision:row.due_precision,date:row.due_local_date,at:row.due_at_utc};
      const next={precision:norm.duePrecision,date:norm.dueLocalDate,at:norm.dueAtUtc};
      if(JSON.stringify(old)!==JSON.stringify(next)){
        set('due_precision',norm.duePrecision);
        set('due_local_date',norm.dueLocalDate);
        set('due_at_utc',norm.dueAtUtc);
        event('deadline_changed',JSON.stringify(old),JSON.stringify(next));
      }
    }
  }
  if(!events.length) return {id:taskId,revision:row.revision,updatedAt:row.updated_at,unchanged:true};
  const timestamp=now();
  if(!Number.isFinite(Date.parse(timestamp)))err('invalid_timestamp',503);
  const targetRevision=row.revision+1;
  const sets=changes.map(x=>x.column+'=?').join(',');
  const statements=[db.prepare(`UPDATE academic_assignments SET ${sets},updated_at=?,revision=revision+1
    WHERE id=? AND user_id=? AND revision=?`)
    .bind(...changes.map(x=>x.value),timestamp,taskId,userId,row.revision)];
  // The conditional INSERT runs in the same D1 batch transaction and therefore
  // records an event only if the optimistic update won.
  for(const e of events){
    statements.push(db.prepare(`INSERT INTO academic_assignment_events
      (id,assignment_id,event_type,previous_value,current_value,observed_at)
      SELECT ?,a.id,?,?,?,? FROM academic_assignments a
      WHERE a.id=? AND a.user_id=? AND a.revision=?`)
      .bind(id(),e.type,e.oldValue,e.newValue,timestamp,taskId,userId,targetRevision));
  }
  const results=await db.batch(statements);
  const affected=results?.[0]?.meta?.changes??results?.[0]?.changes??0;
  if(affected!==1)err('revision_conflict',409);
  return {id:taskId,revision:targetRevision,updatedAt:timestamp,unchanged:false};
}

export async function readTaskHistory(db,userId,taskId,{limit=30}={}){
  if(!taskIdValid(taskId))err('invalid_id',400);
  if(!Number.isInteger(limit)||limit<1||limit>100)err('invalid_limit',400);
  const owned=await db.prepare('SELECT id FROM academic_assignments WHERE id=? AND user_id=?')
    .bind(taskId,userId).first();
  if(!owned)err('not_found',404);
  const {results}=await db.prepare(`SELECT e.id,e.event_type,e.previous_value,e.current_value,e.observed_at
    FROM academic_assignment_events e JOIN academic_assignments a ON a.id=e.assignment_id
    WHERE e.assignment_id=? AND a.user_id=?
    ORDER BY e.observed_at DESC,e.id DESC LIMIT ?`).bind(taskId,userId,limit).all();
  return results??[];
}

export async function readSourceHealth(db,userId){
  const connections=await db.prepare(`SELECT provider,mode,status,last_attempt_at,last_success_at,last_error_code
    FROM academic_connections WHERE user_id=? ORDER BY provider,profile_key`).bind(userId).all();
  const snapshots=await db.prepare(`SELECT kind,source,last_success_at
    FROM academic_verified_batches WHERE user_id=? ORDER BY kind`).bind(userId).all();
  // Snapshots are user-supplied records; do NOT present them as proven platform sessions.
  return {connections:connections.results??[],userSnapshots:(snapshots.results??[])
    .map(x=>({kind:x.kind,source:x.source,lastReceivedAt:x.last_success_at,
      state:'user_supplied_snapshot'}))};
}
