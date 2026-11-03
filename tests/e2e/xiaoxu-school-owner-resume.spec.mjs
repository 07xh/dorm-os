import {test,expect} from '@playwright/test';

const overview=(linked=false,lastSuccessAt=null)=>({
 schema:'xiaoxu/connections-v1',
 platforms:[
  {id:'ncwu',status:'not_connected',loginMode:'unavailable',
   sessionState:'not_connected',accountAuthorization:'not_verified',trustedKinds:[],importedKinds:[]},
  {id:'chaoxing',status:linked?'connected':'not_connected',loginMode:'password',
   sessionState:linked?'active':'not_connected',
   accountAuthorization:linked?'verified_session':'not_verified',
   trustedKinds:linked?['assignments']:[],importedKinds:[],lastSuccessAt},
  {id:'second_class',status:'not_connected',loginMode:'unavailable',
   sessionState:'not_connected',accountAuthorization:'not_verified',trustedKinds:[],importedKinds:[]}
 ]
});
const respond=(route,body,status=200)=>route.fulfill({
 status,contentType:'application/json',body:JSON.stringify(body)
});

test('school first login opts device into owner-verified restore and reloads real-source records',async({page})=>{
 let linked=false,reads=0,authChecks=0;
 await page.addInitScript(()=>{
  if(sessionStorage.getItem('owner-test-init')!=='1'){
   localStorage.setItem('xiaoxu_owner_login_seen','1');
   localStorage.removeItem('xiaoxu-cloud-opted-in-v1');
   sessionStorage.setItem('owner-test-init','1');
  }
 });
 await page.route('**/api/v1/**',route=>{
  const u=new URL(route.request().url()),method=route.request().method();
  if(u.pathname==='/api/v1/auth/me'){authChecks++;return respond(route,{authenticated:true,expiresAt:'2026-11-30T12:00:00Z'});}
  if(u.pathname==='/api/v1/connections'){reads++;return respond(route,overview(linked,new Date().toISOString()));}
  if(u.pathname==='/api/v1/connections/chaoxing/login'&&method==='POST'){
   const body=route.request().postDataJSON();
   if(body.username!=='SYNTHETIC_XUEXITONG_ACCOUNT'||body.password!=='SYNTHETIC_SECRET')
    return respond(route,{error:'invalid_credentials'},400);
   linked=true;return respond(route,{provider:'chaoxing',connected:true});
  }
  if(u.pathname==='/api/v1/connections/chaoxing/sync'&&method==='POST')
   return respond(route,{provider:'chaoxing',refreshed:true});
  if(u.pathname==='/api/v1/verified/assignments')
   return respond(route,{status:linked?'ready':'not_connected',records:linked?
    [{externalId:'SYNTHETIC_TASK_1',title:'虚构作业',term:'2026-2027-1'}]:[],
    provenance:linked?'trusted_readonly':undefined,lastSuccessAt:linked?new Date().toISOString():null});
  if(u.pathname.startsWith('/api/v1/verified/')||u.pathname==='/api/v1/second-class')
   return respond(route,{status:'not_connected',records:[]});
  return respond(route,{error:'not_configured'},503);
 });
 await page.goto('/#settings');
 const center=page.getByRole('region',{name:'统一数据连接中心'});
 const row=center.locator('.xu-connection-source').filter({hasText:'学习通'});
 await expect(row.locator('.xu-chaoxing-direct summary')).toBeVisible();
 await row.locator('.xu-chaoxing-direct summary').click();
 await row.locator('input[name="username"]').fill('SYNTHETIC_XUEXITONG_ACCOUNT');
 await row.locator('input[name="password"]').fill('SYNTHETIC_SECRET');
 await row.locator('form[data-xu-school-login="chaoxing"] button[type="submit"]').click();
 await expect.poll(()=>page.evaluate(()=>
  localStorage.getItem('xiaoxu-cloud-opted-in-v1'))).toBe('1');
 await expect.poll(()=>page.evaluate(()=>window.XiaoxuAcademic.hasData('assignments'))).toBe(true);
 const before=reads;
 await page.reload();
 await expect.poll(()=>page.evaluate(()=>window.XiaoxuAcademic.hasData('assignments'))).toBe(true);
 expect(reads).toBeGreaterThan(before);
 expect(authChecks).toBeGreaterThanOrEqual(2);
 await expect(row).toContainText('已授权');
});

test('verified Qingguo schedule auto-refreshes on foreground but pending review never syncs',async({page})=>{
 let connected=true,attempts=0,successAt='2026-10-01T00:00:00Z';
 await page.route('**/api/v1/**',route=>{
  const url=new URL(route.request().url()).pathname,method=route.request().method();
  if(url==='/api/v1/connections')return respond(route,{
   schema:'xiaoxu/connections-v1',platforms:[
    {id:'ncwu',status:connected?'partial':'not_connected',loginMode:'native_cas_probe',
     sessionState:connected?'active':'review_pending',
     accountAuthorization:connected?'verified_session':'not_verified',
     trustedKinds:connected?['schedule']:[],importedKinds:[],lastSuccessAt:successAt},
    {id:'chaoxing',status:'not_connected',sessionState:'not_connected',trustedKinds:[]},
    {id:'second_class',status:'not_connected',sessionState:'not_connected',trustedKinds:[]}
   ]});
  if(url==='/api/v1/connections/ncwu/sync'&&method==='POST'){
   attempts++;successAt=new Date().toISOString();
   return respond(route,{provider:'ncwu',refreshed:true,lastSuccessAt:successAt});
  }
  if(url==='/api/v1/verified/schedule')return respond(route,{
   status:'ready',provenance:'trusted_readonly',lastSuccessAt:successAt,
   records:[{externalId:'TEST_ONLY',title:'合成课程',weekday:2,term:'2026-2027-1'}]});
  if(url.startsWith('/api/v1/verified/')||url==='/api/v1/second-class')
   return respond(route,{status:'not_connected',records:[]});
  return respond(route,{error:'auth_not_configured'},503);
 });
 await page.goto('/#settings');
 await page.evaluate(()=>window.XiaoxuAcademic.connect());
 await expect.poll(()=>attempts).toBe(1);
 await page.evaluate(()=>window.XiaoxuAcademic.connect());
 expect(attempts).toBe(1);
 connected=false;
 await page.evaluate(()=>window.XiaoxuAcademic.refresh());
 await page.evaluate(()=>window.dispatchEvent(new Event('focus')));
 await page.waitForTimeout(100);
 expect(attempts).toBe(1);
});
