import { test, expect } from '@playwright/test';

const notice1={
 id:'notices:3003',source:'notices',category:'notice',sourceLabel:'通知公告',
 title:'关于校序 V83 教学课表维护的通知',
 url:'https://www2.ncwu.edu.cn/jiaowu/info/1008/3003.htm',dateLabel:'11-03',publishedAt:null
};
const notice2={
 id:'exams:2990',source:'exams',category:'exam',sourceLabel:'考试安排',
 title:'2026学年期末考试安排',
 url:'https://www2.ncwu.edu.cn/jiaowu/info/1034/2990.htm',dateLabel:'11-02',publishedAt:null
};
test.beforeEach(async({page})=>{
 await page.route('**/data/notices.json',r=>r.fulfill({
  status:200,contentType:'application/json',
  body:JSON.stringify({schema:'xiaoxu/notices-v1',mode:'seed',items:[]})
 }));
 await page.route('**/api/notices',r=>r.fulfill({
  status:200,contentType:'application/json',
  body:JSON.stringify({success:true,items:[notice1,notice2],fetchedAt:'2026-11-03T08:00:00.000Z'})
 }));
});

test('V8.3 official notice component filters live verified records without reloading or mock records',async({page})=>{
 await page.goto('/#academic');
 await expect(page.locator('.x-v7-notice-item')).toHaveCount(2);
 await expect(page.locator('link[href="/xiaoxu-workspace.css"]')).toHaveCount(1);
 const input=page.locator('#noticeSearch');
 await input.fill('期末考试');
 await expect(page.locator('#publicNoticeList .x-v7-notice-item')).toHaveCount(1);
 await expect(page.locator('#publicNoticeList')).toContainText('期末考试安排');
 await expect(input).toBeFocused();
 await input.fill('没有这种通知');
 await expect(page.locator('#publicNoticeList')).toContainText('没有匹配的已核验通知');
 await input.fill('');
 await expect(page.locator('#publicNoticeList .x-v7-notice-item')).toHaveCount(2);
 await page.locator('[data-notice-filter="exam"]').click();
 await expect(page.locator('#publicNoticeList .x-v7-notice-item')).toHaveCount(1);
 await expect(page.locator('#publicNoticeList .x-v7-notice-item').first()).toHaveAttribute('href',notice2.url);
});

test('V8.3 global search includes retrieved verified notices and opens selected record',async({page})=>{
 await page.goto('/#academic');
 await expect(page.locator('.x-v7-notice-item')).toHaveCount(2);
 await page.locator('[data-action="global-search"]').click();
 await page.locator('#globalSearchInput').fill('V83 教学课表');
 await expect(page.locator('.search-result')).toHaveCount(1);
 await expect(page.locator('.search-result')).toContainText('公开通知');
 await page.locator('[data-search-open="notice"]').click();
 await expect(page).toHaveURL(/#academic$/);
 await expect(page.locator('#noticeSearch')).toHaveValue(notice1.title);
 await expect(page.locator('#publicNoticeList .x-v7-notice-item')).toHaveCount(1);
 await expect(page.locator('#publicNoticeList .x-v7-notice-item').first()).toHaveAttribute('href',notice1.url);
});

test('V8.3 notice control remains responsive on narrow mobile and keeps PWA asset reference',async({page})=>{
 await page.goto('/#academic');
 await expect(page.locator('.x-v7-notice-item')).toHaveCount(2);
 for(const width of [360,393,768,1280]){
  await page.setViewportSize({width,height:850});
  const overflow=await page.evaluate(()=>document.documentElement.scrollWidth-window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(2);
  await expect(page.locator('#noticeSearch')).toBeVisible();
 }
 await expect(page.locator('link[href="/xiaoxu-workspace.css"]')).toHaveCount(1);
});
