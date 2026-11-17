/** Static-only evidence checks; never create requests to a university server. */
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const text=readFileSync(new URL('../docs/ncwu-xiqueer-route-catalog.json',import.meta.url),'utf8');
const catalog=JSON.parse(text);
assert.equal(catalog.schemaVersion,1);
assert.equal(catalog.evidence,'static_only');
assert.equal(catalog.validatedAgainstNcwu,false);
const counts={getKb:11,oriKsap:6,getKaoshidjs:2,oriJkap:9,oriKxjs:2,oriCk:3};
assert.deepEqual(Object.keys(catalog.modules).sort(),Object.keys(counts).sort());
const rows=Object.values(catalog.modules).flat();
assert.equal(rows.length,33);
assert.equal(new Set(rows.map(r=>r.action)).size,28);
for(const [module,count] of Object.entries(counts)){
  const entries=catalog.modules[module];
  assert.equal(entries.length,count,module);
  assert.equal(new Set(entries.map(x=>x.alias)).size,count,module+' alias collision');
}
const mutations=new Set(['kb_add_bz','kb_delete_bz','ksap_addCustomer',
  'ksap_delCustomer','jkap_addCustomer','jkap_delCustomer']);
for(const row of rows){
  assert.match(row.action,/^[A-Za-z][A-Za-z0-9_]*$/);
  assert.equal(row.classification,mutations.has(row.action)?
    'mutation':'unverified_read_candidate');
  assert(!Object.hasOwn(row,'url'));
  for(const [wire,source] of Object.entries(row.extraParam||{})){
    assert.match(wire,/^[A-Za-z][A-Za-z0-9_]*$/);
    assert.match(source,/^loginbean\.(?:userid|userId|usertype|xh|xxdm)$/);
  }
}
assert.equal(rows.filter(r=>r.classification==='mutation').length,6);
const get=(mod,alias)=>catalog.modules[mod].find(x=>x.alias===alias);
assert.equal(get('getKb','detail').action,'mycourseschedule');
assert.equal(get('getKb','kbdetail_bz').action,'mycourseschedule');
assert.equal(get('oriKsap','detail').action,'MyExamArrange');
assert.deepEqual(get('oriKsap','detail').extraParam,{userid:'loginbean.userid'});
assert.equal(get('getKaoshidjs','lb').action,'getNotOverExam');
assert.deepEqual(get('getKaoshidjs','lb').extraParam,
  {userId:'loginbean.userid',userType:'loginbean.usertype'});
assert.equal(get('oriKxjs','detail').action,'getJsxx');
assert.equal(get('oriCk','kc').action,'getckkb');
// Identical actions can use different identifiers depending on the module.
assert.deepEqual(get('oriKsap','list').extraParam,{userid:'loginbean.userid'});
assert.deepEqual(get('oriJkap','xnxq').extraParam,{userid:'loginbean.xh'});
assert.equal(get('oriKsap','addCustomer').extraParam.uuid,'loginbean.userId');
assert.equal(get('oriKsap','addCustomer').extraParam.userId,'loginbean.userid');
// Do not promote vendor debug configuration or guessed encryption into application code.
assert(!/192\.168\.|sysURL|encrptKey|https?:\/\//i.test(text));
console.log('NCWU XiQueEr static route catalog: 33 aliases, 28 actions, 6 mutations; no live endpoint enabled.');
