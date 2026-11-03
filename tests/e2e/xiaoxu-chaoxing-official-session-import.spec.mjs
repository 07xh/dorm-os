import {test,expect} from '@playwright/test';

test('official Chaoxing session import uses existing connection center and owner-only restore',async({page})=>{
 const syntheticCookies=[{name:'cx_test_session',value:'synthetic_session_value_not_real',
  domain:'.chaoxing.com',path:'/',hostOnly:false}];
 const attempted=[];
 let linked=false;
 await page.addInitScript(()=>{
  localStorage.setItem('xiaoxu_owner_login_seen','1');
 });
 await page.route('**/api/v1/**',async route=>{
  const u=new URL(route.request().url()),pathname=u.pathname;
  const respond=(body,status=200)=>route.fulfill({
   status,contentType:'application/json',body:JSON.stringify(body)});
  if(pathname==='/api/v1/auth/me')
   return respond({authenticated:true,expiresAt:'2026-11-30T12:00:00Z'});
  if(pathname==='/api/v1/connections')return respond({
   schema:'xiaoxu/connections-v1',
   platforms:[
    {id:'ncwu',status:'not_connected',loginMode:'unavailable',
     sessionState:'not_connected',trustedKinds:[],importedKinds:[]},
    {id:'chaoxing',status:linked?'connected':'not_connected',
     loginMode:'password',sessionState:linked?'active':'not_connected',
     accountAuthorization:linked?'verified_session':'not_verified',
     trustedKinds:linked?['assignments']:[],importedKinds:[],
     lastSuccessAt:linked?new Date().toISOString():null},
    {id:'second_class',status:'not_connected',loginMode:'unavailable',
     sessionState:'not_connected',trustedKinds:[],importedKinds:[]}
   ]});
  if(pathname==='/api/v1/connections/chaoxing/import'){
   attempted.push({method:route.request().method(),payload:route.request().postDataJSON()});
   linked=true;
   return respond({provider:'chaoxing',connected:true});
  }
  if(pathname==='/api/v1/verified/assignments')return respond({
   status:linked?'ready':'not_connected',provenance:linked?'trusted_readonly':undefined,
   lastSuccessAt:linked?new Date().toISOString():null,
   records:linked?[{externalId:'synthetic_work_123',title:'合成作业（测试数据）'}]:[]});
  if(pathname.startsWith('/api/v1/verified/')||pathname==='/api/v1/second-class')
   return respond({status:'not_connected',records:[]});
  return respond({error:'not_configured'},503);
 });
 await page.goto('/#settings');
 await page.locator('[data-action="academic-connect"]').click();
 const panel=page.getByRole('region',{name:'统一数据连接中心'});
 const source=panel.locator('.xu-connection-source').filter({hasText:'学习通'});
 await source.locator('.xu-school-manage > summary').click();
 const importPanel=source.locator('.xu-chaoxing-import');
 await expect(importPanel.getByText('已在官方学习通登录？导入本人有效会话（无需校园密码）')).toBeVisible();
 await expect(importPanel.locator('textarea[name="cookieExport"]')).toBeHidden();
 await importPanel.locator('summary').click();
 const textarea=importPanel.locator('textarea[name="cookieExport"]');
 await textarea.fill(JSON.stringify(syntheticCookies));
 await importPanel.locator('button[type="submit"]').click();
 await expect.poll(()=>attempted.length).toBe(1);
 expect(attempted[0].method).toBe('POST');
 expect(attempted[0].payload.cookies).toEqual(syntheticCookies);
 await expect.poll(()=>page.evaluate(()=>window.XiaoxuAcademic.hasData('assignments'))).toBe(true);
 await expect.poll(()=>page.evaluate(()=>
  localStorage.getItem('xiaoxu-cloud-opted-in-v1'))).toBe('1');
 const before=attempted.length;
 await page.reload();
 await expect.poll(()=>page.evaluate(()=>window.XiaoxuAcademic.hasData('assignments'))).toBe(true);
 expect(attempted.length).toBe(before,'reopening must reuse server session, never reimport browser cookies');
 const saved=await page.evaluate(()=>{
  const values=[];
  for(const storage of [localStorage,sessionStorage]){
   for(let i=0;i<storage.length;i++){
    const key=storage.key(i);
    values.push(key,storage.getItem(key));
   }
  }
  return values.join(' ');
 });
 expect(saved).not.toContain('synthetic_session_value_not_real');
 const inputs=await page.locator('textarea[name="cookieExport"]').evaluateAll(
  elements=>elements.map(element=>element.value));
 expect(inputs.every(value=>value==='')).toBe(true);
 expect(attempted.length).toBe(before,'re-opening must not upload imported cookies again');
});
