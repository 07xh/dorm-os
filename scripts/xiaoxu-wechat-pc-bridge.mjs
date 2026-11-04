// Windows WeChat -> Academic OS; consumes the already installed upstream wxcli.cmd.
// Explicit opt-in via env. Raw chat history remains in this process; only task
// candidates from named groups go to Academic OS after device pairing.
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {createHmac,randomBytes} from 'node:crypto';
import {readFile,mkdir,writeFile,rename} from 'node:fs/promises';
import {homedir} from 'node:os';
import {join,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {WECHAT_CANDIDATE_SCHEMA,normalizeWechatCandidateBatch}
  from '../lib/academic-wechat-candidate-contract.mjs';

const execute=promisify(execFile);
const oneHour=3600000;
const defaultState=join(homedir(),'.xiaoxu','wechat-sync-state.json');
const text=v=>typeof v==='string'?v.trim():'';
const psQuote=v=>String(v).replaceAll("'","''");
const opaque=(secret,...parts)=>createHmac('sha256',secret).update(parts.join('\u0000')).digest('base64url');
const taskWords=/(作业|实验报告|课后练习|课程设计|上交|提交|上传|截止|考试|测验|报名|课程任务|填写.*表|交.*报告)/u;
const completedWords=/(我已(经)?(提交|上交|上传)|已经(提交|上交|上传)|作业已(完成|提交))/u;

export function parseHistoryLine(line){
 if(typeof line!=='string')return null;
 const match=/^\[(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2})\] ([\s\S]*)$/u.exec(line);
 if(!match)return null;
 const [,year,month,day,hour,minute,rest]=match;
 const date=new Date(+year,+month-1,+day,+hour,+minute,0);
 if(date.getFullYear()!==+year||date.getMonth()!==+month-1||
   date.getDate()!==+day||date.getHours()!==+hour||date.getMinutes()!==+minute)return null;
 // The upstream formatter includes a sender label but no raw message id.
 // Do not upload a sender; retain only a trimmed, task-like message.
 const colon=rest.indexOf(': ');
 const body=(colon>=0?rest.slice(colon+2):rest).replace(/\s+/gu,' ').trim();
 if(!body||body.length>4000||!taskWords.test(body)||completedWords.test(body)||
   /^(\[|<|系统消息)/u.test(body))return null;
 return {body,sentAt:date.toISOString(),timeMs:date.getTime(),raw:line};
}

function localDate(y,m,d){
 const iso=[y,m,d].map((v,i)=>i===0?String(v):String(v).padStart(2,'0')).join('-');
 const parsed=new Date(iso+'T00:00:00Z');
 return Number.isFinite(parsed.getTime())&&parsed.toISOString().slice(0,10)===iso?iso:null;
}
export function extractDeadline(body){
 const m=/(20\d{2})[-/.年](\d{1,2})[-/.月](\d{1,2})(?:日|号)?/u.exec(body);
 const date=m?localDate(+m[1],+m[2],+m[3]):null;
 // Relative expressions ('周五之前') are deliberately left unknown.
 return date?{precision:'date',date}:{precision:'unknown'};
}
export function createCandidates(history,group,token,deviceId){
 if(!history||history.is_group!==true||
    typeof history.username!=='string'||!history.username.endsWith('@chatroom')||
    !Array.isArray(history.messages)||!Array.isArray(history.failures)||
    history.failures.length||!history.messages.every(x=>typeof x==='string'))
  throw Error('微信历史记录不完整或不是群聊，未上传');
 const chatName=text(history.chat);
 if(!chatName||chatName.length>80||chatName!==group)
  throw Error('微信群名称与允许列表不匹配，未上传');
 const seen=new Set(),records=[];
 let newest=0;
 for(const line of history.messages){
  const parsed=parseHistoryLine(line);
  // Capture high-water for non-task messages too, but never advance
  // a checkpoint until every candidate in the complete window is accepted.
  const timestamp=/^\[(\d{4}-\d\d-\d\d \d\d:\d\d)\]/u.exec(line);
  if(timestamp){
   const d=new Date(timestamp[1].replace(' ','T')+':00');
   if(Number.isFinite(d.getTime()))newest=Math.max(newest,d.getTime());
  }
  if(!parsed)continue;
  const key=opaque(token,'message',history.username,parsed.raw);
  if(seen.has(key))continue;
  seen.add(key);
  const title=parsed.body.slice(0,120).trim();
  if(!title)continue;
  const kind=/考试|测验/u.test(parsed.body)?'exam':
   /报名/u.test(parsed.body)?'signup':/活动/u.test(parsed.body)?'activity':'homework';
  records.push({
   messageKey:key,candidateKey:opaque(token,'candidate',key),
   chatKey:opaque(token,'group',history.username),
   chatLabel:group,kind,title,sentAt:parsed.sentAt,
   deadline:extractDeadline(parsed.body)
  });
 }
 return {records,newest};
}

function configFromEnv(env=process.env){
 const url=new URL(env.XIAOXU_WECHAT_BASE_URL||'');
 if(url.protocol!=='https:'||url.username||url.password||url.search||url.hash||
   url.pathname!=='/')throw Error('需要 HTTPS 校序站点根网址');
 const token=text(env.XIAOXU_WECHAT_DEVICE_TOKEN);
 const deviceId=text(env.XIAOXU_WECHAT_DEVICE_ID);
 if(!/^wxu_[A-Za-z0-9_-]{43}$/.test(token)||
    !/^[A-Za-z0-9_-]{12,128}$/.test(deviceId))throw Error('需先在校序绑定电脑设备');
 let groups;
 try{groups=JSON.parse(env.XIAOXU_WECHAT_GROUPS||'[]');}catch{throw Error('群聊列表应为 JSON 数组');}
 if(!Array.isArray(groups)||groups.length===0||groups.length>10||
  groups.some(x=>typeof x!=='string'||!x.trim()||x.trim()!==x||x.length>80)||
  new Set(groups).size!==groups.length)throw Error('请明确选择 1–10 个群聊');
 return {url,token,deviceId,groups,
  cli:env.XIAOXU_WECHAT_CLI||join(homedir(),'.wechat-cli','wxcli.cmd'),
  statePath:env.XIAOXU_WECHAT_STATE||defaultState};
}
async function readState(path,deviceId){
 try{
  const v=JSON.parse(await readFile(path,'utf8'));
  if(v?.version!==1||v.deviceId!==deviceId||typeof v.groups!=='object'||
     v.groups===null||Array.isArray(v.groups))throw Error('设备状态不匹配');
  return v;
 }catch(e){
  if(e.code==='ENOENT')return {version:1,deviceId,groups:{}};
  throw e;
 }
}
async function persist(path,value){
 await mkdir(dirname(path),{recursive:true});
 const tmp=path+'.'+randomBytes(6).toString('hex')+'.tmp';
 await writeFile(tmp,JSON.stringify(value,null,2),{mode:0o600,flag:'wx'});
 await rename(tmp,path);
}
async function historyPage(config,group,startTime,offset,limit){
 // Use PowerShell's call operator with quoted literals, no shell interpolation
 // of a group name. The installed wxcli.cmd remains the only reader.
 const command="[Console]::OutputEncoding = [Text.Encoding]::UTF8; "+
  "$OutputEncoding = [Text.Encoding]::UTF8; & '"+psQuote(config.cli)+
  "' history '"+psQuote(group)+"' --start-time '"+psQuote(startTime)+
  "' --limit "+limit+" --offset "+offset+" --format json";
 const {stdout}=await execute(process.platform==='win32'?'powershell.exe':'pwsh',
  ['-NoProfile','-NonInteractive','-Command',command],
  {timeout:90000,maxBuffer:12*1024*1024,windowsHide:true});
 let data;try{data=JSON.parse(stdout.replace(/^\uFEFF/u,''));}
 catch{throw Error('上游微信 CLI 未返回合法 JSON');}
 return data;
}
async function readCompleteWindow(config,group,since){
 const limit=100,maxPages=30;
 const messages=[],firstMeta={};
 for(let page=0;page<maxPages;page++){
  const response=await historyPage(config,group,since,page*limit,limit);
  if(!Array.isArray(response.messages)||!Number.isInteger(response.count)||
    response.count!==response.messages.length||
    !Array.isArray(response.failures)&&response.failures!==null)
   throw Error('历史记录返回结构异常，未上传');
  if(!page){
   firstMeta.is_group=response.is_group;
   firstMeta.username=response.username;
   firstMeta.chat=response.chat;
  }else if(response.username!==firstMeta.username||response.chat!==firstMeta.chat)
   throw Error('分页过程中群身份变化');
  if(response.failures?.length)throw Error('上游微信 CLI 分页读取失败，未上传');
  messages.push(...response.messages);
  if(response.count<limit)return {...firstMeta,messages,failures:[]};
 }
 throw Error('群聊消息超过单次安全分页上限，未推进同步进度');
}
async function upload(config,records){
 for(let i=0;i<records.length;i+=25){
  const batch={schema:WECHAT_CANDIDATE_SCHEMA,deviceId:config.deviceId,
   batchId:'wxb_'+randomBytes(16).toString('base64url'),
   capturedAt:new Date().toISOString(),records:records.slice(i,i+25)};
  normalizeWechatCandidateBatch(batch);
  const response=await fetch(new URL('/api/v1/wechat/ingest',config.url),{
   method:'POST',redirect:'error',headers:{
    'authorization':'Bearer '+config.token,'content-type':'application/json'
   },body:JSON.stringify(batch),signal:AbortSignal.timeout(30000)
  });
  if(response.status!==202)throw Error('云端未确认接收（HTTP '+response.status+'），保留上次同步进度');
  const result=await response.json();
  if(result.received!==batch.records.length)
   throw Error('云端确认数量不符，保留上次同步进度');
 }
}
export async function runBridge(env=process.env,{dryRun=false}={}){
 const config=configFromEnv(env);
 const state=await readState(config.statePath,config.deviceId);
 const summary=[];
 for(const group of config.groups){
  // The initial scan covers only 48 hours; later runs overlap 1 hour.
  // A failure never advances the persisted cursor.
  const previous=state.groups[group];
  const cutoff=Number.isFinite(previous)?previous-oneHour:Date.now()-48*oneHour;
  const since=new Date(Math.max(0,cutoff)).toLocaleString('sv-SE',{hour12:false})
   .replace('T',' ');
  const history=await readCompleteWindow(config,group,since);
  const {records,newest}=createCandidates(history,group,config.token,config.deviceId);
  if(!dryRun)await upload(config,records);
  if(!dryRun){
   state.groups[group]=Math.max(previous||0,newest);
   await persist(config.statePath,state);
  }
  summary.push({group,candidates:records.length,committed:!dryRun});
 }
 return summary;
}
if(process.argv[1]&&fileURLToPath(import.meta.url)===process.argv[1]){
 const dryRun=process.argv.includes('--dry-run');
 runBridge(process.env,{dryRun}).then(results=>{
  for(const r of results)console.log(r.group+': '+r.candidates+
   ' 条候选'+(r.committed?'，云端已确认':'（本地预览，未上传）'));
 }).catch(e=>{console.error('微信同步失败：'+e.message);process.exitCode=1;});
}
