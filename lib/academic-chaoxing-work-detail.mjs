/* Chaoxing homework HTML -> READ-ONLY question preview.
 * Pure parser, no network, session or writes. The server must verify the
 * logged-in owner and the assignment source before supplying the HTML.
 * Never return raw HTML, hidden answers, signed image URLs or credentials.
 */
import {load} from 'cheerio';

const MAX_HTML=450_000;
const MAX_QUESTIONS=120;
const trim=(value,max=1500)=>String(value??'')
 .replace(/[\u0000-\u0008\u000B-\u001F]/g,' ')
 .replace(/\s+/gu,' ').trim().slice(0,max);
export class ChaoxingWorkDetailError extends Error{
 constructor(code){super(code);this.name='ChaoxingWorkDetailError';this.code=code;}
}
const fail=code=>{throw new ChaoxingWorkDetailError(code);};
const TYPES=[
 [/多选题|多项选择/u,'multiple_choice'],
 [/单选题|单项选择/u,'single_choice'],
 [/判断题|对错题/u,'true_false'],
 [/填空题/u,'fill_blank'],
 [/简答题|论述题|问答题/u,'essay'],
 [/计算题/u,'calculation']
];
function typeOf(title){return TYPES.find(([pattern])=>pattern.test(title))?.[1]||'unknown';}
function visibleText($,element,max=1500){
 const clone=$(element).clone();
 clone.find('script,style,noscript,iframe,object,embed,input,textarea,button,[type="hidden"],.answerBg,.answer,.analysis,.rightAnswer,.correctAnswer,.correct').remove();
 clone.find('br').replaceWith(' ');
 clone.find('img').replaceWith(' [图片] ');
 return trim(clone.text(),max);
}
function imageCount($,element){
 return $(element).find('img').length;
}
/**
 * Only explicit visible 作答时间 metadata establishes a time window.
 * A yearless upstream value is display-only; never infer the current year.
 */
export function parseChaoxingWorkTimeWindow(html){
 if(typeof html!=='string'||!html.trim()||html.length>MAX_HTML)
  return null;
 const $=load(html,{decodeEntities:true});
 const body=$('body').clone();
 body.find('script,style,noscript,iframe,object,embed,input,textarea,'+
  '[hidden],[aria-hidden="true"],[type="hidden"],.answerBg,.answer,.analysis,.rightAnswer,'+
  '.correctAnswer,.correct,.questionLi').remove();
 body.find('[style]').filter((_,el)=>/display\s*:\s*none|visibility\s*:\s*hidden/i.test($(el).attr('style')||'')).remove();
 body.find('br').replaceWith(' ');
 const text=body.text().replace(/\s+/gu,' ').trim();
 // Reject conflicting labels even when one window is malformed.
 if((text.match(/作答时间[:：]/gu)||[]).length!==1)return null;
 const windows=[...text.matchAll(/作答时间[:：]\s*(?:(\d{4})-)?(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{2})\s*至\s*(?:(\d{4})-)?(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{2})(?!\d)/gu)];
 if(windows.length!==1)return null;
 const [sy,sm,sd,sh,smin,ey,em,ed,eh,emin]=windows[0].slice(1).map(v=>v===undefined?null:Number(v));
 if((sy===null)!==(ey===null))return null;
 const valid=(y,m,d,h,min)=>{
  if(y!==null&&(y<1900||y>2199))return false;
  const last=new Date(Date.UTC(y??2000,m,0)).getUTCDate();
  return m>=1&&m<=12&&h>=0&&h<=23&&min>=0&&min<=59&&d>=1&&d<=last;
 };
 if(!valid(sy,sm,sd,sh,smin)||!valid(ey,em,ed,eh,emin))return null;
 const pad=v=>String(v).padStart(2,'0');
 const format=(y,m,d,h,min)=>(y===null?'':String(y)+'-')+
  pad(m)+'-'+pad(d)+' '+pad(h)+':'+pad(min);
 const startTimeRaw=format(sy,sm,sd,sh,smin),deadlineRaw=format(ey,em,ed,eh,emin);
 if(sy!==null&&startTimeRaw>deadlineRaw)return null;
 return {startTimeRaw,deadlineRaw,...(ey===null?{}:{
  due:deadlineRaw.slice(0,10),dueAt:deadlineRaw.replace(' ','T')+':00+08:00'
 })};
}

/**
 * Returns only the requested page's question content.
 * Lack of HTML paging evidence must never be treated as a complete course list.
 */
export function parseChaoxingWorkDetailHtml(html){
 if(typeof html!=='string'||!html.trim()||html.length>MAX_HTML)
  fail('html_missing_or_oversized');
 const $=load(html,{decodeEntities:true});
 if($('form#submitForm').length!==1)fail('work_form_missing_or_ambiguous');
 const form=$('form#submitForm').first();
 const elements=form.find('.questionLi').toArray();
 if(!elements.length||elements.length>MAX_QUESTIONS)
  fail('questions_missing_or_oversized');
 let images=0;
 const questions=elements.map((element,index)=>{
  const block=$(element),heading=block.find('h3.mark_name').first();
  if(!heading.length)fail('question_stem_missing');
  const stem=visibleText($,heading,2000);
  if(!stem)fail('question_stem_missing');
  const items=block.find('.num_option,.num_option_dx').toArray();
  if(items.length>30)fail('options_oversized');
  const options=items.map(item=>visibleText($,item,1000)).filter(Boolean);
  // Question images can appear in stem, option, or body. Count only:
  // upstream media links may carry signatures and must not be persisted.
  const count=imageCount($,element);
  images+=count;
  return {number:index+1,type:typeOf(stem),stem,options,
   imageCount:count,hasImage:count>0};
 });
 return {schema:'xiaoxu/chaoxing-work-detail-v1',source:'chaoxing',
  readOnly:true,coverage:'current_html_page_only',
  questionCount:questions.length,imageCount:images,questions};
}
