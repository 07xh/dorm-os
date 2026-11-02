// Stable domain contract for the new cloud-backed task store.
// Existing xiaoxu-task-batch-v1 imports remain untouched in localStorage.
export class AcademicInputError extends Error {
  constructor(message) { super(message); this.name = 'AcademicInputError'; }
}
const fail = (message) => { throw new AcademicInputError(message); };
const validDay = (day) => {
  if (typeof day !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(day)) return false;
  const parsed = new Date(day + 'T00:00:00Z');
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === day;
};
const validInstant = (raw) => typeof raw === 'string' &&
  /^\d{4}-\d{2}-\d{2}T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:Z|[+-](?:[01]\d|2[0-3]):[0-5]\d)$/.test(raw) &&
  validDay(raw.slice(0,10)) && Number.isFinite(Date.parse(raw));

export function normalizeManualAssignment(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input)) fail('任务必须是对象');
  if (typeof input.title !== 'string' || !input.title.trim() ||
      input.title.trim().length > 120) fail('任务标题不能为空且不得超过120字');
  const title = input.title.trim();
  const courseId = input.courseId ?? null;
  if (courseId !== null &&
      (typeof courseId !== 'string' || !/^[A-Za-z0-9_-]{1,90}$/.test(courseId))) {
    fail('课程标识不合法');
  }
  const localStatus = input.localStatus ?? 'todo';
  if (!['todo', 'in_progress', 'done'].includes(localStatus)) fail('个人任务状态不合法');
  const deadline = input.deadline ?? {precision: 'unknown'};
  if (!deadline || typeof deadline !== 'object' || Array.isArray(deadline)) fail('截止时间结构错误');
  const precision = deadline.precision;
  if (!['unknown', 'date', 'exact'].includes(precision)) fail('截止时间精度错误');
  let dueAtUtc = null;
  let dueLocalDate = null;
  if (precision === 'date' || precision === 'exact') {
    if (!validDay(deadline.date)) fail('截止日期无效');
    dueLocalDate = deadline.date;
  }
  if (precision === 'exact') {
    if (!validInstant(deadline.at) || deadline.at.slice(0,10) !== dueLocalDate) {
      fail('精确截止时刻必须含明确时区且日期一致');
    }
    dueAtUtc = new Date(deadline.at).toISOString();
  }
  if ((precision !== 'exact' && deadline.at != null) ||
      (precision === 'unknown' && deadline.date != null)) {
    fail('截止时间精度与提供字段不一致');
  }
  const personalTargetAt = input.personalTargetAt ?? null;
  if (personalTargetAt !== null && !validInstant(personalTargetAt)) {
    fail('个人计划时间必须是带时区的精确时刻');
  }
  return {
    title, courseId, localStatus, dueAtUtc, dueLocalDate,
    duePrecision: precision,
    personalTargetAt: personalTargetAt === null ? null : new Date(personalTargetAt).toISOString()
  };
}

export async function createManualAssignment(db, userId, input, {
  now = () => new Date().toISOString(),
  id = () => crypto.randomUUID()
} = {}) {
  const item = normalizeManualAssignment(input);
  if (item.courseId) {
    const course = await db.prepare(
      'SELECT id FROM academic_courses WHERE id=? AND user_id=?'
    ).bind(item.courseId, userId).first();
    if (!course) fail('课程不存在或无权访问');
  }
  const taskId = id();
  const timestamp = now();
  await db.batch([
    db.prepare('INSERT OR IGNORE INTO academic_users(id,created_at) VALUES (?,?)')
      .bind(userId,timestamp),
    db.prepare(`INSERT INTO academic_assignments
      (id,user_id,course_id,title,kind,due_at_utc,due_local_date,
       due_timezone,due_precision,personal_target_at,local_status,created_at,updated_at)
      VALUES (?,?,?,?,'manual',?,?,'Asia/Shanghai',?,?,?,?,?)`)
      .bind(taskId,userId,item.courseId,item.title,item.dueAtUtc,item.dueLocalDate,
        item.duePrecision,item.personalTargetAt,item.localStatus,timestamp,timestamp)
  ]);
  return {id:taskId, ...item, createdAt:timestamp, updatedAt:timestamp, kind:'manual'};
}

export async function listAssignments(db, userId, {limit=50, offset=0, status=null}={}) {
  if (!Number.isInteger(limit) || limit < 1 || limit > 100 ||
      !Number.isInteger(offset) || offset < 0 || offset > 100000 ||
      (status !== null && !['todo','in_progress','done'].includes(status))) {
    fail('查询参数无效');
  }
  const filter = status === null ? '' : ' AND a.local_status=?';
  const params = status === null ? [userId,limit,offset] : [userId,status,limit,offset];
  const sql = `SELECT a.id,a.title,a.course_id,a.kind,a.due_at_utc,
       a.due_local_date,a.due_timezone,a.due_precision,
       a.personal_target_at,a.local_status,a.created_at,a.updated_at,a.revision,
       (SELECT l.legacy_id FROM academic_legacy_links l
        WHERE l.assignment_id=a.id AND l.user_id=a.user_id LIMIT 1) AS legacy_id,
       (SELECT s.course_title FROM academic_assignment_sources s
        WHERE s.assignment_id=a.id ORDER BY s.last_seen_at DESC LIMIT 1) AS source_course,
       (SELECT s.platform_status FROM academic_assignment_sources s
        WHERE s.assignment_id=a.id ORDER BY s.last_seen_at DESC LIMIT 1)
         AS platform_status
       FROM academic_assignments a WHERE a.user_id=?${filter}
       ORDER BY (a.due_at_utc IS NULL),a.due_at_utc,
         (a.due_local_date IS NULL),a.due_local_date,a.id
       LIMIT ? OFFSET ?`;
  const result = await db.prepare(sql).bind(...params).all();
  return result.results ?? [];
}
