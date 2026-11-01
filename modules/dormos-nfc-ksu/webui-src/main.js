import { exec } from "kernelsu";

const MODULE_DIR = "/data/adb/modules/dormos_nfc_actions";
const CHECK_COMMAND =
  "if [ -x " + MODULE_DIR + "/run.sh ] && [ -x " + MODULE_DIR + "/action.sh ]; then " +
  "printf 'DORM_MODULE=ready\\n'; else printf 'DORM_MODULE=missing\\n'; exit 1; fi";
const WIFI_STATUS_COMMAND =
  "/system/bin/sh " + MODULE_DIR + "/run.sh wifi-status";

const output = document.getElementById("output");
const apiState = document.getElementById("api-state");
const resultBadge = document.getElementById("result-badge");
const checkButton = document.getElementById("check-btn");
const runButton = document.getElementById("run-btn");
let busy = false;

function setBusy(value) {
  busy = value;
  checkButton.disabled = value;
  runButton.disabled = value;
  checkButton.textContent = value ? "正在执行…" : "检查模块";
  runButton.textContent = value ? "正在执行…" : "运行只读诊断";
}

function showResult(label, detail, success) {
  resultBadge.textContent = label;
  resultBadge.className = "result-badge " + (success ? "good" : "bad");
  output.textContent = detail || "命令已结束，没有返回文本。";
}

async function executeFixed(kind) {
  if (busy) return;
  const command = kind === "check" ? CHECK_COMMAND :
    kind === "wifi-status" ? WIFI_STATUS_COMMAND : null;
  if (!command) {
    showResult("动作被拒绝", "不支持的固定动作。", false);
    return;
  }

  setBusy(true);
  output.textContent = "正在通过 KernelSU 执行固定只读命令…";
  try {
    const result = await exec(command);
    const errno = Number(result.errno);
    const stdout = String(result.stdout || "");
    const stderr = String(result.stderr || "");
    const combined = [stdout.trim(), stderr.trim()].filter(Boolean).join("\n");
    const success = errno === 0 &&
      (kind === "check"
        ? stdout.includes("DORM_MODULE=ready")
        : stdout.includes("DORM_KSU=ok") && stdout.includes("ACTION=wifi-status"));
    apiState.textContent = success ? "可用" : "已响应";
    showResult(success ? "执行成功" : "未通过校验",
      combined + "\n\n退出码：" + errno, success);
  } catch (error) {
    apiState.textContent = "不可用";
    showResult("执行失败", String(error), false);
  } finally {
    setBusy(false);
  }
}

checkButton.addEventListener("click", () => executeFixed("check"));
runButton.addEventListener("click", () => executeFixed("wifi-status"));
