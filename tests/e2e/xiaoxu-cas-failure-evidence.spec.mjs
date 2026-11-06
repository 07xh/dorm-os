import {test,expect} from '@playwright/test';
const respond=(route,body,status=200)=>route.fulfill({status,
 contentType:'application/json',body:JSON.stringify(body)});

for(const [kind,host,expected] of [
 ['flow_execution_marker','cas_auth_server','认证错误页含流程状态异常标记。'],
 ['<img src=x onerror=alert(1)>','cas_auth_server',null],
 ['crypto_marker','second_class_callback',null]
])test('CAS failure evidence is fixed and scoped: '+host+' '+String(expected),async({page})=>{
 let posts=0;
 await page.route('**/api/v1/**',route=>{
  const path=new URL(route.request().url()).pathname;
  if(path==='/api/v1/auth/me')return respond(route,{authenticated:true});
  if(path==='/api/v1/connections')return respond(route,{
   schema:'xiaoxu/connections-v1',platforms:['ncwu','chaoxing','second_class'].map(id=>({
    id,status:'not_connected',sessionState:'not_connected',
    nativeCasPreflight:id==='second_class',loginMode:'unavailable',
    accountAuthorization:'not_verified',trustedKinds:[],importedKinds:[]
   }))
  });
  if(path==='/api/v1/connections/second_class/preflight')return respond(route,{
   nativeLoginReady:true,loginSupported:true,sessionVaultReady:true
  });
  if(path==='/api/v1/connections/second_class/login'){
   posts++;return respond(route,{error:'authorization_failed',failureStage:'cas_return',
    casReturnReason:'cas_server_failure',casReturnHost:host,casErrorKind:kind},422);
  }
  return respond(route,{status:'not_connected',records:[]});
 });
 await page.goto('/#settings');
 await page.getByRole('button',{name:'读取我的连接状态'}).click();
 const row=page.locator('.xu-connection-source').filter({hasText:'第二课堂'});
 await row.locator('.xu-school-manage > summary').click();
 await row.locator('[data-xu-cas-check="second_class"]').click();
 const form=row.locator('form[data-xu-school-login="second_class"]');
 await form.locator('[name="username"]').fill('SYNTHETIC_ACCOUNT');
 await form.locator('[name="password"]').fill('SYNTHETIC_PASSWORD');
 await form.locator('button[type="submit"]').click();
 const status=form.locator('[data-xu-school-result]');
 await expect(status).toContainText('返回服务异常（5xx）');
 await expect(status).toContainText('请勿反复提交密码');
 if(expected)await expect(status).toContainText(expected);
 else await expect(status).not.toContainText('异常标记');
 await expect(status.locator('img')).toHaveCount(0);
 await expect(form.locator('[name="password"]')).toHaveValue('');
 expect(posts).toBe(1);
});
