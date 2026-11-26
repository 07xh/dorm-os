#!/usr/bin/env node
/**
 * 校序 · 一加 Ace 6 Android 16/17 本机视觉证据采集。
 * 只调用用户授权的 adb，不自动点选、登录、上传、清空数据或修改课程。
 * 截图可能包含私人课程/任务，请只保存在自己的电脑。
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { createInterface } from 'node:readline/promises';

const argv = process.argv.slice(2);
const help = argv.includes('--help') || argv.includes('-h');
if (help) {
  console.log([
    '用法: node scripts/xiaoxu-ace6-visual-qa.mjs [选项]',
    '  --upstream-baseline  原版完整源码基线模式；不测试校序业务',
    '  --package <id>  默认校序 QA；基线模式默认 com.tyust.course.benchmark',
    '  --serial <id>   多台 adb 设备时指定真实手机序列号',
    '  --apk <file>    验证本机 QA APK 与手机实际安装 APK 的 SHA256，不会自动安装',
    '  --commit <sha>  记录与截图对应的 GitHub 源码提交',
    '  --reference-package <id>  同一台 Ace 6 上北化课表原版包名，用于对照截图和帧数据',
    '  --page-timing  可选：记录从点击入口至页面有效内容稳定出现的本机录像测量值（至少3次，毫秒）',
    '  --out <folder>  输出目录（默认用户主目录 Xiaoxu-Visual-QA）',
    '说明：需已安装 Android Platform Tools，手机 USB 调试并授权电脑。',
    '本程序只在本机采集屏幕；无设备、非一加 Ace 6、非 Android 16/17 时不出具证据。'
  ].join('\n'));
  process.exit(0);
}

function option(flag, fallback = undefined) {
  const i = argv.indexOf(flag);
  if (i === -1) return fallback;
  if (i + 1 >= argv.length || argv[i + 1].startsWith('--')) {
    throw new Error('参数缺少值: ' + flag);
  }
  return argv[i + 1];
}
const upstreamBaseline = argv.includes('--upstream-baseline');
const packageName = option('--package', upstreamBaseline ? 'com.tyust.course.benchmark' : 'io.github.x07xh.xiaoxu.unifiedqa');
const serial = option('--serial');
const gitCommit = option('--commit', 'UNVERIFIED');
const apkPath = option('--apk');
const referencePackage = option('--reference-package', upstreamBaseline ? 'com.tyust.course' : undefined);
const collectPageTiming = argv.includes('--page-timing');
const referenceSceneNames = upstreamBaseline ? ['02-schedule','03-course-detail','04-schedule-settings','11-glass-rest','12-glass-rapid-tabs','13-glass-drag-return','14-glass-course-content','16-glass-dialog-gray-edge'] : ['02-schedule', '03-course-detail', '04-schedule-settings', '05-schedule-day', '06-course-editor', '11-glass-rest', '12-glass-rapid-tabs', '13-glass-drag-return', '14-glass-course-content', '15-glass-gesture-inset', '16-glass-dialog-gray-edge'];
const referenceSceneSet = new Set(referenceSceneNames);
if (!(upstreamBaseline ? packageName === 'com.tyust.course.benchmark' : /^io\.github\.x07xh\.xiaoxu(?:\.unifiedqa|\.performanceqa)?$/.test(packageName))) {
  throw new Error('只能采集校序指定 QA/正式包或上游隔离 benchmark 包');
}
if (referencePackage && (!/^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$/.test(referencePackage) || referencePackage === packageName)) {
  throw new Error('北化课表原版包名格式错误，或与当前校序 QA 包名相同');
}
const adbPrefix = serial ? ['-s', serial] : [];

function adb(args, binary = false, optional = false) {
  const r = spawnSync('adb', adbPrefix.concat(args), {
    encoding: binary ? null : 'utf8',
    maxBuffer: 30 * 1024 * 1024,
    windowsHide: true
  });
  if (r.error) {
    if (optional) return binary ? Buffer.alloc(0) : '';
    throw new Error('adb 不可用：请安装 Android Platform Tools 并将 adb 加入 PATH');
  }
  if (r.status !== 0) {
    if (optional) return binary ? Buffer.alloc(0) : '';
    throw new Error('adb 命令失败：' + args.slice(0, 3).join(' ') + '；' +
      String(r.stderr ?? '').trim().slice(0, 300));
  }
  return binary ? r.stdout : r.stdout.trim();
}
const devices = adb(['devices', '-l']).split(/\r?\n/).slice(1)
  .filter(x => /\sdevice(?:\s|$)/.test(x) && !x.startsWith('*'));
if (!serial && devices.length !== 1) {
  throw new Error('必须恰好连接一台已授权设备；多台设备请使用 --serial');
}
const model = adb(['shell', 'getprop', 'ro.product.model']);
const brand = adb(['shell', 'getprop', 'ro.product.brand']);
const deviceCode = adb(['shell', 'getprop', 'ro.product.device']);
const sdk = Number(adb(['shell', 'getprop', 'ro.build.version.sdk']));
const release = adb(['shell', 'getprop', 'ro.build.version.release']);
const isEmulator = adb(['shell', 'getprop', 'ro.kernel.qemu'], false, true) === '1' ||
  adb(['shell', 'getprop', 'ro.boot.qemu'], false, true) === '1';
if (isEmulator) {
  throw new Error('当前是 Android 模拟器，不得标记为一加 Ace 6 真机证据');
}
if (!/oneplus/i.test(brand) || !/PLQ110|Ace[\s_-]*6/i.test(model + ' ' + deviceCode)) {
  throw new Error('设备未确认为一加 Ace 6：' + brand + ' ' + model + ' (' + deviceCode + ')');
}
if (!((sdk === 36 && release === '16') || (sdk === 37 && release === '17'))) {
  throw new Error('设备不是支持的 Android 16/17：API=' + sdk + ', release=' + release);
}
const referenceInstalled = referencePackage ? adb(['shell', 'pm', 'path', referencePackage], false, true) : '';
if (referencePackage && !referenceInstalled.includes('package:')) {
  throw new Error('北化课表原版包名未安装：' + referencePackage + '；请在手机上安装原版并核对包名');
}
const installed = adb(['shell', 'pm', 'path', packageName], false, true);
if (!installed.includes('package:')) {
  throw new Error('目标 APK 未安装：' + packageName + '。请先人工安装 CI 的 QA APK。');
}
const date = new Date().toISOString().replace(/[:.]/g, '-');
const output = path.resolve(option('--out', path.join(os.homedir(), 'Xiaoxu-Visual-QA', date)));
fs.mkdirSync(output, { recursive: true });
let apkSha256 = null;
if (apkPath) {
  const bytes = fs.readFileSync(path.resolve(apkPath));
  apkSha256 = crypto.createHash('sha256').update(bytes).digest('hex');
}
// Read the installed package's base APK in the normal ADB shell. If the
// device blocks that read, keep build identity unverified; never require root.
const installedBase = installed.split(/\r?\n/)
  .map(line => line.startsWith('package:') ? line.slice('package:'.length).trim() : '')
  .find(file => file.endsWith('/base.apk'));
const remoteDigestOutput = installedBase
  ? adb(['shell', 'sha256sum', installedBase], false, true) : '';
const installedDigestMatch = /^([a-f0-9]{64})(?:\s|$)/i.exec(remoteDigestOutput);
const installedApkSha256 = installedDigestMatch?.[1]?.toLowerCase() ?? null;
const installedMatchesLocal = apkSha256 && installedApkSha256
  ? apkSha256 === installedApkSha256 : null;
const pinnedUpstreamCommit = 'bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed';
const declaredCommitLooksLikeSha = /^[a-f0-9]{7,40}$/i.test(gitCommit) &&
  (!upstreamBaseline || gitCommit === pinnedUpstreamCommit);
const packageVersions = target => adb(['shell', 'dumpsys', 'package', target], false, true).split(/\r?\n/).filter(x => /\bversion(?:Name|Code)=/.test(x)).slice(0, 4).map(x => x.trim());
const targetVersions = packageVersions(packageName);
const referenceVersions = referencePackage ? packageVersions(referencePackage) : [];
const matchingBaselineVersions = upstreamBaseline ? (
  targetVersions.some(x => x === 'versionName=1.0.135-benchmark') &&
  referenceVersions.some(x => x === 'versionName=1.0.135')
) : null;
const metadata = {
  capturedAt: new Date().toISOString(),
  mode: upstreamBaseline ? 'exact-upstream-baseline' : 'xiaoxu',
  pinnedUpstreamCommit: upstreamBaseline ? pinnedUpstreamCommit : null,
  matchingBaselineVersions,
  // Same version labels do not prove equal source, build flags, signatures or data.
  baselineBuildVariant: upstreamBaseline ? 'original benchmark (release-derived, UI_PREVIEW=true)' : null,
  device: { brand, model, deviceCode, androidRelease: release, api: sdk },
  display: {
    size: adb(['shell', 'wm', 'size'], false, true),
    density: adb(['shell', 'wm', 'density'], false, true),
    peakRefreshRateSetting: adb(['shell', 'settings', 'get', 'system', 'peak_refresh_rate'], false, true),
    minRefreshRateSetting: adb(['shell', 'settings', 'get', 'system', 'min_refresh_rate'], false, true),
    note: 'Refresh rate settings are not a measurement of actual per-frame display refresh.'
  },
  app: {
    packageName,
    versionEvidence: targetVersions,
    installedPath: installed.split('\n')[0],
    gitCommit,
    apkSha256,
    installedApkSha256,
    installedMatchesLocal,
    declaredCommitLooksLikeSha,
    gitCommitVerifiedAgainstRepository: false
  },
  referenceApp: referencePackage ? {
    packageName: referencePackage,
    installedPath: referenceInstalled.split('\n')[0],
    originalVersionVerified: false,
    versionEvidence: adb(['shell', 'dumpsys', 'package', referencePackage], false, true).split(/\r?\n/).filter(x => /\bversion(?:Name|Code)=/.test(x)).slice(0, 4).map(x => x.trim()),
    note: upstreamBaseline ? '固定源码标注 1.0.135；核对版本、实际来源、设置及数据一致性，版本号相同不代表源码相同。' : '原版 APK 版本须人工核对为北化课表 v1.0.8.3，包名不能代替版本证据'
  } : null,
  referenceScenes: [],
  source: 'physical-device-adb',
  note: '截图为用户自行导航的真实本机画面；不代表真实学校数据核验通过',
  result: 'pending_capture',
  requiredReferenceScenes: upstreamBaseline ? referenceSceneNames : ['02-schedule', '03-course-detail', '04-schedule-settings', '05-schedule-day', '06-course-editor', '11-glass-rest', '12-glass-rapid-tabs', '13-glass-drag-return', '14-glass-course-content', '16-glass-dialog-gray-edge'],
  scenes: []
};
const steps = upstreamBaseline ? [
  ['02-schedule', '周课表：两包使用相同的本人已有数据或原版内置演示模式；连续滚动，最后停在相同周次'],
  ['03-course-detail', '课程详情：打开相同课程，只查看并返回，不保存或修改'],
  ['04-schedule-settings', '课表设置：滚动同一设置页，不改变设置'],
  ['11-glass-rest', '底栏静止：核对同主题、壁纸、亮度和导航位置'],
  ['12-glass-rapid-tabs', '在课表、课程、成绩、设置等两包相同的非写入页面间连续切换至少20次，最终返回相同页面；不要进入选课提交等业务'],
  ['13-glass-drag-return', '对相同非写入导航标签重复拖动再返回，至少10轮'],
  ['14-glass-course-content', '在相同周课表连续滚动和切换周次，不修改课程'],
  ['16-glass-dialog-gray-edge', '打开并关闭相同设置选择器或课程详情，不保存任何修改']
] : [
  ['01-home', '首页：核对标题、课程列表、任务卡、留白与底部导航；切到其他页再返回'],
  ['02-schedule', '周课表：核对原版周一至周日选择条、周次、课程卡颜色和七日布局；不要更改真实课程'],
  ['03-course-detail', '课程详情：点击本人已有课程后保持底部抽屉打开，对照原图的弹出位置、文字、双信息卡、教师/周次、提醒和关闭按钮；不要切换提醒'],
  ['04-schedule-settings', '课表设置：从现有入口打开并停留在设置页，对照原图的页面位置、分组、文字、节次时间和周/日视图入口；不要改动真实学期'],
  ['05-schedule-day', '日视图：在课表设置中只切换到日视图并点“完成”，采集真实单日课表，最后恢复原视图；不得修改课程、学期或导入演示数据'],
  ['06-course-editor', '编辑课程：打开本人已有课程详情，点课程名称进入编辑页，核对全屏玻璃分组、星期选择和保存按钮；截图后取消，禁止更改真实课程'],
  ['07-tasks', '任务中心：核对三态数量、来源切换、长列表、搜索及返回'],
  ['08-task-detail', '任务详情：打开已有一条任务核对原生详情；不要修改完成状态'],
  ['09-messages', '消息中心：核对官方通知/微信审核来源切换、列表及返回'],
  ['10-message-detail', '消息详情：核对官方公告弹层与返回；无真实公告可跳过'],
  ['11-glass-rest', '玻璃底栏静止态：与北化课表 v1.0.83 原版对比轨道轮廓、高光、透明选中透镜、五项图标与灰色描边；采集截图及 gfxinfo'],
  ['12-glass-rapid-tabs', '快速连续切换五个页面并回到今日：查看滑块跳位、闪白、页面状态丢失与连点卡顿；采集帧统计'],
  ['13-glass-drag-return', '从当前标签拖动到相邻标签再拖回并松手：检查回弹、轻触与拖动分离、没有重复切页或压感残留'],
  ['14-glass-course-content', '在有真实课程颜色的周课表上查看玻璃折射和文字采样；确认星期栏、课程色块与课程详情仍保持原状'],
  ['15-glass-gesture-inset', 'Android 16/17 系统手势、底部安全区、横竖屏短屏切换：检查可点击区域、底栏遮挡和五项侧栏可达性'],
  ['16-glass-dialog-gray-edge', '从我的打开任务备份与恢复和云端同步，再打开一条任务或公告：检查居中玻璃弹窗、灰边、双重卡片、页面滚动及关闭返回；课程详情仍应由底部弹出']
];
// Screenshot validation must check which application is actually in the
// foreground, rather than accepting a PNG of Settings, launcher or another app.
function isTargetInForeground(targetPackage = packageName) {
  const windowDump = adb(['shell', 'dumpsys', 'window', 'windows'], false, true);
  const focus = windowDump.split(/\r?\n/).find(line => /\bmCurrentFocus\s*=/.test(line));
  if (focus) return focus.includes(targetPackage + '/');
  const activityDump = adb(['shell', 'dumpsys', 'activity', 'activities'], false, true);
  const resumed = activityDump.split(/\r?\n/).find(
    line => /\b(?:topResumedActivity|mResumedActivity|ResumedActivity)\s*[:=]/.test(line)
  );
  return Boolean(resumed?.includes(targetPackage + '/'));
}

function frameDurationPercentiles(gfx) {
  const metrics = {};
  for (const p of [50, 90, 95, 99]) {
    const match = gfx.match(new RegExp('^\\s*' + p + '(?:th|st|nd|rd) percentile:\\s*(\\d+(?:\\.\\d+)?)\\s*ms\\b', 'im'));
    metrics['p' + p + 'Ms'] = match ? Number(match[1]) : null;
  }
  return metrics;
}
const pageTimingScenes = new Set(['02-schedule','03-course-detail','04-schedule-settings','05-schedule-day','06-course-editor','12-glass-rapid-tabs','16-glass-dialog-gray-edge']);
async function readPageTiming(rl, scene, appLabel) {
  if (!collectPageTiming || !pageTimingScenes.has(scene)) return null;
  const input = await rl.question(appLabel + ' 页面就绪时间：请用本机录像逐帧测量同一入口从点击至有效内容稳定显示，输入至少3次毫秒值（空格分隔；未测量直接回车）：');
  const values = input.trim().split(/\s+/).filter(Boolean).map(Number);
  if (values.length < 3 || !values.every(x => Number.isFinite(x) && x > 0)) return null;
  const sorted = [...values].sort((a,b) => a-b);
  const middle = Math.floor(sorted.length / 2);
  return { samplesMs: values, medianMs: sorted.length % 2 ? sorted[middle] : (sorted[middle-1] + sorted[middle]) / 2,
    source: 'user_measured_local_video', independentlyVerified: false };
}

const rl = createInterface({ input: process.stdin, output: process.stdout });
try {
  console.log('真机：' + brand + ' ' + model + '，Android ' + release);
  console.log('采集包：' + packageName + '；源码 SHA：' + gitCommit);
  console.log('仅本地保存：' + output);
  for (const [name, instructions] of steps) {
    const ready = await rl.question('\n' + instructions + '\n打开目标界面后按 Enter 开始；输入 skip 跳过：');
    if (ready.trim().toLowerCase() === 'skip') {
      metadata.scenes.push({ name, captured: false, reason: 'manually_skipped' });
      continue;
    }
    adb(['shell', 'dumpsys', 'gfxinfo', packageName, 'reset'], false, true);
    await rl.question('请在当前界面实际点击、滚动和返回，然后停在要对照的画面，按 Enter 截图：');
    const foregroundVerified = isTargetInForeground();
    if (!foregroundVerified) {
      console.log('警告：当前前台无法确认是校序，截图仍保存但不计入有效复刻证据。');
    }
    const png = adb(['exec-out', 'screencap', '-p'], true);
    if (png.length < 100 || !png.subarray(0, 8).equals(
      Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))) {
      throw new Error('截图不是有效 PNG：' + name);
    }
    fs.writeFileSync(path.join(output, name + '.png'), png);
    const gfx = adb(['shell', 'dumpsys', 'gfxinfo', packageName], false, true);
    fs.writeFileSync(path.join(output, name + '.gfxinfo.txt'), gfx, 'utf8');
    const total = gfx.match(/Total frames rendered:\s*(\d+)/i);
    const janky = gfx.match(/Janky frames:\s*(\d+)/i);
    const totalFrames = total ? Number(total[1]) : null;
    const jankyFrames = janky ? Number(janky[1]) : null;
    // dumpsys gfxinfo only reports observed UI frames: short/idle captures do not
    // provide a reliable smoothness verdict. Record raw evidence and a ratio,
    // but never infer 60/120 Hz or a visual pass from these counters alone.
    const validFrameSample = Number.isInteger(totalFrames) && totalFrames > 0 &&
      Number.isInteger(jankyFrames) && jankyFrames >= 0 && jankyFrames <= totalFrames;
    const jankyPercent = validFrameSample
      ? Math.round(10000 * jankyFrames / totalFrames) / 100 : null;
    metadata.scenes.push({
      name, captured: true, foregroundVerified,
      png: name + '.png', frameDump: name + '.gfxinfo.txt',
      totalFrames, jankyFrames, jankyPercent,
      frameDuration: frameDurationPercentiles(gfx),
      pageReady: foregroundVerified ? await readPageTiming(rl, name, '校序') : null,
      frameSampleUsable: validFrameSample && totalFrames >= 30,
      screenshotSha256: crypto.createHash('sha256').update(png).digest('hex')
    });
    console.log('已保存 ' + name + '.png；' +
      (jankyPercent == null ? '系统未返回可解析帧数据' :
        '观测卡顿帧 ' + jankyFrames + '/' + totalFrames + '（' + jankyPercent + '%）') +
      '；仍须与原版进行真机视觉/交互对照。');
    if (referencePackage && referenceSceneSet.has(name)) {
      // Capture comparable original UI on this SAME physical device. Do not
      // auto-control the original app or mix its counters with Xiaoxu's data.
      adb(['shell', 'dumpsys', 'gfxinfo', referencePackage, 'reset'], false, true);
      const referenceReady = await rl.question(
        '\n请打开北化课表原版，停在与 ' + name +
        ' 对应的页面并完成相同的交互；按 Enter 采集原版，输入 skip 跳过：');
      if (referenceReady.trim().toLowerCase() === 'skip') {
        metadata.referenceScenes.push({ name, captured: false, reason: 'manually_skipped' });
      } else {
        const referenceForegroundVerified = isTargetInForeground(referencePackage);
        if (!referenceForegroundVerified) {
          console.log('原版应用不在前台：保存原始截图，但不计入有效原版对照证据。');
        }
        const refPng = adb(['exec-out', 'screencap', '-p'], true);
        if (refPng.length < 100 || !refPng.subarray(0, 8).equals(
          Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))) {
          throw new Error('北化原版截图不是有效 PNG：' + name);
        }
        const refImageName = 'buct-original-' + name + '.png';
        const refGfxName = 'buct-original-' + name + '.gfxinfo.txt';
        fs.writeFileSync(path.join(output, refImageName), refPng);
        const refGfx = adb(['shell', 'dumpsys', 'gfxinfo', referencePackage], false, true);
        fs.writeFileSync(path.join(output, refGfxName), refGfx, 'utf8');
        const refTotal = refGfx.match(/Total frames rendered:\s*(\d+)/i);
        const refJanky = refGfx.match(/Janky frames:\s*(\d+)/i);
        const originalTotal = refTotal ? Number(refTotal[1]) : null;
        const originalJanky = refJanky ? Number(refJanky[1]) : null;
        const validOriginalFrames = Number.isInteger(originalTotal) && originalTotal > 0 &&
          Number.isInteger(originalJanky) && originalJanky >= 0 && originalJanky <= originalTotal;
        metadata.referenceScenes.push({
          name,
          captured: true,
          foregroundVerified: referenceForegroundVerified,
          png: refImageName,
          frameDump: refGfxName,
          totalFrames: originalTotal,
          jankyFrames: originalJanky,
          jankyPercent: validOriginalFrames ?
            Math.round(10000 * originalJanky / originalTotal) / 100 : null,
          frameDuration: frameDurationPercentiles(refGfx),
          pageReady: referenceForegroundVerified ? await readPageTiming(rl, name, '原版') : null,
          frameSampleUsable: validOriginalFrames && originalTotal >= 30,
          screenshotSha256: crypto.createHash('sha256').update(refPng).digest('hex')
        });
        console.log('已保存原版对照：' + refImageName + '；未自动判断视觉是否一致。');
      }
    }
  }
} finally {
  rl.close();
  // A screenshot collector is not a visual comparator. Missing the essential
  // reference scenes must never be reported as a complete QA evidence set.
  const captured = new Set(metadata.scenes
    .filter(x => x.captured && x.foregroundVerified).map(x => x.name));
  metadata.missingReferenceScenes =
    metadata.requiredReferenceScenes.filter(x => !captured.has(x));
  // Only representative motion scenes count toward fluidity evidence.
  // An absent or tiny gfxinfo sample must be marked as missing, not smooth.
  const motionScenes = ['12-glass-rapid-tabs', '13-glass-drag-return', '14-glass-course-content'];
  metadata.performanceSamples = motionScenes.map(name => {
    const scene = metadata.scenes.find(x => x.name === name);
    return {
      name,
      sampled: Boolean(scene?.captured && scene.foregroundVerified && scene.frameSampleUsable),
      totalFrames: scene?.totalFrames ?? null,
      jankyFrames: scene?.jankyFrames ?? null,
      jankyPercent: scene?.jankyPercent ?? null,
      frameDuration: scene?.frameDuration ?? null,
      pageReady: scene?.pageReady ?? null
    };
  });
  metadata.performanceEvidenceComplete = metadata.performanceSamples.every(x => x.sampled);
  metadata.sameDevicePerformance = motionScenes.map(name => {
    const local = metadata.scenes.find(x => x.name === name);
    const original = metadata.referenceScenes.find(x => x.name === name);
    const comparable = Boolean(local?.foregroundVerified && local.frameSampleUsable && original?.foregroundVerified && original.frameSampleUsable);
    const delta = (a, b) => comparable && Number.isFinite(a) && Number.isFinite(b) ? Math.round((a-b)*100)/100 : null;
    return { name, comparable,
      xiaoxu: local ? { jankyPercent: local.jankyPercent, frameDuration: local.frameDuration, pageReady: local.pageReady } : null,
      reference: original ? { jankyPercent: original.jankyPercent, frameDuration: original.frameDuration, pageReady: original.pageReady } : null,
      deltaJankPercentagePoints: delta(local?.jankyPercent, original?.jankyPercent),
      deltaP95Ms: delta(local?.frameDuration?.p95Ms, original?.frameDuration?.p95Ms),
      deltaPageReadyMedianMs: delta(local?.pageReady?.medianMs, original?.pageReady?.medianMs)
    };
  });
  metadata.sameDeviceFrameEvidenceComplete = Boolean(referencePackage) && metadata.sameDevicePerformance.every(x => x.comparable &&
    Number.isFinite(x.xiaoxu?.frameDuration?.p95Ms) && Number.isFinite(x.reference?.frameDuration?.p95Ms));
  metadata.pageReadyEvidence = 'manual_video_measurements_only; not independently verified';
  metadata.baselineEvidenceComplete = upstreamBaseline ? (matchingBaselineVersions === true && metadata.sameDeviceFrameEvidenceComplete && metadata.missingReferenceScenes.length === 0 && installedMatchesLocal === true && declaredCommitLooksLikeSha) : null;
  metadata.baselineVerdict = upstreamBaseline ? 'manual_same_device_review_required; no automatic parity claim' : null;
  const capturedOriginal = new Set(metadata.referenceScenes
    .filter(x => x.captured && x.foregroundVerified).map(x => x.name));
  metadata.referenceMissingScenes = referencePackage
    ? referenceSceneNames.filter(name => !capturedOriginal.has(name)) : [];
  metadata.referenceComparisonComplete = referencePackage
    ? metadata.referenceMissingScenes.length === 0 : false;
  metadata.referenceComparisonVerdict = 'requires_manual_side_by_side_review';
  metadata.performanceVerdict = 'requires_manual_frame_and_visual_review';
  metadata.result = metadata.missingReferenceScenes.length > 0
    ? 'incomplete_reference_screenshots'
    : (!declaredCommitLooksLikeSha || installedMatchesLocal !== true)
      ? 'unverified_installed_build'
      : referencePackage && !metadata.referenceComparisonComplete
        ? 'incomplete_reference_comparison'
        : upstreamBaseline && !metadata.baselineEvidenceComplete
          ? 'incomplete_baseline_evidence' : 'pending_manual_comparison';
  fs.writeFileSync(path.join(output, 'manifest.json'),
    JSON.stringify(metadata, null, 2) + '\n', 'utf8');
}
console.log('\n采集完成：' + output);
console.log('截图采集状态：' + metadata.result);
if (metadata.result === 'unverified_installed_build') {
  console.log('无法核实已安装包与本机 QA APK 一致：请提供 --apk 和 --commit，并检查 adb 读取权限。');
}
console.log('请与北化课表原图逐页人工对照；数据来源和登录态需本人独立确认。');
