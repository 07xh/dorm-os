import {test,expect} from '@playwright/test';
const data={
 schema:'xiaoxu/academic-readiness-v1',checkedAt:'2026-11-03T06:00:00.000Z',
 kind:'public-entry-reachability-only',personalData:false,
 accountConnected:false,fullyVerified:false,
 sources:[
  {id:'kingo',name:'华水青果教务',url:'https://jwmis.ncwu.edu.cn/',reachability:'responded',httpStatus:302,accountStatus:'not-connected'},
  {id:'chaoxing',name:'学习通（华水学堂）',url:'https://ncwu.fanya.chaoxing.com/portal',reachability:'unavailable',httpStatus:503,accountStatus:'not-connected'},
  {id:'second',name:'第二课堂',url:'https://dekt.ncwu.edu.cn/',reachability:'responded',httpStatus:200,accountStatus:'not-connected'}
 ]};
test('settings shows dynamic official-source network checks, not forged account connection',async({page})=>{
 let count=0;
 await page.route('**/api/academic/status',async route=>{
  count++;await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(data)});
 });
 await page.goto('/#settings');
 await expect(page.locator('.x-v84-health-row')).toHaveCount(3);
 await expect(page.locator('.x-v84-health-row').nth(0)).toContainText('官网有响应');
 await expect(page.locator('.x-v84-health-row').nth(1)).toContainText('暂未获取响应');
 await expect(page.locator('.x-v84-health-row').nth(2)).toContainText('官网有响应');
 await expect(page.locator('.x-v84-source-health')).toContainText('个人账号尚未连接');
 await expect(page.locator('.x-v84-source-health')).toContainText('检查于');
 await expect(page.locator('body')).not.toContainText('账号已登录');
 expect(count).toBe(1);
 await page.locator('[data-action="check-academic-sources"]').click();
 await expect.poll(()=>count).toBe(2);
});

test('cannot present unverified or failed readiness response as authenticated academic data',async({page})=>{
 await page.route('**/api/academic/status',r=>r.fulfill({status:200,contentType:'application/json',body:JSON.stringify({...data,accountConnected:true})}));
 await page.goto('/#settings');
 await expect(page.locator('.x-v84-source-health')).toContainText('网络检测暂不可用');
 await expect(page.locator('.x-v84-source-health')).not.toContainText('官网有响应');
 await expect(page.locator('body')).toContainText('公开入口检测不代表本人已授权');
});
