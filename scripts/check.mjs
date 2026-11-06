import { readFileSync } from 'node:fs';
import { Script } from 'node:vm';

const html = readFileSync(new URL('../public/index.html', import.meta.url), 'utf8');
const match = html.match(/<script>([\s\S]*?)<\/script>/i);
if (!match) throw new Error('Missing inline application script');
new Script(match[1], { filename: 'dorm-os-inline-app.js' });
new Script(readFileSync(new URL('../public/xiaoxu-task-import.js', import.meta.url), 'utf8'), {filename:'xiaoxu-task-import.js'});
new Script(readFileSync(new URL('../public/xiaoxu-chaoxing-cli.js', import.meta.url), 'utf8'), {filename:'xiaoxu-chaoxing-cli.js'});
new Script(readFileSync(new URL('../public/xiaoxu-chaoxing-inbox.js', import.meta.url), 'utf8'), {filename:'xiaoxu-chaoxing-inbox.js'});
new Script(readFileSync(new URL('../public/xiaoxu-chaoxing-homework.js', import.meta.url), 'utf8'), {filename:'xiaoxu-chaoxing-homework.js'});
if(!html.includes('<script src="/xiaoxu-chaoxing-homework.js"></script>')||
   !html.includes('XiaoxuChaoxingHomework.panel()'))throw Error('Missing real homework browser');
if(!html.includes('<script src="/xiaoxu-chaoxing-inbox.js"></script>')||!html.includes('XiaoxuChaoxingInbox.panel()')||!html.includes('/xiaoxu-chaoxing-inbox.css'))throw Error('Missing integrated Chaoxing inbox panel');
if(!html.includes('<script src="/xiaoxu-chaoxing-cli.js"></script>'))throw Error('Missing Chaoxing CLI source');
if(!html.includes('<script src="/xiaoxu-task-import.js"></script>'))throw Error('Missing task import source');

new Script(readFileSync(new URL('../public/xiaoxu-deadline-reminders.js', import.meta.url), 'utf8'), {filename:'xiaoxu-deadline-reminders.js'});
if(!html.includes('<script src="/xiaoxu-deadline-reminders.js"></script>'))throw Error('Missing foreground deadline reminders module');
const requiredElements = ['shell', 'app', 'editor', 'toast', 'icsInput', 'importInput', 'taskImportInput'];
for (const id of requiredElements) {
  if (!new RegExp('id=["\x27]'+id+'["\x27]').test(html)) throw new Error('Missing element: '+id);
}
for (const feature of ['function dashboard()', 'function lifePage()', 'function nfcPage()', 'function tagDialog(', 'function tagLink(', 'function activateScene(', 'function reminderDialog(', 'function itemDialog(', 'data-page="nfc"', 'class="main-nav x-v7-primary-nav"']) {
  if (!html.includes(feature)) throw new Error('Missing V3.3 feature: '+feature);
}
for (const old of ['REDMI Book 14 2025', 'XIAOMI_CONNECT_TYPE', '小米互联 externaltype']) {
  if (html.includes(old)) throw new Error('Deprecated laptop-only NFC behavior: '+old);
}
if (!html.includes('<link rel="stylesheet" href="/xiaoxu-base.css">')) throw Error('Base CSS must remain loaded before themed layers');
if (html.includes('<style>')) throw Error('Legacy inline CSS must not return');
for (const stale of ['function devicesPage()', 'function academicSourceHealth()', 'function checkAcademicSources(', 'function taskSummary(', 'const pad =']) if (html.includes(stale)) throw Error('Retired unused or placeholder logic: '+stale);
if (!html.includes("theme: 'light'")) throw new Error('V3.3 default light theme missing');
if (!html.includes('noopener noreferrer')) throw new Error('Safe external links missing');
for (const feature of ["function homeWidgetContent(id)","function homeLayoutDialog()","homeLayout: defaultHomeLayout()","data-home-widget-up","class=\"home-widget-grid v4-main-widgets x-v7-home-grid\""]) if (!html.includes(feature)) throw new Error('Missing V3.6 dashboard feature: '+feature);
for(const feature of ['function profilePage()', 'class="v4-hero x-v7-home-intro"', 'data-page="profile"', 'v4-scene-grid'])if(!html.includes(feature))throw new Error('Missing V4 UX: '+feature);
for(const feature of ['function academicPage()', 'data-academic-tab', 'aria-label="顶部主导航"']) if(!html.includes(feature)) throw Error('Missing V7 academic top-navigation feature: '+feature);
console.log('校序 V7 syntax, academic top-navigation and legacy compatibility checks passed');

import {statSync} from 'node:fs';
for(const size of [192,512]){
 const p = new URL('../public/icons/icon-'+size+'.png',import.meta.url);
 const binary=readFileSync(p);
 if(binary.length<300||binary.toString('hex',0,8)!=='89504e470d0a1a0a'||binary.readUInt32BE(16)!==size||binary.readUInt32BE(20)!==size)throw Error('Invalid PNG icon '+size);
}
const manifest=JSON.parse(readFileSync(new URL('../public/manifest.webmanifest',import.meta.url),'utf8'));
for(const size of [192,512])if(!manifest.icons.some(x=>x.sizes===size+'x'+size))throw Error('Manifest icon missing '+size);
console.log('PWA PNG installability assets checked');
