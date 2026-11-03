import { test, expect } from '@playwright/test';

test('a controlled service worker fetches current profile CSS despite obsolete cached CSS', async ({page}) => {
  await page.goto('/');
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready;
  });
  await expect.poll(() => page.evaluate(() => Boolean(navigator.serviceWorker.controller))).toBe(true);

  // Simulates a previous release's cached CSS after a newer HTML document arrives.
  const seeded=await page.evaluate(async () => {
    const names=(await caches.keys()).filter(name=>name.startsWith('dorm-os-shell-'));
    if(names.length!==1) throw Error('Expected one active offline shell; got '+names.join(', '));
    const cache=await caches.open(names[0]);
    const stylesheet=document.querySelector('link[href*="xiaoxu-owner-auth.css"]');
    if (!stylesheet) throw Error('Owner CSS link missing');
    await cache.put(stylesheet.href,
      new Response('/* obsolete CSS from a previous release */', {
        headers:{'Content-Type':'text/css; charset=utf-8'}
      }));
    return names[0];
  });
  expect(seeded).toMatch(/^dorm-os-shell-/);

  await page.reload();
  await page.evaluate(() => window.setPage('profile'));
  const dimensions=await page.locator('.xu-owner-duration-chevron').evaluate(svg=>({
    width:svg.getBoundingClientRect().width,
    height:svg.getBoundingClientRect().height
  }));
  expect(dimensions.width).toBeLessThanOrEqual(24);
  expect(dimensions.height).toBeLessThanOrEqual(24);
  const row=page.locator('[data-owner-duration-toggle]');
  await expect(row).toBeVisible();
  const rowBox=await row.boundingBox();
  expect(rowBox?.height).toBeLessThanOrEqual(100);
});
