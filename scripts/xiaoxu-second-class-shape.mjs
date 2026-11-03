/* Local-only JSON shape inspector. Outputs field names + types, NEVER values.
 * No HTTP, login, account persistence or raw sample upload. The owner must
 * still review the generated report before sharing it.
 */
import {readFileSync,statSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const SENSITIVE=/(?:passw(?:or)?d|cookie|token|secret|csrf|authorization|session|student.?id|stu.?no|id.?card|mobile|phone|email|identity|nameOfStudent)/i;
const GOODKEY=/^[A-Za-z_][A-Za-z0-9_]{0,64}$/;
function kind(v){
 if(v===null)return 'null';
 if(Array.isArray(v))return 'array';
 return typeof v;
}
export function describeSecondClassJson(raw,{maxDepth=5,maxFields=150}={}){
 if(!Number.isInteger(maxDepth)||maxDepth<1||maxDepth>8||
  !Number.isInteger(maxFields)||maxFields<1||maxFields>400)
  throw Error('invalid_inspection_limits');
 const out=['JSON 响应结构（只包含键与类型，不包含数据值）'];
 const visited=new WeakSet();
 function visit(value,prefix,depth){
  if(out.length>=maxFields||depth>maxDepth)return;
  if(value===null||typeof value!=='object')return;
  if(visited.has(value))return;
  visited.add(value);
  if(Array.isArray(value)){
   if(value.length)visit(value[0],prefix+'[]',depth+1);
   return;
  }
  for(const [rawKey,entry] of Object.entries(value)){
   if(out.length>=maxFields)break;
   const key=GOODKEY.test(rawKey)&&!SENSITIVE.test(rawKey)&&rawKey!=='__proto__'?
    rawKey:'[已隐藏字段名]';
   const p=prefix?prefix+'.'+key:key;
   if(key==='[已隐藏字段名]'){
    out.push(p+' : [不输出]');
    continue;
   }
   out.push(p+' : '+kind(entry));
   visit(entry,p,depth+1);
  }
 }
 visit(raw,'',0);
 return out.join('\n');
}
const invoked=process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url);
if(invoked){
 const input=process.argv[2];
 if(!input||process.argv.length!==3){
  console.error('用法：node scripts/xiaoxu-second-class-shape.mjs ./本机脱敏或待检查的响应.json');
  process.exitCode=2;
 }else{
  try{
   const target=resolve(input);
   if(statSync(target).size>2*1024*1024)throw Error('sample_too_large');
   console.log(describeSecondClassJson(JSON.parse(readFileSync(target,'utf8'))));
   console.error('仅输出字段名和类型；分享前仍须人工确认没有敏感字段名。');
  }catch{
   console.error('读取失败：仅支持小于 2MB 的本机有效 JSON 文件，不会联网。');
   process.exitCode=1;
  }
 }
}
