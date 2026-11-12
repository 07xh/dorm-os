"""Real cross-process proof that the digest CLI shares the collector mutex.

The digest is a second Python entry point (scheduled-digest.ps1 runs
sync_digest.py directly). Without the shared named mutex, a scheduled digest
and a manual sync could read/rewrite the audit journal and run log at the same
time. These tests launch actual child processes; no mocks and no cloud calls.
"""
from __future__ import annotations

import os
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PY = sys.executable
DIGEST = ROOT / "sync_digest.py"
COLLECTOR = ROOT / "collector.py"

# Hold the very same mutex class the collector uses, in a separate process.
_HOLDER = """\
import importlib.util, sys, time
s = importlib.util.spec_from_file_location('c', sys.argv[1])
m = importlib.util.module_from_spec(s)
s.loader.exec_module(m)
with m.SingleInstance(sys.argv[2]):
    print('HOLD', flush=True)
    time.sleep(float(sys.argv[3]))
"""


@unittest.skipUnless(sys.platform == "win32", "Windows named mutex")
class DigestSingleInstanceTests(unittest.TestCase):
    def _isolated(self, home: Path, lock: str) -> dict:
        # Path.home() resolves from USERPROFILE on Windows, so the digest and
        # the collector both write inside the temporary directory only.
        return {**os.environ, "USERPROFILE": str(home), "XIAOXU_LOCK_NAME": lock}

    def _hold(self, env: dict, lock: str, seconds: float):
        proc = subprocess.Popen(
            [PY, "-c", _HOLDER, str(COLLECTOR), lock, str(seconds)],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, env=env,
        )
        line = proc.stdout.readline().strip()
        # Never read stderr while waiting for HOLD: reading it blocks until the
        # holder exits, which would release the mutex before the probe starts.
        proc.stdout.close()
        proc.stderr.close()
        if line != "HOLD":
            self.fail("锁持有进程未启动")
        return proc

    def _render(self, env: dict):
        return subprocess.Popen(
            [PY, str(DIGEST), "render"],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, env=env,
        )

    def test_two_digests_wait_for_the_collector_lock(self):
        with tempfile.TemporaryDirectory() as tmp:
            home = Path(tmp)
            lock = "Local\\XiaoxuDigestLockTest-" + os.urandom(4).hex()
            env = self._isolated(home, lock)
            reports = home / ".wechat-cli" / "daily-reports"
            holder = self._hold(env, lock, 2.0)
            try:
                started = time.monotonic()
                procs = [self._render(env) for _ in range(2)]
                time.sleep(0.5)
                # While the collector lock is held, no report may be written.
                during = list(reports.glob("*.md")) if reports.exists() else []
                self.assertEqual(during, [], "digest bypassed the shared mutex")
                elapsed = []
                for proc in procs:
                    out, err = proc.communicate(timeout=30)
                    elapsed.append(time.monotonic() - started)
                    self.assertEqual(proc.returncode, 0, err)
                    self.assertIn("日报", out)
            finally:
                holder.wait(timeout=10)
            self.assertEqual(len(list(reports.glob("*.md"))), 1)
            self.assertTrue(all(value >= 2.0 for value in elapsed),
                            "both digest processes must be serialized: %r" % elapsed)

    def test_digest_renders_normally_without_a_holder(self):
        with tempfile.TemporaryDirectory() as tmp:
            home = Path(tmp)
            lock = "Local\\XiaoxuDigestLockTest-" + os.urandom(4).hex()
            env = self._isolated(home, lock)
            proc = self._render(env)
            out, err = proc.communicate(timeout=30)
            self.assertEqual(proc.returncode, 0, err)
            reports = list((home / ".wechat-cli" / "daily-reports").glob("*.md"))
            self.assertEqual(len(reports), 1)
            text = reports[0].read_text(encoding="utf-8")
            self.assertEqual(sum(1 for line in text.splitlines()
                                 if line.startswith("## ") and line[3:4].isdigit()), 6)


if __name__ == "__main__":
    unittest.main()
