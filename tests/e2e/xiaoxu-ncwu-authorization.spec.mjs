import {test,expect} from '@playwright/test';

test('NCWU and Chaoxing inline guidance never requests credentials before owner opt-in',async({page})=>{
 const calls=[];
 await page.route('**/api/v1/**',async route=>{
  calls.push(new URL(route.request().url()).pathname);
  await route.fulfill({status:401,contentType:'application/json',body:'{"error":"unauthorized"}'});
 });
 await page.goto('/#settings');
 const center=page.getByRole('region',{name:'统一数据连接中心'});
 await expect(center.locator('.xu-connection-source')).toHaveCount(3);
 await expect(center).toContainText('校序云端未读取');
 const ncwu=center.locator('.xu-connection-source').filter({hasText:'华水青果教务'});
 await expect(ncwu).toContainText('未读取连接状态');
 await expect(ncwu.getByRole('link',{name:'打开学校官网'})).toHaveAttribute('href','https://jwmis.ncwu.edu.cn/');
 await ncwu.locator('.xu-school-manage > summary').click();
 await ncwu.locator('.xu-kingo-guide summary').click();
 await expect(ncwu).toContainText('打开官网不等于绑定成功');
 await expect(ncwu).toContainText('不能读取其他网站的登录 Cookie');
 const chaoxing=center.locator('.xu-connection-source').filter({hasText:'学习通'});
 await chaoxing.locator('.xu-school-manage > summary').click();
 await chaoxing.locator('.xu-provider-guide summary').click();
 await expect(chaoxing).toContainText('学校统一认证和超星独立账号是两种不同的登录方式');
 await expect(chaoxing).toContainText('会话过期可在此重新连接');
 const second=center.locator('.xu-connection-source').filter({hasText:'第二课堂'});
 await second.locator('.xu-school-manage > summary').click();
 await expect(second).toContainText('请在校序 App 的「教务 → 第二课堂」连接');
 await expect(second.getByRole('link',{name:'打开学校官方平台'})).toHaveAttribute('href','https://dekt.ncwu.edu.cn/');
 await expect(second.locator('[data-xu-cas-check="second_class"]')).toHaveCount(0);
 await expect(second.locator('.xu-provider-guide')).toHaveCount(0);
 await expect(center.locator('input[type="password"]')).toHaveCount(0);
 expect(calls).toEqual([],'settings page must not read personal school records before opt-in');
});
test('NCWU expanded authorization detail does not cause horizontal overflow',async({page})=>{
 await page.goto('/#settings');
 for(const width of [360,393,768,1280]){
  await page.setViewportSize({width,height:840});
  await page.reload();
  // This test checks layout at each viewport, not native disclosure timing.
  // Open the details deterministically; click behavior is covered above.
  const overflow=await page.evaluate(()=>{
   const outer=document.querySelector('[data-xu-school-provider="ncwu"]');
   const inner=outer?.querySelector('.xu-kingo-guide');
   if(!outer||!inner)throw Error('NCWU authorization details not rendered');
   outer.open=true;inner.open=true;
   if(!outer.open||!inner.open)throw Error('NCWU disclosure cannot expand');
   return document.documentElement.scrollWidth-window.innerWidth;
  });
  expect(overflow,'viewport width '+width).toBeLessThanOrEqual(2);
 }
});

test('website password fields appear only when trusted server advertises a validated school adapter',async({page})=>{
 const calls=[];
 await page.route('**/api/v1/connections',route=>{
  calls.push(route.request().method());
  return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({
   schema:'xiaoxu/connections-v1',
   platforms:[
    {id:'ncwu',status:'not_connected',loginMode:'password',sessionState:'not_connected',
     trustedKinds:[],importedKinds:[]},
    {id:'chaoxing',status:'not_connected',loginMode:'unavailable',sessionState:'not_connected',
     trustedKinds:[],importedKinds:[]},
    {id:'second_class',status:'not_connected',loginMode:'unavailable',sessionState:'not_connected',
     trustedKinds:[],importedKinds:[]}
   ]
  })});
 });
 await page.goto('/#settings');
 await page.locator('[data-action="academic-connect"]').click();
 const center=page.getByRole('region',{name:'统一数据连接中心'});
 const ncwu=center.locator('.xu-connection-source').filter({hasText:'华水青果教务'});
 await ncwu.locator('.xu-school-manage > summary').click();
 await expect(ncwu.locator('form[data-xu-school-login="ncwu"]')).toBeVisible();
 await expect(ncwu.locator('input[name="username"]')).toHaveCount(1);
 await expect(ncwu.locator('input[name="password"]')).toHaveCount(1);
 await expect(center.locator('.xu-connection-source').filter({hasText:'学习通'}).locator('input[name="password"]')).toHaveCount(0);
 expect(calls).toEqual(['GET']);
});

test('Chaoxing uses the existing data connection form only after a trusted adapter is advertised',async({page})=>{
 const methods=[];
 await page.route('**/api/v1/connections',route=>{
  methods.push(route.request().method());
  return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({
   schema:'xiaoxu/connections-v1',
   platforms:[
    {id:'ncwu',status:'not_connected',loginMode:'unavailable',sessionState:'not_connected',trustedKinds:[],importedKinds:[]},
    {id:'chaoxing',status:'not_connected',loginMode:'password',sessionState:'not_connected',trustedKinds:[],importedKinds:[]},
    {id:'second_class',status:'not_connected',loginMode:'unavailable',sessionState:'not_connected',trustedKinds:[],importedKinds:[]}
   ]
  })});
 });
 await page.goto('/#settings');
 await page.locator('[data-action="academic-connect"]').click();
 const center=page.getByRole('region',{name:'统一数据连接中心'});
 const chaoxing=center.locator('.xu-connection-source').filter({hasText:'学习通'});
 await chaoxing.locator('.xu-school-manage > summary').click();
 await expect(chaoxing.getByText('我另有超星独立账号密码（非校园密码）')).toBeVisible();
 await expect(chaoxing).toContainText('不能把校园密码直接当成超星独立密码');
 await expect(chaoxing.getByRole('link',{name:'打开华水学习通统一认证'}))
  .toHaveAttribute('href','https://authserver.ncwu.edu.cn/authserver/login?service=https%3A%2F%2Ffysso.chaoxing.com%2Fcassso%2Fncwu');
 await expect(chaoxing.locator('input[name="password"]')).toBeHidden();
 await chaoxing.locator('.xu-chaoxing-direct summary').click();
 await expect(chaoxing.locator('input[name="username"]')).toBeVisible();
 await expect(chaoxing.locator('input[name="password"]')).toBeVisible();
 await expect(center.locator('.xu-connection-source').filter({hasText:'华水青果教务'}).locator('input[name="password"]')).toHaveCount(0);
 expect(methods).toEqual(['GET'],'no school credentials or writes on opening the existing center');
});
