import {test,expect} from '@playwright/test';

async function stubConnections(page,qrLoginAvailable){
 await page.route('**/api/v1/connections',route=>route.fulfill({
  status:200,contentType:'application/json',body:JSON.stringify({
   schema:'xiaoxu/connections-v1',
   platforms:['ncwu','chaoxing','second_class'].map(id=>({
    id,status:'not_connected',trustedKinds:[],importedKinds:[],
    sessionState:'not_connected',loginMode:id==='chaoxing'?'password':'unavailable',
    qrLoginAvailable:id==='chaoxing'&&qrLoginAvailable,
    accountAuthorization:'not_verified'
   }))
  })
 }));
}
test('QR button is unavailable until trusted backend advertises capability',async({page})=>{
 await stubConnections(page,false);
 await page.goto('/#settings');
 await page.getByRole('button',{name:'读取我的连接状态'}).click();
 const source=page.locator('.xu-connection-source').filter({hasText:'学习通'});
 await expect(source.locator('[data-xu-chaoxing-qr-start]')).toHaveCount(0);
 await expect(source.locator('form[data-xu-school-login="chaoxing"]')).toHaveCount(1);
});
test('owner QR starts from a real official endpoint and never requests a password',async({page})=>{
 await stubConnections(page,true);
 let starts=0,polls=0;
 await page.route('**/api/v1/connections/chaoxing/qr/start',async route=>{
  starts++;
  expect(route.request().method()).toBe('POST');
  expect(route.request().postDataJSON()).toEqual({});
  await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({
   status:'pending',ticket:'encrypted-opaque-challenge',
   qrImageUrl:'https://passport2.chaoxing.com/createqr?uuid=UUID123456abcDEF789&fid=-1',
   expiresAt:new Date(Date.now()+120000).toISOString()
  })});
 });
 await page.route('**/api/v1/connections/chaoxing/qr/status',async route=>{
  polls++;
  expect(route.request().postDataJSON()).toEqual({ticket:'encrypted-opaque-challenge'});
  await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({
   status:'cancelled'
  })});
 });
 await page.route('https://passport2.chaoxing.com/createqr**',route=>route.fulfill({
  status:200,contentType:'image/png',body:Buffer.from(
   'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAOz8+VkAAAAASUVORK5CYII=','base64')
 }));
 await page.goto('/#settings');
 await page.getByRole('button',{name:'读取我的连接状态'}).click();
 const source=page.locator('.xu-connection-source').filter({hasText:'学习通'});
 await source.locator('.xu-school-manage > summary').click();
 await source.locator('[data-xu-chaoxing-qr-start]').click();
 await expect(source.locator('img[alt="学习通官方登录二维码"]')).toBeVisible();
 await expect(source.locator('[data-xu-chaoxing-qr-output]')).toContainText('使用学习通 APP 扫描');
 await expect(source.locator('[data-xu-chaoxing-qr-output]')).toContainText('已取消扫码连接',{timeout:10000});
 expect(starts).toBe(1);
 expect(polls).toBe(1);
 expect(await page.locator('[data-xu-school-login="chaoxing"] input[name="password"]').isVisible()).toBe(false);
});

test('pending owner QR survives same-tab page reload and resumes status polling',async({page})=>{
 await stubConnections(page,true);
 const opaque='ABCDEFGHIJKLMNOP.abcdefghijklmnopqrstuvwxyz0123456789abcd';
 const url='https://passport2.chaoxing.com/createqr?uuid=UUID123456abcDEF789&fid=-1';
 let polls=0;
 await page.route('**/api/v1/connections/chaoxing/qr/start',route=>route.fulfill({
  status:200,contentType:'application/json',body:JSON.stringify({
   status:'pending',ticket:opaque,qrImageUrl:url,
   expiresAt:new Date(Date.now()+100000).toISOString()
  })
 }));
 await page.route('**/api/v1/connections/chaoxing/qr/status',route=>{
  polls++;
  expect(route.request().postDataJSON()).toEqual({ticket:opaque});
  return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({
   status:'scanned',ticket:opaque
  })});
 });
 await page.route('https://passport2.chaoxing.com/createqr**',route=>route.fulfill({
  status:200,contentType:'image/png',body:Buffer.from(
   'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAOz8+VkAAAAASUVORK5CYII=','base64')
 }));
 await page.goto('/#settings');
 await page.getByRole('button',{name:'读取我的连接状态'}).click();
 const row=page.locator('.xu-connection-source').filter({hasText:'学习通'});
 await row.locator('.xu-school-manage > summary').click();
 await row.locator('[data-xu-chaoxing-qr-start]').click();
 await expect.poll(()=>polls,{timeout:12000}).toBeGreaterThan(0);
 const before=polls;
 await page.reload();
 await expect(row.locator('img[alt="学习通官方登录二维码"]')).toBeVisible();
 await expect.poll(()=>polls,{timeout:10000}).toBeGreaterThan(before);
 await expect(row.locator('[data-xu-qr-feedback]')).toContainText('已扫码');
 expect(await page.evaluate(()=>!!sessionStorage.getItem('xiaoxu-chaoxing-qr-pending-v1'))).toBe(true);
});

test('verified owner logout clears the old owner\'s QR challenge and stops polling',async({page})=>{
 await stubConnections(page,true);
 const opaque='ABCDEFGHIJKLMNOP.abcdefghijklmnopqrstuvwxyz0123456789abcd';
 const url='https://passport2.chaoxing.com/createqr?uuid=UUID123456abcDEF789&fid=-1';
 let polls=0;
 await page.route('**/api/v1/connections/chaoxing/qr/start',route=>route.fulfill({
  status:200,contentType:'application/json',body:JSON.stringify({
   status:'pending',ticket:opaque,qrImageUrl:url,
   expiresAt:new Date(Date.now()+100000).toISOString()
  })
 }));
 await page.route('**/api/v1/connections/chaoxing/qr/status',route=>{
  polls++;
  return route.fulfill({status:200,contentType:'application/json',
   body:JSON.stringify({status:'scanned',ticket:opaque})});
 });
 await page.route('https://passport2.chaoxing.com/createqr**',route=>route.fulfill({
  status:200,contentType:'image/png',body:Buffer.from(
   'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAOz8+VkAAAAASUVORK5CYII=','base64')
 }));
 await page.goto('/#settings');
 await page.locator('[data-action="academic-connect"]').click();
 const row=page.locator('.xu-connection-source').filter({hasText:'学习通'});
 await row.locator('.xu-school-manage > summary').click();
 await row.locator('[data-xu-chaoxing-qr-start]').click();
 await expect(row.locator('img[alt="学习通官方登录二维码"]')).toBeVisible();
 await expect.poll(()=>page.evaluate(()=>
  !!sessionStorage.getItem('xiaoxu-chaoxing-qr-pending-v1'))).toBe(true);
 // The owner controller sends this event only after verified logout/expiry.
 await page.evaluate(()=>window.dispatchEvent(new CustomEvent(
  'xiaoxu-owner-auth-changed',{detail:{authenticated:false}}
 )));
 await expect.poll(()=>page.evaluate(()=>
  sessionStorage.getItem('xiaoxu-chaoxing-qr-pending-v1'))).toBe(null);
 await page.reload();
 await expect(row.locator('img[alt="学习通官方登录二维码"]')).toHaveCount(0);
 const after=polls;
 await page.waitForTimeout(3300);
 expect(polls).toBe(after,'logged-out owner must not continue polling');
});
