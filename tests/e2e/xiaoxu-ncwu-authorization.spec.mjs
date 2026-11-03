import {test,expect} from '@playwright/test';

test('NCWU school login guidance is inline, does not request credentials and cannot impersonate account binding',async({page})=>{
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
 await ncwu.locator('.xu-kingo-guide summary').click();
 await expect(ncwu).toContainText('打开官网不等于绑定成功');
 await expect(ncwu).toContainText('不能读取其他网站的登录 Cookie');
 const chaoxing=center.locator('.xu-connection-source').filter({hasText:'学习通'});
 await chaoxing.locator('.xu-provider-guide summary').click();
 await expect(chaoxing).toContainText('本机导入属于部分覆盖');
 const second=center.locator('.xu-connection-source').filter({hasText:'第二课堂'});
 await second.locator('.xu-provider-guide summary').click();
 await expect(second).toContainText('不等于账号已绑定');
 await expect(center.locator('input[type="password"]')).toHaveCount(0);
 expect(calls).toEqual([],'settings page must not read personal school records before opt-in');
});
test('NCWU expanded authorization detail does not cause horizontal overflow',async({page})=>{
 for(const width of [360,393,768,1280]){
  await page.setViewportSize({width,height:840});
  await page.goto('/#settings');
  await page.locator('.xu-kingo-guide summary').click();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth-window.innerWidth), 'viewport width '+width).toBeLessThanOrEqual(2);
 }
});
