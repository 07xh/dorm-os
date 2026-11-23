import {test, expect} from '@playwright/test';

test('theme toolbar changes CSS without recreating profile DOM', async ({page}) => {
  await page.goto('/#profile');
  const profileContent = page.locator('#app > :first-child');
  await expect(profileContent).toHaveCount(1);
  await page.evaluate(() => {
    window.__profileBeforeTheme = document.querySelector('#app').firstElementChild;
  });
  await page.locator('[data-action="theme-v8"]').click();
  await expect(page.locator('html')).toHaveAttribute('data-theme','dark');
  expect(await page.evaluate(() =>
    window.__profileBeforeTheme === document.querySelector('#app').firstElementChild
  )).toBe(true);
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-theme','dark');
  await expect(page.locator('[data-action="theme-v8"]')).toBeVisible();
});

test('settings theme selection preserves open settings panel and focused control', async ({page}) => {
  await page.goto('/#settings');
  const more = page.locator('.xu-settings-more');
  await expect(more).toHaveCount(1);
  await more.locator('> summary').click();
  const selector = page.locator('#themeSelect');
  await expect(selector).toBeVisible();
  await page.evaluate(() => {
    window.__settingsBeforeTheme = document.querySelector('#app').firstElementChild;
  });
  await selector.focus();
  await expect(selector).toBeFocused();
  await selector.selectOption('dark');
  await expect(page.locator('html')).toHaveAttribute('data-theme','dark');
  expect(await page.evaluate(() =>
    window.__settingsBeforeTheme === document.querySelector('#app').firstElementChild
  )).toBe(true);
  await expect(more).toHaveAttribute('open','');
  await expect(selector).toHaveValue('dark');
  await expect(selector).toBeFocused();
  await selector.selectOption('light');
  await expect(page.locator('html')).toHaveAttribute('data-theme','light');
  expect(await page.evaluate(() =>
    window.__settingsBeforeTheme === document.querySelector('#app').firstElementChild
  )).toBe(true);
});
