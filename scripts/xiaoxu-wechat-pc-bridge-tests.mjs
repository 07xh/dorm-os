import assert from 'node:assert/strict';
import {createCandidates,extractDeadline,parseHistoryLine}
 from './xiaoxu-wechat-pc-bridge.mjs';
import {normalizeWechatCandidateBatch,WECHAT_CANDIDATE_SCHEMA}
 from '../lib/academic-wechat-candidate-contract.mjs';
const group='电工学课程通知群',deviceId='wxd_abcdefghijklmnopqrstuvwx',
 token='wxu_'+('a'.repeat(43));
const history={
 is_group:true,chat:group,username:'12345@chatroom',failures:[],
 messages:[
 '[2026-11-04 11:59] 老师: 周五前提交实验报告',
 '[2026-11-04 12:00] 同学: 已经提交作业啦',
 '[2026-11-04 12:01] 老师: 请在2026年11月6日之前上传电路实验报告',
 '[2026-11-04 12:02] 同学: 今天天气很好',
 '[2026-11-04 12:03] 老师: 2026-02-30 提交报告',
 '[2026-11-04 12:04] 老师: 下周有考试'
 ]
};
const result=createCandidates(history,group,token,deviceId);
assert.equal(result.records.length,4,'only candidate task-like statements are included');
assert.ok(result.newest>0);
assert.equal(result.records[0].deadline.precision,'unknown',
 'do not invent relative deadlines');
assert.deepEqual(result.records[1].deadline,{precision:'date',date:'2026-11-06'});
assert.deepEqual(result.records[2].deadline,{precision:'unknown'});
assert.equal(result.records[3].kind,'exam');
assert.ok(result.records.every(r=>!JSON.stringify(r).includes('老师:')),
 'do not upload sender labels');
assert.ok(result.records.every(r=>!Object.keys(r).includes('excerpt')),
 'do not send full chat excerpts');
assert.deepEqual(result.records,createCandidates(history,group,token,deviceId).records,
 'same local messages must produce stable idempotency keys');
assert.notDeepEqual(result.records.map(x=>x.messageKey),
 createCandidates({...history,username:'99999@chatroom'},group,token,deviceId)
 .records.map(x=>x.messageKey),'IDs must be scoped per actual group');
assert.throws(()=>createCandidates({...history,is_group:false},group,token,deviceId),
 /不是群聊/);
assert.throws(()=>createCandidates({...history,failures:['bad shard']},group,token,deviceId),
 /不完整/);
assert.throws(()=>createCandidates({...history,chat:'其他群'},group,token,deviceId),
 /允许列表/);
assert.equal(parseHistoryLine('[2026-02-30 19:00] 老师: 提交报告'),null);
assert.equal(parseHistoryLine('[2026-11-04 19:00] 老师: 我已提交作业'),null);
assert.deepEqual(extractDeadline('2026-02-30前交作业'),{precision:'unknown'});
assert.deepEqual(extractDeadline('下周一提交作业'),{precision:'unknown'});
const batch={schema:WECHAT_CANDIDATE_SCHEMA,deviceId,
 batchId:'wxb_abcdefghijklmnopqrstuvwx',
 capturedAt:'2026-11-04T20:00:00+08:00',
 records:result.records};
assert.equal(normalizeWechatCandidateBatch(batch).records.length,4,
 'records must match the existing Academic OS server schema');
console.log('校序 WeChat Windows 桥接：固定群、任务筛选、日期保守解析、HMAC ID、隐私字段、服务端契约检查通过');
