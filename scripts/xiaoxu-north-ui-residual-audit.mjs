import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
const root = 'apps/xiaoxu-android/app/src/main/java';
const files = [];
function walk(dir) {
  for (const entry of readdirSync(dir,{withFileTypes:true})) {
    const full = path.join(dir,entry.name);
    if (entry.isDirectory()) walk(full);
    else if (entry.name.endsWith('.kt')) files.push(full);
  }
}
walk(root);
const retired = /\b(?:FloatingBottomBar|PrimaryBottomNavigationReserve|NorthGlassNavChrome|XiaoxuMotion|PageGradient|GlassSheen|GlassModalFallback|GlassIndicator|SheetSheen|GlassControlSurface|GlassRim|SheetInfoSurface|TaskStatusTab|WeekSelector|SelectablePill|CompactPageHeading|MessagePill)\b/g;
const invalid = [];
const remaining = [];
for (const file of files) {
  const source = readFileSync(file,'utf8');
  const code = source.replace(/\/\*[\s\S]*?\*\//g,'').replace(/^[ \t]*\/\/.*$/gm,'');
  for (const match of code.matchAll(retired)) invalid.push({file, symbol:match[0]});
  for (const match of code.matchAll(/XiaoxuPalette\.(?:GlassCard|GlassBorder|GlassTrack|SheetSurface|PressTint|ModalScrim)\b/g)) invalid.push({file, symbol:match[0]});
  const counts = {
    localAnimation:(code.match(/\b(?:animateFloatAsState|animateColorAsState|AnimatedContent|AnimatedVisibility)\s*\(/g)||[]).length,
    localContainer:(code.match(/\b(?:Dialog|AlertDialog|ModalBottomSheet|DropdownMenu|Scaffold)\s*\(/g)||[]).length,
    oldMaterial:(code.match(/XiaoxuPalette\.(?:GlassCard|GlassTrack|GlassBorder|SheetSurface|PressTint|ModalScrim)/g)||[]).length
  };
  if (Object.values(counts).some(Boolean)) remaining.push({file,...counts});
}
console.log(JSON.stringify({filesScanned:files.length,invalid,remaining},null,2));
assert.equal(invalid.length,0,'Retired UI symbols must not return to production.');
