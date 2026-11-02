import {test,expect} from '@playwright/test';

const row=id=>({
 id:'notices:'+id,source:'notices',category:'notice',sourceLabel:'通知公告',
 title:'关于2026学年校园课程安排的官方通知 '+id,
 url:'https://www2.ncwu.edu.cn/jiaowu/info/1008/'+id+'.htm',
 dateLabel:'11-03',publishedAt:null
});
const reply=items=>({success:true,partial:false,items,
  fetchedAt:'2026-11-03T08:00:00.000Z',source:'NCWU'});
test('browser notifications require an explicit opt-in and can be disabled',async({page,context})=>{
 await context.grantPermissions(['notifications']);
 await page.route('**/api/notices',r=>r.fulfill({status:200,contentType:'application/json',body:JSON.stringify(reply([row(3003),row(3004)]))}));
 await page.route('**/data/notices.json',r=>r.fulfill({status:503,contentType:'application/json',body:'{}'}));
 await page.goto('/#academic');
 await expect(page.locator('.x-v7-notice-row')).toHaveCount(2);
 expect(await page.evaluate(()=>localStorage.getItem('xiaoxu-notice-browser-alert-v1'))).toBe(null);
 const toggle=page.locator('[data-action="notice-alert-toggle"]');
 await expect(toggle).toContainText('开启浏览器提醒');
 await toggle.click();
 await expect(toggle).toContainText('关闭浏览器提醒');
 expect(await page.evaluate(()=>localStorage.getItem('xiaoxu-notice-browser-alert-v1'))).toBe('1');
 await page.reload();
 await expect(toggle).toContainText('关闭浏览器提醒');
 await toggle.click();
 await expect(toggle).toContainText('开启浏览器提醒');
 expect(await page.evaluate(()=>localStorage.getItem('xiaoxu-notice-browser-alert-v1'))).toBe(null);
});

test('browser notification only fires after a real new verified article is discovered',async({page,context})=>{
 await context.grantPermissions(['notifications']);
 let items=[row(3003),row(3004)];
 await page.route('**/api/notices',r=>r.fulfill({status:200,contentType:'application/json',body:JSON.stringify(reply(items))}));
 await page.route('**/data/notices.json',r=>r.fulfill({status:503,contentType:'application/json',body:'{}'}));
 await page.goto('/#academic');
 await expect(page.locator('.x-v7-notice-row')).toHaveCount(2);
 await page.evaluate(async()=>{
  await navigator.serviceWorker.ready;
  window.__schoolAlerts=[];
  ServiceWorkerRegistration.prototype.showNotification=async function(title,options){
   window.__schoolAlerts.push({title,body:options.body,tag:options.tag});
  };
 });
 await page.locator('[data-action="notice-alert-toggle"]').click();
 expect(await page.evaluate(()=>window.__schoolAlerts.length)).toBe(0,'Initial baseline cannot trigger alerts');
 items=[...items,row(3005)];
 await page.locator('[data-action="refresh-notices"]').click();
 await expect(page.locator('.x-v7-unread-label')).toHaveCount(1);
 await expect.poll(()=>page.evaluate(()=>window.__schoolAlerts.length)).toBe(1);
 const alert=await page.evaluate(()=>window.__schoolAlerts[0]);
 expect(alert.title).toContain('校序');
 expect(alert.body).toContain('1 条');
 await page.locator('[data-action="refresh-notices"]').click();
 await expect.poll(()=>page.evaluate(()=>window.__schoolAlerts.length)).toBe(1);
});
