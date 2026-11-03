import {test,expect} from '@playwright/test';

const cleanConnections={schema:'xiaoxu/connections-v1',platforms:[
 {id:'ncwu',status:'not_connected'},
 {id:'chaoxing',status:'not_connected'},
 {id:'second_class',status:'not_connected'}
]};
const reply=(route,value,status=200)=>route.fulfill({
 status,contentType:'application/json',body:JSON.stringify(value)
});

test('a returning signed-in owner restores ONLY the previously opted-in cloud tasks',async({page})=>{
 let signedIn=true,reads=0,verifiedSessions=0;
 await page.route('**/api/v1/auth/**',route=>{
  const url=new URL(route.request().url()).pathname;
  if(url.endsWith('/logout')){signedIn=false;return reply(route,{authenticated:false});}
  if(url.endsWith('/me')){
   verifiedSessions++;
   return reply(route,{authenticated:signedIn},signedIn?200:401);
  }
  return reply(route,{error:'unexpected_auth_route'},404);
 });
 await page.route('**/api/v1/connections',route=>reply(route,cleanConnections));
 await page.route('**/api/v1/verified/**',route=>reply(route,{
  status:'not_connected',records:[],lastSuccessAt:null
 }));
 await page.route('**/api/v1/second-class',route=>reply(route,{
  status:'not_connected',records:[],lastSuccessAt:null
 }));
 await page.route('**/api/v1/assignments?*',route=>{
  reads++;
  return reply(route,{items:[{
   id:'cloud-owned',title:'已有云端任务',kind:'manual',revision:2,
   local_status:'todo',due_precision:'unknown',due_at_utc:null,
   due_local_date:null,platform_status:null,source_course:null,legacy_id:null
  }]});
 });
 await page.route('**/api/v1/sources',route=>reply(route,{connections:[],userSnapshots:[]}));
 await page.route('**/api/v1/reminders',route=>reply(route,{
  delivery:'in_app_only',items:[],preferences:{enabled:false,examLeadDays:1,taskLeadDays:1}
 }));
 await page.goto('/#tasks');
 expect(reads).toBe(0,'new visitor never reads private cloud data automatically');
 await page.evaluate(()=>{
  localStorage.setItem('xiaoxu_owner_login_seen','1');
  localStorage.setItem('xiaoxu-cloud-opted-in-v1','1');
 });
 await page.reload();
 await expect(page.locator('.xu-cloud-task')).toContainText('已有云端任务');
 expect(verifiedSessions).toBeGreaterThanOrEqual(1,'server verified owner cookie');
 expect(reads).toBeGreaterThanOrEqual(1,'cloud loaded without a second connect click');
 const beforeLogout=reads;
 await page.evaluate(()=>window.setPage('profile'));
 await page.locator('[data-owner-auth-open]').click();
 await page.locator('#xuOwnerDialog [data-owner-auth-logout]').click();
 await expect(page.locator('#xuOwnerDialog [data-owner-auth-form]')).toBeVisible();
 expect(await page.evaluate(()=>localStorage.getItem('xiaoxu-cloud-opted-in-v1'))).toBeNull();
 await page.evaluate(()=>window.setPage('tasks'));
 await expect(page.locator('.xu-cloud-task')).toHaveCount(0);
 expect(reads).toBe(beforeLogout,'logout does not perform a new private task read');
});

test('expired owner session cannot restore previously remembered cloud data',async({page})=>{
 let privateReads=0;
 await page.route('**/api/v1/auth/me',route=>reply(route,{authenticated:false},401));
 await page.route('**/api/v1/connections',route=>{
  privateReads++;return reply(route,{error:'unauthorized'},401);
 });
 await page.route('**/api/v1/assignments?*',route=>{
  privateReads++;return reply(route,{error:'unauthorized'},401);
 });
 await page.goto('/#tasks');
 await page.evaluate(()=>{
  localStorage.setItem('xiaoxu_owner_login_seen','1');
  localStorage.setItem('xiaoxu-cloud-opted-in-v1','1');
 });
 await page.reload();
 await expect.poll(()=>page.evaluate(()=>localStorage.getItem('xiaoxu-cloud-opted-in-v1'))).toBeNull();
 expect(privateReads).toBe(0,'no private workspace request after rejected owner session');
 await expect(page.locator('.xu-cloud-task')).toHaveCount(0);
});
