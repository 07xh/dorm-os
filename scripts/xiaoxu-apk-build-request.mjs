// Paths filters also match deletions. Only an explicit added/modified pending
// marker may request packaging; manual workflow_dispatch stays available.
import {readFileSync,appendFileSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {pathToFileURL} from 'node:url';
const directory='.github/xiaoxu-apk-build-requests/pending';
export function apkBuildRequested(eventName,event,{cwd=process.cwd()}={}){
 if(eventName==='workflow_dispatch')return true;
 let base,head;
 if(eventName==='push'){
  if(event.ref!=='refs/heads/main'||event.deleted)return false;
  base=event.before;head=event.after;
 }else if(eventName==='pull_request'){
  const pr=event.pull_request;
  if(!event.repository?.full_name||pr?.head?.repo?.full_name!==event.repository.full_name)return false;
  base=pr.base?.sha;head=pr.head?.sha;
 }else return false;
 for(const sha of [base,head]){
  if(typeof sha!=='string'||! /^[a-f0-9]{40}$/.test(sha)||/^0+$/.test(sha))
   throw Error('invalid_apk_request_range');
 }
 const diff=execFileSync('git',['diff','--no-renames','--name-status','-z',
  '--diff-filter=AM',...(eventName==='pull_request'?[`${base}...${head}`]:[base,head]),
  '--',directory],{cwd,encoding:'utf8',stdio:['ignore','pipe','pipe']});
 const fields=diff.split('\0');
 for(let i=0;i+1<fields.length;i+=2){
  if(['A','M'].includes(fields[i])&&
   /^\.github\/xiaoxu-apk-build-requests\/pending\/[^/\r\n]+\.txt$/.test(fields[i+1]))return true;
 }
 return false;
}
if(process.argv[1]&&pathToFileURL(process.argv[1]).href===import.meta.url){
 try{
  const event=JSON.parse(readFileSync(process.env.GITHUB_EVENT_PATH,'utf8'));
  const requested=apkBuildRequested(process.env.GITHUB_EVENT_NAME,event);
  appendFileSync(process.env.GITHUB_OUTPUT,`requested=${requested}\n`);
  console.log(requested?'Explicit APK build requested.':'No added or modified APK request; packaging skipped.');
 }catch{
  console.error('APK request check failed; packaging is not authorized.');
  process.exitCode=1;
 }
}
