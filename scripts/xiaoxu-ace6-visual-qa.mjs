#!/usr/bin/env node
/**
 * 校序 · 一加 Ace 6 Android 16 本机视觉证据采集。
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
    '  --package <id>  默认 io.github.x07xh.xiaoxu.unifiedqa（隔离 QA 包）',
    '  --serial <id>   多台 adb 设备时指定真实手机序列号',
    '  --apk <file>    验证本机 QA APK 与手机实际安装 APK 的 SHA256，不会自动安装',
    '  --commit <sha>  记录与截图对应的 GitHub 源码提交',
    '  --out <folder>  输出目录（默认用户主目录 Xiaoxu-Visual-QA）',
    '说明：需已安装 Android Platform Tools，手机 USB 调试并授权电脑。',
    '本程序只在本机采集屏幕；无设备、非一加 Ace 6、非 Android 16 时不出具证据。'
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
const packageName = option('--package', 'io.github.x07xh.xiaoxu.unifiedqa');
const serial = option('--serial');
const gitCommit = option('--commit', 'UNVERIFIED');
const apkPath = option('--apk');
if (!/^io\.github\.x07xh\.xiaoxu(?:\.unifiedqa)?$/.test(packageName)) {
  throw new Error('只能采集校序正式包或统一 UI 隔离 QA 包');
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
if (sdk !== 36 || release !== '16') {
  throw new Error('设备不是 Android 16：API=' + sdk + ', release=' + release);
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
const declaredCommitLooksLikeSha = /^[a-f0-9]{7,40}$/i.test(gitCommit);
const metadata = {
  capturedAt: new Date().toISOString(),
  device: { brand, model, deviceCode, androidRelease: release, api: sdk },
  display: {
    size: adb(['shell', 'wm', 'size'], false, true),
    density: adb(['shell', 'wm', 'density'], false, true)
  },
  app: {
    packageName,
    installedPath: installed.split('\n')[0],
    gitCommit,
    apkSha256,
    installedApkSha256,
    installedMatchesLocal,
    declaredCommitLooksLikeSha,
    gitCommitVerifiedAgainstRepository: false
  },
  source: 'physical-device-adb',
  note: '截图为用户自行导航的真实本机画面；不代表真实学校数据核验通过',
  result: 'pending_capture',
  requiredReferenceScenes: ['02-schedule', '03-course-detail', '04-schedule-settings', '05-schedule-day', '06-course-editor', '11-glass-rest', '12-glass-rapid-tabs', '13-glass-drag-return', '14-glass-course-content', '16-glass-dialog-gray-edge'],
  scenes: []
};
const steps = [
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
  ['15-glass-gesture-inset', 'Android 16 系统手势、底部安全区、横竖屏短屏切换：检查可点击区域、底栏遮挡和五项侧栏可达性'],
  ['16-glass-dialog-gray-edge', '从我的打开任务备份与恢复和云端同步，再打开一条任务或公告：检查居中玻璃弹窗、灰边、双重卡片、页面滚动及关闭返回；课程详情仍应由底部弹出']
];
// Screenshot validation must check which application is actually in the
// foreground, rather than accepting a PNG of Settings, launcher or another app.
function isTargetInForeground() {
  const windowDump = adb(['shell', 'dumpsys', 'window', 'windows'], false, true);
  const focus = windowDump.split(/\r?\n/).find(line => /\bmCurrentFocus\s*=/.test(line));
  if (focus) return focus.includes(packageName + '/');
  const activityDump = adb(['shell', 'dumpsys', 'activity', 'activities'], false, true);
  const resumed = activityDump.split(/\r?\n/).find(
    line => /\b(?:topResumedActivity|mResumedActivity|ResumedActivity)\s*[:=]/.test(line)
  );
  return Boolean(resumed?.includes(packageName + '/'));
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
      frameSampleUsable: validFrameSample && totalFrames >= 30,
      screenshotSha256: crypto.createHash('sha256').update(png).digest('hex')
    });
    console.log('已保存 ' + name + '.png；' +
      (jankyPercent == null ? '系统未返回可解析帧数据' :
        '观测卡顿帧 ' + jankyFrames + '/' + totalFrames + '（' + jankyPercent + '%）') +
      '；仍须与原版进行真机视觉/交互对照。');
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
      jankyPercent: scene?.jankyPercent ?? null
    };
  });
  metadata.performanceEvidenceComplete = metadata.performanceSamples.every(x => x.sampled);
  metadata.performanceVerdict = 'requires_manual_frame_and_visual_review';
  metadata.result = metadata.missingReferenceScenes.length > 0
    ? 'incomplete_reference_screenshots'
    : (!declaredCommitLooksLikeSha || installedMatchesLocal !== true)
      ? 'unverified_installed_build' : 'pending_manual_comparison';
  fs.writeFileSync(path.join(output, 'manifest.json'),
    JSON.stringify(metadata, null, 2) + '\n', 'utf8');
}
console.log('\n采集完成：' + output);
console.log('截图采集状态：' + metadata.result);
if (metadata.result === 'unverified_installed_build') {
  console.log('无法核实已安装包与本机 QA APK 一致：请提供 --apk 和 --commit，并检查 adb 读取权限。');
}
console.log('请与北化课表原图逐页人工对照；数据来源和登录态需本人独立确认。');
