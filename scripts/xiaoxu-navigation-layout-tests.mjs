import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = path => readFileSync(new URL('../'+path, import.meta.url), 'utf8');
const html = read('public/index.html');
const web = read('public/xiaoxu-web-console.css');
const workspace = read('public/xiaoxu-workspace.css');

const headerEnd = html.indexOf('</header>');
const nav = html.indexOf('<nav class="main-nav x-v7-primary-nav"');
const main = html.indexOf('<div class="workspace">');
assert(headerEnd >= 0 && headerEnd < nav && nav < main,
  'Navigation must be a grid sibling after the toolbar header');
assert.equal(html.split('id="sidebarNav"').length - 1, 1, 'Navigation must remain a singleton');
assert(html.includes('aria-label="主导航"'), 'Navigation landmark must be device-independent');
assert(!html.includes('aria-label="顶部主导航"'), 'Retired header-specific landmark label found');
const navMarkup = html.slice(nav, html.indexOf('</nav>',nav));
const pages = ['dashboard','timetable','academic','tasks','profile'];
const positions = pages.map(page => navMarkup.indexOf('data-page="'+page+'"'));
assert(positions.every(v => v > -1), 'Missing a primary route');
assert(positions.every((value,i) => i === 0 || value > positions[i-1]),
  'DOM and keyboard order must follow the five-page information architecture');
assert(navMarkup.indexOf('data-console-route="review"') > positions[4],
  'Additional desktop-only links must follow the five main pages');
assert(!/\border\s*:\s*none\s*;\s*display\s*:\s*none\s*;/.test(web), 'Unexpected CSS');
assert(/grid-template-columns\s*:\s*minmax\(0,\s*240px\)/.test(web), 'Desktop grid sidebar missing');
assert(web.includes('position:sticky') && !web.includes('position:fixed'),
  'Desktop sidebar must use sticky in-flow positioning rather than fixed overlay');
assert(!/\border\s*:\s*0\s*;\s*order\s*:/.test(web) && !/\border\s*:\s*0\s*;\s*order\s*:/.test(workspace),
  'Visual reordering is no longer allowed');
assert(!/\.x-v7-header\s+\.nav-link/.test(workspace), 'Navigation CSS must not require header ancestry');
assert(workspace.includes('grid-template-columns:repeat(5,minmax(0,1fr))'),
  'Five-item mobile navigation has regressed');
assert(workspace.includes('env(safe-area-inset-bottom,0px)'),
  'Mobile floating navigation must respect safe area');
console.log('Responsive navigation landmark, DOM order, real sidebar and mobile safe-area tests passed');
