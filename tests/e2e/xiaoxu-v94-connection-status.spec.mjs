import { test, expect } from '@playwright/test';

const overview={schema:'xiaoxu/connections-v1',platforms:
 ['ncwu','chaoxing','second_class'].map(id=>({
  id,status:'not_connected',trustedKinds:[],importedKinds:[],
  sessionState:'not_connected',loginMode:'unavailable',accountAuthorization:'not_verified'
 }))};
const respond=(route,body,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(body)});

test('without verified owner session, opening data connections does not read private endpoints',async({page})=>{
 let reads=0;
 await page.route('**/api/v1/connections',route=>{reads++;return respond(route,overview);});
 await page.goto('/#settings');
 await expect(page.getByRole('region',{name:'统一数据连接中心'})).toContainText('校序云端未读取');
 expect(reads).toBe(0);
});

test('verified owner session automatically reads connection status without loading academic snapshots',async({page})=>{
 let connections=0,privateSnapshots=0;
 await page.route('**/api/v1/auth/me',route=>respond(route,{authenticated:true,expiresAt:'2026-12-03T12:00:00Z'}));
 await page.route('**/api/v1/connections',route=>{connections++;return respond(route,overview);});
 await page.route('**/api/v1/verified/**',route=>{privateSnapshots++;return respond(route,{status:'not_connected',records:[]});});
 await page.route('**/api/v1/second-class',route=>{privateSnapshots++;return respond(route,{status:'not_connected',records:[]});});
 await page.goto('/#settings');
 await page.evaluate(()=>localStorage.setItem('xiaoxu_owner_login_seen','1'));
 await page.reload();
 const center=page.getByRole('region',{name:'统一数据连接中心'});
 await expect(center).toContainText('校序连接状态已读取');
 await expect(center.locator('[data-school-unavailable]')).toHaveCount(2);
 await expect(center.locator('[data-xu-second-class-official-only]')).toHaveCount(1);
 await expect(center.locator('[data-xu-second-cas-check]')).toHaveCount(0);
 await expect(center.locator('form[data-xu-school-login]')).toHaveCount(0);
 await expect(center.getByRole('button',{name:'读取我的学业数据'})).toBeVisible();
 expect(connections).toBe(1,'server-verified owner triggers exactly one status read');
 expect(privateSnapshots).toBe(0,'status-only does not read assignments or other academic snapshots');
 expect(await page.evaluate(()=>localStorage.getItem('xiaoxu-cloud-opted-in-v1'))).toBeNull();
});

test('logging out while connection status is pending must not restore it',async({page})=>{
 await page.route('**/api/v1/connections',async route=>{
  await new Promise(resolve=>setTimeout(resolve,400));
  return respond(route,overview);
 });
 await page.route('**/api/v1/auth/me',route=>respond(route,{authenticated:true}));
 await page.goto('/#settings');
 await page.evaluate(()=>localStorage.setItem('xiaoxu_owner_login_seen','1'));
 await page.reload();
 await page.evaluate(()=>window.dispatchEvent(new CustomEvent('xiaoxu-owner-auth-changed',{detail:{authenticated:false}})));
 await expect(page.getByRole('region',{name:'统一数据连接中心'})).toContainText('校序云端未读取');
 await page.waitForTimeout(500);
 await expect(page.getByRole('region',{name:'统一数据连接中心'})).toContainText('校序云端未读取');
});
