import {test,expect} from '@playwright/test';

test('second-class personal API renders accurate source, known-only totals and disconnect privacy',async({page})=>{
 const paths=[];
 let trusted=false;
 const title='<img src=x onerror=alert(1)>';
 await page.route('**/api/v1/**',async route=>{
  const url=new URL(route.request().url());
  paths.push(url.pathname);
  const respond=body=>route.fulfill({status:200,contentType:'application/json',
   body:JSON.stringify(body)});
  if(url.pathname==='/api/v1/verified/connections')return respond({status:'ready',sources:[]});
  if(url.pathname==='/api/v1/second-class')return respond({
   kind:'second-class',source:'second_class',status:'ready',
   records:[{externalId:'a1',title,hours:2,points:5,activityStatus:'completed',reviewStatus:'approved'},
    {externalId:'a2',title:'缺少积分字段的活动'}],
   provenance:trusted?'trusted_readonly':'user_import',
   lastSuccessAt:new Date().toISOString(),automaticSync:false,
   summary:{recordCount:2,recordedHours:{knownCount:1,sum:2},
    recordedPoints:{knownCount:1,sum:5},isOfficialAggregate:false}
  });
  if(url.pathname.startsWith('/api/v1/verified/'))return respond({kind:'grades',
   status:'not_connected',records:[],lastSuccessAt:null});
  if(url.pathname==='/api/v1/assignments')return respond({items:[]});
  if(url.pathname==='/api/v1/sources')return respond({connections:[],userSnapshots:[]});
  if(url.pathname==='/api/v1/reminders')return respond({delivery:'in_app_only',items:[],
   unread:0,preferences:{enabled:false,examLeadDays:7,taskLeadDays:3},
   source:{exams:'not_connected',tasks:'personal_cloud_workspace'}});
  return route.fulfill({status:404,contentType:'application/json',body:'{}'});
 });
 await page.goto('/#settings');
 await expect(page.getByRole('button',{name:'读取授权数据'})).toBeVisible();
 expect(paths.length).toBe(0,'private endpoint must not load before user opts in');
 await page.getByRole('button',{name:'读取授权数据'}).click();
 await expect(page.locator('.xu-private-panel')).toContainText('本页已加载本人数据');
 await page.locator('[data-page="academic"]:visible').first().click();
 await page.locator('[data-academic-tab="second"]').click();
 const panel=page.getByRole('region',{name:'第二课堂'});
 await expect(panel).toContainText('个人快照');
 await expect(panel).toContainText('2');
 await expect(panel).toContainText('记录学时');
 await expect(panel).toContainText('记录积分');
 await expect(panel).toContainText('不等于学校官方累计学时');
 await expect(panel.locator('img')).toHaveCount(0,'upstream HTML must be escaped');
 expect(paths).toContain('/api/v1/second-class');
 trusted=true;
 await page.locator('[data-page="settings"]:visible').first().click();
 await page.getByRole('button',{name:'重新读取'}).click();
 await page.locator('[data-page="academic"]:visible').first().click();
 await page.locator('[data-academic-tab="second"]').click();
 await expect(panel).toContainText('授权只读同步');
 await page.locator('[data-page="settings"]:visible').first().click();
 await page.getByRole('button',{name:'断开连接'}).click();
 await page.locator('[data-page="academic"]:visible').first().click();
 await page.locator('[data-academic-tab="second"]').click();
 await expect(page.getByText('第二课堂数据未连接')).toBeVisible();
 await expect(panel).toHaveCount(0);
});
