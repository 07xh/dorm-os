/**
 * 校序 · 华水公开通知适配器
 * 改编自 notice-radar（MIT，Copyright (c) 2026 notice-radar contributors）
 * https://github.com/Yang-Yin734/notice-radar
 * 复用策略：CSS 选择器解析、标题规范化、稳定 ID、低频抓取、保留历史；
 * 不接入其 PWA/UI，也不抓取登录页面。
 */
import { load } from 'cheerio';
import dns from 'node:dns';
import { createHash } from 'node:crypto';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const root = fileURLToPath(new URL('../', import.meta.url));
const configPath = resolve(root, 'config/ncwu-notices.json');
const archivePath = resolve(root, 'public/data/notices.json');
export const SCHEMA = 'xiaoxu/notices-v1';
export const SCHOOL_ID = '4141010078';
const ALLOWED = new Set(['www.ncwu.edu.cn', 'www2.ncwu.edu.cn']);
const ARTICLE_PATH = /^\/(?:xsc\/|jiaowu\/)?info\/\d+\/\d+\.htm$/i;
const MAX_ITEMS = 240;
const REQUEST_TIMEOUT_MS = 30000;
const MAX_BYTES = 1024 * 1024;

export const normalizeTitle = input => String(input || '')
  .replace(/[\u200b-\u200d\ufeff]/g, '')
  .replace(/\s+/g, ' ')
  .replace(/(?<=[\u4e00-\u9fa5])\s+(?=[\u4e00-\u9fa5])/g, '')
  .trim();

export function isOfficialArticle(href, baseUrl) {
  try {
    const u = new URL(String(href || ''), baseUrl);
    return u.protocol === 'https:' && ALLOWED.has(u.hostname) && ARTICLE_PATH.test(u.pathname);
  } catch { return false; }
}
export function canonicalLink(href, baseUrl) {
  if (!isOfficialArticle(href, baseUrl)) return null;
  const u = new URL(href, baseUrl);
  return u.origin + u.pathname;
}
export function dateFromText(raw) {
  const m = String(raw || '').match(/(20\d{2})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})/);
  if (!m) return null;
  const y = +m[1], mon = +m[2], day = +m[3];
  const utc = new Date(Date.UTC(y, mon - 1, day));
  if (utc.getUTCFullYear() !== y || utc.getUTCMonth() !== mon - 1 || utc.getUTCDate() !== day) return null;
  return [m[1], String(mon).padStart(2, '0'), String(day).padStart(2, '0')].join('-');
}
export function stableId(url) {
  return 'ncwu-' + createHash('sha256').update(url).digest('hex').slice(0, 16);
}
export function parseHtmlList(html, source) {
  const $ = load(html);
  const output = new Map();
  // 通用 notice-radar html-list 思路：先取真实列表链接，再用官网文章路由校验。
  // 华水本站官网的文章链接形如 /info/1063/23331.htm，
  // 学工处形如 /xsc/info/1013/4139.htm。
  $('a[href]').each((_, anchor) => {
    const el = $(anchor);
    const url = canonicalLink(el.attr('href'), source.url);
    if (!url || !url.startsWith(new URL(source.url).origin)) return;
    const title = normalizeTitle(el.attr('title') || el.text());
    if (title.length < 8 || title.length > 160 || !/[\u4e00-\u9fa5]/.test(title)) return;
    // 只能从同一个列表项的显式日期提取，不扫整个文档或正文，以免错取报名截止日。
    const item = el.closest('li, tr, .news-item, .list-item, .listrow');
    let date = null;
    if (item.length) {
      const candidates = item.find('time, .date, .time, .rq').toArray();
      for (const node of candidates) {
        date = dateFromText($(node).attr('datetime') || $(node).text());
        if (date) break;
      }
      if (!date) date = dateFromText(item.text().slice(0, 240));
    }
    // 同一篇文章出现多个链接时保留已找到的发布日期。
    const prior = output.get(url);
    output.set(url, {
      id: stableId(url), title: prior?.title || title, url, date: date || prior?.date || null, sourceId: source.id,
      sourceName: source.name, category: source.category,
    });
  });
  return [...output.values()];
}
export function mergeArchive(previous, batches, at = new Date().toISOString()) {
  const prior = previous && previous.schema === SCHEMA && Array.isArray(previous.items)
    ? previous : { items: [], sources: [] };
  const items = new Map();
  for (const item of prior.items) {
    if (!item || !canonicalLink(item.url, item.url)) continue;
    items.set(item.url, item);
  }
  let newCount = 0;
  const statuses = [];
  for (const batch of batches) {
    const src = batch.source;
    const oldStatus = (prior.sources || []).find(s => s.id === src.id);
    if (batch.error) {
      statuses.push({ id: src.id, name: src.name, status: 'error', lastSuccessAt: oldStatus?.lastSuccessAt || null, count: oldStatus?.count || 0 });
      continue;
    }
    for (const item of batch.items) {
      const original = items.get(item.url);
      if (!original) newCount++;
      items.set(item.url, {
        ...item,
        id: original?.id || item.id,
        date: item.date || original?.date || null,
      });
    }
    statuses.push({ id: src.id, name: src.name, status: 'ok', lastSuccessAt: at, count: batch.items.length });
  }
  const array = [...items.values()].sort((a, b) => {
    const d = (b.date || '').localeCompare(a.date || '');
    return d || (a.sourceId || '').localeCompare(b.sourceId || '') || a.url.localeCompare(b.url);
  }).slice(0, MAX_ITEMS);
  const anySuccess = batches.some(b => !b.error && b.items.length > 0);
  return {
    schema: SCHEMA, school: SCHOOL_ID, mode: anySuccess ? 'auto' : prior.mode || 'seed',
    lastSuccessAt: anySuccess ? at : prior.lastSuccessAt || null,
    sources: statuses, items: array, newCount,
  };
}
export async function fetchPublicHtml(url, fetcher = fetch) {
  // 和 notice-radar 一致：国内学校的 CERNET2 IPv6 在海外 CI 中可能无路由。
  // IPv4 优先只改变 DNS 地址顺序，不绕过学校反爬或登录控制。
  dns.setDefaultResultOrder('ipv4first');
  // 尊重站点负载。超时、失败后由下一次定时任务恢复，不进行高频重试。
  const res = await fetcher(url, {
    headers: { 'user-agent': 'XiaoxuNoticeCollector/1.0 (+https://github.com/07xh/dorm-os)', 'accept': 'text/html' },
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  });
  if (!res.ok) throw new Error('HTTP ' + res.status);
  const finalUrl = new URL(res.url || url);
  if(finalUrl.protocol !== 'https:' || finalUrl.hostname !== new URL(url).hostname) throw new Error('非华水官方源的重定向已拒绝');
  if (+(res.headers.get('content-length') || 0) > MAX_BYTES) throw new Error('响应体超出限制');
  const buffer = Buffer.from(await res.arrayBuffer());
  if (buffer.length > MAX_BYTES) throw new Error('响应体超出限制');
  // notice-radar 的编码嗅探方案：仅用于学校公开 HTML，兼容 GB2312。
  const prelude = buffer.subarray(0, 2048).toString('latin1');
  const declared = res.headers.get('content-type') || '';
  const charset = /charset\s*=\s*["']?([\w-]+)/i.exec(declared)?.[1]
    || /charset\s*=\s*["']?([\w-]+)/i.exec(prelude)?.[1]
    || 'utf-8';
  const encoding = /^gb(?:k|2312)$/i.test(charset) ? 'gbk' : charset;
  try { return new TextDecoder(encoding).decode(buffer); }
  catch { return new TextDecoder('utf-8').decode(buffer); }
}

// 仅在三条校方源全部不可达时，使用现有 Cloudflare Pages 官方公开数据 API。
// API 在 /functions/api/notices.js 中自行抓华水官网并严格核对来源。
// 不读取用户账号或密钥；不可把代理响应的时间冒充学校原文发布日期。
export const VERIFIED_API_URL = 'https://dorm-os-dk9.pages.dev/api/notices';
export async function collectFromPublishedApi(fetcher=fetch, at=new Date().toISOString()) {
  const res=await fetcher(VERIFIED_API_URL,{
    headers:{'accept':'application/json'},
    signal:AbortSignal.timeout(27000)
  });
  if(!res.ok)throw new Error('官网数据代理 HTTP '+res.status);
  const final = new URL(res.url||VERIFIED_API_URL);
  if(final.href!==VERIFIED_API_URL)throw new Error('官网数据代理重定向不可信');
  if(res.headers.get('x-xiaoxu-public-notices')!=='1')throw new Error('未命中校序公开公告 API');
  if(+(res.headers.get('content-length')||0)>160000)throw new Error('代理结果超出限制');
  const body=await res.text();
  if(body.length>160000)throw new Error('代理结果超出限制');
  const data=JSON.parse(body);
  if(data?.success!==true||!Array.isArray(data.items)||data.items.length<2||data.items.length>45)
    throw new Error('代理没有通过真实官网采集验收');
  const items=[];
  for(const row of data.items){
    const pathPattern=/^\/jiaowu\/info\/(?:1008|1034)\/\d{1,8}\.htm$/;
    const url=canonicalLink(row?.url,row?.url);
    const href=url?new URL(url):null;
    if(!href||href.hostname!=='www2.ncwu.edu.cn'||!pathPattern.test(href.pathname)
      ||!['notice','exam'].includes(row?.category)||row?.publishedAt!==null
      ||typeof row?.title!=='string'||normalizeTitle(row.title).length<5)
      throw new Error('代理返回的数据来源未通过校方文章链接校验');
    items.push({
      id:stableId(url),title:normalizeTitle(row.title),url,
      date:null, // 原 API 只有 MM-DD，年份未知，禁止推测
      sourceId:'jwc-api',sourceName:'教务处公开公告（校序官方网页代理）',
      category:row.category==='exam'?'考试':'教学',
    });
  }
  const source={id:'jwc-api',name:'教务处公开公告（代理兜底）',category:'教学',url:VERIFIED_API_URL};
  console.log('官方公开公告代理兜底：'+items.length+' 条，源站与 API 鉴别已通过');
  return {source,items};
}

export async function collect({ fetcher = fetch, apiFetcher = fetcher, config = null, previous = null, at = new Date().toISOString() } = {}) {
  const cfg = config || JSON.parse(await readFile(configPath, 'utf8'));
  const old = previous || JSON.parse(await readFile(archivePath, 'utf8'));
  const batches = [];
  for (const source of cfg.sources) {
    if (!source.url.startsWith('https://') || !ALLOWED.has(new URL(source.url).hostname)) {
      throw new Error('来源不在华水官网白名单');
    }
    try {
      const html = await fetchPublicHtml(source.url, fetcher);
      const items = parseHtmlList(html, source);
      // 空列表/挑战页是失败，不得覆盖旧真实数据。
      if (items.length < 2) throw new Error('官网页面未解析到至少两条文章链接');
      batches.push({ source, items });
      console.log(source.id + ': ' + items.length + ' 条真实文章链接');
    } catch (error) {
      batches.push({ source, error: String(error.message || error) });
      console.warn(source.id + ': 抓取失败（保留旧记录）' + String(error.message || error) + (error?.cause?.code ? ' [' + error.cause.code + ']' : ''));
    }
  }
  if (!batches.some(b => b.items?.length >= 2)) {
    try {
      // 海外 GitHub Runner 偶发全部 ETIMEDOUT：利用已验证的 Cloudflare
      // Pages 公告 API 提供独立网络出口，不尝试对学校 WAF 做任何绕过。
      batches.push(await collectFromPublishedApi(apiFetcher,at));
    }catch(error){
      console.warn('官方公告代理同样不可用：'+String(error?.message||error));
      throw new Error('所有公开通知源与可信代理均失败，不覆盖已有档案');
    }
  }
  return mergeArchive(old, batches, at);
}
async function main() {
  const next = await collect();
  const publicData = {
    schema: next.schema, school: next.school, mode: next.mode,
    lastSuccessAt: next.lastSuccessAt, sources: next.sources, items: next.items,
  };
  await mkdir(dirname(archivePath), { recursive: true });
  await writeFile(archivePath, JSON.stringify(publicData, null, 2) + '\n');
  console.log('华水通知已持久化：' + publicData.items.length + ' 条，其中新增 ' + next.newCount);
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(error => { console.error(error); process.exitCode = 1; });
}
