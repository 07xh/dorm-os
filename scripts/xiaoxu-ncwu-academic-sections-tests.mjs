import assert from 'node:assert/strict';
import {createNcwuAcademicCandidateReader} from '../lib/academic-ncwu-academic-sections.mjs';

const principal={studentId:'SYNTHETIC_01',semester:'2026-2027-1'};
const rounds='<info><xh>SYNTHETIC_01</xh><item><xn>2026</xn><xq_m>0</xq_m><kslcdm>2</kslcdm><kslcmc>第9周</kslcmc></item><item><xn>2025</xn><xq_m>1</xq_m><kslcdm>6</kslcdm><kslcmc>历史轮次</kslcmc></item></info>';
const heading='华北水利水电大学考试安排表 / 2026-2027学年第一学期';
function fixture({grade='你还有未评教的课程，不允许查看成绩！',roundXml=rounds,
 exam=heading+' / 没有检索到记录!',missingYear=false,redirect=false}={}){
 const calls=[];
 const request=async(url,opts)=>{
  assert.equal(new URL(url).origin,'https://jwmis.ncwu.edu.cn');
  assert.equal(opts.headers.referer,'https://jwmis.ncwu.edu.cn/hsjw/frame/homes.action');
  calls.push({url,opts});
  const path=new URL(url).pathname;
  let html;
  if(path.endsWith('/xscj.stuckcj.jsp'))html=missingYear?'<html>没有查询条件</html>':
   '<input name="rxnj" value="2026"><input name="nj" value="2026">';
  else if(path.endsWith('/xscj.stuckcj_data.jsp')){
   assert.equal(opts.method,'POST');
   assert.deepEqual(Object.fromEntries(new URLSearchParams(opts.body)),{
    sjxz:'sjxz1',ysyx:'yscj',zx:'1',fx:'0',wz:'0',rxnj:'2026',nj:'2026'});
   html='<html>'+grade+'</html>';
  }else if(path.endsWith('/classPath.xml')){
   assert.equal(opts.format,'xml');
   html='<config><examArrangeInfo>'+('a'.repeat(240))+'</examArrangeInfo></config>';
  }else if(path.endsWith('/STU_DynamicInitDataAction.do')){
   assert.equal(opts.format,'xml');assert.equal(new URLSearchParams(opts.body).get('classPath'),'a'.repeat(240));
   html=roundXml;
  }else if(path.endsWith('/ksap.ksapb_date.jsp')){
   assert.deepEqual(Object.fromEntries(new URLSearchParams(opts.body)),{
    xnxq:'20260',kslc:'2',xn:'2026',xq:'0',title:'第9周'});
   html='<html>'+exam+'</html>';
  }else assert.fail('unexpected path');
  return {url:redirect?'https://authserver.ncwu.edu.cn/authserver/login':url,html};
 };
 return {calls,reader:createNcwuAcademicCandidateReader({request,principal})};
}
const a=fixture();
assert.deepEqual(await a.reader.grades(),{kind:'grades',status:'evaluation_required',
 verified:false,complete:false,trustedSyncAllowed:false});
assert.deepEqual(await a.reader.exams(),{kind:'exams',status:'empty_candidate',
 verified:false,complete:false,trustedSyncAllowed:false,roundCount:1,emptyRoundCount:1});
assert.equal(a.calls.length,5);
const b=fixture({missingYear:true});
await assert.rejects(b.reader.grades(),{code:'grade_query_parameters_unverified'});
assert.equal(b.calls.length,1);
await assert.rejects(fixture({redirect:true}).reader.grades(),{code:'authorization_failed'});
await assert.rejects(fixture({roundXml:rounds.replace('SYNTHETIC_01','OTHER')}).reader.exams(),{code:'authorization_failed'});
await assert.rejects(fixture({exam:'unrelated page'}).reader.exams(),{code:'exam_response_unverified'});
assert.equal((await fixture({exam:heading+'<table><tr><td>synthetic course</td></tr></table>'}).reader.exams()).status,'schema_unverified');
assert.equal((await fixture({grade:'synthetic response'}).reader.grades()).status,'schema_unverified');
console.log('NCWU academic read-only candidates: explicit evaluation gate, current-term round query, owner proof, strict response and no promotion');
