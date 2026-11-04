import {test,expect} from '@playwright/test';

test('second-class QR discovery never masquerades as a linked Xiaoxu session',async({page})=>{
 let preflights=0,loginAttempts=0;
 await page.route('**/api/v1/**',route=>{
  const url=new URL(route.request().url());
  const reply=(body,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(body)});
  if(url.pathname==='/api/v1/connections')return reply({schema:'xiaoxu/connections-v1',platforms:[
   {id:'ncwu',status:'not_connected',loginMode:'unavailable',trustedKinds:[]},
   {id:'chaoxing',status:'not_connected',loginMode:'unavailable',trustedKinds:[]},
   {id:'second_class',status:'not_connected',sessionState:'not_connected',
    loginMode:'native_cas_probe',nativeCasPreflight:true,accountAuthorization:'not_verified',trustedKinds:[]}
  ]});
  if(url.pathname==='/api/v1/connections/second_class/preflight'){
   preflights++;return reply({status:'interactive_login_required',loginSupported:false,
    nativeLoginReady:false,sessionVaultReady:true,officialWeChatEntryObserved:true,
    qrSessionLinked:false});
  }
  if(url.pathname.includes('/second_class/login'))loginAttempts++;
  return reply({error:'not_found'},404);
 });
 await page.goto('/#settings');
 const center=page.getByRole('region',{name:'统一数据连接中心'});
 await center.getByRole('button',{name:'读取我的连接状态'}).click();
 const row=center.locator('.xu-connection-source').filter({hasText:'第二课堂'});
 await row.locator('.xu-school-manage > summary').click();
 await expect(row).toContainText('扫码会话尚未接入校序');
 await expect(row.locator('form[data-xu-school-login]')).toHaveCount(0);
 await expect(row.locator('.xu-provider-guide')).toHaveCount(0);
 expect(preflights).toBe(0);
 await row.locator('[data-xu-cas-check="second_class"]').click();
 await expect(row.locator('[data-xu-cas-result]')).toContainText('官网发现微信扫码入口');
 await expect(row.locator('[data-xu-second-class-hint]')).toBeHidden();
 await expect(row.locator('form[data-xu-school-login]')).toHaveCount(0);
 await expect(row.locator('img')).toHaveCount(0,'never fabricate a QR image');
 await expect(row).toContainText('未连接');
 expect(preflights).toBe(1);
 expect(loginAttempts).toBe(0);
 for(const width of [320,393,768]){
  await page.setViewportSize({width,height:850});
  expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(2);
 }
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
 await expect(row.locator('[data-xu-cas-result]')).toContainText('校序仍未连接');
});

test('verified second-class password transport exposes login and connects only after successful sync',async({page})=>{
 let linked=false,loginAttempts=0;
 await page.route('**/api/v1/**',route=>{
  const url=new URL(route.request().url());
  const reply=body=>route.fulfill({contentType:'application/json',body:JSON.stringify(body)});
  if(url.pathname==='/api/v1/connections')return reply({schema:'xiaoxu/connections-v1',platforms:[
   {id:'ncwu',status:'not_connected',loginMode:'unavailable',trustedKinds:[]},
   {id:'chaoxing',status:'not_connected',loginMode:'unavailable',trustedKinds:[]},
   {id:'second_class',status:linked?'connected':'not_connected',
    sessionState:linked?'active':'not_connected',loginMode:'native_cas_probe',nativeCasPreflight:true,
    accountAuthorization:linked?'verified_session':'not_verified',
    trustedKinds:linked?['second-class']:[],importedKinds:[]}
  ]});
  if(url.pathname==='/api/v1/connections/second_class/preflight')return reply({
   status:'password_form_verified',nativeLoginReady:true,loginSupported:true,sessionVaultReady:true,
   officialWeChatEntryObserved:true,qrSessionLinked:false
  });
  if(url.pathname==='/api/v1/connections/second_class/login'){
   expect(route.request().postDataJSON()).toMatchObject({username:'SYNTHETIC',password:'TEST_ONLY'});
   linked=true;loginAttempts++;return reply({connected:true,provider:'second_class'});
  }
  return reply({status:'not_connected',records:[]});
 });
 await page.goto('/#settings');
 const center=page.getByRole('region',{name:'统一数据连接中心'});
 await center.getByRole('button',{name:'读取我的连接状态'}).click();
 const row=center.locator('.xu-connection-source').filter({hasText:'第二课堂'});
 await row.locator('.xu-school-manage > summary').click();
 await expect(row.locator('input[type="password"]')).toHaveCount(0);
 await row.locator('[data-xu-cas-check="second_class"]').click();
 const form=row.locator('form[data-xu-school-login="second_class"]');
 await expect(form).toBeVisible();
 expect(loginAttempts).toBe(0);
 await form.locator('[name="username"]').fill('SYNTHETIC');
 await form.locator('[name="password"]').fill('TEST_ONLY');
 await form.locator('button[type="submit"]').click();
 await expect.poll(()=>loginAttempts).toBe(1);
 await expect.poll(()=>page.evaluate(()=>window.XiaoxuAcademic.hasVerifiedSchoolConnection())).toBe(true);
});

