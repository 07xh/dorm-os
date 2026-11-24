import {test,expect} from '@playwright/test';

test('ordinary task and source filters keep task tools and platform panels mounted',async ({page})=>{
 await page.goto('/#tasks');
 const buttons=page.locator('.v4-task-toolbar [data-filter]');
 await expect(buttons).toHaveCount(4);
 const tools=page.locator('.xu-task-tools');
 await expect(tools).toBeVisible();
 await tools.locator('summary').click();
 await expect(tools).toHaveAttribute('open','');
 const source=page.locator('[data-task-source]');
 await expect(source).toBeVisible();
 await page.evaluate(()=>{
  window.__taskDom={
   tool:document.querySelector('.xu-task-tools'),
   list:document.querySelector('.v4-task-list'),
   chaoxing:document.querySelector('#xuChaoxingWorkHost'),
   reminders:document.querySelector('[data-task-list-view]'),
   source:document.querySelector('[data-task-source]')
  };
 });
 const done=page.locator('[data-filter="done"]');
 await done.click();
 await expect(done).toHaveAttribute('aria-pressed','true');
 await expect(page).toHaveURL(/#tasks$/);
 await expect(tools).toHaveAttribute('open','');
 expect(await page.evaluate(()=>{
  const b=window.__taskDom;
  return b.tool===document.querySelector('.xu-task-tools')&&
    b.list===document.querySelector('.v4-task-list')&&
    b.chaoxing===document.querySelector('#xuChaoxingWorkHost')&&
    b.reminders===document.querySelector('[data-task-list-view]')&&
    b.source===document.querySelector('[data-task-source]');
 })).toBe(true);
 await source.selectOption('manual');
 await expect(source).toHaveValue('manual');
 expect(await page.evaluate(()=>window.__taskDom.source===document.querySelector('[data-task-source]'))).toBe(true);
 await expect(tools).toHaveAttribute('open','');
 await expect(page.locator('[data-workspace-count]')).toContainText('项任务');
 const review=page.locator('[data-filter="review"]');
 await review.click();
 await expect(page).toHaveURL(/#review$/);
 await expect(page.locator('[data-task-review-view]')).toBeVisible();
 await expect(review).toHaveAttribute('aria-pressed','true');
 await page.locator('[data-filter="all"]').click();
 await expect(page).toHaveURL(/#tasks$/);
 await expect(page.locator('[data-task-list-view]')).toBeVisible();
});
