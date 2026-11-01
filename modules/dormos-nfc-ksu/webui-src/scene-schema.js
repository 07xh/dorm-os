export const SCENE_HEADER = "DORM_NFC_SCENES_V1";
export const MAX_SCENES = 32;
export const ALLOWED_ACTIONS = new Set(["wifi-status", "ncwu-check", "open-url", "dorm-scene"]);
export const ALLOWED_DORM_SCENES = new Set(["study", "rest", "dev", "away"]);
const UUID = /^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/;

export function validId(id) {
  return typeof id === "string" && UUID.test(id);
}
export function isSafeHttpsUrl(raw) {
  if (typeof raw !== "string" || raw.length < 9 || raw.length > 500 || /[\u0000-\u0020\u007f]/.test(raw) || !/^[A-Za-z0-9:/?&=._#%+-]+$/.test(raw)) return false;
  let url;
  try { url = new URL(raw); } catch (_) { return false; }
  if (url.protocol !== "https:" || !url.hostname || url.username || url.password) return false;
  const host = url.hostname.toLowerCase();
  if (!host.includes(".") || host === "localhost" || host.endsWith(".local") || host.endsWith(".internal") || url.port === "0") return false;
  if (/^(127\.|10\.|169\.254\.|192\.168\.)/.test(host) || /^172\.(1[6-9]|2[0-9]|3[01])\./.test(host)) return false;
  const blocked = new Set([
    "password", "passwd", "pwd", "token", "access_token", "refresh_token",
    "id_token", "api_key", "apikey", "client_secret", "secret", "session",
    "sessionid", "sid", "auth", "authorization", "cookie", "jwt", "signature",
    "code", "credential"
  ]);
  const checkPairs = value => {
    if (!value) return true;
    const source = value.replace(/^#/, "").includes("?")
      ? value.slice(value.indexOf("?") + 1)
      : value.replace(/^#/, "");
    for (const pair of source.split(/[&;]/)) {
      const rawKey = pair.split("=")[0];
      let key;
      try { key = decodeURIComponent(rawKey.replace(/\+/g, " ")).toLowerCase().replace(/[-.]/g, "_"); }
      catch (_) { return false; }
      if (blocked.has(key) || key.endsWith("_token") || key.endsWith("_password") || key.endsWith("_secret")) return false;
    }
    return true;
  };
  return checkPairs(url.search) && checkPairs(url.hash);
}
export function validateScene(scene) {
  if (!scene || !validId(scene.id)) return false;
  if (typeof scene.name !== "string" || !scene.name.trim() || Array.from(scene.name).length > 40 || /[\u0000-\u001f\u007f]/.test(scene.name)) return false;
  if (!ALLOWED_ACTIONS.has(scene.action)) return false;
  const target = typeof scene.target === "string" ? scene.target : "";
  if (scene.action === "wifi-status" || scene.action === "ncwu-check") return target === "";
  if (scene.action === "open-url") return isSafeHttpsUrl(target);
  if (scene.action === "dorm-scene") return ALLOWED_DORM_SCENES.has(target);
  return false;
}
export function normalizeLegacyBackup(input) {
  if (!input || input.type !== "dorm-os-nfc-profiles" || input.version !== 1 ||
      !Array.isArray(input.profiles) || input.profiles.length > MAX_SCENES) return null;
  const mapping = { ncwu:"ncwu-check", "ksu-wifi-status":"wifi-status", https:"open-url", scene:"dorm-scene" };
  const result = [];
  const seen = new Set();
  for (const old of input.profiles) {
    if (!old || typeof old !== "object") return null;
    const scene = { id:old.id, name:old.name, action:mapping[old.action], target:typeof old.target === "string" ? old.target : "" };
    if (!validateScene(scene) || seen.has(scene.id)) return null;
    seen.add(scene.id);
    result.push(scene);
  }
  return result;
}
export function parseSceneLines(text, decodeBase64) {
  const lines = String(text || "").trimEnd().split("\n");
  if (lines.shift() !== SCENE_HEADER) throw new Error("场景配置头不匹配");
  const scenes = [];
  const seen = new Set();
  for (const line of lines) {
    if (!line) continue;
    const fields = line.split("|");
    if (fields.length !== 4) throw new Error("场景配置字段数量错误");
    const scene = { id:fields[0], action:fields[1], target:fields[2] ? decodeBase64(fields[2]) : "", name:decodeBase64(fields[3]) };
    if (!validateScene(scene) || seen.has(scene.id)) throw new Error("场景配置无效或存在重复 ID");
    seen.add(scene.id);
    scenes.push(scene);
  }
  if (scenes.length > MAX_SCENES) throw new Error("场景数量超过 32 个");
  return scenes;
}
