/**
 * NCWU's independently observed personal schedule AJAX grid (2026-11-17).
 * Pure HTML contract inspection only. Data is never marked trusted or synced.
 * A first-party, owner-verified session is required by the caller.
 */
import {load} from 'cheerio';

const WEEKDAYS=Object.freeze(['星期一','星期二','星期三','星期四','星期五','星期六','星期日']);
const SECTION=/^(\d{1,2})\s*[（(]\s*(\d{2}:\d{2})\s*[-–—]\s*(\d{2}:\d{2})\s*[)）]$/u;
const TERM=/^(20\d{2})-(20\d{2})-([12])$/;
const clean=s=>String(s??'').replace(/\s+/gu,' ').trim();

export class NcwuLessonDetailError extends Error{
 constructor(code){super(code);this.name='NcwuLessonDetailError';this.code=code;}
}
const fail=code=>{throw new NcwuLessonDetailError(code);};

/**
 * Inspect the original 12-section by seven-weekday grid, not the older
 * /student/wsxk.xskcb10319.jsp page. Preserve cells verbatim as candidates:
 * the supplied evidence does not prove a machine-readable course identifier,
 * week-parity grammar or reliable course/teacher segmentation.
 */
export function parseNcwuLessonDetailHtml(html,{semester}={}){
 const term=TERM.exec(semester||'');
 if(!term||Number(term[2])!==Number(term[1])+1)fail('invalid_semester');
 if(typeof html!=='string'||!html.trim()||html.length>650000)
  fail('invalid_detail_html');
 if(/凭证已失效|登录已失效|请先登录|<input[^>]+type\s*=\s*["']?password/i.test(html))
  fail('session_expired');
 const $=load(html);
 const candidateTables=[];
 for(const table of $('table').toArray()){
  const rows=$(table).find('tr').toArray();
  const matches=rows.filter(row=>{
   const words=$(row).children('td,th').toArray().map(c=>clean($(c).text()).replace(/\s/g,''));
   return WEEKDAYS.every((day,i)=>words.includes(day)&&words.indexOf(day)<words.indexOf(WEEKDAYS[i+1]??day)+1);
  });
  if(matches.length===1)candidateTables.push({rows,header:matches[0]});
 }
 if(candidateTables.length!==1)fail('schedule_grid_missing_or_ambiguous');
 const {rows,header}=candidateTables[0];
 const details=[],sectionTimes=[],seen=new Set();
 let afterHeader=false;
 for(const row of rows){
  if(row===header){afterHeader=true;continue;}
  if(!afterHeader)continue;
  const cells=$(row).children('td,th').toArray();
  const positions=cells.map((cell,i)=>({i,m:SECTION.exec(clean($(cell).text()))}))
   .filter(x=>x.m);
  if(!positions.length)continue;
  if(positions.length!==1)fail('ambiguous_section_row');
  const {i,m}=positions[0],section=Number(m[1]);
  if(section<1||section>12||seen.has(section)||cells.length-i-1!==7)
   fail('unexpected_schedule_grid');
  seen.add(section);
  sectionTimes.push({section,startTime:m[2],endTime:m[3]});
  for(let weekday=1;weekday<=7;weekday++){
   const cell=cells[i+weekday];
   if($(cell).attr('rowspan')||$(cell).attr('colspan'))fail('merged_schedule_cell');
   const text=clean($(cell).text());
   if(text.length>2200)fail('oversized_course_cell');
   if(text)details.push({weekday,section,text});
  }
 }
 if(seen.size!==12||Array.from({length:12},(_,i)=>i+1).some(s=>!seen.has(s)))
  fail('incomplete_schedule_grid');
 sectionTimes.sort((a,b)=>a.section-b.section);
 return {
  source:'ncwu.frame.desk.showLessonScheduleDetail.action',
  semester,verified:false,complete:false,trustedSyncAllowed:false,
  sections:sectionTimes,nonEmptyCells:details.length,cells:details
 };
}
