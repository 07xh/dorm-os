import test from "node:test";
import assert from "node:assert/strict";
import { isSafeHttpsUrl, normalizeLegacyBackup, parseSceneLines, validateScene } from "./scene-schema.js";

const id = "123e4567-e89b-12d3-a456-426614174000";
const b64 = value => Buffer.from(value, "utf8").toString("base64");
const decode = value => Buffer.from(value, "base64").toString("utf8");

test("scene action allowlist rejects shell injection", () => {
  assert.equal(validateScene({id, name:"Wi-Fi", action:"wifi-status", target:""}), true);
  assert.equal(validateScene({id, name:"bad", action:"shell", target:"id"}), false);
  assert.equal(validateScene({id, name:"bad", action:"wifi-status", target:"reboot"}), false);
});

test("HTTPS URL validation rejects credentials and sensitive parameters", () => {
  assert.equal(isSafeHttpsUrl("https://example.org/course"), true);
  assert.equal(isSafeHttpsUrl("http://example.org"), false);
  assert.equal(isSafeHttpsUrl("https://user:pass@example.org"), false);
  assert.equal(isSafeHttpsUrl("https://example.org/?access_token=x"), false);
  assert.equal(isSafeHttpsUrl("https://example.org/?access%5Ftoken=x"), false);
  assert.equal(isSafeHttpsUrl("https://localhost/"), false);
});

test("legacy backup imports atomically with supported action mapping", () => {
  const backup = {
    type:"dorm-os-nfc-profiles",
    version:1,
    profiles:[
      {id,name:"Ncwu 检查",action:"ncwu",target:""},
      {id:"123e4567-e89b-12d3-a456-426614174001",name:"学习模式",action:"scene",target:"study"}
    ]
  };
  const result = normalizeLegacyBackup(backup);
  assert.equal(result.length, 2);
  assert.equal(result[0].action, "ncwu-check");
  assert.equal(result[1].action, "dorm-scene");
  assert.equal(normalizeLegacyBackup({...backup, profiles:[...backup.profiles, backup.profiles[0]]}), null);
});

test("TSV parser preserves Chinese labels and rejects duplicate or malformed rows", () => {
  const source = [
    "DORM_NFC_SCENES_V1",
    [id,"ncwu-check","",b64("校园网检查")].join("|")
  ].join("\n") + "\n";
  const scenes = parseSceneLines(source, decode);
  assert.equal(scenes[0].name, "校园网检查");
  assert.throws(() => parseSceneLines(source + source.split("\n")[1] + "\n", decode));
});
