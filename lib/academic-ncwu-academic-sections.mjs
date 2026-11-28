/** NCWU read-only academic queries, independently implemented from the
 * owner's de-identified November 2026 school interface contract.
 * Nonempty grades/exams are not promoted without verified field samples.
 * This module never logs in, submits evaluations, exposes HTML or writes D1.
 */
import {load} from 'cheerio';

const ROOT='https://jwmis.ncwu.edu.cn/hsjw/';
const HOME=ROOT+'frame/homes.action';
const GRADE_ENTRY=ROOT+'student/xscj.stuckcj.jsp';
const GRADE_DATA=ROOT+'student/xscj.stuckcj_data.jsp';
const EXAM_CONFIG=ROOT+'student/data/classPath.xml';
const EXAM_ROUNDS=ROOT+'STU_DynamicInitDataAction.do';
const EXAM_DATA=ROOT+'student/ksap.ksapb_date.jsp';
const clean=v=>String(v??'').replace(/\s+/g,' ').trim();
const fail=code=>{const e=new Error(code);e.code=code;throw e;};
const candidate=(kind,status,extra={})=>({kind,status,verified:false,complete:false,trustedSyncAllowed:false,...extra});
function parseTerm(term){
 const m=/^(20\d{2})-(20\d{2})-([12])$/.exec(term??'');
 if(!m||Number(m[2])!==Number(m[1])+1)fail('semester_unverified');
 return {year:m[1],next:m[2],index:String(Number(m[3])-1),label:m[3]==='1'?'第一学期':'第二学期'};
}
function document(html,xml=false){
 if(typeof html!=='string'||!html.trim()||html.length>650000||
    (xml&&/<!DOCTYPE|<!ENTITY/i.test(html)))fail('invalid_response');
 const $=load(html,xml?{xml:true}:{});
 if($('input[type=password], input[name=password], form[action*="/authserver/login"]').length)
  fail('authorization_failed');
 return $;
}
function formValue($,name){
 const values=$('input[name="'+name+'"],input[id="'+name+'"],select[name="'+name+'"],select[id="'+name+'"]')
  .toArray().map(el=>clean($(el).val())).filter(Boolean);
 if(!values.length||new Set(values).size!==1||!/^20\d{2}$/.test(values[0]))
  fail('grade_query_parameters_unverified');
 return values[0];
}
function exactResponse(response,url){
 if(response?.url!==url||typeof response?.html!=='string')fail('authorization_failed');
 return response.html;
}
export function createNcwuAcademicCandidateReader({request,principal}={}){
 if(typeof request!=='function'||!principal?.studentId)fail('authorization_failed');
 const term=parseTerm(principal.semester);
 const read=async(url,{format='html',params}={})=>{
  const headers={referer:HOME,accept:format==='xml'?'application/xml,text/xml':'text/html'};
  const options={method:params?'POST':'GET',headers,format};
  if(params){headers['content-type']='application/x-www-form-urlencoded; charset=UTF-8';
   options.body=new URLSearchParams(params).toString();}
  return exactResponse(await request(url,options),url);
 };
 return Object.freeze({
  async grades(){
   const $=document(await read(GRADE_ENTRY));
   // Use year parameters exposed by the actual school form, not the user's
   // current semester or a guessed enrollment year.
   const rxnj=formValue($,'rxnj'),nj=formValue($,'nj');
   const result=document(await read(GRADE_DATA,{params:{
    sjxz:'sjxz1',ysyx:'yscj',zx:'1',fx:'0',wz:'0',rxnj,nj
   }}));
   const text=clean(result.root().text());
   if(/未评教[^。！!]{0,100}不允许查看成绩|评教[^。！!]{0,100}后[方才]可查看成绩/.test(text))
    return candidate('grades','evaluation_required');
   // No nonempty real-school response contract exists yet. Even an empty
   // grade page must not delete previously verified records.
   return candidate('grades','schema_unverified');
  },
  async exams(){
   const config=document(await read(EXAM_CONFIG,{format:'xml'}),true);
   const nodes=config('examArrangeInfo');
   const classPath=clean(nodes.first().text());
   if(nodes.length!==1||classPath.length<32||classPath.length>1024||/\s/.test(classPath))
    fail('exam_round_config_unverified');
   const rounds=document(await read(EXAM_ROUNDS,{format:'xml',params:{classPath}}),true);
   const info=rounds('info');
   if(info.length!==1||info.children('xh').length!==1||
      clean(info.children('xh').text())!==principal.studentId)fail('authorization_failed');
   const items=info.children('item').toArray();
   if(items.length>100)fail('invalid_response');
   const current=[],seen=new Set();
   for(const item of items){
    const cell=name=>{const n=rounds(item).children(name);if(n.length!==1)fail('invalid_response');return clean(n.text());};
    const year=cell('xn'),index=cell('xq_m'),id=cell('kslcdm'),title=cell('kslcmc');
    if(!/^20\d{2}$/.test(year)||! /^[01]$/.test(index)||!/^\d{1,12}$/.test(id)||
       !title||title.length>100)fail('invalid_response');
    if(year!==term.year||index!==term.index)continue;
    if(seen.has(id))fail('invalid_response');
    seen.add(id);current.push({id,title});
   }
   if(!current.length)return candidate('exams','no_current_rounds',{roundCount:0});
   let empty=0;
   for(const round of current){
    const $=document(await read(EXAM_DATA,{params:{
     xnxq:term.year+term.index,kslc:round.id,xn:term.year,xq:term.index,title:round.title
    }}));
    const body=clean($.root().text());
    if(!body.includes('华北水利水电大学考试安排表')||
       !body.includes(term.year+'-'+term.next+'学年'+term.label))
     fail('exam_response_unverified');
    if(/没有检索到记录[!！]?/.test(body))empty++;
   }
   return candidate('exams',empty===current.length?'empty_candidate':'schema_unverified',
    {roundCount:current.length,emptyRoundCount:empty});
  }
 });
}
