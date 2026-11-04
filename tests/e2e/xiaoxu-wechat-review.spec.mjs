import {test,expect} from '@playwright/test';

// Browser-level App-first test. All messages and tokens are synthetic and
// intercepted before reaching a server. No real WeChat or school account.
test('WeChat candidates auto-read after verified login but require manual confirmation',async({page})=>{
 const device='wxd_'+'a'.repeat(24),candidate='b'.repeat(20);
 const token='wxu_'+'c'.repeat(43);
 let devices=[],records=[{
  id:candidate, title:'请在周五前提交示例报告',
  chatLabel:'已允许的示例班群',kind:'homework',
  sentAt:'2026-11-04T12:00:00+08:00',
  duePrecision:'date',dueLocalDate:'2026-11-06',
  reviewStatus:'needs_review'
 }];
 const calls=[];
 await page.addInitScript(()=>localStorage.setItem('xiaoxu_owner_login_seen','1'));
 await page.route('**/api/v1/auth/me',async route=>route.fulfill({
  status:200,contentType:'application/json',body:JSON.stringify({authenticated:true,expiresAt:'2026-11-07T00:00:00Z'})
 }));
 await page.route('**/api/v1/wechat/**',async route=>{
  const req=route.request(),url=new URL(req.url());
  calls.push({path:url.pathname,method:req.method(),origin:req.headers()['origin']||''});
  let result,code=200;
  if(url.pathname==='/api/v1/wechat/devices'&&req.method()==='GET')
   result={devices};
  else if(url.pathname==='/api/v1/wechat/candidates'&&req.method()==='GET')
   result={status:'personal_candidates',items:records};
  else if(url.pathname==='/api/v1/wechat/devices'&&req.method()==='POST'){
   const input=req.postDataJSON();
   expect(input.name).toBe('我的 Windows 电脑');
   devices=[{id:device,name:input.name,lastSeenAt:null,revokedAt:null}];
   result={device:{id:device,name:input.name},token};code=201;
  }else if(url.pathname==='/api/v1/wechat/candidates/'+candidate&&req.method()==='POST'){
   expect(req.postDataJSON()).toEqual({decision:'accept'});
   records=[];result={id:candidate,status:'accepted',assignmentId:'task-accepted'};
  }else if(url.pathname==='/api/v1/wechat/devices/'+device&&req.method()==='DELETE'){
   devices[0].revokedAt='2026-11-04T12:01:00Z';
   result={revoked:true,id:device};
  }else result={error:'route_not_mocked'},code=404;
  await route.fulfill({status:code,contentType:'application/json',body:JSON.stringify(result)});
 });
 await page.goto('/#tasks');
 // Auto-read only follows a verified owner session; the Review tab shows cached candidates.
 await expect.poll(()=>calls.filter(x=>x.method==='GET').length).toBe(2);
 await page.locator('[data-filter="review"]').click();
 const panel=page.getByRole('region',{name:'微信待审核任务'});
 await expect(panel).toBeVisible();
 await expect(panel.locator('.xu-wc-details')).toContainText('1 条待确认');
 await panel.locator('summary').click();
 await expect(panel.getByText('请在周五前提交示例报告')).toBeVisible();
 expect(calls.filter(x=>x.method==='GET').length).toBe(2);
 expect(calls.some(x=>x.method==='POST'&&x.path.includes('/candidates/'))).toBe(false);

 // The same original tasks page is responsible for reviewing and confirming.
 await panel.getByRole('button',{name:'确认加入待办'}).click();
 await expect(panel.getByText('已加入校序云端任务')).toBeVisible();
 await expect(panel.getByText('请在周五前提交示例报告')).toHaveCount(0);
 expect(calls.some(x=>x.method==='POST'&&x.path.includes('/candidates/'))).toBe(true);

 await panel.locator('.xu-wc-devices summary').click();
 await panel.getByPlaceholder('我的 Windows 电脑').fill('我的 Windows 电脑');
 await panel.getByRole('button',{name:'生成一次性设备凭据'}).click();
 await expect(panel.getByText(device)).toBeVisible();
 await expect(panel.getByText(token)).toBeVisible();

 await panel.getByRole('button',{name:'隐藏并清除'}).click();
 await expect(panel.getByText(token)).toHaveCount(0);
 expect(calls.some(x=>x.method==='POST'&&x.path==='/api/v1/wechat/devices')).toBe(true);

 // Owner logout must not leave the bearer token in the DOM.
 await page.evaluate(()=>window.XiaoxuWechat.disconnect());
 await expect(panel.getByText(token)).toHaveCount(0);
});
