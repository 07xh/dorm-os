import {test, expect} from '@playwright/test';

const STORAGE='dorm-os-state-v1';
const schoolToday=()=>{
 const fields=Object.fromEntries(new Intl.DateTimeFormat('en-US',{
  timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'
 }).formatToParts(new Date()).map(p=>[p.type,p.value]));
 return fields.year+fields.month+fields.day;
};
const event=(id,date,subject,valid=true)=>[
 'BEGIN:VEVENT','UID:'+id,'DTSTART:'+date+'T140000',
 ...(valid?['DTEND:'+date+'T154000']:[]),
 'SUMMARY:'+subject,'LOCATION:A201','END:VEVENT'
].join('\r\n');
const ics=body=>'BEGIN:VCALENDAR\r\nVERSION:2.0\r\n'+body+'\r\nEND:VCALENDAR\r\n';
const upload=(page,contents)=>page.locator('#icsInput').setInputFiles({
 name:'my-schedule.ics',mimeType:'text/calendar',buffer:Buffer.from(contents)
});

test('ICS import is visible in selected week/day, and disappears outside its date',async({page})=>{
 await page.goto('/#timetable');
 const day=schoolToday();
 page.once('dialog',dialog=>dialog.accept());
 await upload(page,ics(event('test-occurrence@invalid',day,'日期课程可见性')));
 await expect.poll(async()=>page.evaluate(key=>JSON.parse(localStorage.getItem(key)).calendarEvents.length,STORAGE)).toBe(1);
 await page.locator('[data-timetable-mode="week"]').click();
 await expect(page.locator('.v42-week-grid')).toContainText('日期课程可见性');
 await expect(page.locator('.v42-week-grid [aria-label="按日期导入的课程"]')).toHaveCount(1);
 await page.locator('[data-timetable-week="-1"]').click();
 await expect(page.locator('.v42-week-grid')).not.toContainText('日期课程可见性');
 await page.locator('[data-timetable-week="0"]').click();
 await expect(page.locator('.v42-week-grid')).toContainText('日期课程可见性');
 await page.reload();
 await page.locator('[data-timetable-mode="week"]').click();
 await expect(page.locator('.v42-week-grid')).toContainText('日期课程可见性');
});

test('partial ICS import states skipped events before any data is saved',async({page})=>{
 await page.goto('/#timetable');
 const day=schoolToday();
 const dialogs=[];
 page.once('dialog',async dialog=>{
  dialogs.push(dialog.message());
  await dialog.dismiss();
 });
 await upload(page,ics(event('ok@invalid',day,'有效示例')+'\r\n'+event('bad@invalid',day,'缺少结束时间',false)));
 await expect.poll(()=>dialogs.length).toBe(1);
 expect(dialogs[0]).toContain('跳过 1 条');
 expect(dialogs[0]).toContain('缺少开始或结束时间');
 expect(await page.evaluate(key=>JSON.parse(localStorage.getItem(key)).calendarEvents.length,STORAGE)).toBe(0);
});
