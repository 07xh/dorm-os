import assert from 'node:assert/strict';
const url=process.argv[2];
const sha=process.env.GITHUB_SHA;
if(!url||!/^https:\/\/[a-z0-9-]+\.dorm-os-dk9\.pages\.dev\/?$/.test(url))throw Error('Invalid deployment-specific Pages URL');
if(!/^[a-f0-9]{40}$/.test(sha||''))throw Error('Missing expected full Git commit SHA');
const base=url.replace(/\/$/,'');
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function readRoute(path){
  let last;
  for(let attempt=0;attempt<12;attempt++){
    try{
      const response=await fetch(base+path,{signal:AbortSignal.timeout(12000),cache:'no-store'});
      if(!response.ok)throw Error(path+' returned '+response.status);
      return await response.text();
    }catch(err){
      last=err;if(attempt<11)await sleep(1800);
    }
  }
  throw new Error('Failed to fetch published '+path+': '+String(last));
}
const revision=JSON.parse(await readRoute('/revision.json'));
assert.equal(revision.commit,sha,'Published release revision does not match deployed commit');
const html=await readRoute('/');
assert.ok(html.includes('DORM OS'),'Published site is missing app marker');
assert.ok(html.includes('id="app"'),'Published site is missing app root');
const manifest=JSON.parse(await readRoute('/manifest.webmanifest'));
assert.ok(manifest.name.includes('DORM OS'),'PWA manifest missing');
for(const size of [192,512]){
  assert.ok(manifest.icons.some(x=>x.sizes===size+'x'+size),'Missing '+size+'px manifest icon');
}
const sw=await readRoute('/sw.js');
assert.ok(sw.includes('dorm-os-shell-'),'PWA service worker missing');
console.log('Verified live published release '+sha+' at '+base);
