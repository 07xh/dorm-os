/* Pure personal event projection; no sample records and no assumption of verified provider sync.
 * Source academic_verified_records can be a SELF-REPORTED upload, not server-attested.
 */
const DATE=/^20\d{2}-\d{2}-\d{2}$/;
const ISO=/^20\d{2}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?(?:Z|[+-]\d{2}:\d{2})$/;
const dayNumber=s=>Date.parse(s+'T00:00:00Z')/86400000;
// A read receipt belongs to a specific deadline, not forever to a task ID.
// New dates/times must produce a new unread event while retaining old receipts.
const clock=s=>typeof s==='string'&&/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(s)?s:'unknown';
const examEventKey=e=>'exam-v2:'+e.externalId+':'+(e.term||'')+':'+e.date+
  ':'+clock(e.startTime)+':'+clock(e.endTime);
const taskEventKey=(t,exact)=>'task-v2:'+t.id+':'+
  (exact?'exact:'+new Date(t.due_at_utc).toISOString():'date:'+t.due_local_date);
const validDay=s=>typeof s==='string'&&DATE.test(s)&&Number.isFinite(dayNumber(s))&&new Date(dayNumber(s)*86400000).toISOString().slice(0,10)===s;
function shanghaiDay(now){
 const parts=new Intl.DateTimeFormat('en-US',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date(now));
 const v=Object.fromEntries(parts.map(x=>[x.type,x.value]));
 return v.year+'-'+v.month+'-'+v.day;
}
function buildReminderInbox({exams=[],tasks=[],preferences={},readKeys=[],now=Date.now()}={}){
 const prefs={enabled:preferences.enabled===true,
  examLeadDays:Number.isInteger(preferences.examLeadDays)?preferences.examLeadDays:7,
  taskLeadDays:Number.isInteger(preferences.taskLeadDays)?preferences.taskLeadDays:3};
 if(prefs.examLeadDays<0||prefs.examLeadDays>30||prefs.taskLeadDays<0||prefs.taskLeadDays>30)
  throw Error('提醒提前天数不合法');
 const today=shanghaiDay(now),todayIndex=dayNumber(today);
 const read=new Set(readKeys),items=[],keys=new Set();
 function add(event){
  if(keys.has(event.key)||event.key.length>220)return;
  keys.add(event.key);
  items.push({...event,read:read.has(event.key)});
 }
 for(const e of exams){
  if(!e||!validDay(e.date)||typeof e.externalId!=='string'||typeof e.title!=='string')continue;
  const diff=dayNumber(e.date)-todayIndex;
  if(diff<0||diff>prefs.examLeadDays)continue;
  add({key:examEventKey(e),kind:'exam',
   title:e.title.slice(0,120),date:e.date,precision:'date',
   subtitle:'个人导入的考试快照 · 请以学校最新考场安排为准',
   when:e.startTime&&/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(e.startTime)?
    e.date+' '+e.startTime:e.date+'（时刻未验证）',daysAway:diff});
 }
 for(const t of tasks){
  if(!t||typeof t.id!=='string'||typeof t.title!=='string'||t.local_status==='done')continue;
  const day=t.due_local_date;
  if(!validDay(day))continue;
  const diff=dayNumber(day)-todayIndex;
  if(diff< -1||diff>prefs.taskLeadDays)continue;
  const exact=t.due_precision==='exact'&&typeof t.due_at_utc==='string'&&
   ISO.test(t.due_at_utc)&&Number.isFinite(Date.parse(t.due_at_utc));
  add({key:taskEventKey(t,exact),kind:'task',title:t.title.slice(0,120),date:day,
   precision:exact?'exact':'date',subtitle:'云端个人任务 · 平台提交状态请另行核对',
   when:exact?new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',
    year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'
   }).format(new Date(t.due_at_utc)):day+'（截止时刻未知）',daysAway:diff});
 }
 items.sort((a,b)=>a.date.localeCompare(b.date)||a.kind.localeCompare(b.kind)||a.key.localeCompare(b.key));
 return {preferences:prefs,items:items.slice(0,100),unread:items.filter(e=>!e.read).length,
  status:items.length?'has_events':'no_due_events'};
}
export {buildReminderInbox,shanghaiDay};
