/* Real, owner-scoped Chaoxing course > homework > question viewer.
 * Read-only: never sends answers, submits forms, or exposes signed URLs.
 */
(function(root){
 'use strict';
 let courses=[],items=[],detail=null,selected='',loaded=false,loading='',error='',epoch=0;
 let listVerified=false,checkedAt=null;
 const sourceStatus={todo:'官方显示：未提交',submitted:'官方显示：已提交 / 待批阅',
  graded:'官方显示：已批阅',returned:'官方显示：已退回',unknown:'官方状态未知'};
 const displayedStatus=x=>x?.platformVerified===true&&sourceStatus[x.statusCode]?
  sourceStatus[x.statusCode]:'官方状态未核实';
 const esc=x=>String(x??'').replace(/[&<>"']/g,c=>({
  '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
 }[c]));
 const online=()=>root.XiaoxuAcademic?.isReady?.()===true;
 const key=c=>[c.courseId,c.classId,c.cpi].join(':');
 const choice=()=>courses.find(c=>key(c)===selected)||null;
 function panel(){
  const ready=online(),current=choice();
  return '<section class="card xu-inbox-panel" aria-label="学习通课程作业">'+
   '<details class="xu-work-details"><summary><strong>学习通课程作业</strong>'+
   ' <span class="small muted">本人会话 · 只读浏览</span></summary>'+
   '<p class="small muted">根据本人官方课程和作业 ID 同步提交状态到校序任务；通知不会冒充完成结果。不会自动作答或提交。</p>'+
   (!ready?'<p class="small muted">请先在「我的 → 数据连接」连接校序及学习通账号。</p>':
    '<div class="button-row"><button class="secondary" type="button" data-xu-work-courses '+(loading?'disabled':'')+
    '>'+(loading==='courses'?'同步中…':'同步我的课程作业')+'</button></div>')+
   (ready&&loaded?'<div class="config-row"><label for="xuWorkCourseSelect">选择课程（'+courses.length+' 门）</label>'+
    '<select id="xuWorkCourseSelect" data-xu-work-select>'+courses.map(c=>
      '<option value="'+esc(key(c))+'" '+(key(c)===selected?'selected':'')+'>'+esc(c.title)+'</option>').join('')+
    '</select><button type="button" class="secondary" data-xu-work-list '+(loading?'disabled':'')+
     '>重新同步本课程</button></div>':'')+
   (ready&&listVerified&&current?'<p class="small muted" data-xu-work-verification>官方作业状态已同步：'+
    items.length+' 份 · '+items.filter(x=>x.statusCode==='todo').length+' 份待提交 · '+
    items.filter(x=>['submitted','graded'].includes(x.statusCode)).length+' 份已提交/批阅'+
    (checkedAt?' · 读取于 '+esc(new Date(checkedAt).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'})):'')+
    '</p>':'')+
   (error?'<p class="small muted" role="alert">'+esc(error)+'</p>':'')+
   (loading?'<p class="small muted" role="status">正在读取官方'+
      esc(({courses:'课程列表',list:'作业列表',detail:'题目'})[loading]||'数据')+'…</p>':'')+
   (ready&&current&&items.length?'<div class="xu-inbox-entries">'+items.map(x=>
    '<article class="xu-inbox-entry"><div><strong>'+esc(x.title)+'</strong>'+
     '<div class="small muted">'+esc(displayedStatus(x))+
     (x.status&&x.statusCode==='unknown'?' · '+esc(x.status):'')+'</div></div>'+
     '<button class="secondary" type="button" data-xu-work-detail="'+esc(x.workId)+'" '+
      (loading?'disabled':'')+'>查看题目</button></article>').join('')+'</div>':'')+
   (ready&&detail?'<div class="xu-inbox-panel"><h4>'+esc(detail.title)+'</h4>'+
    '<p class="small muted">'+esc(detail.course||'')+' · '+esc(detail.note||'')+'</p>'+
    (detail.questionsAvailable?detail.questions.map(q=>
     '<article class="xu-inbox-entry"><strong>'+q.number+'. '+esc(q.stem)+'</strong>'+
     (q.options?.length?'<div class="small">'+q.options.map(o=>'<p>'+esc(o)+'</p>').join('')+'</div>':'')+
     (q.hasImage?'<p class="small muted">此题含图片，请到学习通原页面查看。</p>':'')+
     '</article>').join(''):
     '<p class="small muted">该作业的题目暂不可直接读取，可在学习通官方页面查看。</p>')+
    '</div>':'')+
   '</details></section>';
 }
 function update(){
  const host=document.getElementById('xuChaoxingWorkHost');if(!host)return;
  const expanded=host.querySelector('.xu-work-details')?.open===true;
  host.innerHTML=panel();
  const d=host.querySelector('.xu-work-details');if(d)d.open=expanded;
 }
 async function read(view,params={}){
  const url=new URL('/api/v1/chaoxing-homework',location.origin);
  url.searchParams.set('view',view);
  for(const [k,v] of Object.entries(params))url.searchParams.set(k,v);
  const sync=view==='list';
  const r=await fetch(sync?'/api/v1/chaoxing-homework/sync':url.pathname+url.search,{
   method:sync?'POST':'GET',credentials:'same-origin',cache:'no-store',
   redirect:'error',headers:{accept:'application/json',
    ...(sync?{'content-type':'application/json'}:{})},
   ...(sync?{body:JSON.stringify(params)}:{})
  });
  const d=await r.json().catch(()=>null);
  if(!r.ok)throw Error(({
   session_expired:'学习通会话已失效，请在「我的」重新连接',
   school_login_unavailable:'学习通读取服务尚未准备好',
   courses_unverified:'未能从本人学习通会话核实课程所属学校标识或课程列表，请重新连接学习通并检查官方课程是否可访问',
   work_not_available:'该课程作业页未开放或缺少官方作业签名，不能据此认定作业已完成',
   database_unavailable:'云端任务同步数据库不可用，官方完成状态未保存',
   not_found:'未找到这份作业，刷新列表后重试'
  })[d?.error]||'官方页面读取失败，已保留原有数据');
  if(d?.schema!=='xiaoxu/chaoxing-work-browser-v1'||d?.view!==view||
    d.live!==true||typeof d.data!=='object')throw Error('学习通返回结构不匹配');
  return d.data;
 }
 async function refreshSyncedTasks(){
  try{
   if(root.XiaoxuCloudTasks?.isReady?.()){
    await root.XiaoxuCloudTasks.refresh();
    root.dispatchEvent(new Event('xiaoxu-chaoxing-tasks-synced'));
   }
  }catch{} // Owner may not have opted into the cloud task view yet.
 }
 async function load(view,params={}){
  if(loading||!online())return;
  loading=view;error='';const at=epoch;update();
  try{
   const data=await read(view,params);
   if(at!==epoch||!online())return;
   if(view==='courses'){
    if(!Array.isArray(data.courses)||data.courses.length>120)throw Error('课程列表无效');
    courses=data.courses.filter(x=>x&&typeof x.title==='string'&&/^\d{1,24}$/.test(x.courseId)&&
      /^\d{1,24}$/.test(x.classId)&&/^\d{1,24}$/.test(x.cpi));
    selected=key(courses[0]||{courseId:'',classId:'',cpi:''});
    if(!courses.length)throw Error('学习通课程列表未包含可验证的课程信息');
    loaded=true;items=[];detail=null;listVerified=false;checkedAt=null;
    // One explicit user click reads the official course list and the first
    // course's homework status. No background scraping of every course.
    const first=choice();
    if(first){
     try{
      const list=await read('list',{courseId:first.courseId,classId:first.classId,cpi:first.cpi});
      if(at!==epoch||!online())return;
      if(!Array.isArray(list.items)||list.items.length>200)
       throw Error('首门课程的官方作业列表无效');
      items=list.items.filter(x=>x&&/^\d{1,24}$/.test(x.workId)&&typeof x.title==='string');
      listVerified=true;checkedAt=Date.now();
      if(!items.length)error=list.verifiedEmpty?'首门课程官方显示暂无作业':'首门课程未获取到完整作业列表，状态仍未知';
      if(list.sync)void refreshSyncedTasks();
     }catch(ex){
      if(at===epoch)error='课程已读取，但首门课程作业未同步：'+String(ex.message||'官方页面异常');
     }
    }
   }else if(view==='list'){
    if(!Array.isArray(data.items)||data.items.length>200)throw Error('作业列表无效');
    items=data.items.filter(x=>x&&/^\d{1,24}$/.test(x.workId)&&typeof x.title==='string');
    detail=null;listVerified=true;checkedAt=Date.now();
    if(!items.length)error=data.verifiedEmpty?'官方页面显示暂无作业':'未读取到完整作业列表，不能据此断定该课程没有作业';
    if(data.sync)void refreshSyncedTasks();
   }else{
    if(!Array.isArray(data.questions)||data.questions.length>120)throw Error('作业内容无效');
    detail=data;
   }
  }catch(e){if(at===epoch)error=String(e.message||'读取失败');}
  finally{if(at===epoch){loading='';update();}}
 }
 function disconnect(){epoch++;courses=[];items=[];detail=null;selected='';loaded=false;loading='';error='';listVerified=false;checkedAt=null;update();}
 document.addEventListener('click',e=>{
  if(e.target.closest('[data-xu-work-courses]')){load('courses');return;}
  if(e.target.closest('[data-xu-work-list]')){
   const c=choice();if(c)load('list',{courseId:c.courseId,classId:c.classId,cpi:c.cpi});return;
  }
  const btn=e.target.closest('[data-xu-work-detail]');
  if(btn){const c=choice();if(c)load('detail',{courseId:c.courseId,
   classId:c.classId,cpi:c.cpi,workId:btn.dataset.xuWorkDetail});}
 });
 document.addEventListener('change',e=>{
  if(e.target.matches('[data-xu-work-select]')){
   selected=e.target.value;items=[];detail=null;error='';listVerified=false;checkedAt=null;update();
   const course=choice();
   if(course)void load('list',{courseId:course.courseId,classId:course.classId,cpi:course.cpi});
  }
 });
 root.XiaoxuChaoxingHomework=Object.freeze({panel,disconnect});
})(window);
