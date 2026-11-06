/*
 * NCWU second-class activity listing: observed, read-only wire contract.
 *
 * Evidence: a user-authorized, private HAR on 2026-11-06 records seven
 * successful POST /sys/stu/activity calls, filtered by title; each returned
 * {data:[],startIndex:0,totalPage:0,totalSize:0}. The school's own activity
 * HTML lists other response fields but NO non-empty row was observed.
 *
 * Never copy raw HAR, school credentials, account IDs or private responses
 * into source control. This module does not make HTTP requests or establish
 * that the owner is authenticated.
 */
const READ_PATH='/sys/stu/activity';
const TITLES=Object.freeze(['报名中','待验证','即将开始报名']);
const ALLOWED_FILTERS=Object.freeze(['activity_name_like','hold_unit','activity_type']);
const fail=code=>{throw new Error('second_class_activity_source:'+code);};
const plain=obj=>obj!==null&&typeof obj==='object'&&!Array.isArray(obj);

/** Only the actual, observed official filter/field combination is emitted. */
export function buildObservedSecondClassActivityRead(title, filters={}) {
  if(!TITLES.includes(title)||!plain(filters))fail('unsupported_filter');
  if(Object.keys(filters).some(k=>!ALLOWED_FILTERS.includes(k)))fail('unsupported_filter');
  const form=new URLSearchParams({title});
  for(const name of ALLOWED_FILTERS) {
    if(!Object.hasOwn(filters,name))continue;
    const value=filters[name];
    if(typeof value!=='string'||value.length>120||
       /[\u0000-\u001F\u007F]/.test(value))fail('unsupported_filter');
    form.set(name,value);
  }
  return Object.freeze({
    method:'POST',path:READ_PATH,
    contentType:'application/x-www-form-urlencoded; charset=UTF-8',
    body:form.toString(),source:'ncwu_official_activity_page'
  });
}

/** HAR-verified empty response only. No invented activity entries or pagination.
 * A nonempty page is explicitly BLOCKED until a real, privacy-safe field mapping
 * and pagination/identity validation are independently checked.
 */
export function verifyObservedSecondClassActivityResponse(payload,title) {
  if(!TITLES.includes(title)||!plain(payload)||
     !Array.isArray(payload.data)||
     !Number.isSafeInteger(payload.startIndex)||
     !Number.isSafeInteger(payload.totalPage)||
     !Number.isSafeInteger(payload.totalSize)||
     payload.startIndex<0||payload.totalPage<0||payload.totalSize<0)
    fail('invalid_response');
  if(payload.data.length===0 &&
     payload.startIndex===0 &&
     payload.totalPage===0 &&
     payload.totalSize===0) {
    return Object.freeze({
      source:'ncwu_official_activity_page',
      path:READ_PATH,filter:title,
      status:'verified_empty_response',
      records:[],
      // This only proves that the official page answered this exact filter.
      // It does not prove all activities (or other filters) are empty.
      scope:'single_filter',nonemptyContractVerified:false
    });
  }
  // Today's observed response sample is empty: do not infer mandatory fields,
  // activity status, availability, page count or a student's signup eligibility.
  fail('nonempty_response_not_yet_verified');
}
