import {test,expect} from '@playwright/test';
import {readFile} from 'node:fs/promises';
const OLD='a'.repeat(40),NEW='b'.repeat(40);
const source=await readFile(new URL('../../public/index.html',import.meta.url),'utf8');
// These are separate documents, as a real deployment would serve them. No real account data.
async function releases(page,{broken=false,initial=NEW}={}){
 let latest=initial;const documents=[];
 await page.route('**/*',route=>{
  const request=route.request(),url=new URL(request.url());
  if(url.pathname==='/revision.json')return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({commit:latest})});
  if(request.isNavigationRequest()&&url.pathname==='/'){
   const current=url.searchParams.get('_xu_release')===NEW.slice(0,12);
   let html=source.replaceAll('__XIAOXU_RELEASE_SHA__',current?NEW:OLD);
   if(broken&&!current)html=html.replace('<script>','<script>throw new Error("synthetic stale app startup");');
   documents.push(current?'new':'old');
   return route.fulfill({status:200,contentType:'text/html',headers:{'cache-control':'no-store'},body:html});
  }
  return route.continue();
 });
 return {documents,deploy(){latest=NEW;}};
}
test('stale document recovers despite broken application startup and old reload-target guard',async({page})=>{
 await page.addInitScript(newSha=>{
  sessionStorage.setItem('xiaoxu-release-reload-target',newSha);
  localStorage.setItem('update-preserve-marker','keep');
 },NEW);
 const fixture=await releases(page,{broken:true});
 await page.goto('/#tasks',{waitUntil:'domcontentloaded'});
 await expect(page.locator('meta[name="xiaoxu-release"]')).toHaveAttribute('content',NEW);
 await expect(page.getByRole('heading',{name:'我的任务',exact:true})).toBeVisible();
 expect(fixture.documents).toEqual(['old','new']);
 expect(await page.evaluate(()=>localStorage.getItem('update-preserve-marker'))).toBe('keep');
 await expect.poll(()=>page.evaluate(()=>new URL(location.href).searchParams.has('_xu_release'))).toBe(false);
});
test('deployment waits for task editor then updates automatically after saving without losing task',async({page})=>{
 const fixture=await releases(page,{initial:OLD});
 await page.goto('/#tasks');
 await page.locator('#quickAdd').click();
 await page.locator('#taskTitle').fill('更新时保留的任务');
 fixture.deploy();
 await page.evaluate(()=>window.dispatchEvent(new Event('online')));
 await expect(page.locator('#xuUpdateNotice')).toContainText('保存或关闭编辑后');
 await expect(page.locator('#taskTitle')).toHaveValue('更新时保留的任务');
 expect(fixture.documents).toEqual(['old']);
 await page.locator('#modalForm [type="submit"]').click();
 await expect(page.locator('meta[name="xiaoxu-release"]')).toHaveAttribute('content',NEW);
 await expect(page.locator('.v4-task-list')).toContainText('更新时保留的任务');
 const tasks=await page.evaluate(()=>JSON.parse(localStorage.getItem('dorm-os-state-v1')).tasks);
 expect(tasks.some(t=>t.title==='更新时保留的任务')).toBe(true);
 expect(fixture.documents).toEqual(['old','new']);
});
