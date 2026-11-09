import {test,expect} from '@playwright/test';
const localTask={id:'local-1',title:'手绘工程制图',category:'工程制图',
 due:'2026-11-23',dueAt:'',done:false,source:'manual',sourceId:'',
 sourceStatus:'unknown',course:'',createdAt:''};
test('cloud tasks stay opt-in, migration previews before commit, and platform state remains separate',async({page})=>{
 const calls=[];
 let cloud=[{id:'cloud-1',title:'Python 平台练习',kind:'platform',
   due_precision:'date',due_local_date:'2026-11-20',due_at_utc:null,
   local_status:'todo',platform_status:'submitted',source_course:'Python',
   revision:0,legacy_id:null}];
 await page.addInitScript(task=>localStorage.setItem('dorm-os-state-v1',JSON.stringify({
   version:1,settings:{campus:'jianghuai',theme:'light'},tasks:[task],classes:[]
 })),localTask);
 await page.route('**/api/v1/connections',route=>route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({schema:'xiaoxu/connections-v1',platforms:[{id:'ncwu',status:'not_connected'},{id:'chaoxing',status:'not_connected'},{id:'second_class',status:'not_connected'}]})}));
 await page.route('**/api/v1/verified/**',route=>{
   const kind=route.request().url().split('/').pop();
   return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(
    kind==='connections'?{status:'ready',sources:[]}:
    {kind,status:'not_connected',records:[],lastSuccessAt:null}
   )});
 });
 await page.route('**/api/v1/sources',route=>route.fulfill({status:200,
   contentType:'application/json',body:JSON.stringify({connections:[],userSnapshots:[]})}));
 await page.route('**/api/v1/assignments?*',route=>{
   calls.push('list');
   return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({items:cloud})});
 });
 await page.route('**/api/v1/assignments/legacy-import',route=>{
   const body=route.request().postDataJSON();
   calls.push(body.mode);
   expect(body.tasks[0].id).toBe('local-1');
   expect(body.tasks[0].title).toBe('手绘工程制图');
   return route.fulfill({status:body.mode==='commit'?201:200,
    contentType:'application/json',body:JSON.stringify({
      mode:body.mode,total:1,toCreate:1,unchanged:0,conflicts:[],
      created:body.mode==='commit'?1:0
    })});
 });
 await page.route('**/api/v1/assignments/cloud-1',route=>{
   const body=route.request().postDataJSON();
   expect(route.request().method()).toBe('PATCH');
   expect(body).toEqual({revision:0,localStatus:'done'});
   calls.push('patch');
   cloud=[{...cloud[0],local_status:'done',revision:1}];
   return route.fulfill({status:200,contentType:'application/json',
     body:JSON.stringify({item:{id:'cloud-1',revision:1}})});
 });
 await page.goto('/#settings');
 await page.locator('.xu-settings-more > summary').click();
 await page.locator('.xu-advanced-tools:not(.xu-settings-more)').filter({hasText:'云端任务与提醒'}).locator('summary').first().click();
 await expect(page.locator('.xu-cloud-panel')).toContainText('未连接');
 expect(calls).toEqual([],'no private cloud requests before explicit authorization');
 await page.locator('[data-action="academic-connect"]').click();
 await expect(page.locator('.xu-cloud-panel')).toContainText('已载入 1 项');
 await expect(page.locator('[data-action="cloud-commit"]')).toHaveCount(0);
 await page.locator('[data-action="cloud-preview"]').click();
 await expect(page.locator('.xu-cloud-panel')).toContainText('新增 1 项');
 expect(calls).toContain('preview');
 page.once('dialog',d=>d.accept());
 await page.locator('[data-action="cloud-commit"]').click();
 await expect(page.locator('.xu-cloud-panel')).not.toContainText('确认上传到私人云端');
 expect(calls).toContain('commit');
 const preserved=await page.evaluate(()=>JSON.parse(localStorage.getItem('dorm-os-state-v1')).tasks);
 expect(preserved).toHaveLength(1);
 expect(preserved[0].done).toBe(false);
 await page.goto('/#tasks');
 await expect(page.locator('.xu-cloud-task')).toContainText('Python 平台练习');
 await expect(page.locator('.xu-cloud-task')).toContainText('平台已提交');
 await expect(page.locator('.xu-cloud-task')).toContainText('时间未知');
 await page.locator('.xu-cloud-task [data-action^="cloud-toggle:"]').click();
 await expect(page.locator('.xu-cloud-task')).toContainText('恢复待办');
 await expect(page.locator('.xu-cloud-task')).toContainText('平台已提交');
 expect(calls).toContain('patch');
 await page.reload();
 await expect(page.locator('.xu-cloud-task')).toHaveCount(0);
 await expect(page.locator('#app')).toContainText('手绘工程制图');
});


test('mobile resume refreshes a previously authorized cloud session without changing local tasks',async({page})=>{
 await page.setViewportSize({width:390,height:844});
 let remote=[{id:'cloud-shared',title:'跨设备同步任务',kind:'manual',
  due_precision:'date',due_local_date:'2026-11-25',due_at_utc:null,
  local_status:'todo',platform_status:null,source_course:null,
  revision:0,legacy_id:null}];
 let reads=0;
 await page.route('**/api/v1/connections',route=>route.fulfill({status:200,
  contentType:'application/json',body:JSON.stringify({schema:'xiaoxu/connections-v1',
   platforms:[{id:'ncwu',status:'not_connected'},{id:'chaoxing',status:'not_connected'},
    {id:'second_class',status:'not_connected'}]})}));
 await page.route('**/api/v1/verified/**',route=>route.fulfill({status:200,
  contentType:'application/json',body:JSON.stringify({status:'not_connected',
   records:[],lastSuccessAt:null})}));
 await page.route('**/api/v1/second-class',route=>route.fulfill({status:200,
  contentType:'application/json',body:JSON.stringify({status:'not_connected',
   records:[],lastSuccessAt:null})}));
 await page.route('**/api/v1/sources',route=>route.fulfill({status:200,
  contentType:'application/json',body:JSON.stringify({connections:[],userSnapshots:[]})}));
 // This test verifies normal completion after a confirmed dependency snapshot.
 // Graph-endpoint outages have their own fail-closed regression in the
 // NextTodo graph suite and must not enable an unverified completion button.
 await page.route('**/api/v1/assignments/dependencies',route=>route.fulfill({
  status:200,contentType:'application/json',
  body:JSON.stringify({revision:0,edges:[]})
 }));
 await page.route('**/api/v1/assignments?*',route=>{
  reads++;
  return route.fulfill({status:200,contentType:'application/json',
   body:JSON.stringify({items:remote})});
 });
 await page.goto('/#settings');
 await expect(page.locator('.xu-cloud-panel')).toContainText('未连接');
 expect(reads).toBe(0,'no automatic private-data requests before owner opts in');
 await page.locator('[data-action="academic-connect"]').click();
 await expect(page.locator('.xu-cloud-panel')).toContainText('已载入 1 项');
 await page.locator('#sidebarNav [data-page="tasks"]').click();
 await expect(page.locator('.xu-cloud-task')).toContainText('标记完成');
 remote=[{...remote[0],local_status:'done',revision:1}]; // second device changed D1
 await page.evaluate(()=>window.dispatchEvent(new Event('online'))); // mobile network resume
 await expect(page.locator('.xu-cloud-task')).toContainText('恢复待办');
 expect(reads).toBeGreaterThanOrEqual(2);
 const overflow=await page.evaluate(()=>document.documentElement.scrollWidth-window.innerWidth);
 expect(overflow).toBeLessThanOrEqual(1);
});

test('one task form saves to cloud across devices and keeps local-only selection',async({page,context})=>{
 await page.setViewportSize({width:390,height:844});
 let rows=[],posts=0;
 const respond=(route,value,status=200)=>route.fulfill({
  status,contentType:'application/json',body:JSON.stringify(value)
 });
 await context.route('**/api/v1/connections',route=>respond(route,{
  schema:'xiaoxu/connections-v1',platforms:[
   {id:'ncwu',status:'not_connected'},{id:'chaoxing',status:'not_connected'},
   {id:'second_class',status:'not_connected'}
  ]
 }));
 await context.route('**/api/v1/verified/**',route=>respond(route,{
  status:'not_connected',records:[],lastSuccessAt:null
 }));
 await context.route('**/api/v1/sources',route=>respond(route,{
  connections:[],userSnapshots:[]
 }));
 await context.route('**/api/v1/assignments**',route=>{
  const request=route.request(),url=new URL(request.url()),method=request.method();
  if(url.pathname==='/api/v1/assignments'&&method==='GET')
   return respond(route,{items:rows.map(x=>({...x}))});
  if(url.pathname==='/api/v1/assignments'&&method==='POST'){
   const payload=request.postDataJSON();
   posts++;
   const item={id:'manual-'+posts,title:payload.title,kind:'manual',
    local_status:'todo',revision:0,legacy_id:null,platform_status:null,
    due_precision:payload.deadline.precision,
    due_local_date:payload.deadline.date||null,due_at_utc:null};
   rows.push(item);return respond(route,{item},201);
  }
  if(method==='PATCH'&&url.pathname==='/api/v1/assignments/manual-1'){
   const patch=request.postDataJSON();
   const current=rows[0];
   if(current.revision!==patch.revision)return respond(route,{error:'revision_conflict'},409);
   rows[0]={...current,title:patch.title,revision:current.revision+1,
    ...(patch.deadline?{due_precision:patch.deadline.precision,
       due_local_date:patch.deadline.date||null}:{})};
   return respond(route,{item:{id:current.id,revision:rows[0].revision}});
  }
  return respond(route,{error:'unexpected_request'},405);
 });
 await page.goto('/#settings');
 await page.locator('[data-action="academic-connect"]').click();
 await expect(page.locator('.xu-cloud-panel')).toContainText('已载入 0 项');
 await page.locator('#sidebarNav [data-page="tasks"]').click();
 await page.locator('#quickAdd').click();
 await expect(page.locator('#taskSaveScope')).toHaveValue('cloud');
 await page.locator('#taskTitle').fill('跨设备高数作业');
 await page.locator('#taskDue').fill('2026-11-27');
 await page.locator('#modalForm button[type="submit"]').click();
 await expect(page.locator('.xu-cloud-task')).toContainText('跨设备高数作业');
 expect(posts).toBe(1);
 expect(await page.evaluate(()=>{const saved=localStorage.getItem('dorm-os-state-v1');return saved?JSON.parse(saved).tasks.length:0;})).toBe(0,
  'cloud create must not also create a duplicate local-only task');
 const second=await context.newPage();
 await second.goto('/#settings');
 await second.locator('[data-action="academic-connect"]').click();
 await second.locator('#sidebarNav [data-page="tasks"]').click();
 await expect(second.locator('.xu-cloud-task')).toContainText('跨设备高数作业');
 await second.locator('[data-action="cloud-edit:manual-1"]').click();
 await second.locator('#taskTitle').fill('高数作业已补充');
 await second.locator('#modalForm button[type="submit"]').click();
 await expect(second.locator('.xu-cloud-task')).toContainText('高数作业已补充');
 await page.evaluate(()=>window.dispatchEvent(new Event('online')));
 await expect(page.locator('.xu-cloud-task')).toContainText('高数作业已补充');
 await page.locator('#quickAdd').click();
 await page.locator('#taskSaveScope').selectOption('local');
 await page.locator('#taskTitle').fill('仅本机的临时任务');
 await page.locator('#modalForm button[type="submit"]').click();
 await expect(page.locator('#app')).toContainText('仅本机的临时任务');
 expect(posts).toBe(1,'explicit local-only create never writes cloud');
 const overflow=await page.evaluate(()=>document.documentElement.scrollWidth-window.innerWidth);
 expect(overflow).toBeLessThanOrEqual(1,'cloud task form and action buttons must fit on mobile');
 await second.close();
});
