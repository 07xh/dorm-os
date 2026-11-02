/**
 * 校序学校课表数据边界：兼容拾光 qingguo_01.js (MIT) 课块输出。
 * This only normalizes an authorized parser output. It does NOT log in or
 * demonstrate that NCWU has been connected. Bell schedule must be verified.
 */
const SOURCE='shiguang/qingguo_01';
const asText=(value,max)=>typeof value==='string'?value.trim().slice(0,max):'';
const int=(v,min,max)=>Number.isInteger(v)&&v>=min&&v<=max;
function normalizeKingoCourseRows(rows,{semester}={}){
 if(!Array.isArray(rows)||rows.length===0||rows.length>350)
  throw new Error('青果课表课块数量异常；不能把空响应当成无课');
 const sem=asText(semester,40);
 if(!sem||sem.length<3)throw new Error('缺少已验证的学期标识');
 const result=[],seen=new Set();
 for(const raw of rows) {
  if(!raw||typeof raw!=='object'||Array.isArray(raw))throw new Error('课程结构异常');
  const title=asText(raw.name,120),teacher=asText(raw.teacher,90),
   location=asText(raw.position,130),weekday=raw.day,
   from=raw.startSection,to=raw.endSection;
  if(!title||!int(weekday,1,7)||!int(from,1,24)||!int(to,from,24)||
    !Array.isArray(raw.weeks)||raw.weeks.length===0||raw.weeks.length>35)
   throw new Error('课程字段/周次/节次不完整');
  const weeks=[...new Set(raw.weeks)];
  if(!weeks.every(w=>int(w,1,35)))throw new Error('课程周次无效');
  weeks.sort((a,b)=>a-b);
  const fingerprint=[sem,title,teacher,location,weekday,from,to,weeks.join(',')].join('\u001f');
  if(seen.has(fingerprint))throw new Error('相同课块重复；须先验证来源去重');
  seen.add(fingerprint);
  result.push({
   source:SOURCE,semester:sem,title,teacher,location,weekday,
   startSection:from,endSection:to,weeks,
   // A timetable block is not a verified official course ID.
   externalId:null,verifiedAt:null,
   // School bell times, week-one date and overrides require real NCWU evidence.
   startTime:null,endTime:null,calendarDates:null,syncState:'unverified'
  });
 }
 return result;
}
export {SOURCE,normalizeKingoCourseRows};
