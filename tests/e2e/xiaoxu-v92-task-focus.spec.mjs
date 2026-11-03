import { test, expect } from '@playwright/test';

async function seedTasks(page){
 await page.goto('/#tasks');
 await page.evaluate(()=>{
  state.tasks=[
   {id:'v92-first',title:'列表第一项',category:'个人',due:'',done:false,source:'manual'},
   {id:'v92-second',title:'列表第二项',category:'个人',due:'',done:false,source:'manual'}
  ];
  persist();render();
 });
}

test('finishing filtered task keeps focus on the next task, then on filter when empty',async({page})=>{
 await seedTasks(page);
 await page.locator('#app [data-filter="open"]').click();
 const first=page.locator('#app .v4-task-list [data-task-toggle="v92-first"]');
 await first.focus();
 await page.keyboard.press('Space');
 await expect(first).toHaveCount(0);
 const second=page.locator('#app .v4-task-list [data-task-toggle="v92-second"]');
 await expect(second).toBeFocused();
 await page.keyboard.press('Space');
 await expect(second).toHaveCount(0);
 await expect(page.locator('#app [data-filter="open"]')).toBeFocused();
 const stored=await page.evaluate(()=>state.tasks.map(x=>({id:x.id,done:x.done})));
 expect(stored).toEqual([{id:'v92-first',done:true},{id:'v92-second',done:true}]);
 await page.locator('#app [data-filter="done"]').click();
 await expect(page.locator('#app [data-task-toggle="v92-first"]')).toHaveAttribute('aria-label',/恢复待完成/);
});

test('task controls provide touch area without mobile horizontal overflow',async({page})=>{
 await seedTasks(page);
 for(const width of [320,393,768]){
  await page.setViewportSize({width,height:780});
  const el=page.locator('#app .v4-task-list .x-v92-task-toggle').first();
  await expect(el).toBeVisible();
  const box=await el.boundingBox();
  expect(box?.width).toBeGreaterThanOrEqual(44);
  expect(box?.height).toBeGreaterThanOrEqual(44);
  // Only rendered controls have a hit target. Some task actions are
  // intentionally hidden until their row is active; Playwright returns null
  // for such elements even though they exist in the DOM.
  const buttons=page.locator('#app .v4-task-list .task-actions button:visible');
  expect(await buttons.count()).toBeGreaterThan(0);
  for(const button of await buttons.all()){
   const rect=await button.boundingBox();
   expect(rect,'visible action must have a hit rectangle').not.toBeNull();
   expect(rect.width).toBeGreaterThanOrEqual(44);
   expect(rect.height).toBeGreaterThanOrEqual(44);
  }
  const overflow=await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth);
  expect(overflow,'width '+width).toBeLessThanOrEqual(2);
 }
});

test('keyboard focus is not disrupted when toggled task stays visible',async({page})=>{
 await seedTasks(page);
 await page.locator('#app [data-filter="all"]').click();
 const checkbox=page.locator('#app [data-task-toggle="v92-first"]');
 await checkbox.focus();
 await page.keyboard.press('Space');
 await expect(page.locator('#app [data-task-toggle="v92-first"]')).toBeFocused();
 await expect(page.locator('#app [data-task-toggle="v92-first"]')).toBeChecked();
});
