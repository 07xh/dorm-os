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
 * Reuse the MIT xuexitong-cli detail-page evidence rule: only the explicit
 * "作答时间: MM-DD HH:mm 至 MM-DD HH:mm" visible in the selected official
 * homework detail can establish a date. The upstream omits the year, so
 * return raw month/day strings rather than inventing dueAt/overdue.
 * This is additional metadata of the EXISTING trusted detail read.
 */
export function parseChaoxingWorkTimeWindow(html){
 if(typeof html!=='string'||!html.trim()||html.length>MAX_HTML)
  return null;
 const $=load(html,{decodeEntities:true});
 const body=$('body').clone();
 body.find('script,style,noscript,iframe,object,embed,input,textarea,'+
  '[hidden],[type="hidden"],.answerBg,.answer,.analysis,.rightAnswer,'+
  '.correctAnswer,.correct').remove();
 body.find('br').replaceWith(' ');
 const text=body.text().replace(/\s+/gu,' ').trim();
 const windows=[...text.matchAll(/作答时间[:：]\s*(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{2})\s*至\s*(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{2})/gu)];
 // Multiple distinct windows mean an ambiguous page; never select one.
 if(windows.length!==1)return null;
 const values=windows[0].slice(1).map(Number);
 const [sm,sd,sh,smin,em,ed,eh,emin]=values;
 const valid=(m,d,h,min)=>m>=1&&m<=12&&h>=0&&h<=23&&
  min>=0&&min<=59&&d>=1&&
  d<=(m===2?29:([4,6,9,11].includes(m)?30:31));
 // Day-of-month is only a shape check; leap year is deliberately unknown.
 if(!valid(sm,sd,sh,smin)||!valid(em,ed,eh,emin))return null;
 const pad=v=>String(v).padStart(2,'0');
 return {startTimeRaw:pad(sm)+'-'+pad(sd)+' '+pad(sh)+':'+pad(smin),
  deadlineRaw:pad(em)+'-'+pad(ed)+' '+pad(eh)+':'+pad(emin)};
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
