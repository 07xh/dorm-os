import {test,expect} from '@playwright/test';
const notices=[
 {id:'notices:3003',source:'notices',category:'notice',sourceLabel:'通知公告',
  title:'关于2026-2027学年第二学期线上线下混合式课程申报的通知',
  url:'https://www2.ncwu.edu.cn/jiaowu/info/1008/3003.htm',dateLabel:'11-02',publishedAt:null},
 {id:'notices:2995',source:'notices',category:'notice',sourceLabel:'通知公告',
  title:'关于对2026-2027第一学期课程进行学生网上评教的通知',
  url:'https://www2.ncwu.edu.cn/jiaowu/info/1008/2995.htm',dateLabel:'10-26',publishedAt:null},
 {id:'exams:2990',source:'exams',category:'exam',sourceLabel:'考试安排',
  title:'2026-2027学年第一学期9-12周考试安排表',
  url:'https://www2.ncwu.edu.cn/jiaowu/info/1034/2990.htm',dateLabel:'10-19',publishedAt:null}
];
const response={success:true,partial:false,items:notices,failedSources:[],
 source:'华北水利水电大学教务处官网',fetchedAt:'2026-11-03T08:00:00.000Z',warnings:[]};
test.beforeEach(async({page})=>{await page.route('**/data/notices.json',r=>r.fulfill({status:200,contentType:'application/json',body:JSON.stringify({schema:'xiaoxu/notices-v1',mode:'seed',items:[]})}));});
test('real academic notice UI consumes live API response with source, filters and official links',async({page})=>{
 let count=0;
 await page.route('**/api/notices',async route=>{
  count++;await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(response)});
 });
 await page.goto('/#academic');
 await expect(page.locator('.x-v7-notice-item')).toHaveCount(3);
 await expect(page.locator('.x-v7-notice-feed')).toContainText('网上评教');
 await expect(page.locator('.x-v7-notice-item').first()).toHaveAttribute('href',notices[0].url);
 await expect(page.locator('.x-v7-notice-meta')).toContainText('上次取得于');
 await page.locator('[data-notice-filter="exam"]').click();
 await expect(page.locator('.x-v7-notice-item')).toHaveCount(1);
 await expect(page.locator('.x-v7-notice-item').first()).toHaveAttribute('href',notices[2].url);
 await page.locator('[data-notice-filter="all"]').click();
 await page.locator('[data-action="refresh-notices"]').click();
 await expect.poll(()=>count).toBeGreaterThan(1);
 await page.locator('#sidebarNav [data-page="dashboard"]').click();
 await expect(page.locator('.x-v7-home-notices')).toContainText('线上线下混合式课程申报');
 await expect(page.locator('.x-v7-home-notices .x-v7-notice-item')).toHaveCount(3);
});
test('official source outage preserves cached notices and never claims school is empty',async({page})=>{
 let fail=false;
 await page.route('**/api/notices',async route=>{
  if(fail)await route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({success:false,items:[],warnings:['暂不可用']})});
  else await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(response)});
 });
 await page.goto('/#academic');
 await expect(page.locator('.x-v7-notice-item')).toHaveCount(3);
 fail=true;
 await page.locator('[data-action="refresh-notices"]').click();
 await expect(page.locator('.x-v7-notice-meta')).toContainText('上次成功取得的缓存');
 await expect(page.locator('.x-v7-notice-item')).toHaveCount(3);
 await page.reload();
 await expect(page.locator('.x-v7-notice-item')).toHaveCount(3);
 await expect(page.locator('.x-v7-notice-meta')).toContainText('缓存');
});
test('first-time official notices failure shows source fallback instead of fake data',async({page})=>{
 await page.route('**/api/notices',async route=>route.fulfill({status:503,contentType:'application/json',body:'{}'}));
 await page.goto('/#academic');
 await expect(page.locator('.x-v7-notice-meta')).toContainText('当前无法读取学校官网');
 await expect(page.locator('.x-v7-notice-item')).toHaveCount(0);
 await expect(page.locator('a[href="https://www2.ncwu.edu.cn/jiaowu/jwdt/tzgg.htm"]')).toBeVisible();
});
