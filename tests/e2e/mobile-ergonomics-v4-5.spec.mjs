import {test,expect} from '@playwright/test';

test('360px V7 top-only header keeps content inside viewport',async({page})=>{
 await page.setViewportSize({width:360,height:780});
 await page.goto('/');
 await expect(page.locator('#sidebarNav')).toBeVisible();
 await expect(page.locator('.sidebar,.mobile-tabs')).toHaveCount(0);
 const m=await page.evaluate(()=>{
   const nav=document.querySelector('.x-v7-header'), main=document.querySelector('.workspace main');
   return {
     position:getComputedStyle(nav).position, top:nav.getBoundingClientRect().top,
     bottomPadding:parseFloat(getComputedStyle(main).paddingBottom),
     width:document.documentElement.scrollWidth-innerWidth
   };
 });
 expect(m.position).toBe('sticky');
 expect(m.top).toBeGreaterThanOrEqual(0);
 expect(m.bottomPadding).toBeGreaterThanOrEqual(40);
 expect(m.width).toBeLessThanOrEqual(2);
 await page.locator('#sidebarNav [data-page="academic"]').click();
 for(const id of ['notices','grades','exams','second','services'])
   await expect(page.locator('[data-academic-tab="'+id+'"]')).toBeVisible();
});

test('legacy NFC tag controls remain usable from direct links on a narrow phone',async({page})=>{
 await page.setViewportSize({width:360,height:780});
 await page.goto('/#nfc');
 await page.locator('[data-action="tag-add"]').click();
 await page.locator('#modalForm [name="name"]').fill('旧版 NFC 标签');
 await page.locator('#modalForm [type="submit"]').click();
 const entry=page.locator('.tag-entry').last();
 await entry.locator('.v41-tag-more summary').click();
 await expect(entry.locator('[data-tag-delete]')).toBeVisible();
 // The old menu may include buttons hidden by the active browser feature gate.
 // Touch targets are measured only for actions actually displayed to the user.
 // Inspect one DOM snapshot. The legacy menu may dynamically hide a button
 // after an initial visibility query; separate locator reads can race that change.
 const touchTargets=await entry.locator('.v41-tag-menu button').evaluateAll(nodes=>
  nodes.map(button=>{
   const box=button.getBoundingClientRect();
   const style=getComputedStyle(button);
   return {visible:style.display!=='none' && style.visibility!=='hidden' &&
     box.width>0 && box.height>0,height:box.height};
  }).filter(target=>target.visible)
 );
 expect(touchTargets.length).toBeGreaterThan(0);
 for(const target of touchTargets)
  expect(target.height).toBeGreaterThanOrEqual(44);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(2);
});

test('course edit overflow remains tappable without a bottom nav',async({page})=>{
 await page.setViewportSize({width:360,height:780});
 await page.goto('/#timetable');
 await page.locator('#quickAdd').click();
 await page.locator('#classSubject').fill('手机端课程菜单验收');
 await page.locator('#modalForm [name="day"]').selectOption('1');
 await page.locator('#modalForm [type="submit"]').click();
 await page.locator('[data-timetable-mode="day"]').click();
 await page.locator('[data-timetable-day="1"]').click();
 const row=page.locator('.v42-day-content .v42-row-more').last();
 await row.locator('summary').click();
 // Public data refresh can redraw the timetable between Playwright calls.
 // Exercise that redraw, then measure visibility and size in one DOM sample;
 // a detached ElementHandle from boundingBox() must not turn into a null crash.
 await page.evaluate(()=>render());
 const remove=row.getByRole('button',{name:'删除课程'});
 await expect.poll(()=>remove.evaluate(button=>{
  const box=button.getBoundingClientRect(),style=getComputedStyle(button);
  return button.isConnected&&style.display!=='none'&&style.visibility!=='hidden'&&box.width>0?box.height:0;
 })).toBeGreaterThanOrEqual(44);
 page.once('dialog',dialog=>dialog.accept());
 await remove.click();
 await expect(page.locator('.v42-day-content')).not.toContainText('手机端课程菜单验收');
});

test('legacy life reminder toast and menu remain accessible',async({page})=>{
 await page.setViewportSize({width:360,height:780});
 await page.goto('/#life');
 await page.locator('[data-action="reminder-add"]').click();
 await page.locator('#modalForm [name="text"]').fill('提醒回归');
 await page.locator('#modalForm [type="submit"]').click();
 await expect(page.locator('#toast')).toContainText('已');
 const trigger=page.locator('[data-life-more]').last();
 await trigger.click();
 await expect(page.locator('.v42-life-more.is-open .v42-menu')).toBeVisible();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(2);
});
