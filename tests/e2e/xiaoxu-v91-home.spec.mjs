import { test, expect } from '@playwright/test';

test('V9.1 dashboard stays compact and uses real local empty states', async ({ page }) => {
 await page.goto('/');
 const app=page.locator('#app');
 await expect(app.locator('.x-v91-next-area')).toHaveCount(1);
 await expect(app.locator('.x-v7-home-courses')).toHaveCount(0);
 await expect(app.locator('.x-v8-next-card')).toHaveCount(0);
 await expect(app.locator('.x-v8-connection')).toHaveCount(0);
 await expect(app.locator('.x-v7-home-intro h1')).toHaveText('今日安排');
 await expect(app.locator('.x-v91-course-empty')).toBeVisible();
 await app.locator('.x-v91-course-empty [data-action="add-class"]').click();
 await expect(page.locator('#editor')).toBeVisible();
 await expect(page.locator('#classSubject')).toBeVisible();
 await page.locator('#editor').press('Escape');
 await expect(page.locator('#editor')).toBeHidden();
});

test('V9.1 day chooser keeps keyboard focus and changes the actual day heading', async ({ page }) => {
 await page.goto('/');
 const day=page.locator('#app [data-home-day="1"]');
 await day.focus();
 await page.keyboard.press('Enter');
 await expect(page.locator('#app .x-v7-home-intro h1')).toHaveText('明天安排');
 await expect(page.locator('#app [data-home-day="1"]')).toBeFocused();
 await expect(page.locator('#app [data-home-day="1"]')).toHaveAttribute('aria-pressed','true');
 await page.locator('#app [data-home-day="2"]').click();
 await expect(page.locator('#app .x-v7-home-intro h1')).toHaveText('后天安排');
});

test('V9.1 task filters retain keyboard focus through render', async ({ page }) => {
 await page.goto('/');
 await page.locator('#sidebarNav [data-page="tasks"]').click();
 const control=page.locator('#app [data-filter="done"]');
 await control.focus();
 await page.keyboard.press('Enter');
 await expect(page.locator('#app [data-filter="done"]')).toBeFocused();
 await expect(page.locator('#app [data-filter="done"]')).toHaveAttribute('aria-pressed','true');
});
