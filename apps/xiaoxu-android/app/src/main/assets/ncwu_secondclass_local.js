// Fixed, device-local read. Never read inputs, cookies, storage or full URLs.
(async function readSecondClassScores(accountSalt = '') {
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
  const timer = setTimeout(()=>controller.abort(),15000);
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
    let accountHash;
    if (/^[a-f0-9]{32}$/.test(accountSalt)) {
      const digest = await crypto.subtle.digest('SHA-256',new TextEncoder().encode(accountSalt+':'+expected));
      accountHash = Array.from(new Uint8Array(digest),b=>b.toString(16).padStart(2,'0')).join('');
      const finalIdentity = visibleIdentity();
      if (!context() || finalIdentity.length !== 1 || finalIdentity[0] !== expected)
        return fail('identity','visible_account_changed');
    }
    return {...base,...diagnostic,ok:true,identityMatched:true,rows,...(accountHash?{accountHash}:{})};
  } catch { return fail('score_request','network_redirect_or_timeout'); }
  finally { clearTimeout(timer); }
})
