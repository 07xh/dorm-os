/* 校序 Academic OS - strict, source-verified personal snapshot contract.
 * Never store passwords, cookies, signed URLs, raw HTML or unverified campus data.
 */
const KIND_SOURCES=Object.freeze({
 schedule:'ncwu',grades:'ncwu',exams:'ncwu',
 assignments:'chaoxing','second-class':'second_class'
});
const KINDS=Object.freeze(Object.keys(KIND_SOURCES));
const ID=/^[A-Za-z0-9:_-]{1,120}$/;
const DATE=/^20\d{2}-\d{2}-\d{2}$/;
const TIMESTAMP=/^20\d{2}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?(?:Z|[+-]\d{2}:\d{2})$/;
const FIELDS=new Set(['externalId','term','title','course','teacher','location','weekday',
 'startSection','endSection','weeks','startTime','endTime','date','dueAt','platformStatus',
 'score','credit','gradePoint','activityStatus','hours','points','note']);
const STATUS=new Set(['todo','submitted','graded','returned','unknown']);
const ACTIVITY_STATUS=new Set(['registered','completed','cancelled','unknown']);
const limit=(v,max,name)=>{
 if(v===null||v===undefined||v==='')return '';
 if(typeof v!=='string'||v.length>max)throw Error(name+'格式无效');
 return v.trim();
};
function date(value,name){
 const valueText=limit(value,10,name);
 if(!valueText)return '';
 if(!DATE.test(valueText)||!Number.isFinite(Date.parse(valueText))||
    new Date(valueText+'T00:00:00Z').toISOString().slice(0,10)!==valueText)throw Error(name+'无效');
 return valueText;
}
function timestamp(value,name,required=false){
 const t=limit(value,40,name);
 if(!t){if(required)throw Error(name+'缺失');return '';}
 if(!TIMESTAMP.test(t)||!Number.isFinite(Date.parse(t)))throw Error(name+'无效');
 return t;
}
function numberOrNull(value,name,min,max){
 if(value===null||value===undefined||value==='')return null;
 if(typeof value!=='number'||!Number.isFinite(value)||value<min||value>max)throw Error(name+'超出范围');
 return value;
}
function indexOrNull(value,name,min,max){
 if(value===null||value===undefined)return null;
 if(!Number.isInteger(value)||value<min||value>max)throw Error(name+'无效');
 return value;
}
function normalizeRecord(kind,row){
 if(!row||typeof row!=='object'||Array.isArray(row))throw Error('记录不是对象');
 for(const key of Object.keys(row))if(!FIELDS.has(key))throw Error('不支持的记录字段：'+key);
 const id=limit(row.externalId,120,'externalId');
 if(!ID.test(id))throw Error('缺少可信稳定 externalId');
 const term=limit(row.term,40,'term');
 if(kind!=='assignments'&&kind!=='second-class'&&!term)throw Error('学期未经确认');
 const title=limit(row.title,120,'title');
 if(!title)throw Error('缺少真实记录标题');
 const result={externalId:id,term,title};
 for(const field of ['course','teacher','location','note']){
  const value=limit(row[field],field==='note'?220:120,field);
  if(value)result[field]=value;
 }
 const d=date(row.date,'date');if(d)result.date=d;
 const t=timestamp(row.dueAt,'dueAt');if(t)result.dueAt=t;
 for(const field of ['startTime','endTime']){
  const str=limit(row[field],5,field);
  if(str&&!/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(str))throw Error(field+'无效');
  if(str)result[field]=str;
 }
 if(kind==='schedule'){
  const weekday=indexOrNull(row.weekday,'weekday',1,7);
  const first=indexOrNull(row.startSection,'startSection',1,24);
  const last=indexOrNull(row.endSection,'endSection',1,24);
  if(weekday===null||first===null||last===null||last<first)throw Error('课程周次/节次未验证');
  if(!Array.isArray(row.weeks)||!row.weeks.length||row.weeks.length>35||
   row.weeks.some(w=>!Number.isInteger(w)||w<1||w>35))throw Error('课程 weeks 未验证');
  const uniq=[...new Set(row.weeks)].sort((a,b)=>a-b);
  if(uniq.length!==row.weeks.length)throw Error('课程 weeks 重复');
  Object.assign(result,{weekday,startSection:first,endSection:last,weeks:uniq});
 }else if(kind==='assignments'){
  const status=limit(row.platformStatus,20,'platformStatus')||'unknown';
  if(!STATUS.has(status))throw Error('未知作业提交状态');
  result.platformStatus=status;
  if(!result.course)throw Error('作业所属课程缺失');
 }else if(kind==='grades'){
  const score=limit(row.score,40,'score');
  if(score)result.score=score;
  const credit=numberOrNull(row.credit,'credit',0,30);
  if(credit!==null)result.credit=credit;
  const gp=numberOrNull(row.gradePoint,'gradePoint',0,10);
  if(gp!==null)result.gradePoint=gp;
 }else if(kind==='exams'){
  if(!result.date)throw Error('个人考试日期缺失');
 }else if(kind==='second-class'){
  const credit=numberOrNull(row.credit,'credit',0,10000);
  if(credit!==null)result.credit=credit;
  const hours=numberOrNull(row.hours,'hours',0,10000);
  if(hours!==null)result.hours=hours;
  const points=numberOrNull(row.points,'points',0,100000);
  if(points!==null)result.points=points;
  const activityStatus=limit(row.activityStatus,20,'activityStatus')||'unknown';
  if(!ACTIVITY_STATUS.has(activityStatus))throw Error('未知二课活动状态');
  result.activityStatus=activityStatus;
 }
 return result;
}
function validateSnapshot(input,clock=Date.now()){
 if(!input||typeof input!=='object'||Array.isArray(input))throw Error('缺少快照对象');
 const permitted=new Set(['schema','source','kind','verifiedSchool','capturedAt','records','complete','verifiedEmpty']);
 for(const key of Object.keys(input))if(!permitted.has(key))throw Error('快照字段不受支持：'+key);
 if(input.schema!=='xiaoxu/academic-snapshot-v1')throw Error('快照协议版本不匹配');
 const {source,kind}=input;
 if(!KINDS.includes(kind)||KIND_SOURCES[kind]!==source)throw Error('平台和业务类型不匹配');
 if(input.verifiedSchool!=='ncwu')throw Error('未核验华水本人学校来源');
 const at=timestamp(input.capturedAt,'capturedAt',true);
 const age=clock-Date.parse(at);
 if(age< -5*60_000 || age>30*86400_000)throw Error('快照日期不可信或过期');
 if(input.complete!==true)throw Error('不完整快照不能写入已连接状态');
 if(!Array.isArray(input.records)||input.records.length>250)throw Error('快照数量超限');
 if(!input.records.length&&input.verifiedEmpty!==true)throw Error('空列表不能证明查询成功');
 const records=input.records.map(row=>normalizeRecord(kind,row));
 const seen=new Set();
 for(const row of records){const key=row.externalId+'|'+row.term;if(seen.has(key))throw Error('快照存在重复记录');seen.add(key);}
 return {schema:input.schema,source,kind,capturedAt:at,complete:true,records};
}
export {KIND_SOURCES,KINDS,validateSnapshot,normalizeRecord};
