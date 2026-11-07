// 微信本机采集端 → 校序 App 待审核队列的数据契约。
// 这不是微信读取器，也不代表已建立设备授权或真实同步。
export const WECHAT_CANDIDATE_SCHEMA = 'xiaoxu/wechat-candidates-v1';

export class WechatCandidateError extends Error {
  constructor(code) { super(code); this.name = 'WechatCandidateError'; this.code = code; }
}
const fail = code => { throw new WechatCandidateError(code); };
const obj = v => v !== null && typeof v === 'object' && !Array.isArray(v);
function fields(value, allowed, code) {
  if (!obj(value) || Object.keys(value).some(k => !allowed.includes(k))) fail(code);
}
function string(value, max, code, {optional = false} = {}) {
  if (optional && value === undefined) return null;
  if (typeof value !== 'string' || !value.trim() ||
      value !== value.trim() || value.length > max || /[\u0000-\u0008\u000B\u000C\u000E-\u001F]/u.test(value)) fail(code);
  return value;
}
function key(value, code) {
  const v = string(value, 128, code);
  // 只允许不透明 ID，不能将微信群名、wxid 或原文当作消息 ID 上传。
  if (!/^[A-Za-z0-9_-]{12,128}$/.test(v)) fail(code);
  return v;
}
function iso(value, code) {
  if (typeof value !== 'string' || !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,3})?(?:Z|[+-]\d\d:\d\d)$/.test(value) ||
      !Number.isFinite(Date.parse(value))) fail(code);
  return new Date(value).toISOString();
}
function dateOnly(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d\d-\d\d$/.test(value)) fail('deadline_invalid');
  const parsed = new Date(value + 'T00:00:00Z');
  if (!Number.isFinite(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== value)
    fail('deadline_invalid');
  return value;
}
const dayShanghai = utc => new Intl.DateTimeFormat('en-CA', {
  timeZone:'Asia/Shanghai', year:'numeric', month:'2-digit', day:'2-digit'
}).format(new Date(utc)).replace(/\//g, '-');

// Optional per-record links (V1): at most 3 structurally validated HTTPS URLs.
// The collector never opens them; this contract again rejects credentials in
// the URL and any non-https scheme. Older batches without `links` stay valid.
// The rules mirror the Python collector (WHATWG URL semantics): numeric port
// in range via new URL(), plus the same credential-style query/fragment
// parameter denylist (?token=, ?api_key=, #access_token=, ... are rejected).
const MAX_LINKS=3, MAX_LINK_LEN=512;
// Two tiers keep camelCase composites covered without over-matching:
// - SUBSTR: high-specificity credential words match anywhere in the name
//   (authToken, clientSecret, secretKey, refreshToken, ... all hit "token"/"secret").
// - PARAM: generic words (code, key, auth, session, ...) only match as whole
//   tokens separated by _ or -, so ?classcode= and ?keyboard= stay safe.
const SENSITIVE_SUBSTR=/token|secret|password|passwd|credential|jwt|bearer|oauth/i;
const SENSITIVE_PARAM=/(?:^|[_-])(?:auth|authorization|session|sessionid|sid|sig|signature|sas|apikey|api[_-]?key|api[_-]?secret|code|key|pass|pwd)(?:$|[_-])/i;
function sensitiveParams(raw){
  return raw.split(/[&;]/).some(piece=>{
    if(!piece) return false;
    const rawName=piece.split('=')[0];
    // Malformed percent-encoding is unvalidatable and rejected, matching the
    // Python collector's PERCENT_INVALID behaviour.
    if(/%(?![0-9A-Fa-f]{2})/.test(rawName)) return true;
    // decodeURIComponent throws on byte sequences that are not valid UTF-8
    // (e.g. ?%FF=1): such names are unvalidatable and rejected. The Python
    // collector decodes with errors='strict' to mirror this exactly.
    let name;try{name=decodeURIComponent(rawName);}catch{return true;}
    name=name.trim();
    return !!name&&(SENSITIVE_SUBSTR.test(name)||SENSITIVE_PARAM.test(name));
  });
}
function links(value){
  if(value===undefined) return [];
  if(!Array.isArray(value)||value.length>MAX_LINKS) fail('links_invalid');
  const seen=new Set();
  const out=value.map(item=>{
    if(typeof item!== 'string'||item!==item.trim()||item.length<12||item.length>MAX_LINK_LEN)
      fail('links_invalid');
    if(/[\u0000-\u001f\u007f]/.test(item)) fail('links_invalid');
    let parsed;try{parsed=new URL(item);}catch{fail('links_invalid');}
    if(parsed.protocol!=='https:'||!parsed.hostname||parsed.username||parsed.password)
      fail('links_invalid');
    if(sensitiveParams(parsed.search.slice(1))||sensitiveParams(parsed.hash.slice(1)))
      fail('links_invalid');
    if(seen.has(item)) fail('links_invalid');
    seen.add(item);
    return item;
  });
  return out;
}

function deadline(value) {
  fields(value, ['precision','date','at'], 'deadline_invalid');
  if (!['unknown','date','exact'].includes(value.precision)) fail('deadline_invalid');
  if (value.precision === 'unknown') {
    if (value.date !== undefined || value.at !== undefined) fail('deadline_invalid');
    return {precision:'unknown', date:null, atUtc:null};
  }
  const date = dateOnly(value.date);
  if (value.precision === 'date') {
    if (value.at !== undefined) fail('deadline_invalid');
    return {precision:'date', date, atUtc:null};
  }
  const atUtc = iso(value.at, 'deadline_invalid');
  if (dayShanghai(atUtc) !== date) fail('deadline_invalid');
  return {precision:'exact', date, atUtc};
}

// 消息和群标识须在电脑端使用设备持有的密钥计算不透明 HMAC。
// 这里不生成 HMAC，不接收设备密钥，也不接受完整聊天记录。
// 同一消息中多个截止时间由不同 candidateKey 表示，不按标题合并。
export function normalizeWechatCandidateBatch(input) {
  fields(input, ['schema','deviceId','batchId','capturedAt','records'], 'batch_invalid');
  if (input.schema !== WECHAT_CANDIDATE_SCHEMA) fail('schema_unsupported');
  const deviceId = key(input.deviceId, 'device_id_invalid');
  const batchId = key(input.batchId, 'batch_id_invalid');
  const capturedAt = iso(input.capturedAt, 'captured_at_invalid');
  if (!Array.isArray(input.records) || input.records.length > 100) fail('batch_size_invalid');

  const seen = new Set();
  const records = input.records.map(record => {
    fields(record, [
      'messageKey','candidateKey','chatKey','chatLabel','kind','title',
      'excerpt','sentAt','deadline','links'
    ], 'record_invalid');
    const messageKey = key(record.messageKey, 'message_key_invalid');
    const candidateKey = key(record.candidateKey, 'candidate_key_invalid');
    const chatKey = key(record.chatKey, 'chat_key_invalid');
    if (seen.has(candidateKey)) fail('duplicate_candidate');
    seen.add(candidateKey);
    if (!['homework','exam','activity','signup','other'].includes(record.kind)) fail('kind_invalid');
    const title = string(record.title, 120, 'title_invalid');
    const chatLabel = record.chatLabel === undefined ? null :
      string(record.chatLabel, 80, 'chat_label_invalid');
    const excerpt = record.excerpt === undefined ? null :
      string(record.excerpt, 240, 'excerpt_invalid');
    const sentAt = iso(record.sentAt, 'sent_at_invalid');
    if (Date.parse(sentAt) > Date.parse(capturedAt) + 300000) fail('sent_at_future');
    return {
      messageKey, candidateKey, chatKey, chatLabel, kind:record.kind,
      title, excerpt, sentAt, deadline:deadline(record.deadline),
      links:links(record.links),
      provenance:'wechat_local_candidate', reviewStatus:'needs_review'
    };
  });
  return {schema:WECHAT_CANDIDATE_SCHEMA,deviceId,batchId,capturedAt,records};
}
