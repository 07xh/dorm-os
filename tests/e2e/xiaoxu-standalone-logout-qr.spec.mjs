import {test,expect} from '@playwright/test';

test('standalone login page invalidates an old QR ticket when server confirms logout',async({page})=>{
 await page.addInitScript(()=>{
  sessionStorage.setItem('xiaoxu-chaoxing-qr-pending-v1',
   JSON.stringify({ticket:'synthetic-owner-bound-ticket',expiresAt:Date.now()+60000}));
 });
 await page.route('**/api/v1/auth/me',route=>route.fulfill({
  status:200,contentType:'application/json',
  body:JSON.stringify({authenticated:true,expiresAt:new Date(Date.now()+600000).toISOString()})
 }));
 let logoutCalls=0;
 await page.route('**/api/v1/auth/logout',route=>{
  logoutCalls++;
  return route.fulfill({status:200,contentType:'application/json',
   body:JSON.stringify({ok:true})});
 });
 await page.goto('/login/');
 await expect(page.locator('[data-owner-auth-logout]')).toBeVisible();
 expect(await page.evaluate(()=>!!sessionStorage.getItem('xiaoxu-chaoxing-qr-pending-v1'))).toBe(true);
 await page.locator('[data-owner-auth-logout]').click();
 await expect.poll(()=>page.evaluate(()=>
  sessionStorage.getItem('xiaoxu-chaoxing-qr-pending-v1'))).toBe(null);
 expect(logoutCalls).toBe(1);
 await expect(page.locator('[data-owner-auth-form]')).toBeVisible();
});
