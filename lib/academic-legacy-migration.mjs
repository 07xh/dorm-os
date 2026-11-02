// V1 localStorage task -> authenticated D1 migration, preserving original browser data.
// This is an explicit user-driven import, NOT authenticated third-party synchronization.
export class LegacyMigrationError extends Error {
  constructor(message) {super(message);this.name='LegacyMigrationError';}
}
const invalid = (message) => {throw new LegacyMigrationError(message);};
const SOURCES = ['manual','chaoxing','ncwu','second_class'];
const STATUSES = ['todo','submitted','graded','returned','unknown'];
function validDay(day) {
  if (typeof day!=='string' || !/^\d{4}-\d{2}-\d{2}$/.test(day)) return false;
  const parsed=new Date(day+'T00:00:00Z');
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0,10)===day;
}
function safeText(value,max,field,{required=false}={}) {
  if(typeof value!=='string' || value.length>max || value!==value.trim() ||
      (required && !value)) invalid(field+' 无效');
  return value;
}
export function normalizeLegacyBatch(input) {
  if (!input || typeof input!=='object' || Array.isArray(input) ||
      input.format!=='xiaoxu-legacy-tasks-v1' ||
      !['preview','commit'].includes(input.mode)) {
    invalid('迁移版本或模式不支持');
  }
  if (!Array.isArray(input.tasks) || input.tasks.length<1 || input.tasks.length>20) {
    invalid('每次迁移必须包含 1 至 20 条任务');
  }
  const seen=new Set();
  const tasks=input.tasks.map((raw,index)=>{
    if(!raw||typeof raw!=='object'||Array.isArray(raw))invalid('任务结构错误: '+index);
    const legacyId=safeText(raw.id,60,'原任务 ID',{required:true});
    if(seen.has(legacyId))invalid('同一批次重复任务 ID');
    seen.add(legacyId);
    const title=safeText(raw.title,100,'标题',{required:true});
    const source=raw.source??'manual';
    const externalId=safeText(raw.sourceId??'',80,'外部作业 ID');
    const course=safeText(raw.course??'',80,'原课程');
    const category=safeText(raw.category??'',80,'原分类');
    const createdAt=safeText(raw.createdAt??'',40,'原创建时间');
    if(!SOURCES.includes(source)) invalid('来源不支持');
    const platformStatus=raw.sourceStatus??'unknown';
    if(!STATUSES.includes(platformStatus))invalid('平台状态无效');
    if(typeof raw.done!=='boolean')invalid('必须显式提供本地完成状态');
    const due=raw.due??'';
    const dueAt=raw.dueAt??'';
    if(typeof due!=='string' || (due && !validDay(due)))invalid('截止日期无效');
    if(typeof dueAt!=='string')invalid('截止时刻类型无效');
    const exact=/^(\d{4}-\d{2}-\d{2})T(?:[01]\d|2[0-3]):[0-5]\d:00\+08:00$/.exec(dueAt);
    if(dueAt && (!exact || exact[1]!==due)) invalid('精确截止时间必须含中国标准时间且与日期一致');
    if(source==='manual' && externalId)invalid('手动任务不允许伪造平台 ID');
    if(source!=='manual' && !externalId)invalid('平台导入任务缺少来源 ID');
    const precision=dueAt?'exact':due?'date':'unknown';
    return {
      legacyId,title,source,externalId,course,category,createdAt,
      platformStatus,localStatus:raw.done?'done':'todo',
      duePrecision:precision,dueLocalDate:due||null,
      dueAtUtc:dueAt?new Date(dueAt).toISOString():null
    };
  });
  return {mode:input.mode,tasks};
}
const hex=(buffer)=>Array.from(new Uint8Array(buffer),x=>x.toString(16).padStart(2,'0')).join('');
async function rowFingerprint(row,subtle) {
  return hex(await subtle.digest('SHA-256',
    new TextEncoder().encode(JSON.stringify(row))));
}
export async function migrateLegacyBatch(db,userId,input,{
  id=()=>crypto.randomUUID(),
  now=()=>new Date().toISOString(),
  subtle=crypto.subtle
}={}) {
  if(typeof userId!=='string'||!userId)invalid('用户未验证');
  const normalized=normalizeLegacyBatch(input);
  const items=await Promise.all(normalized.tasks.map(async row=>({
    ...row,fingerprint:await rowFingerprint(row,subtle)
  })));
  // Queries are scoped to the trusted identity, never to caller-provided userId.
  const existing=await db.prepare(`SELECT legacy_id,original_fingerprint
      FROM academic_legacy_links WHERE user_id=? AND
      legacy_id IN (${items.map(()=>'?').join(',')})`)
      .bind(userId,...items.map(x=>x.legacyId)).all();
  const matches=new Map((existing.results??[]).map(x=>[x.legacy_id,x.original_fingerprint]));
  const conflicts=items.filter(x=>matches.has(x.legacyId)&&matches.get(x.legacyId)!==x.fingerprint)
    .map(x=>x.legacyId);
  const pending=items.filter(x=>!matches.has(x.legacyId));
  const result={mode:normalized.mode,total:items.length,
    toCreate:pending.length,unchanged:items.length-pending.length,conflicts};
  if(conflicts.length || normalized.mode==='preview')return result;
  if(!pending.length)return result;
  const stamp=now();
  const statements=[
    db.prepare('INSERT OR IGNORE INTO academic_users(id,created_at) VALUES (?,?)')
      .bind(userId,stamp)
  ];
  const freshIds=new Set();
  for(const row of pending) {
    const taskId=id();
    if(typeof taskId!=='string'||!taskId||freshIds.has(taskId))invalid('新任务 ID 冲突');
    freshIds.add(taskId);
    statements.push(db.prepare(`INSERT INTO academic_assignments
      (id,user_id,title,kind,due_at_utc,due_local_date,due_timezone,due_precision,
      local_status,created_at,updated_at)
      VALUES (?,?,?, ?, ?,?,'Asia/Shanghai',?,?,?,?)`)
      .bind(taskId,userId,row.title,row.source==='manual'?'manual':'platform',
        row.dueAtUtc,row.dueLocalDate,row.duePrecision,row.localStatus,stamp,stamp));
    statements.push(db.prepare(`INSERT INTO academic_legacy_links
      (user_id,legacy_id,assignment_id,legacy_source,legacy_external_id,
       legacy_platform_status,legacy_course_text,legacy_category,
       legacy_created_at,original_fingerprint,migrated_at)
      VALUES (?,?,?,?,?,?,?,?,?,?,?)`)
      .bind(userId,row.legacyId,taskId,row.source,row.externalId||null,
        row.platformStatus,row.course,row.category,row.createdAt,row.fingerprint,stamp));
  }
  // D1 batch is transactional. Any malformed row or duplicate aborts this entire chunk.
  await db.batch(statements);
  return {...result,created:pending.length};
}
