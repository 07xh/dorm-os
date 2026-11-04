/* 微信候选任务 UI：嵌入现有「任务」页面，不新建另一套待办和登录。
 * 默认不读取私有消息、不生成设备凭据；所有网络访问需用户主动点击。
 * 只有手动审核接受，才会调用现有服务端任务入库逻辑。
 */
(function(root){
 'use strict';
 const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({
  '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
 }[c]));
 const safeId=v=>typeof v==='string'&&/^[A-Za-z0-9_-]{12,128}$/.test(v);
 let devices=[],candidates=[],loaded=false,busy=false,error='',note='',opened=false;
 let oneTimeToken=null,tokenDevice=null,epoch=0;

 async function request(path,method='GET',payload){
  const url='/api/v1/wechat/'+path;
  const res=await fetch(url,{
   method,credentials:'same-origin',cache:'no-store',redirect:'error',
   headers:{accept:'application/json',
    ...(method!=='GET'?{'content-type':'application/json',
     origin:location.origin}:{})},
   ...(payload!==undefined?{body:JSON.stringify(payload)}:{})
  });
  if(!(res.headers.get('content-type')||'').includes('application/json'))
   throw Error('校序私人接口暂不可用');
  const data=await res.json();
  if(!res.ok){
   const codes={unauthorized:'请先登录校序',auth_not_configured:'校序账号服务未配置',
    database_unavailable:'云端数据表尚未就绪',device_not_found:'设备不存在或已撤销',
    candidate_already_reviewed:'此消息已被审核，请刷新',
    candidate_not_found:'这条消息已不可用',
    forbidden_origin_or_format:'页面授权检查失败，请从校序网址操作'};
   const ex=Error(codes[data.error]||'操作失败，请检查登录及云端数据库');
   ex.status=res.status;throw ex;
  }
  return data;
 }
 function disconnect(){
  epoch++;devices=[];candidates=[];loaded=false;busy=false;error='';note='';
  oneTimeToken=null;tokenDevice=null;opened=false;
  update();
 }
 function when(t){
  if(typeof t!=='string'||!Number.isFinite(Date.parse(t)))return '时间未知';
  return new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',
   year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'
  }).format(new Date(t));
 }
 function deadline(row){
  if(row.duePrecision==='exact'&&row.dueAtUtc)return '截止 '+when(row.dueAtUtc);
  if(row.duePrecision==='date'&&row.dueLocalDate)return '截止 '+esc(row.dueLocalDate)+'（时间未核实）';
  return '截止时间未知';
 }
 function panel(){
  const count=loaded?candidates.length:null;
  const actions=loaded?
   '<div class="button-row"><button type="button" class="secondary" data-xu-wc-refresh'+(busy?' disabled':'')+
   '>刷新候选</button></div>'+
   (candidates.length?'<div class="xu-wc-list">'+candidates.map(row=>
    '<article class="xu-inbox-entry">'+
    '<div class="xu-inbox-entry-title"><strong>'+esc(row.title)+'</strong></div>'+
    '<p class="small muted">'+esc(row.chatLabel||'已选择的群聊')+' · '+when(row.sentAt)+
    ' · '+deadline(row)+'</p>'+
    (row.excerpt?'<p class="small muted">'+esc(row.excerpt)+'</p>':'')+
    '<div class="button-row">'+
    '<button type="button" class="primary" data-xu-wc-review="'+esc(row.id)+'" data-xu-wc-decision="accept"'+(busy?' disabled':'')+'>确认加入待办</button>'+
    '<button type="button" class="secondary" data-xu-wc-review="'+esc(row.id)+'" data-xu-wc-decision="dismiss"'+(busy?' disabled':'')+'>忽略</button></div>'+
    '</article>').join('')+'</div>':
    '<p class="small muted">暂无待审核消息。这只说明目前没有已同步到校序的候选，不代表微信群没有新任务。</p>')
   :'<div class="button-row"><button type="button" class="secondary" data-xu-wc-refresh'+
     (busy?' disabled':'')+'>读取我的候选消息</button></div>';
  const paired=loaded?'<details class="xu-wc-devices"><summary>电脑设备管理</summary>'+
   '<p class="small muted">仅允许你主动绑定的电脑上传候选；群消息读取仍在电脑本地完成。</p>'+
   '<div class="form-field"><label for="xuWcDeviceName">电脑名称</label>'+
   '<input id="xuWcDeviceName" data-xu-wc-device-name maxlength="48" placeholder="我的 Windows 电脑" autocomplete="off"></div>'+
   '<div class="button-row"><button class="secondary" type="button" data-xu-wc-pair'+
   (busy?' disabled':'')+'>生成一次性设备凭据</button></div>'+
   (oneTimeToken?'<div class="notice neutral"><strong>仅显示一次的设备凭据</strong>'+
    '<p class="small muted">设备 ID：<code>'+esc(tokenDevice)+'</code>（电脑初始化时需要填写）</p>'+
    '<p class="small muted">此凭据相当于授权密码。仅通过可信方式传至你的电脑，切勿发送到聊天、Issue 或截图中。</p>'+
    '<code data-xu-wc-token>'+esc(oneTimeToken)+'</code>'+
    '<div class="button-row"><button type="button" class="secondary" data-xu-wc-copy>复制凭据</button>'+
    '<button type="button" class="secondary" data-xu-wc-hide>隐藏并清除</button></div></div>':'')+
   (devices.length?devices.map(dev=>'<div class="config-row"><div><strong>'+esc(dev.name)+
    '</strong><div class="small muted">'+(dev.revokedAt?'已撤销':dev.lastSeenAt?'最后同步 '+when(dev.lastSeenAt):'已绑定，尚未同步')+
    '</div></div>'+(dev.revokedAt?'':'<button type="button" class="secondary" data-xu-wc-revoke="'+esc(dev.id)+'"'+(busy?' disabled':'')+'>撤销</button>')+'</div>').join(''):
    '<p class="small muted">尚未绑定电脑</p>')+'</details>':'';
  return '<section class="card xu-wechat-panel" aria-label="微信待审核任务">'+
   '<details class="xu-wc-details"'+(opened?' open':'')+'><summary>'+
   '<strong>微信待审核</strong> <span class="small muted">'+
   (count===null?'按需读取':count+' 条待确认')+'</span></summary>'+
   '<p class="small muted">电脑采集 · 校序审核 · 仅本人确认后加入现有任务。当前不代表微信实时在线。</p>'+
   (error?'<p class="small" role="alert">'+esc(error)+'</p>':'')+
   (note?'<p class="small" role="status">'+esc(note)+'</p>':'')+
   actions+paired+'</details></section>';
 }
 function update(){
  const host=document.getElementById('xuWechatTasksHost');
  if(!host)return;
  const details=host.querySelector('.xu-wc-details');
  if(details)opened=details.open;
  host.innerHTML=panel();
 }
 async function refresh(){
  if(busy)return;
  busy=true;error='';note='';
  const turn=epoch;
  try{
   const [d,c]=await Promise.all([
    request('devices'),request('candidates?limit=50')
   ]);
   if(turn!==epoch)return;
   if(!Array.isArray(d.devices)||!Array.isArray(c.items)||c.status!=='personal_candidates')
    throw Error('云端数据格式不符');
   devices=d.devices.filter(x=>safeId(x.id)&&typeof x.name==='string');
   candidates=c.items.filter(x=>safeId(x.id)&&typeof x.title==='string');
   loaded=true;
  }catch(e){if(turn===epoch)error=String(e.message||'读取失败');}
  finally{if(turn===epoch){busy=false;update();}}
 }
 async function mutate(work){
  if(busy)return;
  const turn=epoch;busy=true;error='';note='';update();
  try{await work(turn);
   if(turn!==epoch)return;
  }catch(e){if(turn===epoch)error=String(e.message||'操作失败');}
  finally{if(turn===epoch){busy=false;update();}}
 }
 document.addEventListener('click',event=>{
  const button=event.target.closest('[data-xu-wc-refresh],[data-xu-wc-pair],[data-xu-wc-revoke],[data-xu-wc-review],[data-xu-wc-copy],[data-xu-wc-hide]');
  if(!button)return;
  event.preventDefault();
  if(button.hasAttribute('data-xu-wc-refresh')){void refresh();return;}
  if(button.hasAttribute('data-xu-wc-hide')){
   oneTimeToken=null;tokenDevice=null;update();return;
  }
  if(button.hasAttribute('data-xu-wc-copy')){
   if(oneTimeToken&&navigator.clipboard?.writeText)
    void navigator.clipboard.writeText(oneTimeToken).then(()=>{note='已复制，请妥善保管';update();})
     .catch(()=>{error='无法复制，请检查浏览器剪贴板权限';update();});
   return;
  }
  if(button.hasAttribute('data-xu-wc-pair')){
   const name=document.querySelector('[data-xu-wc-device-name]')?.value.trim();
   if(!name){error='请输入电脑名称';update();return;}
   void mutate(async turn=>{
    const r=await request('devices','POST',{name});
    if(turn!==epoch)return;
    if(!safeId(r.device?.id)||typeof r.token!=='string'||!r.token.startsWith('wxu_'))
     throw Error('设备返回数据不完整');
    oneTimeToken=r.token;tokenDevice=r.device.id;
    const data=await request('devices');
    if(turn!==epoch)return;
    devices=data.devices;
    note='凭据仅在本页面显示一次；尚未同步任何微信消息。';
   });
   return;
  }
  const revokeId=button.getAttribute('data-xu-wc-revoke');
  if(revokeId){
   if(!safeId(revokeId)||!confirm('确定撤销这台电脑的同步权限？撤销后需要重新绑定。'))return;
   void mutate(async turn=>{
    await request('devices/'+encodeURIComponent(revokeId),'DELETE');
    if(turn!==epoch)return;
    if(tokenDevice===revokeId){oneTimeToken=null;tokenDevice=null;}
    const data=await request('devices');
    if(turn!==epoch)return;
    devices=data.devices;
    note='设备已撤销，旧凭据不再有效。';
   });return;
  }
  const candidateId=button.getAttribute('data-xu-wc-review');
  const decision=button.getAttribute('data-xu-wc-decision');
  if(candidateId){
   if(!safeId(candidateId)||!['accept','dismiss'].includes(decision))return;
   void mutate(async turn=>{
    const result=await request('candidates/'+encodeURIComponent(candidateId),'POST',{decision});
    if(turn!==epoch)return;
    if(result.status!==(decision==='accept'?'accepted':'dismissed'))
     throw Error('审核结果未确认');
    candidates=candidates.filter(x=>x.id!==candidateId);
    note=decision==='accept'?'已加入校序云端任务；查看现有待办即可':'已忽略这条候选';
    if(decision==='accept'){
     if(root.XiaoxuCloudTasks?.isReady?.())await root.XiaoxuCloudTasks.refresh().catch(()=>{});
     if(turn===epoch)root.dispatchEvent(new Event('xiaoxu-wechat-task-reviewed'));
    }
   });return;
  }
 });
 document.addEventListener('toggle',event=>{
  if(event.target.matches?.('.xu-wc-details'))opened=event.target.open;
 },true);
 root.XiaoxuWechat=Object.freeze({panel,disconnect,refresh});
})(globalThis);
