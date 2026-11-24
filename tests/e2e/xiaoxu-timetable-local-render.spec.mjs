import {test,expect} from '@playwright/test';

test('课表单日与整周切换保留导入工具及学校原始课表',async({page})=>{
 await page.goto('/#timetable');
 const view=page.locator('[data-timetable-view]');
 const toolbar=page.locator('.v42-schedule-toolbar');
 const weekRange=page.locator('[data-timetable-range]');
 const imported=page.locator('.v4-import-tools');
 await expect(view).toBeVisible();
 await expect(weekRange).toContainText('本周');
 // Desktop defaults to week, mobile to day; normalize the test's starting view.
 await page.locator('[data-timetable-mode="day"]').click();
 await expect(page.locator('.v42-day-picker')).toBeVisible();
 await imported.locator('summary').click();
 await expect(imported).toHaveAttribute('open','');
 const originalRange=await weekRange.innerText();
 await page.evaluate(()=>{
  const app=document.querySelector('#app');
  window.__timetableStable={
   appFirst:app.firstElementChild,
   view:app.querySelector('[data-timetable-view]'),
   toolbar:app.querySelector('.v42-schedule-toolbar'),
   imports:app.querySelector('.v4-import-tools'),
   officialHint:app.querySelector('.v42-schedule-hint'),
   officialPanel:app.querySelector('.v42-schedule-hint')?.previousElementSibling,
   dayPicker:app.querySelector('.v42-day-picker')
  };
 });
 const thursday=page.locator('[data-timetable-day="4"]');
 await expect(thursday).toBeVisible();
 await thursday.click();
 await expect(thursday).toHaveAttribute('aria-pressed','true');
 expect(await page.evaluate(()=>{
  const a=window.__timetableStable;
  return a.dayPicker===document.querySelector('.v42-day-picker') &&
    a.view===document.querySelector('[data-timetable-view]') &&
    a.officialPanel===document.querySelector('.v42-schedule-hint')?.previousElementSibling;
 })).toBe(true);

 await page.locator('[data-timetable-mode="week"]').click();
 await expect(page.locator('.v42-week-grid .v42-day-card')).toHaveCount(7);
 await expect(page.locator('[data-timetable-mode="week"]')).toHaveAttribute('aria-pressed','true');
 await expect(imported).toHaveAttribute('open','');
 await page.locator('[data-timetable-week="1"]').click();
 await expect(weekRange).not.toHaveText(originalRange);
 const reset=page.locator('[data-timetable-week="0"]');
 await expect(reset).toBeVisible();
 await reset.click();
 await expect(reset).toBeHidden();
 await expect(weekRange).toHaveText(originalRange);
 await expect(page.locator('[data-timetable-week="1"]')).toBeFocused();
 await page.locator('[data-timetable-mode="day"]').click();
 await expect(page.locator('[data-timetable-day="4"]')).toHaveAttribute('aria-pressed','true');

 expect(await page.evaluate(()=>{
  const a=window.__timetableStable,app=document.querySelector('#app');
  return a.appFirst===app.firstElementChild &&
   a.view===app.querySelector('[data-timetable-view]') &&
   a.toolbar===app.querySelector('.v42-schedule-toolbar') &&
   a.imports===app.querySelector('.v4-import-tools') &&
   a.officialHint===app.querySelector('.v42-schedule-hint') &&
   a.officialPanel===app.querySelector('.v42-schedule-hint')?.previousElementSibling;
 })).toBe(true);
});
