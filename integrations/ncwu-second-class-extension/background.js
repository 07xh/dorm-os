const ALARM='xiaoxu-ncwu-second-class-sync';
const DEFAULTS={enabled:false,intervalMinutes:120,lastStatus:'not_started',
 lastSyncAt:null,lastError:null,lastActivityCount:0,lastSemesterCount:0};
let running=false;
const errorText={
 AUTH_EXPIRED:'华水第二课堂登录已过期，请在官方页面重新登录。',
 NETWORK_ERROR:'连接华水失败，请检查网络。',
 UPSTREAM_ERROR:'华水查询接口暂时不可用。',
 INVALID_RESPONSE:'华水返回结构与已验证版本不一致，本次未覆盖旧数据。',
 TOO_MANY_RECORDS:'活动记录超过当前安全上限，需要扩展分页容量。',
 DUPLICATE_ACTIVITY:'活动列表出现重复记录，本次同步已中止。',
 PORTAL_AUTH_REQUIRED:'请先在同一浏览器登录校序并完成 Cloudflare Access 授权。',
 D1_NOT_CONFIGURED:'校序云端 D1 尚未配置。',
 PORTAL_UPLOAD_FAILED:'校序拒绝了本次数据，请检查登录和服务状态。',
 PORTAL_NETWORK_ERROR:'无法连接校序云端。',
 ADAPTER_MISSING:'同步助手未正确加载，请重新加载扩展。',
 PORTAL_ORIGIN_MISMATCH:'校序域名不匹配，请检查扩展配置。'
};
async function state(){return {...DEFAULTS,...await chrome.storage.local.get(DEFAULTS)};}
async function save(patch){await chrome.storage.local.set(patch);return state();}
function wait(ms){return new Promise(resolve=>setTimeout(resolve,ms));}
async function ensureTab(pattern,url,active=false){
 const existing=await chrome.tabs.query({url:pattern});
 if(existing.length&&existing[0].id!==undefined)return {id:existing[0].id,created:false};
 const tab=await chrome.tabs.create({url,active});
 await new Promise((resolve,reject)=>{
  let finished=false;
  const done=(err,value)=>{
   if(finished)return;finished=true;chrome.tabs.onUpdated.removeListener(listener);
   clearTimeout(timer);err?reject(err):resolve(value);
  };
  const listener=(id,info,updated)=>{
   if(id===tab.id&&info.status==='complete')done(null,updated);
  };
  const timer=setTimeout(()=>done(new Error('TAB_LOAD_TIMEOUT')),30000);
  chrome.tabs.onUpdated.addListener(listener);
  chrome.tabs.get(tab.id).then(current=>{if(current.status==='complete')done(null,current);}).catch(done);
 });
 return {id:tab.id,created:true};
}
async function sendReady(tabId,message){
 let last;
 for(let i=0;i<12;i++){
  try{return await chrome.tabs.sendMessage(tabId,message);}
  catch(error){last=error;await wait(250);}
 }
 throw last||new Error('CONTENT_SCRIPT_UNAVAILABLE');
}
async function readSchool(){
 let tab;
 try{
  tab=await ensureTab('https://dekt.ncwu.edu.cn/*',
   'https://dekt.ncwu.edu.cn/sys/stu/center/signupactivity',false);
  const current=await chrome.tabs.get(tab.id);
  if(!current.url?.startsWith('https://dekt.ncwu.edu.cn/'))
   throw Object.assign(new Error('AUTH_EXPIRED'),{code:'AUTH_EXPIRED',keepTab:true});
  const result=await sendReady(tab.id,{type:'NCWU_READ_ONLY_SYNC'});
  if(!result?.ok)throw Object.assign(new Error(result?.error||'SYNC_FAILED'),{code:result?.error||'SYNC_FAILED'});
  return {snapshot:result.snapshot,stats:result.stats,tab};
 }catch(error){
  if(tab?.created&&error?.keepTab!==true)await chrome.tabs.remove(tab.id).catch(()=>{});
  throw error;
 }
}
async function uploadPortal(snapshot){
 let tab;
 try{
  tab=await ensureTab('https://dorm-os-dk9.pages.dev/*','https://dorm-os-dk9.pages.dev/#academic',false);
  const current=await chrome.tabs.get(tab.id);
  if(!current.url?.startsWith('https://dorm-os-dk9.pages.dev/')){
   if(tab.created)await chrome.tabs.update(tab.id,{active:true}).catch(()=>{});
   throw Object.assign(new Error('PORTAL_AUTH_REQUIRED'),{code:'PORTAL_AUTH_REQUIRED',keepTab:true});
  }
  const result=await sendReady(tab.id,{type:'NCWU_UPLOAD_SNAPSHOT',snapshot});
  if(!result?.ok)throw Object.assign(new Error(result?.error||'PORTAL_UPLOAD_FAILED'),{code:result?.error||'PORTAL_UPLOAD_FAILED'});
  return {result,tab};
 }catch(error){
  if(tab?.created&&error?.keepTab!==true)await chrome.tabs.remove(tab.id).catch(()=>{});
  throw error;
 }
}
async function runSync(source='manual'){
 if(running)return {ok:false,error:'BUSY'};
 running=true;
 let schoolTab,portalTab;
 await save({lastStatus:'reading_school',lastError:null});
 try{
  const school=await readSchool();schoolTab=school.tab;
  await save({lastStatus:'uploading'});
  const portal=await uploadPortal(school.snapshot);portalTab=portal.tab;
  const stats=school.stats||{};
  await save({lastStatus:'success',lastSyncAt:portal.result.capturedAt||new Date().toISOString(),
   lastError:null,lastActivityCount:stats.activityCount||0,lastSemesterCount:stats.semesterCount||0});
  if(schoolTab?.created)await chrome.tabs.remove(schoolTab.id).catch(()=>{});
  if(portalTab?.created)await chrome.tabs.remove(portalTab.id).catch(()=>{});
  return {ok:true,stats};
 }catch(error){
  const code=error?.code||error?.message||'SYNC_FAILED';
  await save({lastStatus:'error',lastError:errorText[code]||'同步失败，本次没有覆盖上一次成功的数据。'});
  if(source==='manual'&&error?.keepTab&&portalTab?.created)
   await chrome.tabs.update(portalTab.id,{active:true}).catch(()=>{});
  if(source==='manual'&&error?.keepTab&&schoolTab?.created)
   await chrome.tabs.update(schoolTab.id,{active:true}).catch(()=>{});
  return {ok:false,error:errorText[code]||'同步失败，本次没有覆盖上一次成功的数据。'};
 }finally{running=false;}
}
async function setAuto(enabled,intervalMinutes){
 const minutes=[30,120,360].includes(Number(intervalMinutes))?Number(intervalMinutes):120;
 await chrome.storage.local.set({enabled:Boolean(enabled),intervalMinutes:minutes});
 if(enabled)await chrome.alarms.create(ALARM,{periodInMinutes:minutes,delayInMinutes:1});
 else await chrome.alarms.clear(ALARM);
 return state();
}
chrome.runtime.onInstalled.addListener(async()=>{
 const current=await state();await chrome.storage.local.set(current);
 if(current.enabled)await chrome.alarms.create(ALARM,{periodInMinutes:current.intervalMinutes});
});
chrome.runtime.onStartup.addListener(async()=>{
 const current=await state();if(current.enabled)await chrome.alarms.create(ALARM,{periodInMinutes:current.intervalMinutes});
});
chrome.alarms.onAlarm.addListener(alarm=>{
 if(alarm.name===ALARM)runSync('alarm').catch(()=>{});
});
chrome.runtime.onMessage.addListener((message,sender,sendResponse)=>{
 if(message?.type==='XU_GET_STATE'){state().then(sendResponse);return true;}
 if(message?.type==='XU_SET_AUTO'){
  setAuto(message.enabled,message.intervalMinutes).then(sendResponse).catch(()=>sendResponse({error:'设置失败'}));return true;
 }
 if(message?.type==='XU_SYNC_NOW'){
  runSync('manual').then(sendResponse).catch(()=>sendResponse({ok:false,error:'同步失败'}));return true;
 }
 if(message?.type==='XU_OPEN_SCHOOL'){
  chrome.tabs.create({url:'https://dekt.ncwu.edu.cn/sys/stu/center/signupactivity',active:true},sendResponse);return true;
 }
 if(message?.type==='XU_OPEN_PORTAL'){
  chrome.tabs.create({url:'https://dorm-os-dk9.pages.dev/#academic',active:true},sendResponse);return true;
 }
});
