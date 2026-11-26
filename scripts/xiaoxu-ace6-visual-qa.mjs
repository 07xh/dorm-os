#!/usr/bin/env node
/**
 * 校序 · 一加 Ace 6 Android 16/17 原版课表迁移视觉与流畅度证据采集。
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
    '  --package <id>  默认 io.github.x07xh.xiaoxu（同包名候选）；调试隔离包使用 .northui',
    '  --serial <id>   多台 adb 设备时指定真实手机序列号',
    '  --apk <file>    验证本机 QA APK 与手机实际安装 APK 的 SHA256，不会自动安装',
    '  --commit <sha>  记录与截图对应的 GitHub 源码提交',
    '  --reference-package <id>  可选：同一台 Ace 6 上北化课表原版包名，用于对照截图和帧数据',
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
const packageName = option('--package', 'io.github.x07xh.xiaoxu');
const serial = option('--serial');
const gitCommit = option('--commit', 'UNVERIFIED');
const apkPath = option('--apk');
const referencePackage = option('--reference-package');
const referenceSceneNames = ['02-schedule', '03-course-detail', '04-schedule-settings', '05-schedule-day', '06-course-editor', '07-navigation-transitions', '08-scroll-performance'];
const referenceSceneSet = new Set(referenceSceneNames);
if (!/^io\.github\.x07xh\.xiaoxu(?:\.(?:unifiedqa|northui))?$/.test(packageName)) {
  throw new Error('只能采集校序正式包、North UI 隔离包或统一 UI 隔离 QA 包');
}
if (referencePackage && (!/^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$/.test(referencePackage) || referencePackage === packageName)) {
  throw new Error('参考应用包名格式错误，或与当前校序包名相同');
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
  throw new Error('设备不是受支持的 Android 16/17：API=' + sdk + ', release=' + release);
}
const referenceInstalled = referencePackage ? adb(['shell', 'pm', 'path', referencePackage], false, true) : '';
if (referencePackage && !referenceInstalled.includes('package:')) {
  throw new Error('参考应用包名未安装：' + referencePackage + '；请先核对包名');
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
  referenceApp: referencePackage ? {
    packageName: referencePackage,
    installedPath: referenceInstalled.split('\n')[0],
    originalVersionVerified: false,
    note: '参考 APK 版本须人工核对；包名不能代替版本证据'
  } : null,
  referenceScenes: [],
  source: 'physical-device-adb',
  note: '截图为用户自行导航的真实本机画面；不代表真实学校数据核验通过',
  result: 'pending_capture',
  requiredReferenceScenes: ['02-schedule', '03-course-detail', '04-schedule-settings', '05-schedule-day', '06-course-editor', '07-navigation-transitions', '08-scroll-performance'], 
  scenes: []
};
const steps = [
  ['01-home', '首页与根导航：核对原版主页面、底部导航、图标与安全区；切换到课表再返回'],
  ['02-schedule', '周课表：核对周次、星期条、课程卡、颜色、滚动与横向切周；不要更改真实课程'],
  ['03-course-detail', '课程详情：点击本人已有课程，核对原版底部抽屉位置、内容层级、教师/周次、提醒状态和关闭交互；不要更改提醒'],
  ['04-schedule-settings', '课表设置：从原版入口打开，核对分组、节次时间、学期日期和周/日视图设置；不要修改真实学期'],
  ['05-schedule-day', '日视图：仅切换视图并完成，核对真实单日课程与返回；结束后恢复原视图，不修改课程或学期'],
  ['06-course-editor', '课程编辑：打开已有课程的编辑页，核对原版字段、星期/节次/周次选择和取消返回；截图后取消，不保存修改'],
  ['07-navigation-transitions', '导航转场：快速在原版主导航各页面之间切换，再返回课表；观察滑动、淡入、状态保留、返回和底栏同步；采集帧统计'],
  ['08-scroll-performance', '课表性能：横向切周、上下滚动、打开再关闭课程详情、进入设置再返回；连续操作后停在课表，采集帧统计']
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
    if (referencePackage && referenceSceneSet.has(name)) {
      // Capture comparable original UI on this SAME physical device. Do not
      // auto-control the original app or mix its counters with Xiaoxu's data.
      adb(['shell', 'dumpsys', 'gfxinfo', referencePackage, 'reset'], false, true);
      const referenceReady = await rl.question(
        '\n请打开参考原版应用，停在与 ' + name +
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
        const refImageName = 'reference-original-' + name + '.png';
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
  const motionScenes = ['07-navigation-transitions', '08-scroll-performance'];
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
        ? 'incomplete_reference_comparison' : 'pending_manual_comparison';
  fs.writeFileSync(path.join(output, 'manifest.json'),
    JSON.stringify(metadata, null, 2) + '\n', 'utf8');
}
console.log('\n采集完成：' + output);
console.log('截图采集状态：' + metadata.result);
if (metadata.result === 'unverified_installed_build') {
  console.log('无法核实已安装包与本机 QA APK 一致：请提供 --apk 和 --commit，并检查 adb 读取权限。');
}
console.log('请人工核对原版页面布局、导航转场和帧统计；模拟器数据不得替代 Ace 6 真机证据。');
