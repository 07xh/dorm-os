const $=id=>document.getElementById(id);
const time=value=>value?new Date(value).toLocaleString('zh-CN'):'尚无同步记录';
async function send(message){return chrome.runtime.sendMessage(message);}
async function render(){
 const s=await send({type:'XU_GET_STATE'});
 $('enabled').checked=Boolean(s.enabled);$('interval').value=String(s.intervalMinutes||120);
 $('last').textContent=s.lastSyncAt?'上次同步：'+time(s.lastSyncAt):'尚无同步记录';
 $('activities').textContent=String(s.lastActivityCount??'—');
 $('terms').textContent=String(s.lastSemesterCount??'—');
 $('status').textContent=s.lastStatus==='success'?'同步成功':s.lastStatus==='reading_school'?'正在读取华水数据':s.lastStatus==='uploading'?'正在同步到校序':s.lastStatus==='error'?'需要处理':'尚未同步';
 $('error').hidden=!s.lastError;$('error').textContent=s.lastError||'';
 document.querySelector('.status').dataset.state=s.lastStatus==='success'?'success':s.lastStatus==='error'?'error':'idle';
}
$('enabled').addEventListener('change',async()=>{
 await send({type:'XU_SET_AUTO',enabled:$('enabled').checked,intervalMinutes:Number($('interval').value)});await render();
});
$('interval').addEventListener('change',async()=>{
 await send({type:'XU_SET_AUTO',enabled:$('enabled').checked,intervalMinutes:Number($('interval').value)});await render();
});
$('sync').addEventListener('click',async()=>{
 $('sync').disabled=true;$('sync').textContent='正在同步…';
 const result=await send({type:'XU_SYNC_NOW'});
 $('sync').disabled=false;$('sync').textContent='立即同步';
 if(result?.error){$('error').hidden=false;$('error').textContent=result.error;}
 await render();
});
$('school').addEventListener('click',()=>send({type:'XU_OPEN_SCHOOL'}));
$('portal').addEventListener('click',()=>send({type:'XU_OPEN_PORTAL'}));
render();
