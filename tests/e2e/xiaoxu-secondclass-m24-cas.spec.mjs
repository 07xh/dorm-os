import {test,expect} from '@playwright/test';

test('second-class official CAS preflight gates credentials and verified personal sync',async({page})=>{
 let preflights=0,logins=0,linked=false;
 await page.route('**/api/v1/**',async route=>{
  const url=new URL(route.request().url());
  const reply=(data,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(data)});
  if(url.pathname==='/api/v1/connections')return reply({schema:'xiaoxu/connections-v1',platforms:[
   {id:'ncwu',status:'not_connected',sessionState:'not_connected',loginMode:'unavailable',trustedKinds:[],importedKinds:[]},
   {id:'chaoxing',status:'not_connected',sessionState:'not_connected',loginMode:'unavailable',trustedKinds:[],importedKinds:[]},
   {id:'second_class',status:linked?'connected':'not_connected',sessionState:linked?'active':'not_connected',
    loginMode:'native_cas_probe',nativeCasPreflight:true,accountAuthorization:linked?'verified_session':'not_verified',
    trustedKinds:linked?['second-class']:[],importedKinds:[],lastSuccessAt:linked?new Date().toISOString():null}
  ]});
  if(url.pathname==='/api/v1/connections/second_class/preflight'){
   preflights++;return reply({status:'password_form_verified',loginSupported:true,nativeLoginReady:true,sessionVaultReady:true});
  }
  if(url.pathname==='/api/v1/connections/second_class/login'){
   const input=route.request().postDataJSON();
   expect(input.username).toBe('SYNTHETIC_STUDENT');
   expect(input.password).toBe('SYNTHETIC_PASSWORD');
   expect(input).not.toHaveProperty('academicPassword');
   logins++;linked=true;
   return reply({provider:'second_class',connected:true,lastSuccessAt:new Date().toISOString()});
  }
  if(url.pathname==='/api/v1/second-class')return reply({kind:'second-class',source:'second_class',
   status:linked?'ready':'not_connected',records:[],lastSuccessAt:linked?new Date().toISOString():null,
   provenance:linked?'trusted_readonly':null,automaticSync:false,summary:null,officialScore:null});
  if(url.pathname.startsWith('/api/v1/verified/'))return reply({status:'not_connected',records:[]});
  return reply({error:'not_found'},404);
 });
 await page.goto('/#settings');
 const center=page.getByRole('region',{name:'统一数据连接中心'});
 await center.getByRole('button',{name:'读取我的连接状态'}).click();
 const row=center.locator('.xu-connection-source').filter({hasText:'第二课堂'});
 await row.locator('.xu-school-manage > summary').click();
 await expect(row.locator('form[data-xu-school-login]')).toHaveCount(0);
 expect(preflights).toBe(0);
 await row.locator('[data-xu-cas-check="second_class"]').click();
 await expect(row.locator('form[data-xu-school-login="second_class"]')).toBeVisible();
 expect(preflights).toBe(1);
 await row.locator('form input[name="username"]').fill('SYNTHETIC_STUDENT');
 await row.locator('form input[name="password"]').fill('SYNTHETIC_PASSWORD');
 await row.locator('form button[type="submit"]').click();
 await expect.poll(()=>logins).toBe(1);
 await expect.poll(()=>page.evaluate(()=>window.XiaoxuAcademic.hasVerifiedSchoolConnection())).toBe(true);
 await expect(row).toContainText('已授权 · 可同步');
});

test('second-class preflight needing interactive school login never reveals password input',async({page})=>{
 await page.route('**/api/v1/connections',route=>route.fulfill({status:200,contentType:'application/json',
  body:JSON.stringify({schema:'xiaoxu/connections-v1',platforms:[
   {id:'ncwu',status:'not_connected',loginMode:'unavailable',trustedKinds:[]},
   {id:'chaoxing',status:'not_connected',loginMode:'unavailable',trustedKinds:[]},
   {id:'second_class',status:'not_connected',loginMode:'native_cas_probe',nativeCasPreflight:true,trustedKinds:[]}
  ]})
 }));
 await page.route('**/api/v1/connections/second_class/preflight',route=>route.fulfill({
  status:200,contentType:'application/json',body:JSON.stringify({status:'interactive_login_required',
   loginSupported:false,nativeLoginReady:false,reason:'interactive_challenge_required'})
 }));
 await page.goto('/#settings');
 const center=page.getByRole('region',{name:'统一数据连接中心'});
 await center.getByRole('button',{name:'读取我的连接状态'}).click();
 const row=center.locator('.xu-connection-source').filter({hasText:'第二课堂'});
 await row.locator('.xu-school-manage > summary').click();
 await row.locator('[data-xu-cas-check="second_class"]').click();
 await expect(row.locator('form[data-xu-school-login]')).toHaveCount(0);
 await expect(row.locator('input[type="password"]')).toHaveCount(0);
});
