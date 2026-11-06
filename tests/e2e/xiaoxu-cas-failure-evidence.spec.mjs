import {test,expect} from '@playwright/test';
const respond=(route,body,status=200)=>route.fulfill({status,
 contentType:'application/json',body:JSON.stringify(body)});

async function setup(page){
 const requests=[];
 await page.route('**/api/v1/**',route=>{
  const path=new URL(route.request().url()).pathname;
  if(path.startsWith('/api/v1/connections/second_class/'))requests.push(path);
  if(path==='/api/v1/auth/me')return respond(route,{authenticated:true});
  if(path==='/api/v1/connections')return respond(route,{
   schema:'xiaoxu/connections-v1',platforms:['ncwu','chaoxing','second_class'].map(id=>({
    id,status:'not_connected',sessionState:'not_connected',
    // A stale server must not resurrect the removed password/scan checker.
    nativeCasPreflight:id==='second_class',loginMode:'native_cas_probe',
    accountAuthorization:'not_verified',trustedKinds:[],importedKinds:[]
   }))
  });
  return respond(route,{status:'not_connected',records:[]});
 });
 await page.goto('/#settings');
 await page.getByRole('button',{name:'读取我的连接状态'}).click();
 const row=page.locator('.xu-connection-source').filter({hasText:'第二课堂'});
 await row.locator('.xu-school-manage > summary').click();
 return {row,requests};
}

test('second-class entry uses the official App and cannot resurrect a cloud password form',async({page})=>{
 const {row,requests}=await setup(page);
 await expect(row).toContainText('请在校序 App');
 await expect(row.locator('form[data-xu-school-login="second_class"]')).toHaveCount(0);
 await expect(row.locator('[data-xu-cas-check="second_class"]')).toHaveCount(0);
 await expect(row).not.toContainText('检测扫码接入');
 expect(requests).toEqual([]);
});

test('an old cached second-class form cannot post credentials through the current client',async({page})=>{
 const {requests}=await setup(page);
 await page.evaluate(()=>{
  const form=document.createElement('form');form.dataset.xuSchoolLogin='second_class';
  form.innerHTML='<input name="username" value="SYNTHETIC"><input name="password" value="SYNTHETIC_SECRET">'+
   '<button type="submit">old</button><p data-xu-school-result></p>';
  document.body.append(form);form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
 });
 const form=page.locator('form[data-xu-school-login="second_class"]');
 await expect(form.locator('[data-xu-school-result]')).toContainText('学校官方授权');
 await expect(form.locator('[name="password"]')).toHaveValue('');
 expect(requests).toEqual([]);
});
