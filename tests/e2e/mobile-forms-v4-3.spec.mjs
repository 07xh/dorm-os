import { test,expect } from '@playwright/test';

test('mobile task form acts as a bottom sheet with persistent actions and no data loss on cancel',async({page},info)=>{
 await page.goto('/#tasks');
 await page.locator('#quickAdd').click();
 const dialog=page.locator('#editor');
 await expect(dialog).toBeVisible();
 await expect(dialog).toHaveClass(/form-editor/);
 await expect(dialog.locator('.dorm-form-header h2')).toHaveText('新增任务');
 await expect(page.locator('#taskTitle')).toBeFocused();
 const viewport=page.viewportSize();
 const rect=await dialog.boundingBox();
 expect(rect).not.toBeNull();
 expect(rect.x).toBeGreaterThanOrEqual(-1);
 expect(rect.x+rect.width).toBeLessThanOrEqual(viewport.width+1);
 if(info.project.name==='mobile-chromium')expect(Math.abs(rect.y+rect.height-viewport.height)).toBeLessThanOrEqual(3);
 const footer=dialog.locator('.dorm-form-actions');
 await expect(footer).toBeVisible();
 await page.locator('#taskTitle').fill('不应保存的草稿');
 await page.locator('[data-action="close-modal"]').click();
 await expect(dialog).not.toBeVisible();
 const saved=await page.evaluate(()=>JSON.parse(localStorage.getItem('dorm-os-state-v1')));
 expect(saved?.tasks||[]).toHaveLength(0);
});

test('NFC, reminders and items have accessible labeled inputs and retain save',async({page})=>{
 await page.goto('/#nfc');
 await page.locator('[data-action="tag-add"]').click();
 await expect(page.getByLabel('标签名称')).toBeVisible();
 await expect(page.getByLabel('绑定的网站场景')).toBeVisible();
 await page.getByLabel('标签名称').fill('床头标签');
 await page.getByLabel('绑定的网站场景').selectOption('rest');
 await page.locator('#modalForm [type="submit"]').click();
 await expect(page.locator('.tag-entry')).toContainText('床头标签');
 await page.goto('/#life');
 await page.locator('[data-action="reminder-add"]:visible').click();
 await expect(page.getByLabel('提醒内容')).toBeVisible();
 await page.getByLabel('提醒内容').fill('洗衣提醒');
 await page.locator('#modalForm [type="submit"]').click();
 await page.locator('[data-life-tab="items"]').click();
 await page.locator('[data-action="item-add"]').click();
 await expect(page.getByLabel('物品名称')).toBeVisible();
 await expect(page.getByLabel('存放位置（可选）')).toBeVisible();
 await page.getByLabel('物品名称').fill('网线');
 await page.locator('#modalForm [type="submit"]').click();
 const data=await page.evaluate(()=>JSON.parse(localStorage.getItem('dorm-os-state-v1')));
 expect(data.nfcTags).toHaveLength(1);
 expect(data.reminders).toHaveLength(1);
 expect(data.items).toHaveLength(1);
});

test('long editable forms keep footer visible and fit a small mobile viewport',async({page},info)=>{
 await page.goto('/#timetable');
 await page.locator('#quickAdd').click();
 await expect(page.locator('.dorm-form-body')).toBeVisible();
 const geometry=await page.evaluate(()=>{
 const dlg=document.querySelector('.editor.form-editor'),footer=dlg.querySelector('.dorm-form-actions'),body=dlg.querySelector('.dorm-form-body');
 const d=dlg.getBoundingClientRect(),f=footer.getBoundingClientRect();
 return {width:document.documentElement.scrollWidth,viewport:innerWidth,dtop:d.top,dbottom:d.bottom,ftop:f.top,fbottom:f.bottom,scrollable:body.scrollHeight>=body.clientHeight};
 });
 expect(geometry.width-geometry.viewport).toBeLessThanOrEqual(2);
 expect(geometry.ftop).toBeGreaterThanOrEqual(geometry.dtop-1);
 expect(geometry.fbottom).toBeLessThanOrEqual(geometry.dbottom+1);
 if(info.project.name==='mobile-chromium'){
  const dialog=page.locator('#editor');
  await expect(dialog).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(dialog).not.toBeVisible();
 }
});

test('global search no longer inherits previous bottom-sheet editor state',async({page})=>{
 await page.goto('/#tasks');
 await page.locator('#quickAdd').click();
 await expect(page.locator('#editor')).toHaveClass(/form-editor/);
 await page.locator('[data-action="close-modal"]').click();
 await page.locator('[data-action="global-search"]').click();
 await expect(page.locator('#editor')).toHaveClass(/search-editor/);
 await expect(page.locator('#editor')).not.toHaveClass(/form-editor/);
 await expect(page.locator('#editor')).toHaveAttribute('aria-label','全站搜索');
 await expect(page.locator('#globalSearchInput')).toBeFocused();
});
