import assert from 'node:assert/strict';
import {mkdtempSync,mkdirSync,readFileSync,writeFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';import {join,resolve} from 'node:path';import {execFileSync} from 'node:child_process';
const dir=mkdtempSync(join(tmpdir(),'xiaoxu-release-'));
try{
 mkdirSync(join(dir,'public'));
 for(const name of ['index.html','sw.js'])writeFileSync(join(dir,'public',name),readFileSync('public/'+name));
 const sha='d'.repeat(40);execFileSync(process.execPath,[resolve('scripts/stamp-release.mjs')],{cwd:dir,env:{...process.env,RELEASE_SHA:sha}});
 const html=readFileSync(join(dir,'public/index.html'),'utf8');
 const urls=Array.from(html.matchAll(/(?:src|href)="(\/xiaoxu-[^"]+\.(?:js|css)(?:\?[^"]*)?)"/g),m=>m[1]);
 assert.ok(urls.length>12);
 for(const url of urls)assert.equal(new URL(url,'https://test.invalid').searchParams.get('_xu_asset'),sha.slice(0,12),'all app assets must bypass old worker / browser cache keys');
 assert.equal(new URL(urls.find(u=>u.includes('owner-auth.css')),'https://test.invalid').searchParams.get('v'),'98-profile-styles','retain existing owner style key');
 assert.equal(JSON.parse(readFileSync(join(dir,'public/revision.json'),'utf8')).commit,sha);
 console.log('Release stamping: revision-tagged JS/CSS cache keys and original query preservation passed');
}finally{rmSync(dir,{recursive:true,force:true});}
