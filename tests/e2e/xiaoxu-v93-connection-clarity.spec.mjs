import { test, expect } from '@playwright/test';

async function mockStatus(page, mode = 'unavailable') {
 await page.route('**/api/v1/connections', route => route.fulfill({
  status:200,contentType:'application/json',
  body:JSON.stringify({
   schema:'xiaoxu/connections-v1',
   platforms:['ncwu','chaoxing','second_class'].map(id=>({
    id,status:'not_connected',trustedKinds:[],importedKinds:[],
    sessionState:'not_connected',
    loginMode:id==='ncwu'?mode:'unavailable',
    accountAuthorization:'not_verified'
   }))
  })
 }));
 await page.route('**/api/v1/verified/**',route=>route.fulfill({
  status:200,contentType:'application/json',body:JSON.stringify({status:'not_connected',records:[]})
 }));
 await page.route('**/api/v1/second-class',route=>route.fulfill({
  status:200,contentType:'application/json',body:JSON.stringify({status:'not_connected',records:[]})
 }));
}

test('connection status button comes before three compact source rows on phones',async({page})=>{
 await page.goto('/#settings');
 const panel=page.getByRole('region',{name:'统一数据连接中心'});
 const button=panel.getByRole('button',{name:'读取我的连接状态'});
 await expect(button).toBeVisible();
 await expect(panel.locator('.xu-connection-source')).toHaveCount(3);
 const control=await button.boundingBox(),source=await panel.locator('.xu-connection-source').first().boundingBox();
 expect(control.y).toBeLessThan(source.y);
 await expect(panel.locator('form[data-xu-school-login]')).toHaveCount(0);
 await expect(panel.locator('.xu-connection-not-loaded')).toBeVisible();
 for(const width of [320,393,768]){
  await page.setViewportSize({width,height:800});
  expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(2);
 }
});

test('unavailable school adapter does not collect school account or password',async({page})=>{
 await mockStatus(page);
 await page.goto('/#settings');
 const panel=page.getByRole('region',{name:'统一数据连接中心'});
 await panel.getByRole('button',{name:'读取我的连接状态'}).click();
 await expect(panel.locator('[data-school-unavailable]')).toHaveCount(3);
 await expect(panel.locator('form[data-xu-school-login]')).toHaveCount(0);
 await expect(panel).toContainText('校序云端已读取');
 await expect(panel).toContainText('此平台暂不支持站内登录');
 await expect(panel.locator('.xu-connection-not-loaded')).toHaveCount(0);
});

test('school account form appears only after server reports real adapter available',async({page})=>{
 await mockStatus(page,'password');
 await page.goto('/#settings');
 const panel=page.getByRole('region',{name:'统一数据连接中心'});
 await panel.getByRole('button',{name:'读取我的连接状态'}).click();
 await expect(panel.locator('.xu-school-connect')).toHaveCount(1);
 await expect(panel.locator('form[data-xu-school-login="ncwu"]')).toHaveCount(1);
 await expect(panel.locator('form[data-xu-school-login="chaoxing"]')).toHaveCount(0);
 await expect(panel.locator('form[data-xu-school-login="second_class"]')).toHaveCount(0);
 await expect(panel.locator('[data-school-unavailable]')).toHaveCount(2);
});
