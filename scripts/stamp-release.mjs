/* Deploy-only immutable revision stamp; never changes user data. */
import {readFileSync,writeFileSync} from 'node:fs';
const sha=process.env.RELEASE_SHA;
if(!/^[a-f0-9]{40}$/.test(sha||''))throw Error('Missing immutable RELEASE_SHA');
for(const path of ['public/index.html','public/sw.js']){
 const source=readFileSync(path,'utf8');
 if(!source.includes('__XIAOXU_RELEASE_SHA__'))
  throw Error('Missing release placeholder in '+path);
 let stamped=source.replaceAll('__XIAOXU_RELEASE_SHA__',sha);
 if(path==='public/index.html')stamped=stamped.replace(/(src|href)="(\/xiaoxu-[^"]+\.(?:js|css)(?:\?[^"]*)?)"/g,(_,attribute,value)=>{
  const url=new URL(value,'https://release.invalid');
  url.searchParams.set('_xu_asset',sha.slice(0,12));
  return attribute+'="'+url.pathname+url.search+'"';
 });
 writeFileSync(path,stamped);
}
writeFileSync('public/revision.json',
 JSON.stringify({commit:sha,builtAt:new Date().toISOString()},null,2)+'\n');
console.log('Stamped HTML, PWA cache and revision with '+sha);
