import {test,expect} from '@playwright/test';

const storage='dorm-os-state-v1';
const tasks=page=>page.evaluate(k=>JSON.parse(localStorage.getItem(k)).tasks,storage);
const batch=(items)=>({format:'xiaoxu-task-batch-v1',source:'chaoxing',tasks:items});
const item=(id,title,due='2026-11-20',status='todo')=>({externalId:id,title,course:'高等数学',due,status});
async function upload(page,doc){
  await page.locator('#taskImportInput').setInputFiles({
    name:'task-snapshot.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(doc))
  });
}

test('local read-only task snapshot import is idempotent and retains personal completion',async({page})=>{
 const issues=[];page.on('pageerror',err=>issues.push(err.message));
 await page.goto('/#tasks');
 await expect(page.getByRole('button',{name:'导入学习通 / 任务 JSON'})).toBeVisible();
 const first=batch([item('course-9/work-2','第一次发布的高数作业')]);
 page.once('dialog',d=>d.accept());
 await upload(page,first);
 await expect(page.locator('#app')).toContainText('第一次发布的高数作业');
 const imported=await tasks(page);
 expect(imported).toHaveLength(1);
 expect(imported[0].source).toBe('chaoxing');
 expect(imported[0].sourceId).toBe('course-9/work-2');
 await page.locator('[data-task-toggle]').check();
 page.once('dialog',d=>d.accept());
 await upload(page,batch([item('course-9/work-2','新的高数作业要求','2026-11-24','submitted')]));
 const revised=await tasks(page);
 expect(revised).toHaveLength(1);
 expect(revised[0].done).toBe(true);
 expect(revised[0].due).toBe('2026-11-24');
 expect(revised[0].sourceStatus).toBe('submitted');
 await expect(page.locator('#app')).toContainText('平台已提交');
 await upload(page,batch([]));
 expect(await tasks(page)).toHaveLength(1);
 await page.reload();
 await expect(page.locator('#app')).toContainText('新的高数作业要求');
 expect(issues).toEqual([]);
});

test('bad task snapshot is rejected without changing existing tasks, second-class entry is explicit',async({page})=>{
 await page.goto('/#tasks');
 page.once('dialog',d=>d.accept());
 await upload(page,batch([item('unique','真实任务')]));
 const saved=await tasks(page);
 await upload(page,batch([item('duplicate','甲'),item('duplicate','乙')]));
 await expect(page.locator('#toast')).toContainText('失败');
 expect(await tasks(page)).toEqual(saved);
 await page.goto('/#campus');
 await expect(page.getByRole('link',{name:/华北水利水电大学第二课堂/})).toHaveAttribute('href','https://dekt.ncwu.edu.cn/');
 await expect(page.locator('#app')).toContainText('未连接');
});
