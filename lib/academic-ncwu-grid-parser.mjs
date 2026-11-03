/**
 * Independent, UNVERIFIED grid read for Qingguo timetable HTML.
 * The 8-column detail list and the weekday grid are different presentations
 * of the same upstream page; agreement is NOT independent identity proof.
 *
 * Inspired by the grid parsing strategy in ShiGuangSchedule/shiguang_warehouse
 * (MIT): resources/TSU/tsu_01.js. We intentionally do not copy school-specific
 * bell times, synthesize locations, or infer weekdays from missing columns.
 */
import {load} from 'cheerio';
import {candidateSchedule} from './academic-provider-candidates.mjs';

const TERM=/^(\d{4})-(\d{4})-([1-3])$/;
const TIME=/^([\d,\uFF0C,\s\-—–~～至]+?)(?:\(([单双])\)|([单双]))?周\s*\[\s*(\d{1,2})\s*[-~～至]\s*(\d{1,2})\s*\]$/u;
const clean=v=>String(v??'').replace(/\s+/gu,' ').trim();
const sensible=(v,max)=>v.length>0&&v.length<=max&&!/[\u0000-\u001f]/u.test(v)&&
 !/^(待定|未安排|未知|无|不详|待通知)$/u.test(v);
const resultBase=semester=>({
 source:'ncwu.student.wsxk.weekday_grid',semester,
 verified:false,complete:false,trustedSyncAllowed:false,
 available:false,gridItems:0,parsedItems:0,blockers:[],candidate:null
});
function weeks(spec,parity){
 const found=new Set();
 const ranges=String(spec).replace(/[\s，]/gu,',').replace(/[—–～~至]/gu,'-')
  .split(',').filter(Boolean);
 if(!ranges.length||ranges.length>35)return null;
 for(const range of ranges){
  const m=/^(\d{1,2})(?:-(\d{1,2}))?$/.exec(range);
  if(!m)return null;
  const from=Number(m[1]),to=Number(m[2]??m[1]);
  if(from<1||to>35||from>to)return null;
  for(let n=from;n<=to;n++)if(!parity||(parity==='单'?n%2===1:n%2===0))found.add(n);
 }
 return found.size?[...found].sort((a,b)=>a-b):null;
}
function linesFromDiv($,div){
 const part=$(div).clone();
 part.find('script,style').remove();
 part.find('br').replaceWith('\n');
 return part.text().split(/[\r\n]+/u).map(clean).filter(Boolean);
}
function parseGridBlock(lines,day){
 // In this Qingguo grid shape the four explicit lines are
 // course, teacher, week/sections, and location. Never guess a missing line.
 if(lines.length!==4||!sensible(lines[0],120)||
  !sensible(lines[1],90)||!sensible(lines[3],130))
  return {code:'incomplete_grid_block'};
 const time=TIME.exec(lines[2]);
 if(!time)return {code:'unsupported_grid_time'};
 const first=Number(time[4]),last=Number(time[5]);
 if(first<1||last>24||first>last)return {code:'invalid_grid_sections'};
 const weekList=weeks(time[1],time[2]||time[3]);
 if(!weekList)return {code:'invalid_grid_weeks'};
 return {item:{
  name:lines[0],teacher:lines[1],position:lines[3],
  day,startSection:first,endSection:last,weeks:weekList
 }};
}
/**
 * Only 7 explicit weekday cells per row are accepted. When colspan or
 * rowspan makes day numbering ambiguous we block the whole row.
 * The returned candidate is never a trusted official snapshot.
 */
export function parseNcwuTimetableGridHtml(html,{semester}={}){
 const term=TERM.exec(semester||'');
 if(typeof html!=='string'||html.length>500_000||!term||
  Number(term[2])!==Number(term[1])+1)throw Error('invalid_grid_input');
 const $=load(html),base=resultBase(semester);
 const tables=$('table').toArray().filter(table=>$(table).find('td.td').length>0);
 if(tables.length!==1)return {...base,blockers:tables.length>1?[{code:'ambiguous_grid_tables'}]:[]};
 const rows=$(tables[0]).find('tr').toArray()
  .filter(row=>$(row).children('td.td').length>0);
 if(!rows.length||rows.length>80)return {...base,blockers:[{code:'invalid_grid_rows'}]};
 const items=[],blockers=[];
 let seen=0;
 rows.forEach((row,i)=>{
  const cells=$(row).children('td.td').toArray();
  if(cells.length!==7){
   blockers.push({row:i+1,code:'ambiguous_grid_columns'});
   return;
  }
  cells.forEach((cell,dayIndex)=>{
   const blocks=$(cell).find('div').toArray().filter(div=>
    /padding-bottom\s*:\s*5px/i.test($(div).attr('style')||''));
   blocks.forEach(div=>{
    seen++;
    if(seen>350){blockers.push({row:i+1,code:'grid_limit_exceeded'});return;}
    const result=parseGridBlock(linesFromDiv($,div),dayIndex+1);
    if(result.code)blockers.push({row:i+1,code:result.code});
    else items.push(result.item);
   });
  });
 });
 if(!seen)return {...base,available:true,blockers:[{code:'grid_blocks_missing'}]};
 let candidate=null;
 if(items.length){
  try{candidate=candidateSchedule(items,semester);}
  catch{blockers.push({code:'invalid_grid_candidate'});candidate=null;}
 }
 return {
  ...base,available:true,gridItems:seen,parsedItems:candidate?items.length:0,
  blockers,candidate
 };
}
