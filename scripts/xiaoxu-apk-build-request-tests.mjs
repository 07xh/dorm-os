import assert from 'node:assert/strict';
import {mkdtempSync,writeFileSync,mkdirSync,rmSync,readFileSync} from 'node:fs';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {execFileSync,spawnSync} from 'node:child_process';
const gateUrl=new URL('./xiaoxu-apk-build-request.mjs',import.meta.url);
// Missing guard is the original bug: deleting a pending marker starts a build.
const {apkBuildRequested}=await import(gateUrl);
const cwd=mkdtempSync(join(tmpdir(),'xiaoxu-build-request-'));
const git=(...args)=>execFileSync('git',args,{cwd,encoding:'utf8'}).trim();
const dir='.github/xiaoxu-apk-build-requests/pending';
const commit=()=>{git('add','-A');git('commit','-qm','synthetic');return git('rev-parse','HEAD');};
try{
 git('init','-q');git('config','user.name','synthetic');git('config','user.email','synthetic@example.invalid');
 mkdirSync(join(cwd,dir),{recursive:true});writeFileSync(join(cwd,dir,'old.txt'),'synthetic old request');
 const base=commit();
 mkdirSync(join(cwd,'docs/archive'),{recursive:true});
 git('mv',dir+'/old.txt','docs/archive/old.txt');
 const archived=commit();
 const push=(before,after)=>({ref:'refs/heads/main',before,after});
 assert.equal(apkBuildRequested('push',push(base,archived),{cwd}),false,'archive/deletion must not authorize APK packaging');
 mkdirSync(join(cwd,dir),{recursive:true});writeFileSync(join(cwd,dir,'new.txt'),'synthetic explicit request');
 const added=commit();
 assert.equal(apkBuildRequested('push',push(archived,added),{cwd}),true,'new explicit request stays supported');
 writeFileSync(join(cwd,dir,'new.txt'),'synthetic corrected request');
 const modified=commit();
 assert.equal(apkBuildRequested('push',push(added,modified),{cwd}),true,'editing explicit request stays supported');
 writeFileSync(join(cwd,'unrelated.txt'),'ordinary source edit');
 const unrelated=commit();
 assert.equal(apkBuildRequested('push',push(modified,unrelated),{cwd}),false);
 // A PR compares against its base, never HEAD~1: a marker in an earlier PR
 // commit must remain eligible when a later source-fix commit is added.
 const pr={repository:{full_name:'synthetic/repo'},pull_request:{base:{sha:archived},head:{sha:unrelated,repo:{full_name:'synthetic/repo'}}}};
 assert.equal(apkBuildRequested('pull_request',pr,{cwd}),true);
 assert.equal(apkBuildRequested('pull_request',{...pr,pull_request:{...pr.pull_request,base:{sha:archived},head:{sha:base,repo:{full_name:'synthetic/repo'}}}},{cwd}),false,'a stale PR must not revive markers archived only on main');
 assert.equal(apkBuildRequested('pull_request',{...pr,pull_request:{...pr.pull_request,base:{sha:base},head:{sha:archived,repo:{full_name:'synthetic/repo'}}}},{cwd}),false);
 assert.equal(apkBuildRequested('pull_request',{...pr,pull_request:{...pr.pull_request,head:{...pr.pull_request.head,repo:{full_name:'synthetic/fork'}}}},{cwd}),false);
 assert.equal(apkBuildRequested('push',{...push(base,added),ref:'refs/heads/feature'},{cwd}),false);
 assert.equal(apkBuildRequested('push',{...push(base,added),deleted:true},{cwd}),false);
 assert.equal(apkBuildRequested('workflow_dispatch',{}, {cwd}),true);
 assert.equal(apkBuildRequested('schedule',{}, {cwd}),false);
 assert.throws(()=>apkBuildRequested('push',push('invalid',added),{cwd}));
 assert.throws(()=>apkBuildRequested('push',push('f'.repeat(40),added),{cwd}),'unknown Git range fails closed');
 const eventPath=join(cwd,'event.json'),outputPath=join(cwd,'output.txt');
 writeFileSync(eventPath,JSON.stringify(push(base,archived)));
 const cli=spawnSync(process.execPath,[gateUrl.pathname],{cwd,encoding:'utf8',env:{...process.env,GITHUB_EVENT_NAME:'push',GITHUB_EVENT_PATH:eventPath,GITHUB_OUTPUT:outputPath}});
 assert.equal(cli.status,0,cli.stderr);
 assert.equal(readFileSync(outputPath,'utf8'),'requested=false\n');
 console.log('APK request gate: archive/delete, add/edit, full PR range, fork, manual recovery and fail-closed Git/CLI checks passed');
}finally{rmSync(cwd,{recursive:true,force:true});}
