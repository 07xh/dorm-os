import {test,expect} from '@playwright/test';
const STORAGE='dorm-os-state-v1';
test('global search finds personal records across pages and opens edit forms',async({page})=>{
 await page.goto('/');
 await page.evaluate(key=>{
  const s=JSON.parse(localStorage.getItem(key));
  s.tasks=[{id:'task-math',title:'高数周五作业',category:'高数',due:'2026-11-06',done:false,createdAt:'2026-11-01'}];
  s.classes=[{id:'class-math',day:1,start:'08:00',end:'09:40',subject:'高数',location:'A101'}];
  s.calendarEvents=[{id:'calendar-math',uid:'math-ics',date:'2026-11-02',start:'10:00',end:'11:00',subject:'高数答疑',location:'教学楼'}];
  s.reminders=[{id:'reminder-earphones',text:'给耳机充电',due:'2026-11-03',done:false}];
  s.items=[{id:'item-earphones',name:'蓝牙耳机',place:'书桌抽屉'}];
  s.nfcTags=[{id:'tag-desk',name:'书桌学习标签',scene:'study'}];
  s.sceneLinks=[{id:'link-math',scene:'study',name:'高数课程资料',url:'https://example.org/math'}];
  s.campusLinks=[{id:'campus-link',name:'学校教务平台',url:'https://example.org/portal'}];
  localStorage.setItem(key,JSON.stringify(s));
 },STORAGE);
 await page.reload();
 await page.keyboard.press('Control+k');
 await expect(page.locator('#globalSearchInput')).toBeVisible();
 await page.locator('#globalSearchInput').fill('高数');
 await expect(page.locator('.search-result')).toHaveCount(4);
 await expect(page.locator('.search-results')).toContainText('高数周五作业');
 await expect(page.locator('.search-results')).toContainText('高数答疑');
 await page.locator('[data-search-open="class"][data-search-id="class-math"]').click();
 await expect(page.locator('#breadcrumb')).toContainText('我的课表');
 await page.locator('[data-action="global-search"]').click();
 await page.locator('#globalSearchInput').fill('耳机');
 await expect(page.locator('.search-result')).toHaveCount(2);
 await page.locator('[data-search-edit="item"][data-search-id="item-earphones"]').click();
 await expect(page.locator('#editor')).toBeVisible();
 await expect(page.locator('#editorContent')).toContainText('编辑个人物品');
 await expect(page.locator('#editorContent input[name="name"]')).toHaveValue('蓝牙耳机');
});
test('global search escapes stored text, handles no matches and leaves data untouched',async({page})=>{
 await page.goto('/');
 await page.evaluate(key=>{
  const s=JSON.parse(localStorage.getItem(key));
  s.tasks=[{id:'task-xss',title:'<img src=x onerror=alert(1)>',category:'其他',due:'',done:false,createdAt:''}];
  localStorage.setItem(key,JSON.stringify(s));
 },STORAGE);
 await page.reload();
 await page.locator('[data-action="global-search"]').click();
 await page.locator('#globalSearchInput').fill('img');
 await expect(page.locator('.search-result')).toContainText('<img src=x onerror=alert(1)>');
 await expect(page.locator('.search-results img')).toHaveCount(0);
 await page.locator('#globalSearchInput').fill('不存在的关键词');
 await expect(page.locator('.search-empty')).toContainText('没有找到匹配内容');
 expect(await page.evaluate(key=>JSON.parse(localStorage.getItem(key)).tasks[0].title,STORAGE)).toBe('<img src=x onerror=alert(1)>');
});
