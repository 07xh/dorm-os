import {test,expect} from '@playwright/test';
const row=(id,date=null)=>({
 id:'ncwu-'+id,title:'关于华水2026学年学校公告事项的通知 '+id,
 url:'https://www.ncwu.edu.cn/info/1063/'+id+'.htm',
 date,sourceId:'jwc',sourceName:'教务处通知',category:'教学'
});
const archive=(items,at='2026-11-03T02:00:00Z')=>({
 schema:'xiaoxu/notices-v1',school:'4141010078',mode:'auto',lastSuccessAt:at,
 sources:[{id:'jwc',name:'教务处通知',status:'ok',lastSuccessAt:at,count:items.length}],
 items
});
test('scheduled archive powers academic notices when direct Cloudflare API is unavailable',async({page})=>{
 await page.route('**/api/notices',r=>r.fulfill({status:503,contentType:'application/json',body:'{}'}));
 await page.route('**/data/notices.json',r=>r.fulfill({status:200,contentType:'application/json',
  body:JSON.stringify(archive([row(23331,'2026-11-02'),row(23332)]))}));
 await page.goto('/#academic');
 await expect(page.locator('.x-v7-notice-row')).toHaveCount(2);
 await expect(page.locator('.x-v7-notice-meta')).toContainText('定时归档');
 await expect(page.locator('.x-v7-notice-meta')).toContainText('部分通知源不可用');
 await expect(page.locator('.x-v7-notice-item').first()).toHaveAttribute('href',row(23331).url);
 await expect(page.locator('.x-v7-unread-label')).toHaveCount(0);
 await expect(page.locator('.x-v7-notice-row').last()).toContainText('日期以原文为准');
});

test('new archived entries notify once and can be marked read without affecting existing tasks',async({page})=>{
 let data=archive([row(23331,'2026-11-02'),row(23332)]);
 await page.route('**/api/notices',r=>r.fulfill({status:503,contentType:'application/json',body:'{}'}));
 await page.route('**/data/notices.json',r=>r.fulfill({status:200,contentType:'application/json',
   body:JSON.stringify(data)}));
 await page.goto('/#academic');
 await expect(page.locator('.x-v7-notice-row')).toHaveCount(2);
 await expect(page.locator('.x-v7-unread-label')).toHaveCount(0);
 data=archive([...data.items,row(23333,'2026-11-03')], '2026-11-03T10:00:00Z');
 await page.locator('[data-action="refresh-notices"]').click();
 await expect(page.locator('.x-v7-unread-label')).toHaveCount(1);
 await expect(page.locator('.x-v7-mark-all')).toContainText('全部标记已读（1）');
 await page.locator('[data-notice-filter="unread"]').click();
 await expect(page.locator('.x-v7-notice-row')).toHaveCount(1);
 await page.locator('.x-v7-mark-read').click();
 await expect(page.locator('.x-v7-unread-label')).toHaveCount(0);
 await expect(page.locator('.x-v7-notice-row')).toHaveCount(0);
 await page.reload();
 await expect(page.locator('.x-v7-unread-label')).toHaveCount(0);
 const state=await page.evaluate(()=>JSON.parse(localStorage.getItem('xiaoxu-notice-reading-v1')));
 expect(state.initialized).toBe(true);
 expect(state.unread).toEqual([]);
 expect(state.seen).toContain(row(23333).url);
});

test('failed archive and API never use seed notices as successful live results',async({page})=>{
 await page.route('**/api/notices',r=>r.fulfill({status:503,contentType:'application/json',body:'{}'}));
 await page.route('**/data/notices.json',r=>r.fulfill({status:200,contentType:'application/json',body:
  JSON.stringify({schema:'xiaoxu/notices-v1',mode:'seed',lastSuccessAt:null,items:[row(23331),row(23332)]})}));
 await page.goto('/#academic');
 await expect(page.locator('.x-v7-notice-row')).toHaveCount(0);
 await expect(page.locator('.x-v7-notice-meta')).toContainText('当前无法读取学校官网');
});


test('auto archive never displays initial unverified seed records',async({page})=>{
 await page.route('**/api/notices',r=>r.fulfill({status:503,contentType:'application/json',body:'{}'}));
 const mixed=archive([
  row(23331,'2026-11-02'),row(23332,null),
  {...row(23333,'2026-11-03'),id:'seed-school-23333',title:'早期演示数据不应展示'}
 ]);
 await page.route('**/data/notices.json',r=>r.fulfill({status:200,contentType:'application/json',body:JSON.stringify(mixed)}));
 await page.goto('/#academic');
 await expect(page.locator('.x-v7-notice-row')).toHaveCount(2);
 await expect(page.locator('#app')).not.toContainText('早期演示数据不应展示');
 await expect(page.locator('.x-v7-notice-meta')).toContainText('定时归档');
 await page.reload();
 await expect(page.locator('.x-v7-notice-row')).toHaveCount(2);
});


test('opening a real official notice preserves navigation while marking it read',async({page})=>{
 await page.context().route('https://www.ncwu.edu.cn/info/**',r=>r.fulfill({
  status:200,contentType:'text/html',body:'<html><title>学校公告正文</title></html>'
 }));
 await page.route('**/api/notices',r=>r.fulfill({status:503,contentType:'application/json',body:'{}'}));
 let items=[row(23331,'2026-11-02'),row(23332)];
 await page.route('**/data/notices.json',r=>r.fulfill({status:200,contentType:'application/json',
  body:JSON.stringify(archive(items))}));
 await page.goto('/#academic');
 await expect(page.locator('.x-v7-notice-row')).toHaveCount(2);
 items=[...items,row(23334,'2026-11-03')];
 await page.locator('[data-action="refresh-notices"]').click();
 await expect(page.locator('.x-v7-unread-label')).toHaveCount(1);
 const popupPromise=page.waitForEvent('popup');
 await page.locator('.x-v7-notice-item[href$="/23334.htm"]').click();
 const popup=await popupPromise;
 await expect(popup).toHaveURL('https://www.ncwu.edu.cn/info/1063/23334.htm');
 await expect(page.locator('.x-v7-unread-label')).toHaveCount(0);
 await popup.close();
});
