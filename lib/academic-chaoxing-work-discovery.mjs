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
 if(/已批阅|已批閱|已评分|已評分|已评阅|已評閱|已批改|已打分|批阅完成|批改完成/.test(value))return 'graded';
 if(/已交|已提交|待批阅|待批閱|待评阅|待評閱|待批改|已完成|已作答|已互评|已互評/.test(value))return 'submitted';
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
/** Some legitimate course templates expose only workEnc. openc and stuenc
 * are optional request hints, not independent proof of course ownership.
 * The owner is verified separately before requesting the middle page.
 */
export function parseChaoxingWorkCredentials(html,{finalUrl}={}){
 if(!validHtml(html))fail('invalid_response');
 const $=load(html),get=id=>String($('input#'+id).first().attr('value')||'');
 let workEnc=get('workEnc');
 // The official stucoursemiddle redirect may carry the only work token.
 // Accept it only from the exact documented official student-course page;
 // do not parse arbitrary HTML links or redirects for a signed credential.
 if(!workEnc&&typeof finalUrl==='string'){
  let u;try{u=new URL(finalUrl);}catch{}
  if(u?.protocol==='https:'&&u.hostname==='mooc2-ans.chaoxing.com'&&
     u.pathname==='/mooc2-ans/mycourse/stu'&&u.username===''&&
     u.password===''&&u.searchParams.getAll('enc').length===1){
   const token=u.searchParams.get('enc');
   if(/^[a-f0-9]{32}$/i.test(token||''))workEnc=token;
  }
 }
 const fields={workEnc,openc:get('openc'),pageEnc:get('enc')};
 if(!fields.workEnc||Object.values(fields).some(x=>
   typeof x!=='string'||x.length>500||/[\r\n\u0000-\u001f<>"']/.test(x)))fail('work_not_available');
 return fields;
}
/** Follow a work-list entry published by this already verified course.
 * Query hints and timestamps belong to the official entry, not our clock.
 * Returned URL is session-local and must never be persisted or sent to clients.
 */
export function parseChaoxingCourseWorkEntry(html,course,{finalUrl}={}){
 if(!validHtml(html))fail('invalid_response');
 const $=load(html);
 const base=new URL(finalUrl||'https://mooc1.chaoxing.com/visit/stucoursemiddle');
 if(base.protocol!=='https:'||base.username||base.password||
    !['mooc1.chaoxing.com','mooc2-ans.chaoxing.com'].includes(base.hostname))
  fail('invalid_response');
 const nodes=$('a[data-url*="work/list"],iframe[src*="work/list"],a[href*="work/list"]');
 for(const node of nodes.toArray()){
  const el=$(node),raw=el.attr('data-url')||el.attr('src')||el.attr('href');
  let u;try{u=new URL(raw,base);}catch{fail('invalid_response');}
  if(u.protocol==='http:'&&u.hostname==='mooc1.chaoxing.com'&&!u.port)u.protocol='https:';
  if(u.protocol!=='https:'||u.hostname!=='mooc1.chaoxing.com'||u.port||
     u.username||u.password||u.hash||u.href.length>4000||
     !['/mooc2/work/list','/mooc-ans/mooc2/work/list'].includes(u.pathname))
   fail('invalid_response');
  const p=u.searchParams;
  for(const [key,value] of Object.entries({courseId:course.courseId,classId:course.classId,cpi:course.cpi})){
   if(!NUM.test(value||'')||p.getAll(key).length>1||
      (p.has(key)&&p.get(key)!==value))fail('authorization_failed');
   p.set(key,value);
  }
  // Validate all hidden identities too, before filling the entry's query.
  for(const [id,expected] of Object.entries({courseid:course.courseId,clazzid:course.classId,cpi:course.cpi})){
   const value=String($('input#'+id).first().attr('value')||'');
   if(value&&value!==expected)fail('authorization_failed');
  }
  const get=id=>String($('input#'+id).first().attr('value')||'');
  if(get('workEnc')){
   p.set('enc',get('workEnc'));p.set('ut','s');
   if(get('enc'))p.set('stuenc',get('enc'));
   if(get('t'))p.set('t',get('t'));
  }
  if(p.getAll('enc').length!==1||!p.get('enc')||
     [...p].some(([k,v])=>k.length>100||v.length>500||/[\r\n\u0000-\u001f<>"']/.test(k+v)))
   fail('invalid_response');
  return u.href;
 }
 return null;
}
export function safeChaoxingWorkUrl(raw,{courseId,classId}){
 if(typeof raw!=='string'||raw.length>2200)fail('invalid_response');
 // An official li[data] may encode the whole signed HTTPS URL. Decode once
 // before enforcing the same exact trusted origin, work path and source IDs.
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
// The official work page can use li[data] without an onclick handler, notably
// for completed work. Only signed work URLs with the selected course's IDs count.
// Pagination is owner-scoped and bounded; never persist a partial page as full.
export function parseChaoxingWorkList(html,course,{pageNumber=1,embedded=false}={}){
 if(!validHtml(html)||!course||!NUM.test(course.courseId||'')||
   !NUM.test(course.classId||'')||!Number.isSafeInteger(pageNumber)||
   pageNumber<1||pageNumber>10)fail('invalid_response');
 const $=load(html);
 // For a verified course-entry document, use only the course's own
 // homework panel. Unrelated li[data] controls must never block work parsing.
 const links=(embedded?$('.bottomList li[data]'):$('li[data]')).filter((_,el)=>{
  const li=$(el);
  return li.find('p.overHidden2').length>0||
   /goTask/.test(li.attr('onclick')||'');
 });
 // Do not flag a legitimate homework title mentioning 验证码.
 if(!links.length&&/没有此页面访问权限|访问受限|人机验证|验证码/.test($('body').text()))
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
 // Official #page control: only its numeric buttons are counted. Never
 // infer an additional page number from an arbitrary number in the body.
 let totalPages=1;
 const pager=$('#page').first();
 if(pager.length){
  const numbers=pager.find('li').toArray().filter(el=>
    !$(el).hasClass('xl-prevPage')&&!$(el).hasClass('xl-nextPage')
  ).map(el=>$(el).text().trim()).filter(s=>/^[1-9]\d{0,2}$/.test(s))
    .map(Number);
  if(numbers.length)totalPages=Math.max(...numbers);
  const active=pager.find('li.xl-active');
  if(active.length>1||(active.length===1&&active.first().text().trim()!==String(pageNumber)))
   fail('work_list_incomplete');
 }
 if(totalPages>10||totalPages<pageNumber)fail('work_list_incomplete');
 // The xuexitong-cli total is for the course, not necessarily this page.
 // The cross-page total must be verified after all pages were fetched.
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
  if(totalPages===1&&total!==items.length)fail('work_list_incomplete');
 }
 if(pageNumber>1&&!items.length)fail('work_list_incomplete');
 return {items,verifiedEmpty:pageNumber===1&&totalPages===1&&
    !items.length&&/暂无作业|没有作业/.test($('body').text()),
  progress,totalPages};
}
