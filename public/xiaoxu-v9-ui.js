
/* 校序 V9：增强已有导航，不创建第二套路由、登录或数据源。 */
(() => {
 'use strict';
 const nav=document.getElementById('sidebarNav');
 if(!nav)return;
 const ns='http://www.w3.org/2000/svg';
 const paths={
  dashboard:'<path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z"/>',
  timetable:'<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v4m10-4v4M3 10h18M7 14h3m4 0h3M7 18h3"/>',
  academic:'<path d="M4 19V7l8-4 8 4v12M2 21h20M9 21v-6h6v6M8 9h.01M16 9h.01"/>',
  tasks:'<rect x="4" y="4" width="16" height="17" rx="2"/><path d="m8 11 2 2 4-4M8 17h8"/>',
  profile:'<circle cx="12" cy="8" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/>'
 };
 nav.querySelectorAll('button[data-page]').forEach(button=>{
  if(button.querySelector('.x-v9-nav-svg'))return;
  const icon=document.createElementNS(ns,'svg');
  icon.setAttribute('viewBox','0 0 24 24');
  icon.setAttribute('class','x-v9-nav-svg');
  icon.setAttribute('aria-hidden','true');
  icon.innerHTML=paths[button.dataset.page]||'';
  const label=document.createElement('span');
  label.className='x-v9-nav-text';
  label.textContent=button.textContent.trim();
  button.replaceChildren(icon,label);
 });
 const motionEnabled=window.matchMedia('(prefers-reduced-motion: no-preference)');
 let motionCleanup=0;
 document.addEventListener('click',event=>{
  const button=event.target.closest?.('[data-page]');
  if(!button||!button.closest('#sidebarNav,#app')||!motionEnabled.matches)return;
  const target=button.dataset.page;
  if(!target||target===(location.hash.slice(1)||'dashboard'))return;
  document.documentElement.classList.add('x-v9-page-changing');
  clearTimeout(motionCleanup);
  motionCleanup=setTimeout(()=>document.documentElement.classList.remove('x-v9-page-changing'),270);
 },true);
})();
