// 校序唯一的个人会话 UI：首页原位登录 + /login/ 备用页共用同一控制器。
// 只把 localStorage 用作非敏感的“本设备曾登录”提示，鉴权完全由服务端完成。
(() => {
  'use strict';
  const key = 'xiaoxu_owner_login_seen';
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
  const render = authenticated => {
    if (link) {
      link.textContent = authenticated ? '云端已连接' : '云端登录';
      link.setAttribute('aria-label', authenticated ? '管理本人校序登录' : '登录校序');
      link.dataset.ownerSession = authenticated ? 'connected' : 'disconnected';
    }
    if (form) form.hidden = authenticated;
    if (logout) logout.hidden = !authenticated;
    if (title) title.textContent = authenticated ? '已登录' : dialog ? '登录校序' : '登录';
  };
  async function verify({notify = false} = {}) {
    try {
      const response = await fetch('/api/v1/auth/me', {
        method:'GET', credentials:'same-origin', cache:'no-store',
        headers:{accept:'application/json'}
      });
      if (!response.ok) {
        render(false);
        if (response.status === 401 || response.status === 503) writeHint(false);
        if (notify && response.status === 503) report('登录服务尚未启用。');
        return false;
      }
      const payload = await response.json();
      const verified = payload?.authenticated === true;
      render(verified);
      writeHint(verified);
      if (notify && verified) report('当前设备已登录。');
      return verified;
    } catch {
      render(false);
      if (notify) report('网络暂时不可用，请稍后重试。');
      return false;
    }
  }

  if (link) {
    render(false); // A new visitor must not trigger private API queries.
    link.addEventListener('click', event => {
      if (!dialog || typeof dialog.showModal !== 'function') return;
      event.preventDefault();
      report('');
      dialog.showModal();
      if (readHint()) {
        void verify({notify:true});
      } else {
        // Clicking the login control is an explicit request to check this session.
        void verify();
      }
      if (!logout?.hidden) logout.focus({preventScroll:true});
      else input?.focus({preventScroll:true});
    });
  }
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
        body:JSON.stringify({passphrase:input.value})
      });
      input.value = '';
      if (response.ok) {
        writeHint(true);
        render(true);
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
      render(false);
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
