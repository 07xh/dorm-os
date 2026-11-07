// Internal only: normalize and reconcile output from an already-authorized,
// verified read-only connector. No public POST route exposes this method.
// Missing tasks never imply deletion or submission; manual completion is untouched.
export class AcademicSyncError extends Error {
  constructor(message){super(message);this.name='AcademicSyncError';}
}
const invalid=(message)=>{throw new AcademicSyncError(message);};
const SOURCES=['chaoxing','ncwu'];
const STATUSES=['todo','submitted','graded','returned','unknown'];
function validDay(day){
  if(typeof day!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(day))return false;
  const value=new Date(day+'T00:00:00Z');
  return Number.isFinite(value.getTime())&&value.toISOString().slice(0,10)===day;
}
const asText=(raw,max,field,required=false)=>{
  if(typeof raw!=='string'||raw.length>max||raw.trim()!==raw||(required&&!raw))
    invalid('非法 '+field);
  return raw;
};
export function normalizeReadOnlySnapshot(raw,{maxTasks=20}={}){
  if(!Number.isInteger(maxTasks)||maxTasks<1||maxTasks>250) invalid('只读批次任务上限无效');
  if(!raw||typeof raw!=='object'||Array.isArray(raw)||
     raw.format!=='xiaoxu-task-batch-v1'||!SOURCES.includes(raw.source)||
     !Array.isArray(raw.tasks)||raw.tasks.length>maxTasks)
    invalid('只读快照格式、来源或批次大小不支持');
  const ids=new Set();
  const tasks=raw.tasks.map((t)=>{
    if(!t||typeof t!=='object'||Array.isArray(t))invalid('任务结构无效');
    const externalId=asText(t.externalId,80,'任务来源 ID',true);
    if(ids.has(externalId))invalid('重复外部作业 ID');
    ids.add(externalId);
    const title=asText(t.title,100,'作业标题',true);
    const course=asText(t.course??'',80,'课程名称');
    if(!STATUSES.includes(t.status))invalid('平台状态无效');
    const due=t.due??'',dueAt=t.dueAt??'';
    if(typeof due!=='string'||(due&&!validDay(due))||
       typeof dueAt!=='string')invalid('截止日期无效');
    const exact=/^(\d{4}-\d{2}-\d{2})T(?:[01]\d|2[0-3]):[0-5]\d:00\+08:00$/.exec(dueAt);
    if(dueAt&&(!exact||exact[1]!==due))invalid('精确截止时刻无效');
    return {
      externalId,title,course,status:t.status,
      dueAtUtc:dueAt?new Date(dueAt).toISOString():null,
      dueLocalDate:due||null,
      duePrecision:dueAt?'exact':due?'date':'unknown'
    };
  });
  return {source:raw.source,tasks};
}
export async function applyReadOnlySnapshot(db,userId,connectionId,snapshot,{
  coverage='partial',maxTasks=20,id=()=>crypto.randomUUID(),now=()=>new Date().toISOString(),
  additionalStatements=[]
}={}){
  if(typeof userId!=='string'||!userId||
     typeof connectionId!=='string'||!connectionId||
     !['partial','complete'].includes(coverage))invalid('同步身份或覆盖范围非法');
  if(!Array.isArray(additionalStatements)||additionalStatements.length>300||
     additionalStatements.some(stmt=>!stmt||typeof stmt.run!=='function'))
    invalid('额外可信只读记录事务无效');
  const data=normalizeReadOnlySnapshot(snapshot,{maxTasks});
  const connection=await db.prepare(`SELECT id,provider,mode,status FROM academic_connections
     WHERE id=? AND user_id=?`).bind(connectionId,userId).first();
  if(!connection||connection.provider!==data.source||
     connection.mode!=='readonly'||!['connected','partial'].includes(connection.status))
    invalid('尚未验证只读连接器身份和状态，禁止写入已同步数据');

  const keys=data.tasks.map(t=>t.externalId);
  const marks=keys.map(()=>'?').join(',');
  const bound=keys.length?[connectionId,userId,...keys]:[];
  const sourceRows=keys.length?(await db.prepare(`SELECT
      s.external_id,s.assignment_id,s.platform_status,s.course_title,
      a.title,a.due_at_utc,a.due_local_date,a.due_precision
      FROM academic_assignment_sources s JOIN academic_assignments a
        ON a.id=s.assignment_id
      WHERE s.connection_id=? AND a.user_id=? AND s.external_id IN (${marks})`
     ).bind(...bound).all()).results??[]:[];
  const bySource=new Map(sourceRows.map(x=>[x.external_id,x]));
  const unknownKeys=keys.filter(x=>!bySource.has(x));
  const legacyRows=unknownKeys.length?(await db.prepare(`SELECT
      l.legacy_external_id AS external_id,l.assignment_id,
      l.legacy_platform_status AS platform_status,
      l.legacy_course_text AS course_title,
      a.title,a.due_at_utc,a.due_local_date,a.due_precision
      FROM academic_legacy_links l JOIN academic_assignments a
        ON a.id=l.assignment_id AND a.user_id=l.user_id
      WHERE l.user_id=? AND l.legacy_source=?
        AND l.legacy_external_id IN (${unknownKeys.map(()=>'?').join(',')})`
    ).bind(userId,data.source,...unknownKeys).all()).results??[]:[];
  // A legacy import can contain several independent local records claiming
  // the SAME external ID. This must not block reading an entire official course.
  // Never choose a legacy target arbitrarily or overwrite its personal state:
  // create one independently verified, stable source assignment instead.
  const legacy=new Map(),ambiguousLegacy=new Set();
  for(const item of legacyRows){
    const key=item.external_id;
    if(ambiguousLegacy.has(key))continue;
    if(legacy.has(key)&&legacy.get(key).assignment_id!==item.assignment_id){
      legacy.delete(key);
      ambiguousLegacy.add(key);
    }else legacy.set(key,item);
  }
  const targets=new Set(),planned=[];
  for(const task of data.tasks){
    const source=bySource.get(task.externalId);
    const legacyMatch=source??legacy.get(task.externalId);
    if(legacyMatch){
      if(targets.has(legacyMatch.assignment_id))
        invalid('两条外部作业映射同一任务，不能安全写入');
      targets.add(legacyMatch.assignment_id);
    }
    planned.push({task,existing:legacyMatch??null,linkedFromLegacy:!source&&!!legacyMatch});
  }
  const stamp=now(),runId=id();
  const statements=[],events=[];
  let inserted=0,updated=0,linked=0;
  function event(assignmentId,eventType,oldValue,newValue){
    const eventTime=new Date(Date.parse(stamp)+events.length).toISOString();
    events.push({id:id(),assignmentId,eventType,oldValue,newValue,eventTime});
  }
  for(const {task,existing,linkedFromLegacy} of planned){
    const taskId=existing?.assignment_id??id();
    const previousStatus=existing?.platform_status??'unknown';
    const status=task.status==='unknown'?previousStatus:task.status;
    const sourceIsNew=!bySource.has(task.externalId);
    if(!existing){
      inserted++;
      statements.push(db.prepare(`INSERT INTO academic_assignments
        (id,user_id,title,kind,due_at_utc,due_local_date,due_timezone,
         due_precision,local_status,created_at,updated_at)
         VALUES (?,?,?,'platform',?,?,'Asia/Shanghai',?,'todo',?,?)`)
        .bind(taskId,userId,task.title,task.dueAtUtc,task.dueLocalDate,
          task.duePrecision,stamp,stamp));
      event(taskId,'assignment_created',null,task.title);
    }else{
      const deadlineKnown=task.duePrecision!=='unknown';
      const dueAtUtc=deadlineKnown?task.dueAtUtc:existing.due_at_utc;
      const dueLocalDate=deadlineKnown?task.dueLocalDate:existing.due_local_date;
      const duePrecision=deadlineKnown?task.duePrecision:existing.due_precision;
      const titleChanged=existing.title!==task.title;
      const deadlineChanged=deadlineKnown&&(
        existing.due_at_utc!==dueAtUtc||existing.due_local_date!==dueLocalDate||
        existing.due_precision!==duePrecision);
      const statusChanged=previousStatus!==status;
      const courseChanged=(existing.course_title??'')!==task.course && !!task.course;
      if(titleChanged||deadlineChanged||statusChanged||courseChanged)updated++;
      if(titleChanged||deadlineChanged)statements.push(db.prepare(`UPDATE academic_assignments
        SET title=?,due_at_utc=?,due_local_date=?,due_precision=?,updated_at=?
        WHERE id=? AND user_id=?`)
        .bind(task.title,dueAtUtc,dueLocalDate,duePrecision,stamp,taskId,userId));
      if(titleChanged)event(taskId,'title_changed',existing.title,task.title);
      if(deadlineChanged)event(taskId,'deadline_changed',
        JSON.stringify({precision:existing.due_precision,date:existing.due_local_date,at:existing.due_at_utc}),
        JSON.stringify({precision:duePrecision,date:dueLocalDate,at:dueAtUtc}));
      if(statusChanged)event(taskId,'platform_status_changed',previousStatus,status);
    }
    if(sourceIsNew){
      if(linkedFromLegacy)linked++;
      statements.push(db.prepare(`INSERT INTO academic_assignment_sources
        (assignment_id,connection_id,user_id,external_id,platform_status,last_seen_at,course_title)
        VALUES (?,?,?,?,?,?,?)`)
        .bind(taskId,connectionId,userId,task.externalId,status,stamp,task.course));
    }else{
      statements.push(db.prepare(`UPDATE academic_assignment_sources
        SET platform_status=?,last_seen_at=?,
        course_title=CASE WHEN ?='' THEN course_title ELSE ? END
        WHERE connection_id=? AND external_id=?`)
        .bind(status,stamp,task.course,task.course,connectionId,task.externalId));
    }
  }
  for(const item of events){
    statements.push(db.prepare(`INSERT INTO academic_assignment_events
       (id,assignment_id,event_type,previous_value,current_value,observed_at)
       VALUES (?,?,?,?,?,?)`)
       .bind(item.id,item.assignmentId,item.eventType,
         item.oldValue,item.newValue,item.eventTime));
  }
  statements.push(db.prepare(`INSERT INTO academic_sync_runs
    (id,connection_id,started_at,completed_at,status,coverage,
     received_count,inserted_count,updated_count)
     VALUES (?,?,?,?,?,?,?,?,?)`)
    .bind(runId,connectionId,stamp,stamp,coverage==='complete'?'success':'partial',
      coverage,data.tasks.length,inserted,updated));
  statements.push(db.prepare(`UPDATE academic_connections
    SET status=?,last_attempt_at=?,last_success_at=?,last_error_code=NULL
    WHERE id=? AND user_id=?`)
    .bind(coverage==='complete'?'connected':'partial',stamp,stamp,connectionId,userId));
  // Trusted source metadata participates in the same transaction as tasks.
  // On any failed statement, D1 rolls back both message and assignment data.
  statements.push(...additionalStatements);
  // Atomic D1 batch: a failure never partially updates official or local states.
  await db.batch(statements);
  return {runId,received:data.tasks.length,inserted,updated,linked,
    events:events.length,coverage};
}
