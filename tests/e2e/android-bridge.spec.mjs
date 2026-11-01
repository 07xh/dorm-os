import { test, expect } from '@playwright/test';

test('website routes scene and device links to safe Android assistant landing', async ({page}) => {
  await page.goto('/#scenes');
  await page.locator('[data-scene="study"]').first().click();
  const link=page.locator('[data-assistant-open="scene"]');
  await expect(link).toHaveAttribute('href',/\/assistant\/\?scene=study$/);
  await link.click();
  await expect(page).toHaveURL(/\/assistant\/\?scene=study$/);
  await expect(page.locator('#description')).toContainText('学习');
  await expect(page.locator('#launch')).toHaveAttribute('href','dormos://assistant/open?scene=study');
  await page.goto('/#devices');
  await expect(page.locator('[data-assistant-open="device"]')).toHaveAttribute('href',/\/assistant\/\?scene=none$/);
});

test('malformed request cannot create a launch link', async ({page}) => {
  await page.goto('/assistant/?scene=study&command=su');
  await expect(page.locator('#launch')).toBeHidden();
  await expect(page.locator('#description')).toContainText('无效');
  await page.goto('/assistant/?scene=unknown');
  await expect(page.locator('#launch')).toBeHidden();
});
