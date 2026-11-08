import assert from 'node:assert/strict';
import {
  WECHAT_CANDIDATE_SCHEMA, WechatCandidateError, normalizeWechatCandidateBatch
} from '../lib/academic-wechat-candidate-contract.mjs';

const k = s => s.repeat(18);
const baseRecord = {
  messageKey:k('a'), candidateKey:k('b'), chatKey:k('c'),
  kind:'homework', title:'提交实验报告', chatLabel:'示例课程群',
  excerpt:'请按课程要求提交实验报告。',
  sentAt:'2026-11-04T09:00:00+08:00',
  deadline:{precision:'exact',date:'2026-11-20',at:'2026-11-20T23:59:00+08:00'}
};
const batch = {
  schema:WECHAT_CANDIDATE_SCHEMA,deviceId:k('d'),batchId:k('e'),
  capturedAt:'2026-11-04T12:00:00+08:00',records:[baseRecord]
};
const verified = normalizeWechatCandidateBatch(batch);
assert.equal(verified.records[0].deadline.atUtc,'2026-11-20T15:59:00.000Z');
assert.equal(verified.records[0].reviewStatus,'needs_review');
assert.equal(verified.records[0].provenance,'wechat_local_candidate');
assert.equal(verified.records[0].excerpt,'请按课程要求提交实验报告。');
assert.equal(verified.records.length,1);

const secondCandidate = {
  ...baseRecord,candidateKey:k('f'),title:'另一个独立截止',
  deadline:{precision:'date',date:'2026-11-21'}
};
const two = normalizeWechatCandidateBatch({...batch, records:[baseRecord,secondCandidate]});
assert.equal(two.records.length,2,'one message may contain two separate tasks');
assert.equal(two.records[1].deadline.atUtc,null);
const unknown = normalizeWechatCandidateBatch({...batch,records:[
  {...baseRecord,deadline:{precision:'unknown'},excerpt:undefined}
]});
assert.equal(unknown.records[0].deadline.date,null);
assert.equal(unknown.records[0].excerpt,null);
// Optional links (V1): absent stays valid for old collectors, present must be
// 1-3 structurally validated HTTPS URLs without credentials or duplicates.
const link='https://course.example/hw/template.zip';
const withLinks=normalizeWechatCandidateBatch({...batch,records:[
  {...baseRecord,links:[link,'https://backup.example/b（附件）'.replace('（附件）','')]}
]});
assert.deepEqual(withLinks.records[0].links,[link,'https://backup.example/b']);
assert.deepEqual(normalizeWechatCandidateBatch(batch).records[0].links,[],
 'old batches without links keep syncing unchanged');
function rejectedLinks(value){
 rejected({...batch,records:[{...baseRecord,links:value}]},'links_invalid');
}
rejectedLinks('https://course.example/a');
assert.deepEqual(normalizeWechatCandidateBatch({...batch,records:[
  {...baseRecord,links:[]}
]}).records[0].links,[],'an explicitly empty link list is valid');
rejectedLinks([link,link]);
rejectedLinks([link,link,link,link]);
rejectedLinks(['http://course.example/insecure']);
rejectedLinks(['https://user:pw@course.example/leak']);
rejectedLinks(['ftp://course.example/file']);
rejectedLinks(['https://']);
rejectedLinks(['javascript:alert(1)']);
rejectedLinks(['https://course.example/'+'a'.repeat(520)]);
rejectedLinks([42]);
// Credential-style query/fragment parameters mirror the Python collector.
rejectedLinks(['https://course.example/form?token=abc']);
rejectedLinks(['https://course.example/a?id=1&api_key=x']);
rejectedLinks(['https://course.example/b#access_token=xyz']);
rejectedLinks(['https://course.example/d?invite_code=7']);
rejectedLinks(['https://course.example/e?na%2me=%zz']);
// camelCase composites hit the substring tier; invalid-UTF-8 percent escapes
// throw in decodeURIComponent and are rejected like the Python collector.
rejectedLinks(['https://course.example/f?authToken=x']);
rejectedLinks(['https://course.example/f?clientSecret=y']);
rejectedLinks(['https://course.example/f?secretKey=z']);
rejectedLinks(['https://course.example/g?%FFx=1']);
// WHATWG port semantics mirror the Python collector.
rejectedLinks(['https://course.example:abc/report']);
rejectedLinks(['https://course.example:99999/x']);
assert.deepEqual(normalizeWechatCandidateBatch({...batch,records:[
  {...baseRecord,links:['https://course.example/c?id=1&page=2',
                        'https://course.example:8443/x?classcode=7']}
]}).records[0].links,
 ['https://course.example/c?id=1&page=2','https://course.example:8443/x?classcode=7'],
 'plain parameters and valid ports stay accepted');
function rejected(payload,code) {
  assert.throws(()=>normalizeWechatCandidateBatch(payload),
    e=>e instanceof WechatCandidateError && e.code===code);
}
rejected({...batch,token:'secret'},'batch_invalid');
rejected({...batch,records:[{...baseRecord,rawMessage:'全文'}]},'record_invalid');
rejected({...batch,records:[baseRecord,baseRecord]},'duplicate_candidate');
rejected({...batch,records:[{...baseRecord,deadline:{
  precision:'exact',date:'2026-11-21',at:'2026-11-20T23:59:00+08:00'
}}]},'deadline_invalid');
rejected({...batch,records:[{...baseRecord,deadline:{
  precision:'unknown',date:'2026-11-21'
}}]},'deadline_invalid');
rejected({...batch,records:[{...baseRecord,kind:'verified_platform'}]},'kind_invalid');
rejected({...batch,records:[{...baseRecord,status:'completed'}]},'record_invalid');
rejected({...batch,records:[{...baseRecord,candidateKey:'wxid_abc'}]},'candidate_key_invalid');
rejected({...batch,records:[{...baseRecord,title:'x'.repeat(121)}]},'title_invalid');
const longBody='第1行说明\n'+'详细要求'.repeat(500);
assert.equal(normalizeWechatCandidateBatch({...batch,records:[
 {...baseRecord,excerpt:longBody}
]}).records[0].excerpt,longBody,'long task text must not be clipped at 240');
rejected({...batch,records:[{...baseRecord,excerpt:'y'.repeat(4001)}]},'excerpt_invalid');
rejected({...batch,records:[{...baseRecord,sentAt:'2026-11-04T13:00:00+08:00'}]},'sent_at_future');
rejected({...batch,records:[{...baseRecord,deadline:{
  precision:'date',date:'2026-02-30'
}}]},'deadline_invalid');
rejected({...batch,records:[{...baseRecord,deadline:{
  precision:'date',date:'2026-11-20',at:'2026-11-20T00:00:00+08:00'
}}]},'deadline_invalid');
console.log('校序微信待审核输入契约：基本字段、时间校验、隐私最小化、双日期、严格拒绝均通过');
