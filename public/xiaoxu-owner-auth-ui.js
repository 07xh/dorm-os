// 校序个人会话入口：仅展示服务端认证状态，不存储口令、Token 或用户资料。
(() => {
  'use strict';
  const link = document.querySelector('[data-owner-session-link]');
  if (!link) return;
  const show = (authenticated) => {
    link.textContent = authenticated ? '云端已连接' : '云端登录';
    link.setAttribute('aria-label', authenticated ? '管理当前设备的云端登录' : '登录个人云端');
    link.dataset.ownerSession = authenticated ? 'connected' : 'disconnected';
  };
  async function verify() {
    try {
      const response = await fetch('/api/v1/auth/me', {
        method: 'GET',
        credentials: 'same-origin',
        cache: 'no-store',
        headers: { accept: 'application/json' }
      });
      if (!response.ok) { show(false); return; }
      const status = await response.json();
      show(status?.authenticated === true);
    } catch {
      // 网络中断时不能假定私有会话仍然有效。
      show(false);
    }
  }
  void verify();
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) void verify();
  });
  window.addEventListener('pageshow', (event) => {
    if (event.persisted) void verify();
  });
})();
