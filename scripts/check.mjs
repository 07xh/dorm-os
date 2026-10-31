import { readFileSync } from 'node:fs';
import { Script } from 'node:vm';

const html = readFileSync(new URL('../public/index.html', import.meta.url), 'utf8');
const match = html.match(/<script\b[^>]*>([\s\S]*?)<\/script>/i);
if (!match) throw new Error('Missing inline application script');
new Script(match[1], { filename: 'dorm-os-inline-app.js' });

const requiredElements = ['shell', 'app', 'editor', 'toast', 'icsInput', 'importInput'];
for (const id of requiredElements) {
  if (!new RegExp('id=["\x27]'+id+'["\x27]').test(html)) throw new Error('Missing element: '+id);
}
for (const feature of ['function dashboard()', 'function lifePage()', 'function nfcPage()', 'function tagDialog(', 'function tagLink(', 'function activateScene(', 'function reminderDialog(', 'function itemDialog(', 'data-page="nfc"', 'class="mobile-tabs"']) {
  if (!html.includes(feature)) throw new Error('Missing V3.3 feature: '+feature);
}
for (const old of ['REDMI Book 14 2025', 'XIAOMI_CONNECT_TYPE', '小米互联 externaltype']) {
  if (html.includes(old)) throw new Error('Deprecated laptop-only NFC behavior: '+old);
}
if (!html.includes("theme: 'light'")) throw new Error('V3.3 default light theme missing');
if (!html.includes('noopener noreferrer')) throw new Error('Safe external links missing');
console.log('DORM OS V3.3 source syntax, baseline IDs and personal feature checks passed');

import {statSync} from 'node:fs';
for(const size of [192,512]){
 const p = new URL('../public/icons/icon-'+size+'.png',import.meta.url);
 const binary=readFileSync(p);
 if(binary.length<300||binary.toString('hex',0,8)!=='89504e470d0a1a0a'||binary.readUInt32BE(16)!==size||binary.readUInt32BE(20)!==size)throw Error('Invalid PNG icon '+size);
}
const manifest=JSON.parse(readFileSync(new URL('../public/manifest.webmanifest',import.meta.url),'utf8'));
for(const size of [192,512])if(!manifest.icons.some(x=>x.sizes===size+'x'+size))throw Error('Manifest icon missing '+size);
console.log('PWA PNG installability assets checked');
