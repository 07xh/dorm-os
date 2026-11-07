// Fixed, device-local read. Never read inputs, cookies, storage or full URLs.
(async function readSecondClassScores(accountSalt = '', includeActivities = false, includeCatalog = false) {
  const host = 'https://dekt.ncwu.edu.cn';
  const path = '/sys/stu/score/list';
  const base = {schema:'xiaoxu/local-secondclass-scores-v1',
    fullSyncVerified:false,nativeSessionHandoffVerified:false};
  const diagnostic = {httpStatus:null,contentType:null};
  const fail = (stage,reason) => ({...base,...diagnostic,ok:false,stage,reason});
  const context = () => location.origin === host && location.pathname === '/sys/stu/score';
  if (!context()) return fail('context','open_official_score_page');
  const visibleIdentity = () => [...new Set(Array.from(document.querySelectorAll('h3,h4'))
    .filter(el=>el.getClientRects().length)
    .map(el=>(el.innerText||'').match(/\[([A-Za-z0-9_-]{1,50})\]/)?.[1])
    .filter(Boolean))];
  const unique = visibleIdentity();
  if (unique.length !== 1) return fail('identity','visible_account_required');
  const expected = unique[0];
  const controller = new AbortController();
  const timer = setTimeout(()=>controller.abort(),includeActivities||includeCatalog?60000:15000);
  let requestStage = 'score_request';
  try {
    const response = await fetch(host+path,{
      method:'POST',credentials:'same-origin',redirect:'error',cache:'no-store',
      headers:{accept:'application/json','content-type':'application/x-www-form-urlencoded; charset=UTF-8',
        'x-requested-with':'XMLHttpRequest'},body:'',signal:controller.signal
    });
    diagnostic.httpStatus = response.status;
    const type = response.headers.get('content-type');
    diagnostic.contentType = type && /^[\w.+-]+\/[\w.+-]+(?:\s*;\s*charset=[\w-]+)?$/i.test(type)
      ? type : null;
    if (response.status !== 200) return fail('score_http',
      response.status === 401 || response.status === 403 ? 'authentication_required' : 'http_failure');
    if (response.redirected || response.url !== host+path || !context())
      return fail('context','page_changed');
    let raw;
    try {
      const text = await response.text();
      if (text.length > 300000) return fail('score_response','response_too_large');
      raw = JSON.parse(text);
    } catch { return fail('score_response','not_business_json'); }
    if (!context()) return fail('context','page_changed');
    const currentIdentity = visibleIdentity();
    if (currentIdentity.length !== 1 || currentIdentity[0] !== expected)
      return fail('identity','visible_account_changed');
    if (!raw || !Array.isArray(raw.data) || raw.data.length < 1 || raw.data.length > 50 ||
        !Number.isSafeInteger(raw.totalSize) || raw.totalSize !== raw.data.length ||
        !Number.isSafeInteger(raw.totalPage) || raw.totalPage < 0 || raw.totalPage > 1 ||
        raw.startIndex !== 0) return fail('score_response','incomplete_or_invalid_rows');
    let identified = 0;
    for (const row of raw.data) {
      if (!row || typeof row !== 'object') return fail('score_record','invalid_row');
      // A school aggregate may omit identity, but it cannot prove identity itself.
      if (row.school_term_index !== 100 || row.student_code != null) {
        if (typeof row.student_code !== 'string' || row.student_code !== expected)
          return fail('identity','account_mismatch_or_missing_identity');
        if (row.school_term_index !== 100) identified++;
      }
    }
    if (!identified) return fail('identity','identified_semester_required');
    const seen = new Set();
    const number = n => typeof n === 'number' && Number.isFinite(n) && n >= 0 && n <= 100000;
    const rows = [];
    for (const row of raw.data) {
      const index = row.school_term_index;
      const categories = Array.from({length:6},(_,i)=>row['activity_type'+(i+1)+'_score']);
      if (!Number.isInteger(index) || index < 1 || index > 100 || seen.has(index) ||
          !number(row.score_value) || !number(row.score_plus) || !categories.every(number))
        return fail('score_record','invalid_score_fields');
      seen.add(index);
      rows.push({termIndex:index,qualityScore:row.score_value,
        volunteerHours:row.score_plus,categories});
    }
    const activities = [];
    if (includeActivities) {
      requestStage = 'activity_request';
      const activityPath = '/sys/stu/center/signupactivity/list';
      let total = null, pageCount = null;
      const ids = new Set();
      for (let page=1;page<=25;page++) {
        diagnostic.httpStatus = null;
        diagnostic.contentType = null;
        const res = await fetch(host+activityPath,{
          method:'POST',credentials:'same-origin',redirect:'error',cache:'no-store',
          headers:{accept:'application/json','content-type':'application/x-www-form-urlencoded; charset=UTF-8',
            'x-requested-with':'XMLHttpRequest'},body:'page='+page+'&pagesize=50',signal:controller.signal
        });
        diagnostic.httpStatus = res.status;
        const activityType=res.headers.get('content-type');
        diagnostic.contentType=activityType&&/^[\w.+-]+\/[\w.+-]+(?:\s*;\s*charset=[\w-]+)?$/i.test(activityType)?activityType:null;
        if (res.status !== 200) return fail('activity_http','http_failure');
        if (res.redirected || res.url !== host+activityPath || !context()) return fail('context','page_changed');
        let batch;
        try { const text=await res.text();if(text.length>300000)return fail('activity_response','response_too_large');batch=JSON.parse(text); }
        catch { return fail('activity_response','not_business_json'); }
        const nowIdentity=visibleIdentity();
        if(!context()||nowIdentity.length!==1||nowIdentity[0]!==expected)return fail('identity','visible_account_changed');
        if(!batch||!Array.isArray(batch.data)||batch.data.length>50||
          !Number.isSafeInteger(batch.totalPage)||batch.totalPage<0||batch.totalPage>25||
          !Number.isSafeInteger(batch.totalSize)||batch.totalSize<0||batch.totalSize>250||
          !Number.isSafeInteger(batch.startIndex)||batch.startIndex<0)
          return fail('activity_response','invalid_pagination');
        if(total===null){total=batch.totalSize;pageCount=batch.totalPage;}
        if(total!==batch.totalSize||pageCount!==batch.totalPage)return fail('activity_response','inconsistent_total');
        if(page===1&&total===0&&pageCount===0&&batch.data.length===0)break;
        if(pageCount<page||!batch.data.length||total<batch.data.length)return fail('activity_response','incomplete_page');
        for(const item of batch.data){
          if(!item||item.student_code!==expected)return fail('identity','activity_account_mismatch');
          if(typeof item.id!=='string'||!/^[A-Za-z0-9:_-]{1,120}$/.test(item.id)||ids.has(item.id)||
            typeof item.activity_name!=='string'||!item.activity_name.trim()||item.activity_name.length>120)
            return fail('activity_record','invalid_or_duplicate_activity');
          let startTime=null;
          if(item.activity_start!=null&&item.activity_start!==''){
            if(typeof item.activity_start!=='string'||!/^20\d{2}-\d{2}-\d{2}(?: \d{2}:\d{2}:\d{2})?$/.test(item.activity_start))
              return fail('activity_record','invalid_activity_time');
            const date=new Date(item.activity_start.replace(' ','T')+(item.activity_start.length===10?'T00:00:00':'')+'Z');
            if(!Number.isFinite(date.getTime())||date.toISOString().slice(0,10)!==item.activity_start.slice(0,10))
              return fail('activity_record','invalid_activity_time');
            startTime=item.activity_start;
          }
          const settlementStatus=item.verify_state==='2'||item.verify_state==='1'&&item.activity_state==='5'?'settled':
            item.verify_state==='1'&&item.activity_state==='4'?'unsettled':
            item.verify_state==='1'&&item.activity_state==='6'?'awaiting_settlement':
            'unknown';
          if(item.score_value!=null&&!number(item.score_value))return fail('activity_record','invalid_points');
          ids.add(item.id);
          activities.push({id:item.id,title:item.activity_name.trim(),startTime,
            signupStatus:item.signup_state==='1'?'registered':'unknown',reviewStatus:'unknown',
            verificationCode:typeof item.verify_state==='string'&&/^[0-9]$/.test(item.verify_state)?item.verify_state:null,
            settlementStatus,...(settlementStatus==='settled'&&item.score_value!=null?{points:item.score_value}:{})});
        }
        if(activities.length>250||activities.length>total)return fail('activity_response','too_many_records');
        if(page===pageCount){if(activities.length!==total)return fail('activity_response','incomplete_total');break;}
      }
    }
    // Use the same identity-checked official session as the verified score read.
    // Prior observations only proved empty envelopes; nonempty rows are now
    // projected strictly from a complete single-page official JSON response.
    // No raw response, session, student identity, HTML, or external ID crosses to Android.
    let catalogStatus='not_requested';
    let catalogEmptyFilters=[];
    let catalogActivities=[];
    if(includeCatalog){
      catalogStatus='verified_empty';
      const catalogPath='/sys/stu/activity';
      const filters=['报名中','待验证','即将开始报名'];
      for(const title of filters){
        requestStage='catalog_request';
        let res;
        try{
          res=await fetch(host+catalogPath,{
            method:'POST',credentials:'same-origin',redirect:'error',cache:'no-store',
            headers:{accept:'application/json',
              'content-type':'application/x-www-form-urlencoded; charset=UTF-8',
              'x-requested-with':'XMLHttpRequest'},
            body:'title='+encodeURIComponent(title),signal:controller.signal
          });
        }catch{catalogStatus='unavailable';break;}
        if(res.status!==200||res.redirected||res.url!==host+catalogPath||
           !context()){catalogStatus='unavailable';break;}
        requestStage='catalog_response';
        let data;
        try{
          const text=await res.text();
          if(text.length>100000){catalogStatus='unavailable';break;}
          data=JSON.parse(text);
        }catch{catalogStatus='unavailable';break;}
        const current=visibleIdentity();
        if(!context()||current.length!==1||current[0]!==expected)
          return fail('identity','visible_account_changed');
        if(!data||!Array.isArray(data.data)||
           !Number.isSafeInteger(data.totalSize)||data.totalSize<0||
           !Number.isSafeInteger(data.totalPage)||data.totalPage<0||
           !Number.isSafeInteger(data.startIndex)||data.startIndex!==0){
          catalogStatus='unavailable';break;
        }
        if(data.data.length===0&&data.totalSize===0&&data.totalPage===0){
          catalogEmptyFilters.push(title);
          continue;
        }
        // We have no confirmed pagination request contract for this endpoint.
        // Never misrepresent a partial catalog as the complete activity list.
        if(data.totalSize<1||data.totalSize!==data.data.length||
           data.totalPage!==1||data.data.length>50){
          catalogStatus='nonempty_unverified';break;
        }
        const names=new Set();
        const projected=[];
        for(const raw of data.data){
          if(!raw||typeof raw!=='object'||Array.isArray(raw)){
            catalogStatus='nonempty_unverified';break;
          }
          // activity_name is the existing first-party second-class business field.
          // No arbitrary object text, ID, owner, cookie or HTML is returned.
          const name=raw.activity_name;
          if(typeof name!=='string'){
            catalogStatus='nonempty_unverified';break;
          }
          const clean=name.replace(/\\s+/g,' ').trim();
          if(clean.length<2||clean.length>120||/[<>\\u0000-\\u001f]/.test(clean)){
            catalogStatus='nonempty_unverified';break;
          }
          // Equal titles can legitimately represent different events.
          projected.push({filter:title,title:clean,ordinal:projected.length});
          names.add(clean);
        }
        if(catalogStatus!=='verified_empty')break;
        catalogActivities.push(...projected);
        if(catalogActivities.length>120){catalogStatus='nonempty_unverified';break;}
      }
      if(catalogStatus==='verified_empty'&&catalogActivities.length)
        catalogStatus='verified_nonempty';
      if(catalogStatus!=='verified_empty'&&catalogStatus!=='verified_nonempty'){
        catalogEmptyFilters=[];
        catalogActivities=[];
      }
    }
    let accountHash;
    if (/^[a-f0-9]{32}$/.test(accountSalt)) {
      const digest = await crypto.subtle.digest('SHA-256',new TextEncoder().encode(accountSalt+':'+expected));
      accountHash = Array.from(new Uint8Array(digest),b=>b.toString(16).padStart(2,'0')).join('');
      const finalIdentity = visibleIdentity();
      if (!context() || finalIdentity.length !== 1 || finalIdentity[0] !== expected)
        return fail('identity','visible_account_changed');
    }
    return {...base,...diagnostic,ok:true,identityMatched:true,rows,...(accountHash?{accountHash}:{}),
      ...(includeActivities?{activitiesComplete:true,activities}: {}),
      ...(includeCatalog?{catalogStatus,catalogEmptyFilters,catalogActivities}: {})};
  } catch { return fail(requestStage,'network_redirect_or_timeout'); }
  finally { clearTimeout(timer); }
})
