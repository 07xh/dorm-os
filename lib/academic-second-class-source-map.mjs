/* Convert a *confirmed* NCWU second-class read-only JSON shape into
 * the existing trusted adapter contract. No URL guessing, credential storage,
 * login automation or school state-changing requests.
 * Mapping is inert until an owner-authorized upstream adapter is provided.
 */
import {normalizeRecord} from './academic-contract.mjs';

export class SecondClassMapError extends Error {
 constructor(code){super(code);this.name='SecondClassMapError';this.code=code;}
}
const fail=code=>{throw new SecondClassMapError(code);};
const RECORD_FIELDS=new Set([
 'activityId','title','term','date','credit','hours','points','activityStatus','reviewStatus'
]);
const STATUS_FIELDS={
 activityStatus:new Set(['registered','completed','cancelled','unknown']),
 reviewStatus:new Set(['pending','approved','rejected','attended','unknown'])
};
const PROHIBITED=/^(?:__proto__|prototype|constructor)$/i;
const SENSITIVE=/(?:passw(?:or)?d|cookie|token|secret|csrf|authorization|session|student.?id|id.?card|mobile|phone|email|identity)/i;
const SAFE_CURSOR=/^[A-Za-z0-9_:.=-]{1,160}$/;
const has=(o,k)=>Object.prototype.hasOwnProperty.call(o,k);
const object=v=>v!==null&&typeof v==='object'&&!Array.isArray(v);
function path(v){
 if(typeof v!=='string'||v.length<1||v.length>100)fail('invalid_mapping_path');
 const parts=v.split('.');
 if(parts.length>5||parts.some(p=>!/^[A-Za-z_][A-Za-z0-9_]*$/.test(p)||
   PROHIBITED.test(p)||SENSITIVE.test(p)))fail('invalid_mapping_path');
 return parts;
}
function read(obj,key){
 if(!key)return undefined;
 let value=obj;
 for(const part of path(key)){
  if(!object(value)||!has(value,part))return undefined;
  value=value[part];
 }
 return value;
}
function textValue(v){
 if(typeof v==='string')return v;
 if(Number.isSafeInteger(v)&&v>=0)return String(v);
 return undefined;
}
function numeric(v){
 if(v===null||v===undefined||v==='')return undefined;
 if(typeof v==='number'&&Number.isFinite(v))return v;
 if(typeof v==='string'&&/^(?:0|[1-9][0-9]{0,4})(?:\.[0-9]{1,3})?$/.test(v.trim()))
  return Number(v.trim());
 fail('invalid_numeric_source_field');
}
function lookup(map,v){
 if(v===null||v===undefined||v==='')return 'unknown';
 const key=textValue(v);
 if(key===undefined||!has(map,key))return 'unknown';
 return map[key];
}
export function validateSecondClassMapping(input){
 if(!object(input)||input.schema!=='xiaoxu/second-class-readonly-map-v1'||
   Object.keys(input).some(k=>!['schema','listPath','completePath','completeWhen','nextCursorPath',
    'verifiedEmptyPath','fields','statusValues'].includes(k)))fail('invalid_mapping');
 const listPath=input.listPath,completePath=input.completePath;
 path(listPath);path(completePath);
 if(typeof input.completeWhen!=='boolean')fail('invalid_mapping');
 if(typeof input.nextCursorPath!=='string')fail('invalid_mapping');
 path(input.nextCursorPath);
 if(input.verifiedEmptyPath!==undefined)path(input.verifiedEmptyPath);
 if(!object(input.fields)||typeof input.fields.activityId!=='string'||
   typeof input.fields.title!=='string')fail('invalid_mapping');
 for(const [field,value] of Object.entries(input.fields)){
  if(!RECORD_FIELDS.has(field)||typeof value!=='string')fail('invalid_mapping');
  path(value);
 }
 const statusValues=input.statusValues??{};
 if(!object(statusValues))fail('invalid_mapping');
 for(const [field,table] of Object.entries(statusValues)){
  if(!STATUS_FIELDS[field]||!object(table)||Object.keys(table).length>40)
   fail('invalid_mapping');
  for(const [raw,target] of Object.entries(table)){
   if(raw.length<1||raw.length>60||PROHIBITED.test(raw)||
      !STATUS_FIELDS[field].has(target))fail('invalid_mapping');
  }
 }
 if(input.fields.activityStatus&&!statusValues.activityStatus||
    input.fields.reviewStatus&&!statusValues.reviewStatus)fail('missing_explicit_status_mapping');
 return input;
}
export function mapSecondClassPage(raw,mapping){
 const config=validateSecondClassMapping(mapping);
 if(!object(raw))fail('invalid_source_response');
 const rawRows=read(raw,config.listPath);
 if(!Array.isArray(rawRows)||rawRows.length>20)fail('invalid_source_response');
 const done=read(raw,config.completePath);
 if(typeof done!=='boolean')fail('invalid_source_response');
 const complete=done===config.completeWhen;
 const cursorRaw=read(raw,config.nextCursorPath);
 const nextCursor=cursorRaw===null||cursorRaw===undefined||cursorRaw===''?
  null:textValue(cursorRaw);
 if(complete&&nextCursor!==null||!complete&&(!nextCursor||!SAFE_CURSOR.test(nextCursor)))
  fail('invalid_pagination');
 const activities=rawRows.map(row=>{
  if(!object(row))fail('invalid_source_record');
  const out={};
  for(const [field,key] of Object.entries(config.fields)){
   const value=read(row,key);
   if(value===null||value===undefined||value==='')continue;
   if(field==='activityId'||field==='title'||field==='term'||field==='date'){
    const str=textValue(value);
    if(str===undefined)fail('invalid_source_record');
    out[field]=str;
   }else if(field==='activityStatus'||field==='reviewStatus')
    out[field]=lookup(config.statusValues[field],value);
   else out[field]=numeric(value);
  }
  try{normalizeRecord('second-class',{externalId:out.activityId,title:out.title,
    ...(out.term!==undefined?{term:out.term}:{}),
    ...(out.date!==undefined?{date:out.date}:{}),
    ...(out.credit!==undefined?{credit:out.credit}:{}),
    ...(out.hours!==undefined?{hours:out.hours}:{}),
    ...(out.points!==undefined?{points:out.points}:{}),
    ...(out.activityStatus!==undefined?{activityStatus:out.activityStatus}:{}),
    ...(out.reviewStatus!==undefined?{reviewStatus:out.reviewStatus}:{})
  });}catch{fail('invalid_source_record');}
  return out;
 });
 const emptyProof=config.verifiedEmptyPath!==undefined?
   read(raw,config.verifiedEmptyPath)===true:false;
 if(!activities.length&&!complete||!activities.length&&!emptyProof)
  fail('empty_not_proven');
 return {activities,complete,nextCursor,
  ...(activities.length===0?{verifiedEmpty:true}:{})};
}
export function makeSecondClassMappedAdapter({mapping,verifySession,fetchPage}={}){
 validateSecondClassMapping(mapping);
 if(typeof verifySession!=='function'||typeof fetchPage!=='function')
  fail('connector_not_configured');
 return Object.freeze({
  provider:'second_class',
  verifySession,
  async readPage({cursor,session}){
   // fetchPage is trusted server-local code that owns the authorized session.
   // No URLs or credentials can be inserted through this mapping.
   const raw=await fetchPage({cursor,session});
   return mapSecondClassPage(raw,mapping);
  }
 });
}
