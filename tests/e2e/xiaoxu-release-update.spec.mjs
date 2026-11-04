import {test as base,expect} from '@playwright/test';
import {readFile,readdir} from 'node:fs/promises';
import {createServer} from 'node:http';
const OLD='a'.repeat(40),NEW='b'.repeat(40),root=new URL('../../public/',import.meta.url);
const files=new Map();
for(const name of await readdir(root))if(/\.(?:js|css)$/.test(name))files.set('/'+name,await readFile(new URL(name,root)));
for(const name of ['index.html','manifest.webmanifest','icons/dorm-os.svg','icons/icon-192.png','icons/icon-512.png','data/notices.json'])files.set('/'+name,await readFile(new URL(name,root)));
// A real same-origin HTTP fixture is required: page.route() cannot mock network
// requests made by an active service worker. Each test gets its own origin/port.
const test=base.extend({releaseSite:async({},use)=>{
 let latest=OLD,broken=false;const documents=[];
 const server=createServer((request,response)=>{
  const url=new URL(request.url,'http://localhost');
  if(url.pathname==='/revision.json'){
   response.writeHead(200,{'content-type':'application/json','cache-control':'no-store'});response.end(JSON.stringify({commit:latest}));return;
  }
  const path=url.pathname==='/'?'/index.html':url.pathname;
  const content=files.get(path);
  if(!content){response.writeHead(404);response.end();return;}
  let body=content;
  if(path==='/index.html'){
   const current=url.searchParams.get('_xu_release')===NEW.slice(0,12);
   body=content.toString().replaceAll('__XIAOXU_RELEASE_SHA__',current?NEW:OLD);
   if(broken&&!current)body=body.replace('<script>','<script>throw new Error("synthetic stale app startup");');
   // Count actual navigations only; a worker also precaches the root document.
   if(request.headers['sec-fetch-mode']==='navigate')documents.push(current?'new':'old');
  }else if(path==='/sw.js')body=content.toString().replaceAll('__XIAOXU_RELEASE_SHA__',latest);
  const type=path.endsWith('.html')?'text/html':path.endsWith('.js')?'application/javascript':path.endsWith('.css')?'text/css':path.endsWith('.png')?'image/png':path.endsWith('.svg')?'image/svg+xml':'application/json';
  response.writeHead(200,{'content-type':type,'cache-control':'no-store'});response.end(body);
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 try{await use({url:'http://127.0.0.1:'+server.address().port,documents,deploy(){latest=NEW;},breakStartup(){broken=true;latest=NEW;}});}
 finally{await new Promise(resolve=>{server.close(resolve);server.closeAllConnections();});}
}});
test('stale document recovers despite broken application startup and old reload-target guard',async({page,releaseSite})=>{
 await page.addInitScript(newSha=>{
  sessionStorage.setItem('xiaoxu-release-reload-target',newSha);
  localStorage.setItem('update-preserve-marker','keep');
 },NEW);
 releaseSite.breakStartup();
 await page.goto(releaseSite.url+'/#tasks',{waitUntil:'domcontentloaded'});
 await expect(page.locator('meta[name="xiaoxu-release"]')).toHaveAttribute('content',NEW);
 await expect(page.getByRole('heading',{name:'我的任务',exact:true})).toBeVisible();
 expect(releaseSite.documents).toEqual(['old','new']);
 expect(await page.evaluate(()=>localStorage.getItem('update-preserve-marker'))).toBe('keep');
 await expect.poll(()=>page.evaluate(()=>new URL(location.href).searchParams.has('_xu_release'))).toBe(false);
});
test('deployment waits for task editor then updates automatically after saving without losing task',async({page,releaseSite})=>{
 await page.goto(releaseSite.url+'/#tasks');
 await page.evaluate(()=>navigator.serviceWorker.ready);
 await expect.poll(()=>page.evaluate(()=>Boolean(navigator.serviceWorker.controller))).toBe(true);
 await page.locator('#quickAdd').click();
 await page.locator('#taskTitle').fill('更新时保留的任务');
 releaseSite.deploy();
 await page.evaluate(()=>window.dispatchEvent(new Event('online')));
 await expect(page.locator('#xuUpdateNotice')).toContainText('保存或关闭编辑后');
 await expect(page.locator('#taskTitle')).toHaveValue('更新时保留的任务');
 expect(releaseSite.documents).toEqual(['old']);
 await page.locator('#modalForm [type="submit"]').click();
 await expect(page.locator('meta[name="xiaoxu-release"]')).toHaveAttribute('content',NEW);
 await expect(page.locator('.v4-task-list')).toContainText('更新时保留的任务');
 const tasks=await page.evaluate(()=>JSON.parse(localStorage.getItem('dorm-os-state-v1')).tasks);
 expect(tasks.some(t=>t.title==='更新时保留的任务')).toBe(true);
 expect(releaseSite.documents).toEqual(['old','new']);
});
