/* NCWU read-only business contracts, from the de-identified 2026-11-17
 * owner report. These are candidate readers: no browser input, no raw HTML,
 * no trusted records until the nonempty response schema is verified. */
import {load} from 'cheerio';
import {normalizeRecord} from './academic-contract.mjs';

const reject=()=>{throw new Error('ncwu_candidate_invalid_response');};
const bounded=raw=>{
 if(typeof raw!=='string'||!raw.trim()||raw.length>650000)reject();
 return raw;
};
const clean=value=>String(value??'').replace(/\s+/g,' ').trim();
function termParts(term){
 const match=/^(20\d{2})-(20\d{2})-([12])$/.exec(term||'');
 if(!match||Number(match[2])!==Number(match[1])+1)reject();
 return {year:match[1],index:String(Number(match[3])-1),
  label:match[1]+'-'+match[2]+'学年'+(match[3]==='1'?'第一':'第二')+'学期'};
}
function xml(raw){
 bounded(raw);
 if(/<!DOCTYPE|<!ENTITY/i.test(raw))reject();
 return load(raw,{xml:true});
}
function visible(raw){
 const $=load(bounded(raw));
 $('script,style,template,noscript').remove();
 const text=clean($('body').text());
 if($('input[type="password"],input[name="password"]').length||
    /凭证已失效|请重新登录|非法导航|受限制或无效页面|权限不足/.test(text))reject();
 return {text,$};
}
export function readExamClassPath(raw){
 const $=xml(raw),nodes=$('examArrangeInfo');
 if(nodes.length!==1||nodes.first().children().length)reject();
 const value=clean(nodes.first().text());
 // Opaque official runtime value; never guess, log, or persist it.
 if(value.length<32||value.length>512||/\s/.test(value))reject();
 return value;
}
export function parseExamRounds(raw,term){
 const {year,index}=termParts(term),$=xml(raw);
 if($.root().children().length!==1||$('info').length!==1||
    $('info > xh').length!==1||!clean($('info > xh').text()))reject();
 const items=$('info > item').toArray();
 if(items.length>150)reject();
 const seen=new Set(),rounds=[];
 for(const row of items){
  const field=name=>{
   const nodes=$(row).children(name);
   if(nodes.length!==1||nodes.first().children().length)reject();
   return clean(nodes.first().text());
  };
  const xn=field('xn'),xq=field('xq_m'),id=field('kslcdm'),title=field('kslcmc');
  if(!/^20\d{2}$/.test(xn)||! /^[01]$/.test(xq)||
     !/^\d{1,12}$/.test(id)||!title||title.length>100)reject();
  if(xn!==year||xq!==index)continue;
  if(seen.has(id)||rounds.length>=12)reject();
  seen.add(id);rounds.push({id,title});
 }
 return rounds;
}
export function examRoundBody(term,round){
 const {year,index}=termParts(term);
 if(!/^\d{1,12}$/.test(round?.id||'')||
    typeof round?.title!=='string'||!round.title.trim()||round.title.length>100)reject();
 return new URLSearchParams({xnxq:year+index,kslc:round.id,xn:year,xq:index,title:round.title}).toString();
}
export function classifyExamCandidate(raw,term){
 const {label}=termParts(term),{text,$}=visible(raw);
 const compact=text.replace(/\s+/g,'');
 if(!compact.includes('华北水利水电大学考试安排表')||
    !compact.includes(label))return 'unrecognized';
 // An empty report is still only a candidate; do not claim a verified
 // empty semester based on one exam round or an ambiguous mixed table.
 if(compact.includes('没有检索到记录')&&
    !$('tr').toArray().some(row=>$(row).children('td').length>1))
  return 'reported_empty';
 return 'schema_review_required';
}
export function readEnrollmentYearCandidate(raw){
 const {text}=visible(raw);
 const matches=[...text.matchAll(/年级\s*[:：]\s*(20\d{2})(?!\d)/g)];
 if(matches.length!==1)reject();
 return matches[0][1];
}
export function gradeQueryBody(year){
 if(!/^20\d{2}$/.test(year||''))reject();
 return new URLSearchParams({sjxz:'sjxz1',ysyx:'yscj',zx:'1',fx:'0',wz:'0',rxnj:year,nj:year}).toString();
}
export function classifyGradeCandidate(raw){
 const {text}=visible(raw);
 return /你还有未评教的课程\s*[,，]?\s*不允许查看成绩/.test(text)
  ?'evaluation_required':'schema_review_required';
}

// Provisional header-based candidates ONLY. The observed school responses
// contain an evaluation denial or an empty round, not a nonempty detail table.
// These aliases are a conservative proposed grammar, not a verified schema.
const aliases={
 grades:{term:['学年学期'],code:['课程代码','课程编号'],title:['课程名称','课程'],score:['成绩'],credit:['学分'],gradePoint:['绩点']},
 exams:{code:['课程代码','课程编号'],title:['课程名称','考试科目','课程'],date:['考试日期'],startTime:['开始时间'],endTime:['结束时间'],location:['考场','考试地点']}
};
const required={grades:['term','code','title','score'],exams:['code','title','date']};
const optionalNumber=(raw,max)=>{
 if(raw==='')return undefined;
 if(!/^\d+(?:\.\d+)?$/.test(raw)||Number(raw)>max)reject();
 return Number(raw);
};
const optionalText=(raw)=>raw||undefined;
function parseGradeTerm(raw){
 const m=/^(20\d{2})[-—–](20\d{2})(?:学年)?(?:[- ]?([12])|第([一二])学期)$/.exec(raw);
 if(!m||Number(m[2])!==Number(m[1])+1)reject();
 return m[1]+'-'+m[2]+'-'+(m[3]||(m[4]==='一'?'1':'2'));
}
function parseDate(raw){
 const m=/^(20\d{2})[-/年](\d{1,2})[-/月](\d{1,2})日?$/.exec(raw);
 if(!m)reject();
 return m[1]+'-'+m[2].padStart(2,'0')+'-'+m[3].padStart(2,'0');
}
/** No raw HTML, student IDs or credentials leave this helper. Normalized
 * records remain internal candidates; the caller must not publish them or
 * write trusted batches until a real nonempty school fixture is verified. */
export function parseNcwuBusinessTableCandidate(raw,{kind,term}={}){
 if(!Object.hasOwn(aliases,kind))reject();
 const {text,$}=visible(raw);
 const result=(status,records=[])=>({kind,status,records,verified:false,complete:false,trustedSyncAllowed:false});
 if(kind==='grades'&&/你还有未评教的课程\s*[,，]?\s*不允许查看成绩/.test(text))
  return result('evaluation_required');
 if(kind==='exams'){
  const {label}=termParts(term),compact=text.replace(/\s+/g,'');
  if(!compact.includes('华北水利水电大学考试安排表')||!compact.includes(label))
   return result('unrecognized');
  if(classifyExamCandidate(raw,term)==='reported_empty')return result('reported_empty');
 }
 const signature=aliases[kind],matches=[];
 for(const table of $('table').toArray()){
  const rows=$(table).find('tr').toArray().filter(row=>$(row).closest('table').get(0)===table);
  if(!rows.length)continue;
  const headers=$(rows[0]).children('th,td').toArray();
  const names=headers.map(el=>clean($(el).text()));
  const fields=names.map(name=>Object.keys(signature).find(key=>signature[key].includes(name))||null);
  if(!required[kind].every(key=>fields.includes(key)))continue;
  if(fields.filter(Boolean).length!==new Set(fields.filter(Boolean)).size||
     headers.some(el=>Number($(el).attr('colspan')||1)!==1||Number($(el).attr('rowspan')||1)!==1))
   return result('schema_review_required');
  matches.push({rows:rows.slice(1),fields});
 }
 if(matches.length!==1)return result('schema_review_required');
 const {rows,fields}=matches[0];
 if(!rows.length||rows.length>250)return result('schema_review_required');
 const records=[],seen=new Set();
 try{
  for(const row of rows){
   const cells=$(row).children('td,th').toArray();
   if(cells.length!==fields.length||cells.some(el=>$(el).is('th')||
      Number($(el).attr('colspan')||1)!==1||Number($(el).attr('rowspan')||1)!==1||
      $(el).find('table,input,select,textarea').length))reject();
   const values=Object.fromEntries(fields.flatMap((key,i)=>key?[[key,clean($(cells[i]).text())]]:[]));
   if(!values.code||values.code.length>90||!values.title)reject();
   const parsedTerm=kind==='grades'?parseGradeTerm(values.term):term;
   const record=kind==='grades'?{term:parsedTerm,title:values.title,score:values.score,
    credit:optionalNumber(values.credit??'',30),gradePoint:optionalNumber(values.gradePoint??'',10)}:
    {term:parsedTerm,title:values.title,date:parseDate(values.date),
     startTime:optionalText(values.startTime),endTime:optionalText(values.endTime),location:optionalText(values.location)};
   if(kind==='grades'&&!record.score)reject();
   if(record.startTime&&record.endTime&&record.startTime>record.endTime)reject();
   // Provisional key, not an official immutable identifier. Retakes or
   // duplicate rows are intentionally blocked rather than silently merged.
   const key=JSON.stringify([parsedTerm,values.code,kind==='exams'?record.date:'',record.startTime??'']);
   if(seen.has(key))reject();
   seen.add(key);
   records.push(normalizeRecord(kind,{externalId:'candidate-'+(records.length+1),...record}));
  }
 }catch{return result('schema_review_required');}
 return result('syntactically_parsed',records);
}
