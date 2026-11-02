import {test,expect} from '@playwright/test';

const stored=page=>page.evaluate(()=>JSON.parse(localStorage.getItem('dorm-os-state-v1')));
async function seed(page) {
 await page.goto('/');
 await page.evaluate(()=>{
  const d=now().day, after=(d+1)%7;
  const s=JSON.parse(localStorage.getItem('dorm-os-state-v1')||'null')||structuredClone(state);
  s.tasks=[
   {id:'ux-t1',title:'真实本地作业一',due:'2026-11-20',category:'高数',done:false,createdAt:'2026-11-02'},
   {id:'ux-t2',title:'真实本地作业二',due:'2026-11-22',category:'Python',done:false,createdAt:'2026-11-02'},
  ];
  s.classes=[
   {id:'ux-c1',subject:'今天的本地高数课程',day:d,start:'08:00',end:'09:40',location:'B201'},
   {id:'ux-c2',subject:'明天的本地英语课程',day:after,start:'09:00',end:'10:40',location:'C301'},
  ];
  localStorage.setItem('dorm-os-state-v1',JSON.stringify(s));
 });
 await page.reload();
}

test('mobile home headline remains horizontal, legible and compact',async({page})=>{
 await page.setViewportSize({width:360,height:780});
 await seed(page);
 const dims=await page.locator('.x-v7-home-intro').evaluate(el=>{
  const hero=el.getBoundingClientRect(),h=el.querySelector('h1');
  const title=h.getBoundingClientRect(),cs=getComputedStyle(h),heroStyle=getComputedStyle(el);
  return {heroHeight:hero.height,width:hero.width,headWidth:title.width,headHeight:title.height,
   direction:cs.writingMode,display:heroStyle.display,heroText:heroStyle.color,headText:cs.color,
   overflow:document.documentElement.scrollWidth-window.innerWidth};
 });
 expect(dims.direction).toBe('horizontal-tb');
 expect(dims.display).toBe('block');
 expect(dims.headWidth).toBeGreaterThan(100);
 expect(dims.headHeight).toBeLessThan(58);
 expect(dims.heroHeight).toBeLessThan(370);
 expect(dims.overflow).toBeLessThanOrEqual(2);
 await expect(page.locator('.x-v7-course-name')).toContainText('今天的本地高数课程');
});

test('day chips switch actual local course content and course cards navigate',async({page})=>{
 await seed(page);
 await expect(page.locator('[data-home-day]')).toHaveCount(3);
 await page.locator('[data-home-day="1"]').click();
 await expect(page.locator('[data-home-day="1"]')).toHaveAttribute('aria-pressed','true');
 await expect(page.locator('.x-v7-course-name')).toContainText('明天的本地英语课程');
 await expect(page.locator('.x-v7-course-name')).not.toContainText('今天的本地高数课程');
 await page.locator('.x-v7-course-preview').click();
 await expect(page).toHaveURL(/#timetable$/);
 await page.goto('/#dashboard');
 await expect(page.locator('[data-home-day="1"]')).toHaveAttribute('aria-pressed','true');
 await page.locator('[data-home-day="0"]').click();
 await expect(page.locator('.x-v7-course-name')).toContainText('今天的本地高数课程');
});

test('homepage task checkbox persists and task title opens existing editor',async({page})=>{
 await seed(page);
 await expect(page.locator('.x-v7-task-preview')).toHaveCount(2);
 await page.locator('.x-v7-task-preview input[data-task-toggle="ux-t1"]').check();
 const data=await stored(page);
 expect(data.tasks.find(x=>x.id==='ux-t1').done).toBe(true);
 await expect(page.locator('.x-v7-task-preview')).toHaveCount(1);
 await page.locator('.x-v7-task-preview [data-task-edit="ux-t2"]').click();
 await expect(page.locator('#editorContent')).toContainText('编辑任务');
 await page.locator('[data-action="close-modal"]').click();
 await page.reload();
 expect((await stored(page)).tasks.find(x=>x.id==='ux-t1').done).toBe(true);
});

test('home responsive task widgets and dark theme keep content visible',async({page})=>{
 for(const width of [360,393,768,1280]){
  await page.setViewportSize({width,height:860});
  await seed(page);
  const over=await page.evaluate(()=>document.documentElement.scrollWidth-window.innerWidth);
  expect(over).toBeLessThanOrEqual(2);
  await expect(page.locator('.x-v7-home-intro h1')).toHaveText('今日安排');
  await expect(page.locator('.x-v7-home-grid [data-home-widget]')).toHaveCount(2);
 }
 await page.evaluate(()=>{
  const s=JSON.parse(localStorage.getItem('dorm-os-state-v1'));
  s.settings.theme='dark';localStorage.setItem('dorm-os-state-v1',JSON.stringify(s));
 });
 await page.reload();
 await expect(page.locator('html')).not.toHaveClass(/theme-light/);
 await expect(page.locator('.x-v7-course-name')).toBeVisible();
 await expect(page.locator('.x-v7-day-chip.selected')).toBeVisible();
});
