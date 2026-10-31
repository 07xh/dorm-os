import {readFileSync} from 'node:fs';
import {Script} from 'node:vm';

const html = readFileSync(new URL('../public/index.html', import.meta.url),'utf8');
const block = html.match(/<script\b[^>]*>([\s\S]*?)<\/script>/i);
if (!block) throw new Error('Cannot find application script');
new Script(block[1], {filename:'inline-app.js'});
for (const id of ['shell','app','editor','toast','icsInput','importInput']) {
  if (!new RegExp('id=["\\x27]'+id+'["\\x27]').test(html)) throw new Error('Missing element: '+id);
}
console.log('DORM OS syntax and structure checks passed');
