"""Real cross-process concurrency tests for the single-instance guard.

No WeChat reader, no cloud call, no cursor write: every subprocess runs the
read-only `status` command, or holds/releases a test-only mutex.
"""
import os
from pathlib import Path
import subprocess
import sys
import time
from unittest import TestCase, main as ut_main

HERE = Path(__file__).parent
COLLECTOR = HERE / "collector.py"
STATE = Path.home() / ".wechat-cli" / "xiaoxu-sync-state.json"
LOCK = "Local\\XiaoxuWechatBridge-ConcurrencyTest"

HOLDER = r"""
import importlib.util, sys, time
spec = importlib.util.spec_from_file_location("bridge_holder", sys.argv[1])
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
with m.SingleInstance(sys.argv[2], int(sys.argv[3])):
    print("HOLD", flush=True)
    time.sleep(float(sys.argv[4]))
print("RELEASE", flush=True)
"""

WAITER = r"""
import importlib.util, sys
spec = importlib.util.spec_from_file_location("bridge_waiter", sys.argv[1])
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
try:
    with m.SingleInstance(sys.argv[2], int(sys.argv[3])):
        print("ACQUIRED", flush=True)
except m.BridgeError as exc:
    print("BLOCKED: " + str(exc), flush=True)
    raise SystemExit(1)
"""


def _spawn_holder(hold_seconds: float, wait_ms: int = 60_000):
    proc = subprocess.Popen(
        [sys.executable, "-c", HOLDER, str(COLLECTOR), LOCK, str(wait_ms), str(hold_seconds)],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    line = proc.stdout.readline().strip()
    proc.stdout.close()
    proc.stderr.close()
    assert line == "HOLD", "锁持有进程未启动"
    return proc


class SingleInstanceTests(TestCase):
    def test_second_terminal_waits_then_succeeds(self):
        holder = _spawn_holder(1.5)
        env = {**os.environ, "XIAOXU_LOCK_NAME": LOCK}
        before = STATE.read_bytes() if STATE.exists() else b""
        started = time.monotonic()
        proc = subprocess.run([sys.executable, str(COLLECTOR), "status"],
                              capture_output=True, text=True, env=env)
        elapsed = time.monotonic() - started
        holder.wait(timeout=10)
        self.assertEqual(proc.returncode, 0, proc.stderr)
        self.assertIn("已选", proc.stdout)
        self.assertGreaterEqual(elapsed, 1.0, "第二个终端没有等待，说明仍可并发运行")
        self.assertEqual(STATE.read_bytes() if STATE.exists() else b"", before,
                         "并发期间同步游标被改动")

    def test_two_terminals_never_run_at_once(self):
        env = {**os.environ, "XIAOXU_LOCK_NAME": LOCK}
        results = []
        procs = [subprocess.Popen([sys.executable, str(COLLECTOR), "status"],
                                  stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                  text=True, env=env) for _ in range(2)]
        for proc in procs:
            out, err = proc.communicate(timeout=30)
            results.append((proc.returncode, out, err))
        for code, out, err in results:
            self.assertEqual(code, 0, err)
            self.assertIn("已选", out)

    def test_timeout_fails_closed_without_touching_state(self):
        holder = _spawn_holder(1.5)
        before = STATE.read_bytes() if STATE.exists() else b""
        proc = subprocess.run(
            [sys.executable, "-c", WAITER, str(COLLECTOR), LOCK, "200"],
            capture_output=True, text=True)
        holder.wait(timeout=10)
        self.assertEqual(proc.returncode, 1)
        self.assertIn("BLOCKED", proc.stdout)
        self.assertIn("等待超时", proc.stdout)
        self.assertEqual(STATE.read_bytes() if STATE.exists() else b"", before)

    def test_crashed_holder_leaves_no_stale_lock(self):
        holder = _spawn_holder(30)
        holder.kill()
        holder.wait(timeout=10)
        env = {**os.environ, "XIAOXU_LOCK_NAME": LOCK}
        started = time.monotonic()
        proc = subprocess.run([sys.executable, str(COLLECTOR), "status"],
                              capture_output=True, text=True, env=env, timeout=20)
        elapsed = time.monotonic() - started
        self.assertEqual(proc.returncode, 0, proc.stderr)
        self.assertLess(elapsed, 10, "进程崩溃后残留锁阻塞了后续运行")


if __name__ == "__main__":
    ut_main(verbosity=2)
