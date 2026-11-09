import assert from 'node:assert/strict';
import {readFileSync,existsSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
const input=readFileSync(new URL('../public/xiaoxu-task-graph.js',import.meta.url),'utf8');
const root={};runInNewContext(input,root);
const graph=root.XiaoxuTaskGraph;
assert.ok(Object.isFrozen(graph));
const row=(id,{origin='local',done=false,prerequisites=[],title=id,status='ready'}={})=>({
 id,origin,source:'manual',title,done,status,
 raw:origin==='cloud'?{kind:'manual',prerequisite_ids:prerequisites}:
  {prerequisiteIds:prerequisites},waitingOn:prerequisites.length?['等待']:[],
 due:'2026-11-20'
});
// A->B->C implies A->C; show only the two meaningful links.
const rows=[
 row('A'),row('B',{status:'waiting',prerequisites:['A']}),
 row('C',{status:'waiting',prerequisites:['A','B']})
];
const graphScene=graph.scene(rows);
assert.equal(graphScene.nodes.length,3);
assert.equal(graphScene.edges.length,2);
assert.equal(graphScene.allEdges.length,3);
assert.deepEqual(Array.from(graphScene.edges,x=>x.join('/')),
 ['local:A/local:B','local:B/local:C']);
const chain=graph.lineage(graphScene.allEdges,'local:B');
assert.deepEqual(Array.from(chain.before),['local:A']);
assert.deepEqual(Array.from(chain.after),['local:C']);
assert.deepEqual(Array.from(graph.scene([row('A'),row('B',{origin:'cloud',
 prerequisites:['A']})],{cloudGraphReady:true}).edges),[],
 'cloud and local IDs must never create cross-store dependencies');
const cloud=[row('A',{origin:'cloud'}),row('B',{origin:'cloud',prerequisites:['A']})];
assert.equal(graph.scene([...rows,...cloud],{cloudGraphReady:true}).edges.length,3);
assert.equal(graph.scene([...rows,...cloud],{cloudGraphReady:false}).nodes.length,3,
 'unknown cloud graph must not appear as falsely unblocked');
const done=[row('completed',{done:true}),row('pending',{prerequisites:['completed']})];
assert.equal(graph.scene(done).nodes.length,1);
assert.equal(graph.scene(done,{includeDone:true}).nodes.length,2);
const selected=graph.render(rows,{picked:'local:B'});
assert.ok(selected.includes('该任务的关联链路：前置 1 项，后续 1 项'));
assert.ok(selected.includes('data-xu-graph-edit="local:B"'));
assert.ok(selected.includes('aria-pressed="true"'));
assert.ok(selected.includes('marker-end="url(#xu-graph-arrow)"'));
assert.ok(graph.render(rows,{cloudConnected:true,cloudGraphReady:false})
 .includes('云端先后关系尚未成功读取'));
const xss=graph.render([row('special',{title:'<img src=x onerror=alert(1)>'})]);
assert.ok(xss.includes('&lt;img src=x'));
assert.ok(!xss.includes('<img src=x'));
const cyclic=graph.scene([
 row('A',{prerequisites:['B']}),
 row('B',{prerequisites:['A']})
]);
assert.equal(cyclic.cyclic,true);
assert.ok(graph.render([
 row('A',{prerequisites:['B']}),
 row('B',{prerequisites:['A']})
]).includes('图形已暂停展示'));
const aLot=Array.from({length:84},(_,i)=>row('T'+i));
const limited=graph.scene(aLot);
assert.equal(limited.nodes.length,72);
assert.equal(limited.truncated,true);
assert.ok(limited.height<=700,'wide plans should not produce a giant vertical graph');
assert.ok(graph.render(aLot).includes('仅展示前 72 项'));
assert.equal(graph.render([]).includes('暂无可绘制的个人任务关系'),true);
const checkCommand=JSON.parse(readFileSync(new URL('../package.json',import.meta.url),'utf8')).scripts.check;
assert.match(checkCommand,/node scripts\/xiaoxu-task-graph-tests\.mjs/,
 'graph regression suite must remain registered after branch synchronization');
assert.match(checkCommand,/node scripts\/xiaoxu-nexttodo-production-audit-tests\.mjs/,
 'D1 read-only preflight safety tests must survive branch synchronization');
// Keep the production audit in one place after rebasing stacked PRs.
for(const obsolete of [
 '../.github/workflows/audit-task-graph-d1.yml',
 './audit-task-graph-d1-production.mjs',
 './xiaoxu-task-production-preflight-tests.mjs'
]){
 assert.equal(existsSync(new URL(obsolete,import.meta.url)),false,
  'obsolete duplicate D1 audit must not return: '+obsolete);
}
const auditWorkflow=readFileSync(new URL('../.github/workflows/audit-nexttodo-d1.yml',import.meta.url),'utf8');
assert.match(auditWorkflow,/workflow_dispatch:/);
assert.doesNotMatch(auditWorkflow,/^  (?:push|pull_request|workflow_run):/m,
 'production audit must be manual-only');
const deployWorkflow=readFileSync(new URL('../.github/workflows/deploy-cloudflare.yml',import.meta.url),'utf8');
assert.match(deployWorkflow,/approve_task_dependency_migration:/,
 'production 0019 write must retain explicit manual consent');
console.log('NextTodo graph: redundant edge reduction, lineage, local/cloud isolation, XSS, cycle, offline and graph cap PASS');
