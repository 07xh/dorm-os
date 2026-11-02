import {test,expect} from '@playwright/test';
test('local task deadline reminder is visible and dismissible without changing completion',async({page})=>{
 await page.addInitScript(()=>{
  const date=new Date(Date.now()+8*3600000).toISOString().slice(0,10);
  localStorage.setItem('dorm-os-state-v1',JSON.stringify({
   version:1,settings:{campus:'jianghuai',theme:'light'},
   classes:[],tasks:[{id:'test-deadline',title:'工程制图新任务',category:'工程制图',due:date,dueAt:'',done:false}]
  }));
 });
 await page.goto('/#tasks');
 const card=page.locator('.xu-deadline-alerts');
 await expect(card).toContainText('工程制图新任务');
 await expect(card).toContainText('时刻未知');
 await card.locator('[data-action^="deadline-ack:"]').click();
 await expect(page.locator('.xu-deadline-alerts')).toHaveCount(0);
 const tasks=await page.evaluate(()=>JSON.parse(localStorage.getItem('dorm-os-state-v1')).tasks);
 expect(tasks[0].done).toBe(false);
 await page.reload();
 await expect(page.locator('.xu-deadline-alerts')).toHaveCount(0);
});
