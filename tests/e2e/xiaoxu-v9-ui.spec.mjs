import { test, expect } from '@playwright/test';

test('V9 preserves five original routes and a single login inside 我的', async ({ page }, testInfo) => {
 await page.goto('/');
 const nav=page.locator('#sidebarNav');
 await expect(nav.locator('button[data-page]')).toHaveCount(5);
 await expect(page.locator('[data-owner-session-link]')).toHaveCount(0);
 if(testInfo.project.name==='mobile-chromium'){
  const box=await nav.boundingBox(),viewport=page.viewportSize();
  expect(box).not.toBeNull();
  expect(Math.abs(box.y+box.height-viewport.height)).toBeLessThan(3);
  expect(Math.abs(box.width-viewport.width)).toBeLessThan(3);
 }else {
  const box=await nav.boundingBox();
  expect(box).not.toBeNull();
  expect(box.y).toBeLessThan(90);
 }
 await nav.locator('[data-page="tasks"]').click();
 await expect(page.locator('#app .v4-task-toolbar')).toBeVisible();
 await expect(nav.locator('[data-page="tasks"]')).toHaveAttribute('aria-current','page');
 await nav.locator('[data-page="timetable"]').click();
 await expect(page.locator('#app .v42-schedule-toolbar')).toBeVisible();
 await nav.locator('[data-page="academic"]').click();
 await expect(page.locator('#app .x-v7-secondary')).toBeVisible();
 await nav.locator('[data-page="profile"]').click();
 await expect(page.locator('[data-owner-auth-open]')).toHaveCount(1);
 await expect(page.locator('[data-owner-auth-entry-title]')).toHaveText('登录校序');
 await page.locator('[data-owner-auth-open]').click();
 await expect(page.locator('#xuOwnerDialog')).toBeVisible();
 await page.keyboard.press('Escape');
 await expect(page.locator('#xuOwnerDialog')).toBeHidden();
 await nav.locator('[data-page="dashboard"]').click();
 await expect(page.locator('#app .x-v7-home-intro')).toBeVisible();
});

test('V9 disables page motion when user requests reduced motion', async ({ page }) => {
 await page.emulateMedia({reducedMotion:'reduce'});
 await page.goto('/');
 await page.locator('#sidebarNav [data-page="tasks"]').click();
 expect(await page.locator('#app > :first-child').evaluate(el=>getComputedStyle(el).animationName)).toBe('none');
});

test('V9 PWA includes its UI script, stylesheet and in-profile login stylesheet', async ({ page }) => {
 await page.goto('/sw.js');
 const text=await page.locator('body').innerText();
 expect(text).toContain("'/xiaoxu-workspace.css'");
 expect(text).toContain("'/xiaoxu-workspace.js'");
 expect(text).toContain("'/xiaoxu-owner-auth.css'");
});
