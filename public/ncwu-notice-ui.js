/* 校序官方通知视图：只读公共档案；完全独立于个人课程/任务和 NFC 数据。 */
(function () {
  'use strict';
  const state = { data:null, loading:false, error:'', filter:'all', query:'' };
  const esc = x => String(x ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const official = href => {
    try {
      const u = new URL(href);
      return u.protocol === 'https:' && ['www.ncwu.edu.cn','www2.ncwu.edu.cn'].includes(u.hostname) &&
        /^\/(?:xsc\/)?info\/\d+\/\d+\.htm$/i.test(u.pathname);
    } catch { return false; }
  };
  function items() {
    return Array.isArray(state.data?.items) ? state.data.items.filter(x => x && typeof x.title === 'string' && official(x.url)) : [];
  }
  function status() {
    if (!state.data) return state.error ? '通知档案暂不可用，请查看学校官网' : state.loading ? '正在读取通知档案…' : '未读取通知档案';
    const last = state.data.lastSuccessAt;
    if (!last) return '官网原文已核对 · 定时同步尚未验收';
    const t = new Date(last);
    if (!Number.isFinite(t.getTime())) return '最近同步时间未确认';
    const readable = t.toLocaleString('zh-CN', {timeZone:'Asia/Shanghai',month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit'});
    return (Date.now() - t.getTime() > 72 * 3600_000 ? '更新较久 · 上次成功 ' : '最近成功同步 ') + readable;
  }
  function rows() {
    const q = state.query.trim().toLowerCase();
    const filtered = items().filter(x => (state.filter === 'all' || x.sourceId === state.filter) &&
      (!q || (x.title + ' ' + x.sourceName).toLowerCase().includes(q))).slice(0, 60);
    return filtered.length ? filtered.map(x =>
      '<a class="x-notice-item" href="' + esc(x.url) + '" target="_blank" rel="noopener noreferrer">' +
      '<strong>' + esc(x.title) + '</strong><small>' + esc(x.sourceName || '学校官网') +
      ' · ' + esc(x.date || '日期待核对') + ' ↗</small></a>').join('') :
      '<p class="small muted">暂无符合条件的官方通知，可切换来源或查看官网原文。</p>';
  }
  function home() {
    const list = items().slice(0, 2);
    if (!list.length) return '<p class="small muted">' + esc(status()) + '</p>';
    return '<p class="small muted">' + esc(status()) + '</p><div class="x-notice-home-items">' +
      list.map(x => '<a class="x-notice-home-link" href="' + esc(x.url) +
      '" target="_blank" rel="noopener noreferrer"><span>' + esc(x.title) + '</span><small>' +
      esc(x.date || x.sourceName) + ' ↗</small></a>').join('') + '</div>';
  }
  function academic() {
    const sources = Array.isArray(state.data?.sources) ? state.data.sources : [];
    const filters = [{id:'all',name:'全部'},...sources.map(x => ({id:x.id,name:x.name}))];
    return '<section class="card x-v7-academic-card x-notice-feed">' +
      '<div class="x-notice-header"><div><h2>华水官方通知</h2><p class="small muted">' +
      esc(status() + (state.error && state.data ? ' · 本次刷新失败，保留旧数据' : '')) + '</p></div>' +
      '<button type="button" class="action-link" data-notice-refresh ' + (state.loading ? 'disabled' : '') + '>刷新</button></div>' +
      (state.data?.mode === 'seed' ? '<p class="small muted">以下是真实官网原文核对记录；首次定时采集成功后才标记为自动同步。</p>' : '') +
      '<div class="x-notice-toolbar"><input id="xiaoxu-notice-search" type="search" maxlength="60" aria-label="搜索官方通知" placeholder="搜索通知标题" value="' + esc(state.query) + '">' +
      '<div class="x-notice-filters" role="group" aria-label="通知来源">' + filters.map(s =>
      '<button type="button" class="x-notice-filter ' + (state.filter === s.id ? 'active' : '') +
      '" data-notice-filter="' + esc(s.id) + '" aria-pressed="' + (state.filter === s.id) + '">' +
      esc(s.name) + '</button>').join('') + '</div></div>' +
      '<div class="x-notice-list" id="xiaoxu-notice-items">' + rows() + '</div>' +
      '<div class="x-notice-footer"><span>仅读取学校公开标题、日期及原文链接</span>' +
      '<a href="https://www.ncwu.edu.cn/xxtz/jwc.htm" rel="noopener noreferrer" target="_blank">教务处原文 ↗</a></div></section>';
  }
  function rerender() { if (typeof window.render === 'function') window.render(); }
  async function refresh() {
    if (state.loading) return;
    state.loading = true; state.error = ''; rerender();
    const abort = new AbortController(), timer = setTimeout(() => abort.abort(), 12000);
    try {
      const response = await fetch('/data/notices.json', {cache:'no-store', signal:abort.signal});
      if (!response.ok) throw new Error('HTTP ' + response.status);
      const data = await response.json();
      if (data?.schema !== 'xiaoxu/notices-v1' || data.school !== '4141010078' || !Array.isArray(data.items)) throw new Error('通知格式无效');
      state.data = data;
    } catch (e) {
      state.error = e?.name === 'AbortError' ? '读取超时' : '读取失败';
    } finally {
      clearTimeout(timer); state.loading = false; rerender();
    }
  }
  document.addEventListener('click', e => {
    const button = e.target.closest('[data-notice-filter],[data-notice-refresh]');
    if (!button) return;
    e.preventDefault(); e.stopPropagation();
    if (button.hasAttribute('data-notice-filter')) {
      state.filter = button.dataset.noticeFilter; rerender();
    } else refresh();
  }, true);
  document.addEventListener('input', e => {
    if (e.target.id !== 'xiaoxu-notice-search') return;
    state.query = e.target.value;
    const content = document.getElementById('xiaoxu-notice-items');
    if (content) content.innerHTML = rows();
  });
  window.XiaoxuNoticeFeed = { home, academic, refresh };
})();