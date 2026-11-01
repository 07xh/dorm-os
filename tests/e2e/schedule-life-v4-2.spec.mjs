import { test, expect } from '@playwright/test';
const STORAGE='dorm-os-state-v1';
const saved=page=>page.evaluate(key=>JSON.parse(localStorage.getItem(key)),STORAGE);

test('schedule day and week views preserve editable course', async({page})=>{
 await page.goto('/#timetable');
 await page.locator('#quickAdd').click();
 await page.locator('#classSubject').fill('高等数学');
 await page.locator('#modalForm [name="day"]').selectOption('1');
 await page.locator('#modalForm [name="location"]').fill('A203');
 await page.locator('#modalForm [type="submit"]').click();
 await page.locator('[data-timetable-mode="day"]').click();
 await page.locator('[data-timetable-day="1"]').click();
 await expect(page.locator('.v42-day-content')).toContainText('高等数学');
 await page.locator('.v42-day-content [data-class-edit]').click();
 await expect(page.locator('#classSubject')).toHaveValue('高等数学');
 await page.locator('[data-action="close-modal"]').click();
 await page.locator('[data-timetable-mode="week"]').click();
 await expect(page.locator('.v42-week-grid .v42-day-card')).toHaveCount(7);
 expect((await saved(page)).classes).toHaveLength(1);
 await page.reload();
 // Phone starts from today's day on reload; navigate to the recorded weekday explicitly.
 await page.locator('[data-timetable-mode="day"]').click();
 await page.locator('[data-timetable-day="1"]').click();
 await expect(page.locator('#app')).toContainText('高等数学');
 expect((await saved(page)).classes[0].location).toBe('A203');
});

test('life tabs and delete confirmation preserve user data',async({page})=>{
 await page.goto('/#life');
 await expect(page.locator('#quickAdd')).toBeHidden();
 await page.locator('[data-action="reminder-add"]').click();
 await page.locator('#modalForm [name="text"]').fill('整理书桌');
 await page.locator('#modalForm [type="submit"]').click();
 await expect(page.locator('[data-life-reminder]')).toContainText('整理书桌');
 await page.locator('[data-life-tab="items"]').click();
 await page.locator('[data-action="item-add"]').click();
 await page.locator('#modalForm [name="name"]').fill('充电线');
 await page.locator('#modalForm [name="place"]').fill('抽屉');
 await page.locator('#modalForm [type="submit"]').click();
 await expect(page.locator('[data-life-item]')).toContainText('充电线');
 await page.locator('[data-life-more]').click();
 page.once('dialog',d=>d.dismiss());
 await page.locator('[data-item-delete]').click();
 expect((await saved(page)).items).toHaveLength(1);
 page.once('dialog',d=>d.accept());
 await page.locator('[data-item-delete]').click();
 expect((await saved(page)).items).toHaveLength(0);
 await page.locator('[data-life-tab="reminders"]').click();
 await page.locator('[data-reminder-toggle]').check();
 await page.reload();
 await expect(page.locator('[data-life-reminder]')).toContainText('整理书桌');
 await expect(page.locator('[data-reminder-toggle]')).toBeChecked();
});

test('week layout stays within the mobile viewport',async({page})=>{
 await page.goto('/#timetable');
 await page.locator('[data-timetable-mode="week"]').click();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(2);
 await page.locator('[data-timetable-mode="day"]').click();
 await expect(page.locator('[data-timetable-day]')).toHaveCount(7);
 await page.goto('/#life');
 await page.locator('[data-life-tab="items"]').click();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(2);
});
