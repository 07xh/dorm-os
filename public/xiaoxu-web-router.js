/* 校序 Web-only route projection. No IO, school authentication or account state.
 * Preserve old #hash routes while exposing an explicit #review task view.
 * The Android return script owns/scrubs its #settings?... nonce BEFORE this runs.
 */
(function(root){
 'use strict';
 const names=Object.freeze({
  dashboard:'总览',study:'学习',timetable:'课表',academic:'教务',
  campus:'校园服务',tasks:'任务',scenes:'场景',profile:'我的',
  settings:'数据连接',nfc:'NFC 标签',life:'生活'
 });
 const navOwners=Object.freeze({
  campus:'academic',scenes:'profile',nfc:'profile',life:'profile',
  settings:'profile',study:'timetable'
 });
 function resolve(target){
  if(typeof target!=='string')return null;
  // #review is a view of the existing tasks page, not a second task backend.
  if(target==='review')return {page:'tasks',hash:'#review',taskFilter:'review'};
  if(target==='devices')return {page:'profile',hash:'#profile',taskFilter:null};
  if(!Object.hasOwn(names,target))return null;
  return {page:target,hash:'#'+target,taskFilter:target==='tasks'?'all':null};
 }
 function fromHash(hash){
  if(typeof hash!=='string'||!hash.startsWith('#'))return null;
  return resolve(hash.slice(1));
 }
 function selected(page){return navOwners[page]||page;}
 function taskFilterHash(filter){return filter==='review'?'#review':'#tasks';}
 root.XiaoxuWebRouter=Object.freeze({names,resolve,fromHash,selected,taskFilterHash});
})(globalThis);
