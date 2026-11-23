import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';

const read = path => readFileSync(new URL('../' + path, import.meta.url), 'utf8');
const html = read('public/index.html');
const tokens = read('public/xiaoxu-tokens.css');
const workspace = read('public/xiaoxu-workspace.css');
const academic = read('public/xiaoxu-academic.css');
const bootstrap = read('public/xiaoxu-theme-bootstrap.js');

const firstPaint = html.indexOf('/xiaoxu-theme-bootstrap.js');
const base = html.indexOf('/xiaoxu-base.css');
const palette = html.indexOf('/xiaoxu-tokens.css');
const components = html.indexOf('/xiaoxu-academic.css');
assert(firstPaint > 0 && firstPaint < base, 'Apply the saved theme before CSS loads');
assert(base < palette && palette < components, 'Tokens must load between base and components');
assert.equal(html.split('/xiaoxu-tokens.css').length - 1, 1);
assert(!workspace.includes('--xu-bg:'), 'Workspace must not redefine shared palette');
assert(!workspace.includes('--base:'), 'Workspace must not duplicate legacy aliases');
assert(tokens.includes(':root[data-theme="dark"]'), 'Dark theme token block missing');
for (const name of ['--xu-danger:', '--xu-warn:', '--xu-success:']) {
  assert(tokens.includes(name), 'Missing semantic status token: ' + name);
}
assert(academic.includes('color:var(--xu-danger)'), 'Error component must use semantic color');
assert(html.includes("document.documentElement.dataset.theme=state.settings.theme==='dark'?'dark':'light';"),
  'Runtime theme changes must update data-theme');

function boot(raw, blocked = false) {
  const classes = new Set();
  const root = {
    dataset: {},
    classList: { toggle(name, active) { if (active) classes.add(name); else classes.delete(name); } }
  };
  const storage = { getItem(key) {
    assert.equal(key, 'dorm-os-state-v1');
    if (blocked) throw new Error('Storage unavailable');
    return raw;
  }};
  runInNewContext(bootstrap, { document: { documentElement: root }, localStorage: storage });
  return { theme: root.dataset.theme, light: classes.has('theme-light') };
}

assert.deepEqual(boot(null), { theme: 'light', light: true });
assert.deepEqual(boot(JSON.stringify({version:1,settings:{theme:'light'}})), { theme:'light', light:true });
assert.deepEqual(boot(JSON.stringify({version:1,settings:{theme:'dark'}})), { theme:'dark', light:false });
assert.deepEqual(boot(JSON.stringify({version:2,settings:{theme:'dark'}})), { theme:'light', light:true });
assert.deepEqual(boot('{invalid'), { theme:'light', light:true });
assert.deepEqual(boot(null,true), { theme:'light', light:true });
// A palette change must not recreate the active page tree.
assert(html.includes('function applyTheme(){'), 'Dedicated theme applicator missing');
assert(html.includes('applyTheme();tick();'), 'Render must keep theme synchronized');
assert(html.includes("if(action==='theme-v8'){state.settings.theme=state.settings.theme==='light'?'dark':'light';persist();applyTheme();return;}"),
  'Toolbar theme switch should apply CSS without a full render');
assert(html.includes("else if(el.id==='themeSelect'){state.settings.theme=el.value==='light'?'light':'dark';persist();applyTheme();}"),
  'Settings theme selection should apply CSS without a full render');
console.log('P0 theme first paint, token ownership, legacy alias and storage fallback checks passed');
