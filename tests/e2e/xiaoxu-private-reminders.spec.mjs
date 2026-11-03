import {test,expect} from '@playwright/test';

test('private reminder inbox requires active authorization, saves read state and clears on disconnect',async({page})=>{
 let calls=0,enabled=false,read=false;
 const event={key:'exam:mock-1:term',kind:'exam',title:'联动验收用考试（合成数据）',
  when:'2026-11-11（时刻未验证）',subtitle:'本人导入考试快照',read:false};
 await page.route('**/api/v1/**',async route=>{
  calls++;
  const request=route.request(),url=new URL(request.url());
  const reply=(body,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(body)});
  if(url.pathname==='/api/v1/connections')return reply({schema:'xiaoxu/connections-v1',platforms:[
   {id:'ncwu',status:'not_connected'},{id:'chaoxing',status:'not_connected'},
   {id:'second_class',status:'not_connected'}]});
  if(url.pathname.startsWith('/api/v1/verified/'))return reply({status:'not_connected',records:[],lastSuccessAt:null});
  if(url.pathname==='/api/v1/assignments')return reply({items:[]});
  if(url.pathname==='/api/v1/sources')return reply({connections:[],userSnapshots:[]});
  if(url.pathname==='/api/v1/reminders'){
   if(request.method()==='PATCH'){
    const payload=request.postDataJSON();
    if(payload.operation==='settings')enabled=payload.enabled;
    if(payload.operation==='read'&&payload.key===event.key)read=payload.read;
   }
   return reply({delivery:'in_app_only',preferences:{enabled,examLeadDays:7,taskLeadDays:3},
    items:[{...event,read}],unread:read?0:1,
    source:{exams:'personal_snapshot',tasks:'personal_cloud_workspace'}});
  }
  return reply({status:'not_found'},404);
 });
 await page.goto('/#settings');
 await page.locator('.xu-settings-more > summary').click();
 await page.locator('.xu-advanced-tools:not(.xu-settings-more)').filter({hasText:'云端任务、提醒与旧数据迁移'}).locator('summary').first().click();
 const panel=page.getByRole('region',{name:'个人云端提醒'});
 await expect(panel).toContainText('未连接');
 expect(calls).toBe(0,'no private API should be fetched before opt-in access');
 await page.getByRole('button',{name:'读取我的连接状态'}).click();
 await expect(panel).toContainText('联动验收用考试');
 await expect(panel).toContainText('个人导入');
 await expect(panel).toContainText('未订阅站内提醒');
 await panel.getByRole('button',{name:'开启站内订阅'}).click();
 await expect(panel).toContainText('已启用站内提醒');
 await panel.getByRole('button',{name:/标为已读/}).click();
 await expect(panel).toContainText('恢复未读');
 await page.locator('.xu-connection-more > summary').click();
 await page.getByRole('button',{name:'清除此页私人数据'}).click();
 await expect(panel).toContainText('未连接');
 await expect(panel).not.toContainText('联动验收用考试');
 await page.reload();
 await page.locator('.xu-settings-more > summary').click();
 await page.locator('.xu-advanced-tools:not(.xu-settings-more)').filter({hasText:'云端任务、提醒与旧数据迁移'}).locator('summary').first().click();
 await expect(panel).toContainText('未连接');
 expect(enabled).toBe(true,'server-side preference persisted independently of page reload');
 expect(read).toBe(true,'server-side receipt persisted independently of page reload');
});
