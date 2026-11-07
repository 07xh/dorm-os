/* School-owned Chaoxing course/work discovery, based on MIT xuexitong-cli.
 * Only a trusted server-side session may request pages. This module never
 * accepts arbitrary URLs from clients and never returns signed links to users.
 */
import {load} from 'cheerio';

const NUM=/^\d{1,24}$/;
const MAX=420000;
const clean=(s,max=120)=>String(s??'').replace(/[\u0000-\u001f\u007f]/g,' ').replace(/\s+/g,' ').trim().slice(0,max);
const fail=code=>{const e=new Error(code);e.code=code;throw e;};
const validHtml=html=>typeof html==='string'&&html.length>0&&html.length<=MAX;
// Official list status is separate from a user's local "marked done" flag.
// Unknown wording must remain unknown, never guessed from a notification.
export function classifyChaoxingWorkStatus(raw){
 const value=clean(raw,60);
 if(!value)return 'unknown';
 if(/退回|重做|重新提交|已驳回/.test(value))return 'returned';
 if(/已批阅|已评分|已评阅|已打分|已批改|批阅完成|批改完成/.test(value))return 'graded';
 if(/已交|已提交|待批阅|待评阅|已完成|已作答/.test(value))return 'submitted';
 if(/未交|未提交|待提交|未完成|待完成|未作答|未交卷/.test(value))return 'todo';
 return 'unknown';
}
export function parseChaoxingCourseList(html){
 if(!validHtml(html))fail('invalid_response');
 const courses=[],seen=new Set();
 // The official course URL can reorder its query fields or HTML-escape &.
 // Extract only the three official numeric identifiers; do not guess IDs
 // from course titles, and never treat a signed URL as a persistent ID.
 for(const m of html.matchAll(/stucoursemiddle\?([^"'<>\s]{1,1000})/gi)){
  const params=new URLSearchParams(m[1].replace(/&amp;/gi,'&'));
  const ids=['courseid','clazzid','cpi'].map(k=>params.getAll(k));
  if(ids.some(v=>v.length!==1||!NUM.test(v[0])))continue;
  const [courseId,classId,cpi]=ids.map(v=>v[0]);
  const key=[courseId,classId,cpi].join(':');
  if(seen.has(key))continue;seen.add(key);
  if(courses.length>=120)fail('invalid_response');
  // The course title follows the course link in the official courselistdata HTML.
  const near=html.slice(m.index+m[0].length,m.index+m[0].length+2500);
  const $=load(near);
  const title=clean($('.course-name[title]').first().attr('title')||
    $('.course-name').first().text(),100);
  if(!title)fail('invalid_response');
  courses.push({courseId,classId,cpi,title});
 }
 if(!courses.length)fail('courses_unverified');
 return courses;
}
export function parseChaoxingWorkCredentials(html){
 if(!validHtml(html))fail('invalid_response');
 const $=load(html),get=id=>String($('input#'+id).first().attr('value')||'');
 const fields={workEnc:get('workEnc'),openc:get('openc'),pageEnc:get('enc')};
 if(!fields.workEnc||!fields.pageEnc||Object.values(fields).some(x=>
   x.length>500||/[\r\n\u0000-\u001f<>"']/.test(x)))fail('work_not_available');
 return fields;
}
export function safeChaoxingWorkUrl(raw,{courseId,classId}){
 if(typeof raw!=='string'||raw.length>2200)fail('invalid_response');
 // xuexitong-cli unquotes the official li[data] value before opening the work.
 // Some legitimate pages encode the entire signed URL, not only query params.
 // Decode once only for an encoded HTTPS scheme, then retain the same strict
 // host/path/official source ID validation as direct URLs.
 let candidate=raw;
 if(/^https%3a%2f%2f/i.test(raw)){
  try{candidate=decodeURIComponent(raw);}catch{fail('invalid_response');}
 }
 if(/[\u0000-\u001f\u007f]/.test(candidate))fail('invalid_response');
 let u;
 try{u=new URL(candidate,'https://mooc1.chaoxing.com');}catch{fail('invalid_response');}
 if(u.protocol!=='https:'||u.hostname!=='mooc1.chaoxing.com'||
   u.username||u.password||u.hash||
   !['/mooc-ans/mooc2/work/task','/mooc-ans/mooc2/work/dowork',
     '/work/dowork'].includes(u.pathname))fail('invalid_response');
 const p=u.searchParams,workId=p.get('workId')||p.get('workid');
 if(!NUM.test(workId||'')||
    !NUM.test(p.get('courseId')||'')||!NUM.test(p.get('classId')||'')||
    p.get('courseId')!==courseId||p.get('classId')!==classId)fail('invalid_response');
 return {workId,url:u.href};
}
export function parseChaoxingWorkList(html,course){
 if(!validHtml(html)||!course||!NUM.test(course.courseId||'')||
   !NUM.test(course.classId||''))fail('invalid_response');
 const $=load(html),links=$('li[onclick*="goTask"]');
 // Do not treat a valid homework title containing '验证码' as a challenge page.
 if(!links.length&&/没有此页面访问权限|访问受限|人机验证|验证码/.test(html))
  fail('interactive_login_required');
 if(links.length>200)fail('invalid_response');
 const seen=new Set(),items=[];
 links.each((_,el)=>{
  const li=$(el),link=safeChaoxingWorkUrl(li.attr('data'),course);
  if(seen.has(link.workId))fail('invalid_response');seen.add(link.workId);
  const title=clean(li.find('p.overHidden2.fl').first().text()||
    li.find('p.overHidden2').first().text(),100);
  const status=clean(li.find('p.status.fl').first().text()||
    li.find('p.status').first().text(),60);
  if(!title)fail('invalid_response');
  items.push({workId:link.workId,title,status,url:link.url});
 });
 // Reuse the xuexitong-cli (MIT) work-page progress marker. If the source
 // explicitly declares a work total, it must equal the entries extracted
 // from the SAME signed official page before any owner-scoped D1 sync.
 const candidates=[...html.matchAll(/<span\b[^>]*>\s*(\d{1,3})\s*\/\s*(\d{1,3})\s*<\/span>\s*<\/div>\s*<\/div>/gi)];
 const distinct=new Set(candidates.map(m=>m[1]+'/'+m[2]));
 if(distinct.size>1)fail('work_list_incomplete');
 const pair=candidates[0];
 let progress=null;
 if(pair){
  const completed=Number(pair[1]),total=Number(pair[2]);
  if(!Number.isSafeInteger(completed)||!Number.isSafeInteger(total)||
     completed<0||total<0||completed>total||total>200)fail('invalid_response');
  progress={completed,total};
  if(total!==items.length)fail('work_list_incomplete');
 }
 // Missing progress is not proof of all-course coverage; the sync remains
 // explicitly partial. A missing list also requires an official empty label.
 return {items,verifiedEmpty:!items.length&&/暂无作业|没有作业/.test($('body').text()),
  progress};
}
