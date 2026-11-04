// 校序唯一的个人会话 UI：首页原位登录 + /login/ 备用页共用同一控制器。
// 只把 localStorage 用作非敏感的“本设备曾登录”提示，鉴权完全由服务端完成。
(() => {
  'use strict';
  const key = 'xiaoxu_owner_login_seen';
  const durationKey = 'xiaoxu_owner_duration_days';
  const validDays = value => Number.isInteger(value) && value >= 1 && value <= 30;
  const presets = new Set([1, 7, 30]);
  const initialDays = (() => {
    try {
      const stored = localStorage.getItem(durationKey);
      if (!stored || !/^[0-9]{1,2}$/.test(stored)) return 7;
      const days = Number(stored);
      return validDays(days) ? days : 7;
    } catch { return 7; }
  })();
  let chosenDays = initialDays;
  let customMode = !presets.has(initialDays);
  let currentExpiry = null;
  const link = document.querySelector('[data-owner-session-link]');
  const surface = document.querySelector('[data-owner-auth-surface]');
  const dialog = surface?.tagName === 'DIALOG' ? surface : null;
  const form = surface?.querySelector('[data-owner-auth-form]');
  const input = surface?.querySelector('[data-owner-auth-password]');
  const submit = surface?.querySelector('[data-owner-auth-submit]');
  const logout = surface?.querySelector('[data-owner-auth-logout]');
  const result = surface?.querySelector('[data-owner-auth-result]');
  const title = surface?.querySelector('[data-owner-auth-title]');
  const toggle = surface?.querySelector('[data-owner-auth-toggle]');
  const close = surface?.querySelector('[data-owner-auth-close]');
  if (!link && !surface) return;

  const readHint = () => {
    try { return localStorage.getItem(key) === '1'; } catch { return false; }
  };
  const writeHint = active => {
    try {
      if (active) localStorage.setItem(key, '1');
      else localStorage.removeItem(key);
    } catch { /* Disabled storage must never affect authorization. */ }
  };
  const report = message => { if (result) result.textContent = message; };
  let connected = false;
  const updateDurationUI = () => {
    document.querySelectorAll('[data-owner-duration-select]').forEach(select => {
      select.value = customMode ? 'custom' : String(chosenDays);
    });
    document.querySelectorAll('[data-owner-duration-custom]').forEach(field => {
      field.hidden = !customMode;
      if (document.activeElement !== field) field.value = String(chosenDays);
    });
    document.querySelectorAll('[data-owner-duration-value]').forEach(value => {
      value.textContent = chosenDays + ' 天';
    });
    document.querySelectorAll('[data-owner-duration-choice]').forEach(button => {
      button.setAttribute('aria-pressed', String(button.dataset.ownerDurationChoice ===
        (customMode ? 'custom' : String(chosenDays))));
    });
    document.querySelectorAll('[data-owner-duration-custom-wrap]').forEach(wrap => {
      wrap.hidden = !customMode;
    });
    const summary = surface?.querySelector('[data-owner-duration-summary]');
    if (summary) summary.textContent = '本次登录保持 ' + chosenDays + ' 天';
  };
  const chooseDays = (days, custom = !presets.has(days)) => {
    if (!validDays(days)) return false;
    chosenDays = days;
    customMode = custom;
    try { localStorage.setItem(durationKey, String(days)); } catch {}
    updateDurationUI();
    return true;
  };
  const closeDurationPanel = root => {
    const panel = root?.querySelector('[data-owner-duration-panel]');
    const toggle = root?.querySelector('[data-owner-duration-toggle]');
    if (!panel || !toggle) return;
    panel.hidden = true;
    toggle.setAttribute('aria-expanded', 'false');
  };
  document.addEventListener('click', event => {
    const target = event.target;
    if (!(target instanceof Element)) return;
    const toggle = target.closest('[data-owner-duration-toggle]');
    if (toggle) {
      const root = toggle.closest('.xu-owner-duration-pref');
      const panel = root?.querySelector('[data-owner-duration-panel]');
      if (!panel) return;
      const expanded = panel.hidden;
      panel.hidden = !expanded;
      toggle.setAttribute('aria-expanded', String(expanded));
      return;
    }
    const choice = target.closest('[data-owner-duration-choice]');
    if (choice) {
      const root = choice.closest('.xu-owner-duration-pref');
      const raw = choice.dataset.ownerDurationChoice;
      const feedback = root?.querySelector('[data-owner-duration-feedback]');
      if (raw === 'custom') {
        customMode = true;
        updateDurationUI();
        if (feedback) feedback.textContent = '输入 1–30 之间的整数天数。';
        root?.querySelector('[data-owner-duration-custom]')?.focus({preventScroll:true});
      } else if (chooseDays(Number(raw), false)) {
        if (feedback) feedback.textContent = '已保存，下次登录生效。';
        closeDurationPanel(root);
      }
      return;
    }
    if (!target.closest('.xu-owner-duration-pref')) {
      document.querySelectorAll('.xu-owner-duration-pref').forEach(closeDurationPanel);
    }
  });
  document.addEventListener('keydown', event => {
    if (event.key !== 'Escape') return;
    const open = document.querySelector('.xu-owner-duration-pref [data-owner-duration-panel]:not([hidden])');
    if (!open) return;
    const root = open.closest('.xu-owner-duration-pref');
    closeDurationPanel(root);
    root?.querySelector('[data-owner-duration-toggle]')?.focus({preventScroll:true});
    event.preventDefault();
    event.stopPropagation();
  });
  document.addEventListener('change', event => {
    const target = event.target;
    if (!(target instanceof Element)) return;
    if (target.matches('[data-owner-duration-select]')) {
      const raw = target.value;
      if (raw === 'custom') {
        customMode = true;
        updateDurationUI();
        const field = target.closest('.xu-owner-duration-fields, .xu-owner-duration-pref, .duration-controls')?.querySelector('[data-owner-duration-custom]');
        field?.focus();
      } else chooseDays(Number(raw), false);
    } else if (target.matches('[data-owner-duration-custom]')) {
      const days = Number(target.value);
      const feedback = target.closest('.xu-owner-duration-pref')?.querySelector('[data-owner-duration-feedback]');
      if (!chooseDays(days, true)) {
        target.setCustomValidity('请输入 1～30 的整数天数');
        target.setAttribute('aria-invalid', 'true');
        if (feedback) feedback.textContent = '请输入 1–30 之间的整数天数。';
      } else {
        target.setCustomValidity('');
        target.removeAttribute('aria-invalid');
        if (feedback) feedback.textContent = '已保存，下次登录生效。';
      }
    }
  });
  // Notify the existing single-page workspace after the SERVER session check,
  // not merely because a local "previously logged in" hint exists.
  const publishAuthState = authenticated => {
    if (authenticated !== true) {
      // /login/ is a standalone fallback without the academic client.
      // Invalidate its pending QR ticket even if logout happens on that page.
      try { sessionStorage.removeItem('xiaoxu-chaoxing-qr-pending-v1'); } catch {}
    }
    window.dispatchEvent(new CustomEvent('xiaoxu-owner-auth-changed',{
      detail:{authenticated:authenticated===true}
    }));
  };
  const render = authenticated => {
    connected = authenticated === true;
    document.querySelectorAll('[data-owner-auth-open]').forEach(entry => {
      const label = entry.querySelector('[data-owner-auth-entry-title]');
      const sub = entry.querySelector('[data-owner-auth-entry-subtitle]');
      if (label) label.textContent = connected ? '云端已连接' : '登录校序';
      if (sub) sub.textContent = connected ? '查看或退出当前设备' : '管理个人云端登录';
    });
    if (link) {
      link.textContent = authenticated ? '云端已连接' : '云端登录';
      link.setAttribute('aria-label', authenticated ? '管理本人校序登录' : '登录校序');
      link.dataset.ownerSession = authenticated ? 'connected' : 'disconnected';
    }
    if (form) form.hidden = authenticated;
    if (logout) logout.hidden = !authenticated;
    if (title) title.textContent = authenticated ? '已登录' : dialog ? '登录校序' : '登录';
    updateDurationUI();
  };
  async function verify({notify = false} = {}) {
    try {
      const response = await fetch('/api/v1/auth/me', {
        method:'GET', credentials:'same-origin', cache:'no-store',
        headers:{accept:'application/json'}
      });
      if (!response.ok) {
        currentExpiry = null;
        render(false);
        if (response.status === 401 || response.status === 503) {
          writeHint(false);
          publishAuthState(false);
        }
        if (notify && response.status === 503) report('登录服务尚未启用。');
        return false;
      }
      const payload = await response.json();
      const verified = payload?.authenticated === true;
      currentExpiry = verified && typeof payload.expiresAt === 'string' ? payload.expiresAt : null;
      render(verified);
      writeHint(verified);
      publishAuthState(verified);
      if (notify && verified) {
        const date = currentExpiry && Number.isFinite(Date.parse(currentExpiry))
          ? new Date(currentExpiry).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai'}) : '';
        report(date ? '当前会话有效至 ' + date + '。修改登录时间将在下次登录时生效。' : '当前设备已登录。');
      }
      return verified;
    } catch {
      render(false);
      if (notify) report('网络暂时不可用，请稍后重试。');
      return false;
    }
  }

  window.XiaoxuOwnerAuth = Object.freeze({
    connected: () => connected,
    durationDays: () => chosenDays,
    isCustomDuration: () => customMode
  });
  const openDialog = () => {
    if (!dialog || typeof dialog.showModal !== 'function') {
      location.assign('/login/');
      return;
    }
    report('');
    try { if (!dialog.open) dialog.showModal(); }
    catch { location.assign('/login/'); return; }
    updateDurationUI();
    // This explicit click is the first private session check for new visitors.
    void verify({notify:readHint()});
    if (connected) logout?.focus({preventScroll:true});
    else input?.focus({preventScroll:true});
  };
  // The "我的" profile view re-renders dynamically, so delegate its one login entry.
  document.addEventListener('click', event => {
    const entry = event.target instanceof Element ? event.target.closest('[data-owner-auth-open]') : null;
    if (!entry) return;
    // A real link still works when JS fails, avoiding a dead mobile control.
    if (!dialog || typeof dialog.showModal !== 'function') return;
    event.preventDefault();
    openDialog();
  });
  link?.addEventListener('click', event => {
    if (!dialog) return; // No modal support: /login/ remains the fallback.
    event.preventDefault();
    openDialog();
  });
  render(false); // No automatic private API call for an unconnected visitor.
  if (dialog) {
    close?.addEventListener('click', () => dialog.close());
    dialog.addEventListener('click', event => {
      if (event.target === dialog) dialog.close();
    });
    dialog.addEventListener('close', () => {
      if (input) input.value = '';
      if (toggle && input) {
        input.type = 'password';
        toggle.textContent = '显示';
        toggle.setAttribute('aria-label','显示密码');
      }
      report('');
    });
  }

  toggle?.addEventListener('click', () => {
    if (!input) return;
    const visible = input.type === 'password';
    input.type = visible ? 'text' : 'password';
    toggle.textContent = visible ? '隐藏' : '显示';
    toggle.setAttribute('aria-label', visible ? '隐藏密码' : '显示密码');
    input.focus({preventScroll:true});
  });

  form?.addEventListener('submit', async event => {
    event.preventDefault();
    if (!input || !submit) return;
    submit.disabled = true;
    report('正在验证…');
    try {
      const response = await fetch('/api/v1/auth/login', {
        method:'POST', credentials:'same-origin', cache:'no-store',
        headers:{'content-type':'application/json'},
        body:JSON.stringify({passphrase:input.value,durationDays:chosenDays})
      });
      input.value = '';
      if (response.ok) {
        const payload = await response.json().catch(() => ({}));
        currentExpiry = typeof payload.expiresAt === 'string' ? payload.expiresAt : null;
        writeHint(true);
        render(true);
        publishAuthState(true);
        if (dialog) {
          dialog.close();
        } else {
          report('登录成功，正在进入校序…');
          location.assign('/');
        }
        return;
      }
      const data = await response.json().catch(() => ({}));
      report(response.status === 429 ? '尝试过多，请 15 分钟后重试。' :
        response.status === 503 ? '登录服务未启用或数据库未就绪。' :
        response.status === 401 ? '密码错误，请重试。' :
        '登录失败：' + (typeof data.error === 'string' ? data.error : response.status));
    } catch {
      report('网络异常，请重试。');
    } finally {
      submit.disabled = false;
    }
  });

  logout?.addEventListener('click', async () => {
    logout.disabled = true;
    report('正在退出…');
    try {
      const response = await fetch('/api/v1/auth/logout', {
        method:'POST', credentials:'same-origin', cache:'no-store'
      });
      if (!response.ok) throw Error('logout_failed');
      writeHint(false);
      currentExpiry = null;
      render(false);
      publishAuthState(false);
      if (input) input.value = '';
      report('已退出当前设备。');
    } catch {
      report('退出失败，请检查网络后重试。');
    } finally {
      logout.disabled = false;
    }
  });

  if (dialog) {
    if (readHint()) void verify();
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden && readHint()) void verify();
    });
    window.addEventListener('pageshow', event => {
      if (event.persisted && readHint()) void verify();
    });
  } else if (surface) {
    // Standalone fallback: visiting /login/ counts as an explicit login intent.
    void verify();
  }
})();
