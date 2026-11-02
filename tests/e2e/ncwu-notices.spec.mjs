import { test, expect } from '@playwright/test';
const data = {
 schema:'xiaoxu/notices-v1',school:'4141010078',mode:'auto',
 lastSuccessAt:'2026-11-03T02:00:00Z',
 sources:[{id:'jwc',name:'教务处通知',status:'ok'},{id:'student',name:'学生工作处',status:'ok'}],
 items:[
  {id:'test-1',title:'真实网站公示测试：2026年课程安排',url:'https://www.ncwu.edu.cn/info/1063/23332.htm',date:'2026-10-29',sourceId:'jwc',sourceName:'教务处通知'},
  {id:'test-2',title:'学生服务测试：电子照片核对',url:'https://www2.ncwu.edu.cn/xsc/info/1013/4139.htm',date:'2026-09-28',sourceId:'student',sourceName:'学生工作处'},
  {id:'evil',title:'<img src=x onerror=alert(1)>',url:'javascript:alert(1)',date:'2026-11-01',sourceId:'jwc',sourceName:'教务处通知'},
 ],
};
test('academic notices fetch verified feed, filter and search without touching local tasks', async ({page})=>{
 await page.route('**/data/notices.json',route=>route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(data)}));
 await page.goto('/#academic');
 await expect(page.locator('.x-notice-item')).toHaveCount(2);
 await expect(page.locator('.x-notice-item').first()).toContainText('课程安排');
 await expect(page.locator('.x-notice-item').first()).toHaveAttribute('href',data.items[0].url);
 await page.locator('[data-notice-filter="student"]').click();
 await expect(page.locator('.x-notice-item')).toHaveCount(1);
 await expect(page.locator('.x-notice-item')).toContainText('电子照片');
 await page.locator('#xiaoxu-notice-search').fill('不存在的词');
 await expect(page.locator('.x-notice-item')).toHaveCount(0);
 await page.locator('#xiaoxu-notice-search').fill('');
 await page.locator('[data-notice-filter="all"]').click();
 await expect(page.locator('.x-notice-item')).toHaveCount(2);
 await expect(page.locator('.x-notice-item img')).toHaveCount(0);
 await page.locator('[data-page="dashboard"]').first().click();
 await expect(page.locator('.x-notice-home-link')).toHaveCount(2);
});
test('network failure keeps official link fallback and does not pretend to be synced',async ({page})=>{
 await page.route('**/data/notices.json',route=>route.fulfill({status:503,body:'fail'}));
 await page.goto('/#academic');
 await expect(page.locator('.x-notice-feed')).toContainText('通知档案暂不可用');
 await expect(page.locator('.x-notice-feed a[href="https://www.ncwu.edu.cn/xxtz/jwc.htm"]')).toBeVisible();
 await expect(page.locator('.x-notice-item')).toHaveCount(0);
});
