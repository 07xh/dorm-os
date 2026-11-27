// Read-only inventory and conservative reference triage. No auto-deletion.
import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const root = fileURLToPath(new URL('../', import.meta.url));
const src = join(root, 'apps/xiaoxu-android/app/src');
const files = [];
function walk(dir) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) walk(path);
    else if (/\.(kt|java|xml|pro|kts|gradle)$/.test(entry.name)) {
      files.push({ path: relative(root, path).replace(/\\/g, '/'), content: readFileSync(path, 'utf8') });
    }
  }
}
walk(src);
files.sort((a, b) => a.path.localeCompare(b.path));
// Include text in comments, strings and imports deliberately: this is an upper
// bound on references, not a compiler reachability proof. Dynamic/JNI/resource
// references and multiline declarations need manual review before deletion.
const tokens = new Map();
for (const file of files) {
  for (const token of new Set(file.content.match(/[A-Za-z_$][A-Za-z0-9_$]*/g) || [])) {
    if (!tokens.has(token)) tokens.set(token, new Set());
    tokens.get(token).add(file.path);
  }
}
const uiFiles = files.filter(f => /\/src\/main\/.*\/(?:ui|manager)\/.*\.kt$/.test(f.path));
const declaration = /^(?:(?:public|internal|private|protected|open|abstract|sealed|data|enum|value|annotation|actual|expect|inline|suspend|tailrec|operator|infix|external|const|lateinit)\s+)*(?:class|object|interface|typealias|fun|val|var)\s+(?:<[^>\n]+>\s*)?([A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*)/gm;
const rows = uiFiles.map(file => {
  const symbols = [...new Set([...file.content.matchAll(declaration)].map(m => m[1].split('.').at(-1)))].sort();
  const refs = Object.fromEntries(symbols.map(symbol => [symbol, [...(tokens.get(symbol) || [])].filter(p => p !== file.path).sort()]));
  const hasSourceMarker = /znjhahaha\/zhengfang-apk|bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed|SPDX-License-Identifier:\s*GPL-3\.0/.test(file.content);
  const references = [...new Set(Object.values(refs).flat())];
  return {
    file: file.path, lines: file.content.split('\n').length,
    provenance: hasSourceMarker ? 'upstream marker (not proof of equivalence)' : 'manual provenance review',
    declarations: symbols, references: refs,
    review: !symbols.length ? 'declarations not detected; manual review'
      : !references.length ? 'candidate: no external textual references; manual review required'
      : references.every(p => !p.includes('/src/main/')) ? 'candidate: referenced only outside main; review tests and entry points'
      : 'external main references present; preserve pending semantic review'
  };
});
const report = {
  commit: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim(),
  scannedSourceFiles: files.length, uiAndManagerFiles: rows.length,
  limitations: 'Lexical scan only; no automatic removal, no claim of semantic reachability or visual parity.',
  rows
};
writeFileSync(join(root, 'xiaoxu-ui-source-audit.json'), JSON.stringify(report, null, 2) + '\n');
console.log('UI source inventory: ' + report.uiAndManagerFiles + ' files; reference scan: ' + report.scannedSourceFiles + ' source files');
for (const row of rows) {
  console.log(row.file + ' | ' + row.provenance + ' | ' + row.review);
}
