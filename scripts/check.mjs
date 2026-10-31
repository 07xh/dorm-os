import {readFileSync} from 'node:fs';
import {Script} from 'node:vm';
const html=readFileSync(new URL('../public/index.html',import.meta.url),'utf8');
const m=html.match(/<script\\b[^>]*>([\\s\\S]*?)<\\/script>/i);
if(!m)throw Error('Missing app script');
new Script(m[1],{filename:'inline-app.js'});
for(const id of ['shell','app','editor','toast','icsInput','importInput']) if(!new RegExp('id=["\\\']'+id+'["\\\']').test(html))throw Error('Missing id '+id);
console.log('DORM OS syntax and required elements check passed');
