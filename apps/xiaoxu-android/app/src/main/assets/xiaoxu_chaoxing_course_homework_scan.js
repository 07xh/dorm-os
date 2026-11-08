/*
 * Owner-initiated course-homework discovery. Executes inside the official
 * mooc1.chaoxing.com document, not Cloudflare or a separate HTTP client.
 * Signed links/workEnc and cookies never cross the WebView boundary.
 * Bounded to one owner-verified course per invocation.
 */
(function () {
  const host=location.hostname, path=location.pathname;
  const allowed=['/visit/stucoursemiddle','/mooc2/work/list',
    '/mooc-ans/mooc2/work/list'];
  const reply=(error,records=[])=>{
    if(window.__xiaoxuCourseHomeworkScanEpoch!==epoch)return;
    window.__xiaoxuCourseHomeworkResult=JSON.stringify({
      host,kind:'homework',records,error
    });
  };
  const epoch=(Number(window.__xiaoxuCourseHomeworkScanEpoch)||0)+1;
  window.__xiaoxuCourseHomeworkScanEpoch=epoch;
  window.__xiaoxuCourseHomeworkResult='';
  const isMiddle=(host==='mooc1.chaoxing.com'&&path==='/visit/stucoursemiddle')||
    (host==='mooc2-ans.chaoxing.com'&&path==='/mooc2-ans/mycourse/stu');
  if(location.protocol!=='https:'||
    !(isMiddle||(host==='mooc1.chaoxing.com'&&allowed.includes(path)))) {
    reply('wrong_origin');return 'blocked';
  }
  const selected=window.__xiaoxuOwnedWorkCourse;
  const numeric=value=>/^\d{1,24}$/.test(String(value??''));
  if(!selected||!numeric(selected.courseId)||!numeric(selected.classId)||
    !numeric(selected.cpi)){
    reply('course_identity');return 'blocked';
  }
  const clean=(value,max=100)=>String(value??'')
    .replace(/[\u0000-\u001f\u007f]/g,' ')
    .replace(/\s+/g,' ').trim().slice(0,max);
  const allowedPaths=new Set(['/mooc-ans/mooc2/work/task',
    '/mooc-ans/mooc2/work/dowork','/work/dowork']);
  const all=new Map();
  const parse=(doc,embedded=false)=>{
    // Course entry panels use .bottomList; the official paginated work
    // endpoint can return a bare li[data] list without that wrapper.
    const selector=embedded?'.bottomList li[data]':'li[data]';
    const rows=Array.from(doc.querySelectorAll(selector)).filter(li=>
      li.querySelector('p.overHidden2.fl')||li.querySelector('p.overHidden2'));
    if(rows.length>120)throw Error('too_many_records');
    let found=0;
    for(const li of Array.from(rows)){
      const title=clean(li.querySelector('p.overHidden2.fl')?.textContent ||
        li.querySelector('p.overHidden2')?.textContent);
      if(!title)continue;
      const status=clean(li.querySelector('p.status.fl')?.textContent ||
        li.querySelector('p.status')?.textContent,60);
      const raw=li.getAttribute('data');
      let link;try{link=new URL(raw,location.origin)}catch{throw Error('invalid_response')}
      const qp=link.searchParams;
      const courseId=qp.get('courseId'), classId=qp.get('classId');
      const workId=qp.get('workId')||qp.get('workid');
      if(link.protocol!=='https:'||link.hostname!==host||link.hash||
        link.username||link.password||!allowedPaths.has(link.pathname)||
        qp.getAll('courseId').length!==1||qp.getAll('classId').length!==1||
        qp.getAll('workId').length+qp.getAll('workid').length!==1||
        !numeric(workId)||courseId!==selected.courseId||
        classId!==selected.classId)throw Error('invalid_response');
      if(all.has(workId))throw Error('duplicate_work');
      all.set(workId,{title,
        details:'当前网页显示的状态：'+(status||'状态未知')+
          ' · 官方作业身份：work:'+courseId+':'+classId+':'+workId,
        sourceKey:'work:'+courseId+':'+classId+':'+workId});
      found++;
      if(all.size>120)throw Error('too_many_records');
    }
    const buttons=Array.from(doc.querySelectorAll('#page li'))
      .map(n=>Number(clean(n.textContent,15)))
      .filter(n=>Number.isInteger(n)&&n>0);
    if(buttons.some(n=>n>10))throw Error('too_many_pages');
    return {found,pages:buttons.length?Math.max(...buttons):1};
  };
  const input=id=>String(document.querySelector('input#'+id)?.value||'');
  const tokenOk=value=>value.length<=500&&!/[\r\n\u0000-\u001f<>"']/.test(value);
  // Follow the actual course-owned work entry, as the reference client's
  // CourseEntry does. Keep signed URLs entirely inside the official WebView.
  if(isMiddle){
    try{
      const candidates=Array.from(document.querySelectorAll(
        'a[data-url*="work/list"],iframe[src*="work/list"],a[href*="work/list"]'));
      if(candidates.length){
        const el=candidates[0];
        const raw=el.getAttribute('data-url')||el.getAttribute('src')||el.getAttribute('href');
        const target=new URL(raw,location.href),p=target.searchParams;
        if(target.protocol==='http:'&&target.hostname==='mooc1.chaoxing.com'&&!target.port)
          target.protocol='https:';
        if(target.protocol!=='https:'||target.hostname!=='mooc1.chaoxing.com'||
          target.port||target.username||target.password||target.hash||
          !['/mooc2/work/list','/mooc-ans/mooc2/work/list'].includes(target.pathname))
          throw Error('invalid_response');
        for(const [key,value] of Object.entries({courseId:selected.courseId,
          classId:selected.classId,cpi:selected.cpi})){
          if(p.getAll(key).length>1||(p.has(key)&&p.get(key)!==value))
            throw Error('course_identity');
          p.set(key,value);
        }
        for(const [id,value] of Object.entries({courseid:selected.courseId,
          clazzid:selected.classId,cpi:selected.cpi}))
          if(input(id)&&input(id)!==value)throw Error('course_identity');
        if(input('workEnc')){
          p.set('enc',input('workEnc'));p.set('ut','s');
          if(input('enc'))p.set('stuenc',input('enc'));
          if(input('t'))p.set('t',input('t'));
        }
        if(p.getAll('enc').length!==1||!p.get('enc')||target.href.length>4000||
          Array.from(p).some(([k,v])=>k.length>100||!tokenOk(k)||!tokenOk(v)))
          throw Error('invalid_response');
        location.replace(target.href);
        return 'navigating';
      }
    }catch(e){reply(e?.message==='course_identity'?'course_identity':'invalid_response');return 'blocked';}
  }
  (async()=>{
    const first=parse(document,path==='/visit/stucoursemiddle');
    if(first.found>0&&first.pages===1) {
      reply('',Array.from(all.values()));return;
    }
    // A visible first page is not the entire assignment history. If the
    // official entry also exposes a work token, fetch every bounded page
    // instead of aborting before reaching the completed-work pages.
    const partialEntry=first.found>0&&first.pages>1;
    all.clear();
    const published=!isMiddle?new URL(location.href).searchParams:null;
    const workEnc=input('workEnc')||published?.get('enc'),stuenc=input('enc'),openc=input('openc');
    if(!workEnc||![workEnc,stuenc,openc].every(tokenOk))
      throw Error(partialEntry?'partial_page':'work_entry_unavailable');
    // Match the existing verified server-side list contract instead of
    // creating a second, incomplete request shape for some course templates.
    const query=published?new URLSearchParams(published):new URLSearchParams({courseId:selected.courseId,
      classId:selected.classId,cpi:selected.cpi,ut:'s',enc:workEnc,
      t:String(Date.now()),isdisplaytable:'2'});
    if(stuenc)query.set('stuenc',stuenc);
    if(openc)query.set('openc',openc);
    const readList=async prefix=>{
      all.clear();
      let pages=1;
      for(let page=1;page<=pages;page++){
        query.set('pageNum',String(page));
        const response=await fetch(prefix+'?'+query.toString(),{
          method:'GET',credentials:'same-origin',redirect:'error',
          cache:'no-store'
        });
        if(!response.ok)throw Error('work_http');
        const html=await response.text();
        if(!html||html.length>420000)throw Error('invalid_response');
        const doc=new DOMParser().parseFromString(html,'text/html');
        if(/验证码|人机验证|访问受限/.test(doc.title||''))
          throw Error('interactive_login_required');
        const parsed=parse(doc,false);
        if(!parsed.found)throw Error('work_entry_unavailable');
        if(page===1)pages=parsed.pages;
        if(parsed.pages>pages)throw Error('partial_page');
      }
      if(!all.size)throw Error('work_entry_unavailable');
      return Array.from(all.values());
    };
    let error=null;
    for(const prefix of (published?[path]:['/mooc2/work/list','/mooc-ans/mooc2/work/list'])){
      try{
        const records=await readList(prefix);
        reply('',records);
        return;
      }catch(e){
        if(e?.message==='interactive_login_required'||
          e?.message==='invalid_response'||e?.message==='duplicate_work'||
          e?.message==='too_many_records'||e?.message==='too_many_pages')
          throw e;
        error=e;
      }
    }
    throw error||Error('work_entry_unavailable');
  })().catch(e=>{
    const permitted=new Set(['wrong_origin','course_identity','work_http',
      'work_entry_unavailable','partial_page','too_many_pages',
      'too_many_records','duplicate_work','interactive_login_required',
      'invalid_response']);
    reply(permitted.has(e?.message)?e.message:'work_scan_failed');
  });
  return 'started';
})()
