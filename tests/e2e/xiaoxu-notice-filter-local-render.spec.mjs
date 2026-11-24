import {test, expect} from '@playwright/test';

test('通知分类筛选只更新列表，不重建搜索框和教务面板', async ({page}) => {
  await page.goto('/#academic');
  const input = page.locator('#noticeSearch');
  await expect(input).toBeVisible({timeout:15000});
  await page.waitForLoadState('networkidle');
  await input.fill('课程');
  const unread = page.locator('[data-notice-filter="unread"]');
  const all = page.locator('[data-notice-filter="all"]');
  await page.evaluate(() => {
    window.__noticeSearchNode = document.querySelector('#noticeSearch');
    window.__noticePanelNode = document.querySelector('#xu-academic-panel');
    window.__noticeListNode = document.querySelector('#publicNoticeList');
  });

  await unread.click();
  await expect(unread).toHaveAttribute('aria-pressed','true');
  await expect(unread).toHaveClass(/selected/);
  await expect(all).toHaveAttribute('aria-pressed','false');
  await expect(input).toHaveValue('课程');
  expect(await page.evaluate(() =>
    window.__noticeSearchNode === document.querySelector('#noticeSearch') &&
    window.__noticePanelNode === document.querySelector('#xu-academic-panel') &&
    window.__noticeListNode === document.querySelector('#publicNoticeList')
  )).toBe(true);

  await all.click();
  await expect(all).toHaveAttribute('aria-pressed','true');
  await expect(unread).toHaveAttribute('aria-pressed','false');
  expect(await page.evaluate(() =>
    window.__noticeSearchNode === document.querySelector('#noticeSearch')
  )).toBe(true);
});
