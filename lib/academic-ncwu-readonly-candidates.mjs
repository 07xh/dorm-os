/* NCWU read-only business contracts, from the de-identified 2026-11-17
 * owner report. These are candidate readers: no browser input, no raw HTML,
 * no trusted records until the nonempty response schema is verified. */
import {load} from 'cheerio';

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
