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
assert.ok(html.includes('校序'),'Published site is missing the 校序 brand marker');
assert.ok(html.includes('id="app"'),'Published site is missing app root');
const manifest=JSON.parse(await readRoute('/manifest.webmanifest'));
assert.ok(manifest.name.includes('校序') && manifest.short_name==='校序','PWA manifest has the wrong brand');
for(const size of [192,512]){
  assert.ok(manifest.icons.some(x=>x.sizes===size+'x'+size),'Missing '+size+'px manifest icon');
}
const importer=await readRoute('/xiaoxu-task-import.js');
assert.ok(importer.includes('xiaoxu-task-batch-v1'),'Task import module missing from production');
const sw=await readRoute('/sw.js');
assert.ok(sw.includes('dorm-os-shell-'),'PWA service worker missing');
// Public history is a separately deployed, bot-maintained snapshot: do not mistake
// a collector-only GitHub commit for a new Cloudflare release.
const publicArchive = JSON.parse(await readRoute('/data/notices.json'));
assert.equal(publicArchive.schema,'xiaoxu/notices-v1','Published public archive has the wrong schema');
assert.equal(publicArchive.school,'4141010078','Published public archive has the wrong school code');
assert.ok(Array.isArray(publicArchive.items) && publicArchive.items.length >= 5,'Published notice archive is missing');
for(const item of publicArchive.items){
 assert.match(item.id||'',/^ncwu-[0-9a-f]{16}$/, 'Deployed archive cannot contain unverified seed or synthetic article IDs');
 assert.ok(typeof item.sourceId==='string'&&item.sourceId&&!item.sourceId.startsWith('seed'), 'Notice source provenance is missing');
 assert.ok(typeof item.url==='string'&&/^https:\/\/www2?\.ncwu\.edu\.cn\//.test(item.url), 'Non-official article URL rejected');
}
if(publicArchive.mode === 'auto'){
 assert.match(publicArchive.lastSuccessAt || '',/^20\d\d-\d\d-\d\dT/, 'Auto-collected archive must carry real collection time');
 assert.ok(publicArchive.sources.some(s=>s.status==='ok'),'Auto archive has no successful source');
}else assert.equal(publicArchive.mode,'seed','Archive provenance status must be explicit');
console.log('NCWU archive deployed: '+publicArchive.items.length+' items; mode '+publicArchive.mode);
// Do not confuse a successful static release with a deployed, functioning Pages API.
// An unreachable NCWU upstream may produce a valid 503 JSON; that is not live data success.
// A newly deployed Pages Function may lag static assets by a few seconds.
// Retry readiness on wrong handler/content-type only. Do not accept SPA HTML
// or fabricate successful school notices; a valid 503 JSON is a real API response.
async function waitForNoticeFunction(){
 let last='unknown';
 for(let attempt=1;attempt<=8;attempt++){
  try{
   const resp=await fetch(base+'/api/notices',{
    signal:AbortSignal.timeout(27000),cache:'no-store',headers:{accept:'application/json'}
   });
   const type=resp.headers.get('content-type')||'';
   const marker=resp.headers.get('x-xiaoxu-public-notices');
   if(/application\/json/i.test(type)&&marker==='1')return resp;
   last='HTTP '+resp.status+', content-type '+type+', function marker '+(marker||'missing');
  }catch(error){last=String(error?.message||error);}
  console.warn('Pages notice Function not ready: '+last+' (attempt '+attempt+'/8)');
  if(attempt<8)await sleep(2500);
 }
 throw new Error('Pages notice Function failed readiness check: '+last);
}
const noticeResp=await waitForNoticeFunction();
assert.match(noticeResp.headers.get('content-type')||'',/application\/json/i,
 'Official notices endpoint is missing: expected Pages Function JSON, not SPA fallback');
assert.equal(noticeResp.headers.get('x-xiaoxu-public-notices'),'1',
 'Official notice request did not reach the Pages Function handler');
const noticeData=await noticeResp.json();
assert.equal(typeof noticeData.success,'boolean');
assert.ok(Array.isArray(noticeData.items),'Notices response missing item list');
if(noticeData.success){
 assert.equal(noticeResp.status,200);
 assert.ok(noticeData.items.length>=2,'A successful notice fetch must include real school items');
 console.log('NCWU notices API verified: '+noticeData.items.length+' items; source '+noticeData.source);
}else{
 assert.equal(noticeResp.status,503);
 assert.equal(noticeData.items.length,0,'Failure must not fabricate school notices');
 console.log('NCWU notices API handler verified; official upstream unavailable (503). No live data claimed.');
}

console.log('Verified live published release '+sha+' at '+base);
