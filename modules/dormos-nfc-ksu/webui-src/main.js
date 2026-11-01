import { exec, toast } from "kernelsu";
import { SCENE_HEADER, MAX_SCENES, validateScene, normalizeLegacyBackup, parseSceneLines } from "./scene-schema.js";

const RUN = "/data/adb/modules/dormos_nfc_actions/run.sh";
const $ = id => document.getElementById(id);
const state = { scenes: [], busy: false, editingId: null };

function encodeBase64(value) {
  const bytes = new TextEncoder().encode(value);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}
function decodeBase64(value) {
  const binary = atob(value);
  const bytes = Uint8Array.from(binary, ch => ch.charCodeAt(0));
  return new TextDecoder("utf-8", {fatal:true}).decode(bytes);
}
function safeArg(value) {
  if (typeof value !== "string" || !/^[A-Za-z0-9_./+=-]*$/.test(value)) throw new Error("参数不符合安全字符集");
  return value;
}
async function runModule(args) {
  return exec("/system/bin/sh " + RUN + " " + args.map(safeArg).join(" "));
}
function setBusy(value) {
  state.busy = value;
  document.querySelectorAll("button").forEach(button => { button.disabled = value; });
}
function setOutput(value, good = true) {
  $("output").textContent = value || "命令已完成，没有返回文本。";
  $("result").textContent = good ? "完成" : "需要处理";
  $("result").className = "pill " + (good ? "good" : "warn");
}
function sceneActionLabel(action) {
  return ({ "ncwu-check":"Ncwu 只读检查", "wifi-status":"Wi-Fi 网卡状态", "open-url":"打开 HTTPS 网页", "dorm-scene":"DORM OS 场景" })[action] || action;
}
function renderScenes() {
  const host = $("scene-list");
  host.replaceChildren();
  if (!state.scenes.length) {
    const empty = document.createElement("p");
    empty.className = "empty";
    empty.textContent = "还没有场景。创建一个场景后即可写入 NFC 标签。";
    host.append(empty);
    return;
  }
  for (const scene of state.scenes) {
    const card = document.createElement("article");
    card.className = "scene";
    const info = document.createElement("div");
    info.className = "scene-info";
    const name = document.createElement("strong");
    name.textContent = scene.name;
    const meta = document.createElement("span");
    meta.textContent = sceneActionLabel(scene.action) + " · " + scene.id.slice(0, 8);
    info.append(name, meta);
    const actions = document.createElement("div");
    actions.className = "scene-actions";
    const add = (label, action, secondary = false) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = secondary ? "small secondary" : "small";
      button.textContent = label;
      button.addEventListener("click", () => action(scene));
      actions.append(button);
    };
    add("写入标签", writeTag);
    add("运行", runScene, true);
    add("复制 ID", copyId, true);
    add("编辑", editScene, true);
    add("删除", deleteScene, true);
    card.append(info, actions);
    host.append(card);
  }
}
function renderTarget() {
  const action = $("scene-action").value;
  $("target-row").hidden = action !== "open-url" && action !== "dorm-scene";
  $("url-label").hidden = action !== "open-url";
  $("scene-label").hidden = action !== "dorm-scene";
  $("scene-target").hidden = action === "dorm-scene";
  $("scene-target-select").hidden = action !== "dorm-scene";
}
function resetForm() {
  state.editingId = null;
  $("scene-id").value = "";
  $("scene-name").value = "";
  $("scene-action").value = "ncwu-check";
  $("scene-target").value = "";
  $("scene-target-select").value = "study";
  $("save-scene").textContent = "创建场景";
  $("cancel-edit").hidden = true;
  renderTarget();
}
function fallbackUuid() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  const hex = [...bytes].map(x => x.toString(16).padStart(2, "0")).join("");
  return hex.slice(0,8)+"-"+hex.slice(8,12)+"-"+hex.slice(12,16)+"-"+hex.slice(16,20)+"-"+hex.slice(20);
}
function readForm() {
  const action = $("scene-action").value;
  const scene = {
    id: state.editingId || (crypto.randomUUID ? crypto.randomUUID() : fallbackUuid()),
    name: $("scene-name").value.trim(),
    action,
    target: action === "dorm-scene" ? $("scene-target-select").value : action === "open-url" ? $("scene-target").value.trim() : ""
  };
  if (!validateScene(scene)) throw new Error("配置无效：名称最多 40 字；动作必须在白名单中；网页必须是安全 HTTPS 地址。");
  return scene;
}
function serializeScenes(scenes) {
  const lines = [SCENE_HEADER];
  for (const scene of scenes) {
    if (!validateScene(scene)) throw new Error("拒绝保存无效场景");
    lines.push([scene.id, scene.action, scene.target ? encodeBase64(scene.target) : "", encodeBase64(scene.name)].join("|"));
  }
  return lines.join("\n") + "\n";
}
async function saveScenes(next) {
  if (next.length > MAX_SCENES) throw new Error("最多支持 32 个场景");
  const result = await runModule(["--save-b64", encodeBase64(serializeScenes(next))]);
  if (result.errno !== 0 || !String(result.stdout).includes("DORM_CONFIG=saved")) throw new Error(String(result.stderr || result.stdout || "保存配置失败"));
  state.scenes = next;
  renderScenes();
  $("scene-count").textContent = String(next.length);
}
async function refresh() {
  setBusy(true);
  try {
    const [status, list, logs] = await Promise.all([runModule(["--status"]), runModule(["--list"]), runModule(["--logs"])]);
    if (status.errno !== 0 || list.errno !== 0) throw new Error(status.stderr || list.stderr || "模块读取失败");
    const statusText = String(status.stdout || "");
    $("module-state").textContent = statusText.includes("DORM_MODULE=ready") ? "已安装" : "不可用";
    $("nfc-enabled").checked = statusText.includes("NFC_ENABLED=1");
    $("scene-count").textContent = (statusText.match(/SCENE_COUNT=(\d+)/) || [,"0"])[1];
    $("receiver-state").textContent = (statusText.match(/NFC_RECEIVER=(\w+)/) || [,"unknown"])[1] === "installed" ? "已安装" : "未安装";
    $("background-state").textContent = "无常驻服务";
    state.scenes = parseSceneLines(String(list.stdout || ""), decodeBase64);
    renderScenes();
    $("logs").textContent = String(logs.stdout || "暂无执行记录。").trim();
    setOutput("模块状态已刷新。\n" + statusText.trim(), true);
  } catch (error) { setOutput(String(error), false); }
  finally { setBusy(false); }
}
async function writeTag(scene) {
  if (!confirm("写卡会覆盖标签原有 NDEF 内容，只写入场景编号。请确认使用自己的可写标签。")) return;
  setBusy(true);
  try {
    const result = await runModule(["--write-tag", scene.id]);
    setOutput(String(result.stdout || result.stderr), result.errno === 0);
    if (result.errno === 0) toast("已启动 NFC 写卡接收器，请将标签靠近手机。");
  } catch (error) { setOutput(String(error), false); }
  finally { setBusy(false); }
}
async function runScene(scene) {
  setBusy(true);
  try {
    const result = await runModule(["--run", scene.id]);
    setOutput(String(result.stdout || result.stderr), result.errno === 0);
    await refreshLogsOnly();
  } catch (error) { setOutput(String(error), false); }
  finally { setBusy(false); }
}
async function copyId(scene) {
  try { await navigator.clipboard.writeText(scene.id); toast("场景 ID 已复制。"); }
  catch (_) { setOutput("无法访问剪贴板，请手动复制：" + scene.id, false); }
}
function editScene(scene) {
  state.editingId = scene.id;
  $("scene-name").value = scene.name;
  $("scene-action").value = scene.action;
  $("scene-target").value = scene.action === "open-url" ? scene.target : "";
  $("scene-target-select").value = scene.action === "dorm-scene" ? scene.target : "study";
  $("save-scene").textContent = "保存修改";
  $("cancel-edit").hidden = false;
  renderTarget();
  $("scene-form").scrollIntoView({behavior:"smooth",block:"center"});
}
async function deleteScene(scene) {
  if (!confirm("删除场景「" + scene.name + "」？已写入的实体标签不会被擦除，但此手机将不再执行它。")) return;
  setBusy(true);
  try { await saveScenes(state.scenes.filter(item => item.id !== scene.id)); setOutput("场景已删除。实体 NFC 标签未改写。", true); }
  catch (error) { setOutput(String(error), false); }
  finally { setBusy(false); }
}
async function refreshLogsOnly() {
  const result = await runModule(["--logs"]);
  $("logs").textContent = String(result.stdout || result.stderr || "暂无执行记录。").trim();
}
async function toggleNfc() {
  setBusy(true);
  try {
    const value = $("nfc-enabled").checked ? "1" : "0";
    const result = await runModule(["--enable", value]);
    if (result.errno !== 0) throw new Error(result.stderr || result.stdout);
    setOutput(value === "1" ? "NFC 场景触发已启用。" : "NFC 场景触发已关闭。", true);
  } catch (error) { $("nfc-enabled").checked = false; setOutput(String(error), false); }
  finally { setBusy(false); }
}
function exportBackup() {
  const profiles = state.scenes.map(scene => ({
    id:scene.id, name:scene.name,
    action:({"ncwu-check":"ncwu","wifi-status":"ksu-wifi-status","open-url":"https","dorm-scene":"scene"})[scene.action],
    target:scene.target
  }));
  const data = JSON.stringify({type:"dorm-os-nfc-profiles",version:1,profiles}, null, 2);
  const url = URL.createObjectURL(new Blob([data], {type:"application/json"}));
  const link = document.createElement("a");
  link.href = url; link.download = "dorm-os-nfc-profiles.json"; link.click();
  URL.revokeObjectURL(url);
}
async function importBackup(file) {
  if (!file || file.size > 65536) throw new Error("备份文件超过 64 KiB 或未选择文件");
  const imported = normalizeLegacyBackup(JSON.parse(await file.text()));
  if (imported === null) throw new Error("备份校验失败：版本、动作、UUID、数量或 HTTPS 目标不安全；现有配置未改变。");
  if (!confirm("将导入 " + imported.length + " 个场景并覆盖当前 " + state.scenes.length + " 个场景？")) return;
  await saveScenes(imported);
  setOutput("已导入 " + imported.length + " 个旧版场景。", true);
}
async function clearLogs() {
  if (!confirm("清空本模块保存的执行记录？")) return;
  const result = await runModule(["--clear-logs"]);
  setOutput(String(result.stdout || result.stderr), result.errno === 0);
  await refreshLogsOnly();
}

$("scene-action").addEventListener("change", renderTarget);
$("scene-form").addEventListener("submit", async event => {
  event.preventDefault();
  if (state.busy) return;
  setBusy(true);
  try {
    const scene = readForm();
    const next = state.editingId
      ? state.scenes.map(item => item.id === state.editingId ? scene : item)
      : [...state.scenes, scene];
    if (!state.editingId && state.scenes.some(item => item.id === scene.id)) throw new Error("场景 ID 重复");
    await saveScenes(next);
    resetForm();
    setOutput("场景配置已保存。接下来可以写入自己的 NFC 标签。", true);
  } catch (error) { setOutput(String(error), false); }
  finally { setBusy(false); }
});
$("cancel-edit").addEventListener("click", resetForm);
$("nfc-enabled").addEventListener("change", toggleNfc);
$("refresh").addEventListener("click", refresh);
$("export").addEventListener("click", exportBackup);
$("import-file").addEventListener("change", async event => {
  try { await importBackup(event.target.files?.[0]); }
  catch (error) { setOutput(String(error), false); }
  finally { event.target.value = ""; }
});
$("clear-logs").addEventListener("click", clearLogs);
$("refresh-logs").addEventListener("click", refreshLogsOnly);
renderTarget();
refresh();
