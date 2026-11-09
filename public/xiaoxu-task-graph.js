/* 校序 task dependency graph.
 * Reuses / adapts NextTodo's graph edge reduction and lineage concepts:
 *   JunieXD/NextTodo, apps/web/src/domain/graph.ts (MIT, copyright 2026 JunieXD).
 * Full license: /licenses/NextTodo-MIT.txt
 * Rendered with lightweight SVG instead of embedding an unrelated React runtime
 * in this existing vanilla-JS PWA. No extra task store or network requests.
 */
(function(root){
 'use strict';
 const html=v=>String(v??'').replace(/[&<>"']/g,c=>({
  '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
 })[c]);
 const MAX_VISIBLE=72;
 const MAX_EDGES=1500;
 const NODE_W=202,NODE_H=80,COL_GAP=92,ROW_GAP=26;
 const nodeKey=(origin,id)=>origin+':'+id;

 /** Return only direct, non-redundant edges of a DAG; direction: before -> after. */
 function reduce(edges){
  const outgoing=new Map(),indegree=new Map();
  for(const [a,b] of edges){
   if(!outgoing.has(a))outgoing.set(a,[]);
   outgoing.get(a).push(b);
   if(!indegree.has(a))indegree.set(a,0);
   indegree.set(b,(indegree.get(b)||0)+1);
  }
  const ready=[...indegree].filter(([,n])=>n===0).map(([id])=>id);
  const rank=new Map();
  while(ready.length){
   const id=ready.pop();rank.set(id,rank.size);
   for(const b of outgoing.get(id)||[]){
    indegree.set(b,indegree.get(b)-1);
    if(indegree.get(b)===0)ready.push(b);
   }
  }
  // A cycle cannot be reduced safely. Caller also checks source graph.
  if(rank.size!==indegree.size)return [];
  const omitted=new Set();
  for(const [a,successors] of outgoing){
   if(successors.length<2)continue;
   const sorted=successors.slice().sort((x,y)=>rank.get(x)-rank.get(y));
   const last=rank.get(sorted[sorted.length-1]);
   const reached=new Set();
   for(const b of sorted){
    if(reached.has(b)){omitted.add(a+'\u0000'+b);continue;}
    const stack=[b];
    while(stack.length){
     for(const next of outgoing.get(stack.pop())||[]){
      if(rank.get(next)<=last&&!reached.has(next)){
       reached.add(next);stack.push(next);
      }
     }
    }
   }
  }
  return edges.filter(([a,b])=>!omitted.has(a+'\u0000'+b));
 }

 /** All transitive prerequisites and successors of the selected node. */
 function lineage(edges,id){
  const forward=new Map(),reverse=new Map();
  for(const [a,b] of edges){
   if(!forward.has(a))forward.set(a,[]);
   forward.get(a).push(b);
   if(!reverse.has(b))reverse.set(b,[]);
   reverse.get(b).push(a);
  }
  const walk=map=>{
   const found=new Set(),stack=[id];
   while(stack.length){
    for(const n of map.get(stack.pop())||[]){
     if(n!==id&&!found.has(n)){found.add(n);stack.push(n);}
    }
   }
   return found;
  };
  return {before:walk(reverse),after:walk(forward)};
 }
 function normalize(rows,{includeDone=false,cloudGraphReady=true}={}){
  if(!Array.isArray(rows))return [];
  const result=[],keys=new Set();
  for(const row of rows){
   if(!row||typeof row.id!=='string')continue;
   const local=row.origin==='local'&&row.source==='manual';
   const cloud=row.origin==='cloud'&&row.raw?.kind==='manual'&&cloudGraphReady;
   if(!local&&!cloud||!includeDone&&row.done)continue;
   const key=nodeKey(row.origin,row.id);
   if(keys.has(key))continue;
   keys.add(key);
   const sources=local?row.raw?.prerequisiteIds:row.raw?.prerequisite_ids;
   result.push({key,id:row.id,origin:row.origin,title:String(row.title||'无标题任务'),
    done:row.done===true,status:row.status||'ready',due:String(row.due||''),
    waitingOn:Array.isArray(row.waitingOn)?row.waitingOn.map(String):[],
    predecessors:Array.isArray(sources)?sources.filter(x=>typeof x==='string').slice(0,30)
     .map(id=>nodeKey(row.origin,id)):[]});
  }
  return result;
 }
 function scene(rows,options={}){
  const all=normalize(rows,options);
  const truncated=all.length>MAX_VISIBLE;
  const nodes=all.slice(0,MAX_VISIBLE);
  const byKey=new Map(nodes.map(r=>[r.key,r]));
  let edges=[];
  for(const node of nodes){
   for(const before of new Set(node.predecessors)){
    if(before!==node.key&&byKey.has(before))edges.push([before,node.key]);
   }
  }
  if(edges.length>MAX_EDGES)edges=edges.slice(0,MAX_EDGES);
  // Validate before layout; never depict a cyclic graph as an ordinary DAG.
  const visiting=new Set(),done=new Set(),beforeOf=new Map(nodes.map(n=>[n.key,[]]));
  for(const [before,after] of edges)beforeOf.get(after).push(before);
  let cyclic=false;
  const depths=new Map();
  function depth(id){
   if(done.has(id))return depths.get(id);
   if(visiting.has(id)){cyclic=true;return 0;}
   visiting.add(id);
   let d=0;
   for(const before of beforeOf.get(id)||[])d=Math.max(d,depth(before)+1);
   visiting.delete(id);done.add(id);depths.set(id,d);
   return d;
  }
  for(const node of nodes)depth(node.key);
  if(cyclic)return {nodes,edges:[],truncated,cyclic:true,width:0,height:0,positions:[],total:all.length};
  const direct=reduce(edges);
  const levels=new Map();
  for(const node of nodes){
   const d=depths.get(node.key);
   if(!levels.has(d))levels.set(d,[]);
   levels.get(d).push(node);
  }
  const layers=[...levels.keys()].sort((a,b)=>a-b);
  // Staggered column layout: earlier tasks on the left, successors on the right.
  const positions=nodes.map(node=>{
   const stage=depths.get(node.key);
   const order=levels.get(stage).indexOf(node);
   return {node,x:24+stage*(NODE_W+COL_GAP),y:26+order*(NODE_H+ROW_GAP)};
  });
  const maxLevel=layers.length?layers[layers.length-1]:0;
  const tallest=Math.max(1,...[...levels.values()].map(list=>list.length));
  const pos=new Map(positions.map(p=>[p.node.key,p]));
  return {nodes,edges:direct,allEdges:edges,positions,pos,truncated,cyclic:false,
   total:all.length,width:Math.max(340,48+(maxLevel+1)*NODE_W+maxLevel*COL_GAP),
   height:Math.max(230,50+tallest*NODE_H+(tallest-1)*ROW_GAP)};
 }
 const statusText=row=>row.done?'已完成':row.status==='waiting'?
  '等待 '+row.waitingOn.length+' 项':row.status==='ready'?'现在可做':'待核对';

 function render(rows,{includeDone=false,picked=null,cloudConnected=false,
  cloudGraphReady=false}={}){
  const s=scene(rows,{includeDone,cloudGraphReady});
  const focus=s.nodes.some(r=>r.key===picked)?picked:null;
  const chain=focus?lineage(s.allEdges,focus):null;
  const inChain=key=>!chain||key===focus||chain.before.has(key)||chain.after.has(key);
  const warning=cloudConnected&&!cloudGraphReady?
   '<p class="small muted" role="status">云端先后关系尚未成功读取，暂不展示云端任务，避免错误显示可执行状态。请检查 D1 迁移和授权。</p>':
   !cloudConnected?'<p class="small muted">未连接云端时仅展示当前浏览器的个人任务。</p>':'';
  const toolbar='<div class="xu-graph-actions"><span class="small muted">前置 → 后续 · '+
   s.total+' 项个人任务</span><button type="button" class="secondary" data-xu-graph-done aria-pressed="'+
   String(includeDone)+'">'+(includeDone?'隐藏已完成':'显示已完成')+'</button>'+
   (focus?'<button type="button" class="secondary" data-xu-graph-clear>取消高亮</button>':'')+'</div>';
  if(!s.nodes.length)return '<section class="card xu-graph-shell" aria-label="任务关系图">'+
   toolbar+warning+'<p class="friendly-empty">暂无可绘制的个人任务关系。可在现有任务编辑窗口设置前置任务。</p></section>';
  if(s.cyclic)return '<section class="card xu-graph-shell" aria-label="任务关系图">'+
   toolbar+'<p role="alert">检测到异常循环关系，图形已暂停展示。请在任务编辑中检查前置任务。</p></section>';
  const paths=s.edges.map(([a,b])=>{
   const first=s.pos.get(a),last=s.pos.get(b);
   if(!first||!last)return '';
   const ax=first.x+NODE_W,ay=first.y+NODE_H/2,bx=last.x,by=last.y+NODE_H/2;
   const delta=Math.max(35,(bx-ax)*0.48);
   const highlighted=!chain||(
    (chain.before.has(a)||a===focus)&&(chain.before.has(b)||b===focus)||
    (chain.after.has(a)||a===focus)&&(chain.after.has(b)||b===focus));
   return '<path class="xu-graph-line '+(highlighted?'':'is-dim')+'" d="M'+ax+','+ay+
    ' C'+(ax+delta)+','+ay+' '+(bx-delta)+','+by+' '+bx+','+by+
    '" marker-end="url(#xu-graph-arrow)" />';
  }).join('');
  const cards=s.positions.map(({node,x,y})=>{
   const selected=node.key===focus;
   const cls=node.done?'is-done':node.status==='waiting'?'is-waiting':'is-ready';
   const hint=node.waitingOn.length?'前置：'+node.waitingOn.join('、'):
    node.due?'截止 '+node.due:'无前置阻塞';
   return '<div class="xu-graph-node '+cls+(selected?' is-selected':'')+
    (!inChain(node.key)?' is-dim':'')+'" style="left:'+x+'px;top:'+y+'px">'+
    '<button type="button" class="xu-graph-select" data-xu-graph-node="'+html(node.key)+
    '" aria-pressed="'+String(selected)+'" title="'+html(hint)+'">'+
    '<span class="xu-graph-node-top"><small>'+(node.origin==='cloud'?'私人云端':'当前浏览器')+
    '</small><span class="xu-graph-state">'+html(statusText(node))+'</span></span>'+
    '<strong class="xu-graph-title">'+html(node.title)+'</strong></button>'+
    '<button type="button" class="xu-graph-edit" data-xu-graph-edit="'+html(node.key)+
    '" aria-label="编辑 '+html(node.title)+' 的先后关系">编辑</button></div>';
  }).join('');
  const footer=focus?'<div class="xu-graph-chain" role="status">该任务的关联链路：前置 '+
   chain.before.size+' 项，后续 '+chain.after.size+' 项。点击节点高亮依赖，编辑可修改先后关系。</div>':
   '<p class="small muted xu-graph-explain">点击节点可高亮上下游链路；“编辑”打开原有任务编辑窗口。连线由左侧前置指向右侧后续。</p>';
  return '<section class="card xu-graph-shell" aria-label="任务关系图">'+toolbar+warning+
   (s.truncated?'<p class="small muted" role="status">任务较多，仅展示前 '+MAX_VISIBLE+
    ' 项；完整任务请查看列表，避免图形过载。</p>':'')+
   '<div class="xu-graph-scroll" role="region" tabindex="0" aria-label="任务依赖关系，可水平滚动">'+
   '<div class="xu-graph-canvas" style="width:'+s.width+'px;height:'+s.height+'px">'+
   '<svg class="xu-graph-svg" aria-hidden="true" width="'+s.width+'" height="'+s.height+
   '" viewBox="0 0 '+s.width+' '+s.height+'"><defs>'+
   '<marker id="xu-graph-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 1L9 5L0 9" /></marker>'+
   '</defs>'+paths+'</svg>'+cards+'</div></div>'+footer+'</section>';
 }
 root.XiaoxuTaskGraph=Object.freeze({reduce,lineage,scene,render,nodeKey});
})(globalThis);
