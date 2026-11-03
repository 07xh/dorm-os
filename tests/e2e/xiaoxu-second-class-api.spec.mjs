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
  if(url.pathname==='/api/v1/connections')return respond({schema:'xiaoxu/connections-v1',platforms:[
   {id:'ncwu',status:'not_connected',trustedKinds:[],importedKinds:[]},
   {id:'chaoxing',status:'not_connected',trustedKinds:[],importedKinds:[]},
   {id:'second_class',status:'not_connected',lastSuccessAt:new Date().toISOString(),
    trustedKinds:trusted?['second-class']:[],importedKinds:trusted?[]:['second-class']}
  ]});
  if(url.pathname==='/api/v1/second-class')return respond({
   kind:'second-class',source:'second_class',status:'ready',
   records:[{externalId:'a1',title,hours:2,points:5,activityStatus:'completed',reviewStatus:'approved'},
    {externalId:'a2',title:'缺少积分字段的活动'}],
   provenance:trusted?'trusted_readonly':'user_import',
   lastSuccessAt:new Date().toISOString(),automaticSync:false,
   officialScore:trusted?{school:'ncwu',units:{qualityScore:'素质分',
     volunteerHours:'志愿服务时长'},
     aggregate:{qualityScore:7.5,volunteerHours:3.5,termIndex:100},
     semesters:[],isSchoolReportedAggregate:true}:null,
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
 await expect(page.getByRole('button',{name:'读取我的连接状态'})).toBeVisible();
 expect(paths.length).toBe(0,'private endpoint must not load before user opts in');
 await page.getByRole('button',{name:'读取我的连接状态'}).click();
 await expect(page.locator('.xu-private-panel')).toContainText('校序云端已读取');
 await page.evaluate(()=>window.setPage('academic'));
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
 await page.evaluate(()=>window.setPage('settings'));
 await expect(page.locator('.xu-private-panel')).toBeVisible();
 await page.getByRole('button',{name:'刷新连接状态与数据'}).click();
 await expect(page.locator('.xu-private-panel')).toContainText('可信只读记录');
 await page.evaluate(()=>window.setPage('academic'));
 await page.locator('[data-academic-tab="second"]').click();
 await expect(panel).toContainText('授权只读同步');
 await expect(panel).toContainText('学校显示合计');
 await expect(panel).toContainText('志愿服务时长');
 await page.evaluate(()=>window.setPage('settings'));
 await expect(page.locator('.xu-private-panel')).toBeVisible();
 await page.getByRole('button',{name:'清除此页私人数据'}).click();
 await page.evaluate(()=>window.setPage('academic'));
 await page.locator('[data-academic-tab="second"]').click();
 await expect(page.getByText('第二课堂数据未连接')).toBeVisible();
 await expect(panel).toHaveCount(0);
});
