import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

const root={};
vm.runInNewContext(readFileSync('public/xiaoxu-web-router.js','utf8'),root);
const router=root.XiaoxuWebRouter;
assert.ok(Object.isFrozen(router));
for(const name of ['dashboard','study','timetable','academic','campus','tasks','scenes','profile','settings','nfc','life']){
 const route=router.fromHash('#'+name);
 assert.equal(route.page,name,'legacy route still resolves: '+name);
 assert.equal(route.hash,'#'+name);
}
assert.equal(router.fromHash('#review').page,'tasks');
assert.equal(router.fromHash('#task-graph').page,'tasks');
assert.equal(router.fromHash('#task-graph').taskFilter,'graph');
assert.equal(router.taskFilterHash('graph'),'#task-graph');
assert.equal(router.fromHash('#review').taskFilter,'review');
assert.equal(router.resolve('review').hash,'#review');
assert.equal(router.fromHash('#tasks').taskFilter,'all');
assert.equal(router.resolve('devices').hash,'#profile');
assert.equal(router.fromHash('#devices').page,'profile');
assert.equal(router.selected('nfc'),'profile');
assert.equal(router.selected('study'),'timetable');
assert.equal(router.selected('academic'),'academic');
assert.equal(router.taskFilterHash('review'),'#review');
assert.equal(router.taskFilterHash('open'),'#tasks');
assert.equal(router.taskFilterHash('done'),'#tasks');
for(const bad of ['#','not-hash','#unknown','#settings?xu_app_source=ncwu','#review?extra=1','#../../admin']){
 assert.equal(router.fromHash(bad),null,'unknown / sensitive fragment never creates a page: '+bad);
}
assert.equal(router.resolve('__proto__'),null,'do not treat prototype properties as routes');
assert.equal(router.resolve('constructor'),null);
assert.equal(router.resolve(''),null);
assert.equal(router.fromHash(undefined),null);
assert.equal(router.resolve(null),null);
console.log('Web route projection: all legacy hashes, review alias, task filters and unsafe fragment rejection passed');
