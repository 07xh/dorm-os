/**
 * 校序 · 华水公开通知适配器
 * 改编自 notice-radar（MIT，Copyright (c) 2026 notice-radar contributors）
 * https://github.com/Yang-Yin734/notice-radar
 * 复用策略：CSS 选择器解析、标题规范化、稳定 ID、低频抓取、保留历史；
 * 不接入其 PWA/UI，也不抓取登录页面。
 */
import { load } from 'cheerio';
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
const ARTICLE_PATH = /^\/(?:xsc\/)?info\/\d+\/\d+\.htm$/i;
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
  // 尊重站点负载。超时、失败后由下一次定时任务恢复，不进行高频重试。
  const res = await fetcher(url, {
    headers: { 'user-agent': 'XiaoxuNoticeCollector/1.0 (+https://github.com/07xh/dorm-os)', 'accept': 'text/html' },
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  });
  if (!res.ok) throw new Error('HTTP ' + res.status);
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
export async function collect({ fetcher = fetch, config = null, previous = null, at = new Date().toISOString() } = {}) {
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
      console.warn(source.id + ': 抓取失败（保留旧记录）' + String(error.message || error));
    }
  }
  if (!batches.some(b => b.items?.length >= 2)) {
    throw new Error('所有公开通知源均失败，不覆盖已有档案');
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
