/* Deploy-only immutable revision stamp; never changes user data. */
import {readFileSync,writeFileSync} from 'node:fs';
const sha=process.env.RELEASE_SHA;
if(!/^[a-f0-9]{40}$/.test(sha||''))throw Error('Missing immutable RELEASE_SHA');
for(const path of ['public/index.html','public/sw.js']){
 const source=readFileSync(path,'utf8');
 if(!source.includes('__XIAOXU_RELEASE_SHA__'))
  throw Error('Missing release placeholder in '+path);
 writeFileSync(path,source.replaceAll('__XIAOXU_RELEASE_SHA__',sha));
}
writeFileSync('public/revision.json',
 JSON.stringify({commit:sha,builtAt:new Date().toISOString()},null,2)+'\n');
console.log('Stamped HTML, PWA cache and revision with '+sha);
