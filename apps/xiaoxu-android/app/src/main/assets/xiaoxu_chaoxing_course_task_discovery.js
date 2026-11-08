/*
 * Xiaoxu owner-initiated course/task discovery.
 * Runs ONLY in the signed-in official first-party WebView origin.
 * Outputs numeric course/task IDs and labels, never session credentials.
 * No form submissions, page navigation, homework actions or third-party calls.
 */
(function () {
  const number = value => /^\d{1,24}$/.test(String(value ?? ''));
  const clean = value => String(value ?? '').replace(/[\u0000-\u001f\u007f]/g, ' ')
    .replace(/\s+/g, ' ').trim().slice(0, 100);
  const reply = payload => JSON.stringify(payload);
  const url = new URL(location.href);
  if (url.protocol !== 'https:') return reply({error:'wrong_origin'});
  if (url.hostname === 'i.chaoxing.com' && url.pathname === '/base') {
    // School FID is a public numeric tenant identifier; no password, UID or
    // browser cookie is read or returned.
    let fid = '';
    for (const node of Array.from(document.scripts || []).slice(0,80)) {
      const source=String(node.textContent || '').slice(0,40000);
      const match=source.match(/(?:var|let|const)\s+fid\s*=\s*["']?(\d{1,20})["']?/);
      if (match) { fid=match[1]; break; }
    }
    return reply(number(fid)?{kind:'profile',fid}:{error:'school_identity'});
  }
  if (url.hostname === 'mooc2-ans.chaoxing.com' &&
      url.pathname === '/mooc2-ans/visit/courselistdata') {
    const courses=[], seen=new Set();
    for (const link of Array.from(document.querySelectorAll('a[href*="stucoursemiddle"]'))) {
      const href = new URL(link.getAttribute('href') || '', location.href);
      if (href.protocol !== 'https:' ||
          !['mooc2-ans.chaoxing.com','mooc1.chaoxing.com'].includes(href.hostname) ||
          !href.pathname.endsWith('/stucoursemiddle')) continue;
      const ids=['courseid','clazzid','cpi'].map(key=>href.searchParams.getAll(key));
      if (ids.some(values=>values.length!==1||!number(values[0]))) continue;
      const [courseId,classId,cpi]=ids.map(values=>values[0]);
      const key=courseId+':'+classId+':'+cpi;
      if (seen.has(key)) continue;
      const title=clean(link.querySelector('.course-name')?.getAttribute('title') ||
        link.querySelector('.course-name')?.textContent ||
        link.parentElement?.querySelector('.course-name')?.getAttribute('title') ||
        link.parentElement?.querySelector('.course-name')?.textContent);
      if (!title) continue;
      if (courses.length >= 36) return reply({error:'too_many_courses'});
      seen.add(key);
      courses.push({courseId,classId,cpi,title});
    }
    return reply(courses.length?{kind:'courses',courses}:{error:'courses_unverified'});
  }
  // mobilelearn may redirect its homepage to another first-party path.
  // The only API ever called remains the fixed read-only getData endpoint.
  if (url.hostname !== 'mobilelearn.chaoxing.com') {
    return reply({error:'wrong_origin'});
  }
  const selected = window.__xiaoxuOwnedCourse;
  if (!selected || !number(selected.courseId) || !number(selected.classId))
    return reply({error:'course_identity'});
  const epoch=(Number(window.__xiaoxuTaskDiscoveryEpoch)||0)+1;
  window.__xiaoxuTaskDiscoveryEpoch=epoch;
  window.__xiaoxuTaskDiscoveryResult='';
  (async()=>{
    const api='/v2/apis/active/getData?DB_STRATEGY=DEFAULT&courseId='+
      encodeURIComponent(selected.courseId)+'&classId='+encodeURIComponent(selected.classId);
    const response=await fetch(api,{
      method:'GET',credentials:'same-origin',redirect:'error',cache:'no-store'
    });
    if (!response.ok) throw Error('course_task_http');
    const body=await response.text();
    if (body.length > 180000) throw Error('invalid_response');
    const parsed=JSON.parse(body);
    if (parsed?.success===false || parsed?.result===false)
      throw Error('course_task_http');
    const routes=[
      parsed?.data,parsed?.data?.data,parsed?.data?.list,
      parsed?.data?.taskList,parsed?.data?.taskListList,
      parsed?.data?.tasks,parsed?.data?.activeList,
      parsed?.data?.records,parsed?.list,parsed?.taskList,
      parsed?.taskListList,parsed?.tasks,parsed?.activeList,
      parsed?.records,parsed
    ];
    const list=routes.find(Array.isArray);
    if (!list || list.length>80) throw Error('invalid_response');
    const tasks=[],seen=new Set();
    for (const item of list) {
      if(!item||typeof item!=='object'||Array.isArray(item))
        throw Error('invalid_response');
      const taskId=String(item.id ?? item.taskId ?? '');
      if (!number(taskId)) continue;
      if (seen.has(taskId)) continue;
      seen.add(taskId);
      const title=clean(item.name || item.nameOne || item.title || '学习计划');
      tasks.push({taskId,classId:selected.classId,title});
      if (tasks.length>30) throw Error('too_many_tasks');
    }
    if (epoch===window.__xiaoxuTaskDiscoveryEpoch)
      window.__xiaoxuTaskDiscoveryResult=reply({kind:'tasks',tasks});
  })().catch(e=>{
    const allowed=['course_task_http','invalid_response','too_many_tasks'];
    if(epoch===window.__xiaoxuTaskDiscoveryEpoch)
      window.__xiaoxuTaskDiscoveryResult=reply({
        error:allowed.includes(e?.message)?e.message:'task_discovery_failed'
      });
  });
  return reply({kind:'started'});
})()
