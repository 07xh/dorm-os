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
    '  --apk <file>    记录本机 APK SHA256，不会自动安装',
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
const metadata = {
  capturedAt: new Date().toISOString(),
  device: { brand, model, deviceCode, androidRelease: release, api: sdk },
  display: {
    size: adb(['shell', 'wm', 'size'], false, true),
    density: adb(['shell', 'wm', 'density'], false, true)
  },
  app: { packageName, installedPath: installed.split('\n')[0], gitCommit, apkSha256 },
  source: 'physical-device-adb',
  note: '截图为用户自行导航的真实本机画面；不代表真实学校数据核验通过',
  result: 'pending_manual_comparison',
  scenes: []
};
const steps = [
  ['01-home', '首页：核对标题、课程列表、任务卡、留白与底部导航；切到其他页再返回'],
  ['02-schedule', '课表：核对星期条、周次、课程颜色、纵横滚动及返回'],
  ['03-schedule-settings', '课表设置弹层：核对位置、周/日切换、字号和完成按钮；不要修改真实学期'],
  ['04-tasks', '任务中心：核对三态数量、来源切换、长列表、搜索及返回'],
  ['05-task-detail', '任务详情：打开已有一条任务核对原生详情；不要修改完成状态'],
  ['06-messages', '消息中心：核对官方通知/微信审核来源切换、列表及返回'],
  ['07-message-detail', '消息详情：核对官方公告弹层与返回；无真实公告可跳过'],
  ['08-glass-rest', '北化玻璃导航静止态：与原版 APK 核对轨道轮廓、透明高光、图标和选中透镜'],
  ['09-glass-rapid-tabs', '连续快速切换五个底栏标签：检查玻璃滑块不跳位、不闪白、不残留放大状态；记录帧统计'],
  ['10-glass-drag-return', '从当前标签拖动到相邻标签再拖回并松手：检查自然回弹和无重复页面刷新'],
  ['11-glass-course-content', '在有课程色块的课表上移动底栏滑块：检查文字/背景折射无脏灰圈和错位采样'],
  ['12-glass-gesture-inset', '使用 Android 16 全屏手势返回与横竖屏切换：确认底栏可点区域和系统安全区无重叠']
];
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
    metadata.scenes.push({
      name, captured: true,
      png: name + '.png', frameDump: name + '.gfxinfo.txt',
      totalFrames: total ? Number(total[1]) : null,
      jankyFrames: janky ? Number(janky[1]) : null,
      jankyFramePercent: total && janky && Number(total[1]) > 0
        ? Math.round(Number(janky[1]) * 10000 / Number(total[1])) / 100 : null,
      screenshotSha256: crypto.createHash('sha256').update(png).digest('hex')
    });
    console.log('已保存 ' + name + '.png；未根据截图推定验收通过。');
  }
} finally {
  rl.close();
  fs.writeFileSync(path.join(output, 'manifest.json'),
    JSON.stringify(metadata, null, 2) + '\n', 'utf8');
}
console.log('\n采集完成：' + output);
console.log('请与北化课表原图逐页人工对照；数据来源和登录态需本人独立确认。');
