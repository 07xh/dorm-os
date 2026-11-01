import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';

const STORAGE='dorm-os-state-v1';
const saved = page => page.evaluate(key=>JSON.parse(localStorage.getItem(key)),STORAGE);
const errors = page => {
  const caught=[];
  page.on('pageerror',err=>caught.push(err.message));
  return caught;
};
test('homepage, navigation and responsive mobile viewport',async({page},info)=>{
  const caught=errors(page);
  await page.goto('/');
  await expect(page.locator('#app')).toContainText('让今天有条不紊');
  await page.goto('/#scenes');
  await expect(page.locator('#app')).toContainText('个人快捷场景');
  await page.goto('/#nfc');
  await expect(page.locator('#app')).toContainText('我的 NFC 标签');
  const overflow=await page.evaluate(()=>document.documentElement.scrollWidth-window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(2);
  expect(caught).toEqual([]);
  if(info.project.name==='mobile-chromium')await expect(page.locator('.mobile-tabs')).toBeVisible();
  await info.attach('nfc-page',{body:await page.screenshot({fullPage:true}),contentType:'image/png'});
});

test('create, complete, edit and retain a task after refresh',async({page})=>{
  const caught=errors(page);
  await page.goto('/');
  await page.locator('[data-action="add-task"]').first().click();
  await page.locator('#taskTitle').fill('自动验收：高数作业');
  await page.locator('#modalForm [type="submit"]').click();
  await expect(page.locator('#editor')).not.toBeVisible();
  await page.goto('/#tasks');
  await expect(page.locator('#app')).toContainText('自动验收：高数作业');
  const stored=await saved(page);
  expect(stored.tasks).toHaveLength(1);
  await page.locator('[data-task-toggle]').check();
  expect((await saved(page)).tasks[0].done).toBe(true);
  await page.reload();
  await expect(page.locator('[data-task-toggle]')).toBeChecked();
  await page.locator('[data-task-edit]').click();
  await page.locator('#taskTitle').fill('自动验收：Python 作业');
  await page.locator('#modalForm [type="submit"]').click();
  expect((await saved(page)).tasks[0].title).toBe('自动验收：Python 作业');
  expect((await saved(page)).tasks[0].done).toBe(true);
  expect(caught).toEqual([]);
});

test('register NFC tag and follow its website scene URL',async({page})=>{
  const caught=errors(page);
  await page.goto('/#nfc');
  await page.locator('[data-action="tag-add"]').click();
  await page.locator('#modalForm [name="name"]').fill('书桌自动验收标签');
  await page.locator('#modalForm [name="scene"]').selectOption('study');
  await page.locator('#modalForm [type="submit"]').click();
  const data=await saved(page);
  expect(data.nfcTags).toHaveLength(1);
  expect(data.nfcTags[0].scene).toBe('study');
  await expect(page.locator('#app')).toContainText('未验证实体标签');
  await page.locator('[data-tag-test]').click();
  await expect(page).toHaveURL(/#scenes$/);
  expect((await saved(page)).scene).toBe('study');
  expect(caught).toEqual([]);
});

test('stale NFC links refuse to switch the scene after rebinding',async({page})=>{
  await page.goto('/#nfc');
  await page.locator('[data-action="tag-add"]').click();
  await page.locator('#modalForm [name="name"]').fill('原书桌标签');
  await page.locator('#modalForm [name="scene"]').selectOption('study');
  await page.locator('#modalForm [type="submit"]').click();
  const id=(await saved(page)).nfcTags[0].id;
  await page.locator('[data-tag-edit]').click();
  await page.locator('#modalForm [name="scene"]').selectOption('dev');
  await page.locator('#modalForm [type="submit"]').click();
  await page.goto('/?dormTag='+encodeURIComponent(id)+'&dormScene=study');
  await expect(page).toHaveURL(/#nfc$/);
  await expect(page.locator('#app')).toContainText('请重新写入');
  expect((await saved(page)).scene).toBe('none');
});

test('custom scene shortcut is saved and only opens by explicit click',async({page})=>{
  await page.goto('/#scenes');
  await page.locator('[data-scene="study"]').first().click();
  await page.locator('[data-action="scene-link-add"]').click();
  await page.locator('#sceneLinkName').fill('高数学习资料');
  await page.locator('#sceneLinkUrl').fill('https://example.org/study');
  await page.locator('#modalForm [type="submit"]').click();
  expect((await saved(page)).sceneLinks).toHaveLength(1);
  await expect(page.locator('#app a[href="https://example.org/study"]')).toBeVisible();
  await page.reload();
  await expect(page.locator('#app a[href="https://example.org/study"]')).toBeVisible();
});

test('encrypted export and restore work between isolated browser contexts',async({page,browser})=>{
  await page.goto('/');
  await page.locator('[data-action="add-task"]').first().click();
  await page.locator('#taskTitle').fill('转移验证资料');
  await page.locator('#modalForm [type="submit"]').click();
  await page.goto('/#settings');
  await page.locator('[data-action="export-encrypted"]').click();
  await page.locator('#backupPassword').fill('A-Long-Backup-Password-2026');
  await page.locator('#backupRepeat').fill('A-Long-Backup-Password-2026');
  const [download]=await Promise.all([
    page.waitForEvent('download'),
    page.locator('#modalForm [type="submit"]').click(),
  ]);
  const contents=JSON.parse(await readFile(await download.path(),'utf8'));
  expect(contents.format).toBe('dorm-os-encrypted-v1');
  expect(JSON.stringify(contents)).not.toContain('转移验证资料');
  const context=await browser.newContext();
  try{
    const restored=await context.newPage();
    await restored.goto('http://127.0.0.1:4173/#settings');
    await restored.locator('#importInput').setInputFiles({
      name:'encrypted-backup.json',mimeType:'application/json',
      buffer:Buffer.from(JSON.stringify(contents)),
    });
    await expect(restored.locator('#restorePassword')).toBeVisible();
    await restored.locator('#restorePassword').fill('A-Long-Backup-Password-2026');
    restored.once('dialog',async dialog=>await dialog.accept());
    await restored.locator('#modalForm [type="submit"]').click();
    await expect.poll(async()=> (await saved(restored))?.tasks?.length).toBe(1);
    expect((await saved(restored)).tasks[0].title).toBe('转移验证资料');
  }finally{await context.close();}
});

test('offline PWA shell opens after first successful installation',async({page,context})=>{
  await page.goto('/');
  await page.waitForFunction(async()=>{if(!('serviceWorker' in navigator))return false;await navigator.serviceWorker.ready;return true;});
  await page.reload();
  await context.setOffline(true);
  try{
    await page.reload();
    await expect(page.locator('#app')).toContainText('让今天有条不紊');
    await expect(page.locator('#offlineBanner')).toBeVisible();
  }finally{await context.setOffline(false);}
});


test('focus session survives reload, saves pause and allows switching duration',async({page})=>{
  await page.goto('/#scenes');
  await page.locator('[data-scene="study"]').first().click();
  await page.locator('[data-timer-duration="15"]').click();
  await expect(page.locator('#timerDisplay')).toHaveText('15:00');
  await page.locator('[data-action="timer-toggle"]').click();
  const initial=await saved(page);
  expect(initial.focusTimer.running).toBe(true);
  expect(initial.focusTimer.duration).toBe(15);
  expect(initial.focusTimer.deadline).toBeGreaterThan(Date.now());
  await page.waitForTimeout(1200);
  await page.reload();
  await expect(page.locator('#timerDisplay')).toBeVisible();
  expect((await saved(page)).focusTimer.running).toBe(true);
  await expect(page.locator('#timerDisplay')).not.toHaveText('15:00');
  await page.locator('[data-action="timer-toggle"]').click();
  const paused=(await saved(page)).focusTimer;
  expect(paused.running).toBe(false);
  expect(paused.remaining).toBeGreaterThan(0);
  expect(paused.remaining).toBeLessThan(900);
  await page.reload();
  await expect(page.locator('#timerDisplay')).toHaveText(new RegExp('^14:'));
  expect((await saved(page)).focusTimer.remaining).toBe(paused.remaining);
  await page.locator('[data-timer-duration="45"]').click();
  await expect(page.locator('#timerDisplay')).toHaveText('45:00');
  expect((await saved(page)).focusTimer.duration).toBe(45);
});

test('study deadline exports standards-shaped all-day iCalendar event',async({page})=>{
 await page.goto('/#tasks');
 await page.locator('[data-action="add-task"]').click();
 await page.locator('#taskTitle').fill('高数作业截止');
 await page.locator('#taskDue').fill('2026-12-31');
 await page.locator('#modalForm [type="submit"]').click();
 const [file]=await Promise.all([page.waitForEvent('download'),page.locator('[data-action="export-deadlines"]').click()]);
 expect(file.suggestedFilename()).toMatch(/\.ics$/);
 const ics=await readFile(await file.path(),'utf8');
 expect(ics).toContain('BEGIN:VCALENDAR\r\n');
 expect(ics).toContain('SUMMARY:学习任务：高数作业截止');
 expect(ics).toContain('DTSTART;VALUE=DATE:20261231');
 expect(ics).toContain('DTEND;VALUE=DATE:20270101');
 expect(ics).toContain('END:VCALENDAR\r\n');
});


test('V4 has one five-item navigation and all legacy features in My',async({page},info)=>{
 await page.goto('/');
 const primary=['dashboard','timetable','tasks','scenes','profile'];
 const nav=info.project.name==='mobile-chromium'?page.locator('.mobile-tabs'):page.locator('#sidebarNav');
 await expect(nav.locator('[data-page]')).toHaveCount(5);
 for(const id of primary)await expect(nav.locator('[data-page="'+id+'"]')).toHaveCount(1);
 await nav.locator('[data-page="profile"]').click();
 await expect(page.locator('#app')).toContainText('我的空间');
 for(const id of ['life','campus','nfc','devices','settings'])await expect(page.locator('#app .v4-profile-link[data-page="'+id+'"]')).toBeVisible();
 await page.locator('#app [data-page="nfc"]').click();
 await expect(page.locator('#app')).toContainText('我的 NFC 标签');
});

test('V4 contextual add avoids duplicated task and schedule actions',async({page})=>{
 await page.goto('/#tasks');
 await expect(page.locator('#quickAdd')).toHaveAttribute('data-action','add-task');
 await expect(page.locator('#app [data-action="add-task"]')).toHaveCount(0);
 await page.locator('#quickAdd').click();
 await expect(page.locator('#taskTitle')).toBeVisible();
 await page.locator('[data-action="close-modal"]').click();
 await page.goto('/#timetable');
 await expect(page.locator('#quickAdd')).toHaveAttribute('data-action','add-class');
 await page.locator('#quickAdd').click();
 await expect(page.locator('#classSubject')).toBeVisible();
});

test('V4 scene quick actions and extended home cards remain accessible',async({page})=>{
 await page.goto('/');
 await expect(page.locator('.v4-hero')).toBeVisible();
 await expect(page.locator('.v4-scene-grid [data-scene]')).toHaveCount(4);
 await expect(page.locator('.v4-main-widgets [data-home-widget]')).toHaveCount(2);
 await page.locator('.v4-expansion summary').first().click();
 await expect(page.locator('.v4-extra-widgets [data-home-widget="weather"]')).toBeVisible();
 await page.locator('.v4-scene-grid [data-scene="study"]').click();
 await expect(page).toHaveURL(/#scenes$/);
 await expect(page.locator('#app')).toContainText('个人快捷场景');
});
