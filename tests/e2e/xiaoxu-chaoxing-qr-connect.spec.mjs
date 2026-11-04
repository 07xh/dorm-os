import {test,expect} from '@playwright/test';
const qrPng=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAOz8+VkAAAAASUVORK5CYII=','base64');
async function stubQrImage(page,readTicket){
 const received=[];
 await page.route('**/api/v1/connections/chaoxing/qr/image',route=>{
  expect(route.request().method()).toBe('POST');
  const payload=route.request().postDataJSON();
  expect(payload).toEqual({ticket:readTicket()});
  received.push(payload.ticket);
  return route.fulfill({status:200,contentType:'image/png',
   body:qrPng,headers:{'cache-control':'private, no-store'}});
 });
 return received;
}

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
test('server-rendered fallback SVG replaces the blank QR and still checks official status',async({page})=>{
 await stubConnections(page,true);
 const ticket='ABCDEFGHIJKLMNOP.abcdefghijklmnopqrstuvwxyz0123456789abcd';
 const url='https://passport2.chaoxing.com/createqr?uuid=UUID123456abcDEF789&fid=-1';
 let polls=0;
 await page.route('**/api/v1/connections/chaoxing/qr/start',route=>route.fulfill({
  status:200,contentType:'application/json',body:JSON.stringify({
   status:'pending',ticket,qrImageUrl:url,
   expiresAt:new Date(Date.now()+100000).toISOString()
  })
 }));
 await page.route('**/api/v1/connections/chaoxing/qr/image',route=>{
  expect(route.request().postDataJSON()).toEqual({ticket});
  return route.fulfill({status:200,contentType:'image/svg+xml',
   headers:{'x-xu-qr-source':'local','cache-control':'private, no-store'},
   body:'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><rect width="100" height="100" fill="white"/><path d="M12 12h76v76H12z" fill="black"/></svg>'});
 });
 await page.route('**/api/v1/connections/chaoxing/qr/status',route=>{
  polls++;
  return route.fulfill({status:200,contentType:'application/json',
   body:JSON.stringify({status:'scanned',ticket})});
 });
 await page.route('https://passport2.chaoxing.com/createqr**',route=>route.abort());
 await page.goto('/#settings');
 await page.getByRole('button',{name:'读取我的连接状态'}).click();
 const source=page.locator('.xu-connection-source').filter({hasText:'学习通'});
 await source.locator('.xu-school-manage > summary').click();
 await source.locator('[data-xu-chaoxing-qr-start]').click();
 await expect(source.locator('img[alt="学习通官方登录二维码"]')).toBeVisible();
 await expect(source.locator('[data-xu-qr-feedback]')).toContainText('备用二维码');
 await source.locator('[data-xu-qr-check-now]').click();
 await expect.poll(()=>polls).toBeGreaterThan(0);
});

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
 let starts=0,polls=0,crossOriginRequests=0,allowCancel=false;
 const imageReads=await stubQrImage(page,()=> 'encrypted-opaque-challenge');
 await page.route('https://passport2.chaoxing.com/createqr**',route=>{
  crossOriginRequests++;return route.abort();
 });
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
  await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(
   allowCancel?{status:'cancelled'}:{status:'scanned',ticket:'encrypted-opaque-challenge'}
  )});
 });

 await page.goto('/#settings');
 await page.getByRole('button',{name:'读取我的连接状态'}).click();
 const source=page.locator('.xu-connection-source').filter({hasText:'学习通'});
 await source.locator('.xu-school-manage > summary').click();
 await source.locator('[data-xu-chaoxing-qr-start]').click();
 await expect(source.locator('img[alt="学习通官方登录二维码"]')).toBeVisible();
 allowCancel=true;
 await expect(source.locator('[data-xu-chaoxing-qr-output]')).toContainText('请使用学习通 APP 扫码');
 await expect(source.locator('[data-xu-chaoxing-qr-output]')).toContainText('已取消扫码连接',{timeout:10000});
 expect(starts).toBe(1);
 expect(polls).toBeGreaterThanOrEqual(1);
 expect(imageReads).toHaveLength(1);
 expect(crossOriginRequests).toBe(0,'QR PNG must not depend on cross-site browser cookies');
 expect(await page.locator('[data-xu-school-login="chaoxing"] input[name="password"]').isVisible()).toBe(false);
});

test('pending owner QR survives same-tab page reload and resumes status polling',async({page})=>{
 await stubConnections(page,true);
 const opaque='ABCDEFGHIJKLMNOP.abcdefghijklmnopqrstuvwxyz0123456789abcd';
 const url='https://passport2.chaoxing.com/createqr?uuid=UUID123456abcDEF789&fid=-1';
 let polls=0;
 const imageReads=await stubQrImage(page,()=>opaque);
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
 await page.route('https://passport2.chaoxing.com/createqr**',route=>route.abort());
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
 expect(imageReads.length).toBeGreaterThanOrEqual(2,'reload must refetch same-owner QR PNG');
 expect(await page.evaluate(()=>!!sessionStorage.getItem('xiaoxu-chaoxing-qr-pending-v1'))).toBe(true);
});

test('confirmed scan becomes a persistent school connection and manual check works',async({page})=>{
 // Real owner login stores only this boolean hint; the server's /auth/me
 // still has to verify the authenticated owner after page reload.
 await page.addInitScript(()=>{
  localStorage.setItem('xiaoxu_owner_login_seen','1');
 });
 const ticket='ABCDEFGHIJKLMNOP.abcdefghijklmnopqrstuvwxyz0123456789abcd';
 const imageReads=await stubQrImage(page,()=>ticket);
 let linked=false,statusReads=0;
 await page.route('**/api/v1/auth/me',route=>route.fulfill({
  status:200,contentType:'application/json',
  body:JSON.stringify({authenticated:true,expiresAt:'2026-12-01T09:00:00Z'})
 }));
 await page.route('**/api/v1/connections',route=>route.fulfill({
  status:200,contentType:'application/json',
  body:JSON.stringify({schema:'xiaoxu/connections-v1',
   platforms:['ncwu','chaoxing','second_class'].map(id=>({
    id,status:'not_connected',trustedKinds:[],importedKinds:[],
    loginMode:id==='chaoxing'?'password':'unavailable',
    sessionState:id==='chaoxing'&&linked?'active':'not_connected',
    accountAuthorization:id==='chaoxing'&&linked?'verified_session':'not_verified',
    syncPending:id==='chaoxing'&&linked,
    qrLoginAvailable:id==='chaoxing'
   }))})
 }));
 await page.route('**/api/v1/verified/**',route=>route.fulfill({
  status:200,contentType:'application/json',body:'{"status":"not_connected","records":[]}'
 }));
 await page.route('**/api/v1/second-class',route=>route.fulfill({
  status:200,contentType:'application/json',body:'{"status":"not_connected","records":[]}'
 }));
 await page.route('**/api/v1/connections/chaoxing/qr/start',route=>route.fulfill({
  status:200,contentType:'application/json',body:JSON.stringify({
   status:'pending',ticket,
   qrImageUrl:'https://passport2.chaoxing.com/createqr?uuid=UUID123456abcDEF789&fid=-1',
   expiresAt:new Date(Date.now()+100000).toISOString()
  })
 }));
 await page.route('**/api/v1/connections/chaoxing/qr/status',route=>{
  expect(route.request().postDataJSON()).toEqual({ticket});
  statusReads++;linked=true;
  return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({
   status:'authenticated_pending_sync',
   expiresAt:new Date(Date.now()+3600000).toISOString(),lastSuccessAt:null
  })});
 });
 await page.goto('/#settings');
 const source=page.locator('.xu-connection-source').filter({hasText:'学习通'});
 // The verified owner session can auto-load before any manual status button.
 await expect(source.locator('[data-xu-chaoxing-qr-start]')).toBeVisible();
 await source.locator('[data-xu-chaoxing-qr-start]').click();
 await expect(source.locator('img[alt="学习通官方登录二维码"]')).toBeVisible();
 await expect(source.locator('[data-xu-qr-check-now]')).toBeVisible();
 await source.locator('[data-xu-qr-check-now]').click();
 await expect(source).toContainText('已认证 · 数据待同步');
 await expect(source.locator('[data-xu-chaoxing-qr-start]')).toHaveCount(0);
 expect(statusReads).toBeGreaterThanOrEqual(1);
 expect(imageReads.length).toBe(1);
 await page.reload();
 await expect(source).toContainText('已认证 · 数据待同步',{timeout:12000});
 expect(await page.evaluate(()=>sessionStorage.getItem('xiaoxu-chaoxing-qr-pending-v1'))).toBeNull();
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
 await stubQrImage(page,()=>opaque);
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
