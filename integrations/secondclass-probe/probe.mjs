/*
 * Independent device-side protocol probe. Uses only the current official
 * page's own session. Never reads Cookie, password inputs or browser storage.
 * No app/cloud ingestion: the result proves at most one score response.
 */
export async function run({
 origin=globalThis.location?.origin,
 expectedStudentCode,
 fetcher=globalThis.fetch
}={}){
 const host='https://dekt.ncwu.edu.cn';
 const path='/sys/stu/score/list';
 const base={schema:'xiaoxu/second-class-device-probe-v1',
  transport:'browser_same_origin',scope:'single_score_sample'};
 let timer;
 const fail=(stage,reason)=>{clearTimeout(timer);return {...base,ok:false,stage,reason};};
 if(origin!==host)return fail('context','open_official_business_page');
 if(typeof expectedStudentCode!=='string'||
    !/^[A-Za-z0-9_-]{1,50}$/.test(expectedStudentCode.trim()))
  return fail('identity_input','local_account_required');
 const expected=expectedStudentCode.trim();
 let response;
 const controller=new AbortController();
 timer=setTimeout(()=>controller.abort(),15000);
 try{
  response=await fetcher(host+path,{
   method:'POST',credentials:'same-origin',redirect:'error',cache:'no-store',
   headers:{accept:'application/json',
    'content-type':'application/x-www-form-urlencoded; charset=UTF-8',
    'x-requested-with':'XMLHttpRequest'},body:'',signal:controller.signal
  });
 }catch{return fail('score_request','network_redirect_or_timeout');}
 if(response.status!==200)return fail('score_http',
  response.status===401||response.status===403?'authentication_required':
  response.status===429?'rate_limited':
  response.status>=500?'school_server_error':'unexpected_status');
 if(response.redirected||response.url!==host+path)
  return fail('score_response','unexpected_response_location');
 let raw;
 try{
  const text=await response.text();
  if(text.length>300000)return fail('score_response','response_too_large');
  raw=JSON.parse(text);
 }catch{return fail('score_response','not_business_json');}
 if(!raw||!Array.isArray(raw.data)||raw.data.length===0||raw.data.length>50)
  return fail('score_response','missing_or_unsupported_score_rows');
 if(raw.data.some(row=>!row||typeof row!=='object'||
    typeof row.student_code!=='string'||row.student_code!==expected))
  return fail('identity','account_mismatch_or_missing_identity');
 const validNumber=n=>typeof n==='number'&&Number.isFinite(n)&&n>=0&&n<=100000;
 const row=raw.data.find(r=>r.school_term_index===100)||raw.data[0];
 if(!Number.isInteger(row.school_term_index)||row.school_term_index<1||
    row.school_term_index>100||!validNumber(row.score_value)||
    (row.score_plus!==null&&row.score_plus!==undefined&&!validNumber(row.score_plus)))
  return fail('score_record','unsupported_score_fields');
 clearTimeout(timer);
 return {...base,ok:true,stage:'sample_verified',
  checkedAt:new Date().toISOString(),sourceHost:'dekt.ncwu.edu.cn',
  identity:{matchesLocalInput:true},
  score:{school_term_index:row.school_term_index,score_value:row.score_value,
   ...(typeof row.score_plus==='number'?{score_plus:row.score_plus}:{})},
  sampleIsSchoolAggregate:row.school_term_index===100,
  returnedRowCount:raw.data.length,
  fullSyncVerified:false,nativeSessionHandoffVerified:false};
}
