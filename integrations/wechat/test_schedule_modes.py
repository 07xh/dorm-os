"""Verify the single-task schedule: two mutually exclusive time windows.

Runs the real PowerShell guard with an injected UTC clock (no side effects),
and checks the installer sources no longer create a second scheduled task.
"""
from __future__ import annotations

import shutil
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent
GUARD = ROOT / "scheduled-run.ps1"
SCHEDULE = ROOT / "schedule.ps1"
DIGEST_SCHEDULE = ROOT / "digest-schedule.ps1"
PS = (r"C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe"
      if sys.platform == "win32" else shutil.which("powershell.exe"))


def run_guard(command: str, at_utc: str) -> str:
    out = subprocess.run(
        [PS, "-NoLogo", "-NoProfile", "-NonInteractive", "-File", str(GUARD),
         command, "-AtUtc", at_utc],
        capture_output=True, text=True, timeout=30, check=True)
    return out.stdout.strip()


@unittest.skipUnless(sys.platform == "win32" and PS and Path(PS).exists(),
                     "Windows PowerShell required")
class ScheduleModeTests(unittest.TestCase):
    def test_check_mode_boundaries(self):
        cases = {
            "2026-11-12T22:59:00Z": "SKIP",   # 06:59 Beijing
            "2026-11-12T23:00:00Z": "SYNC",   # 07:00 Beijing
            "2026-11-12T15:00:00Z": "SYNC",   # 23:00 Beijing
            "2026-11-12T15:09:00Z": "SKIP",   # 23:09 Beijing (gap)
            "2026-11-12T15:10:00Z": "DIGEST", # 23:10 Beijing
            "2026-11-12T15:59:00Z": "DIGEST", # 23:59 Beijing
            "2026-11-12T16:00:00Z": "SKIP",   # 00:00 Beijing
        }
        for at_utc, expected in cases.items():
            with self.subTest(at_utc=at_utc):
                self.assertEqual(run_guard("check-mode", at_utc), expected)

    def test_check_time_still_guards_sync_window(self):
        cases = {
            "2026-11-12T22:59:00Z": "SKIP",
            "2026-11-12T23:00:00Z": "RUN",
            "2026-11-12T15:00:00Z": "RUN",
            "2026-11-12T15:01:00Z": "SKIP",
        }
        for at_utc, expected in cases.items():
            with self.subTest(at_utc=at_utc):
                self.assertEqual(run_guard("check-time", at_utc), expected)

    def test_installer_defines_two_triggers_on_one_task(self):
        text = SCHEDULE.read_text(encoding="utf-8-sig")
        self.assertIn('-Daily -At "07:00"', text)
        self.assertIn('-Daily -At "23:10"', text)
        self.assertIn("Duration=\"PT16H1M\"", text)
        self.assertIn("$triggers=@($trigger,$digestTrigger)", text)
        self.assertNotIn('Duration="P1D"', text,
                         "all-day collection trigger must stay removed")

    def test_digest_installer_never_registers_a_second_task(self):
        text = DIGEST_SCHEDULE.read_text(encoding="utf-8-sig")
        self.assertNotIn("Register-ScheduledTask", text)
        self.assertIn("Unregister-ScheduledTask", text)

    def test_guard_renders_digest_without_sync(self):
        text = GUARD.read_text(encoding="utf-8-sig")
        self.assertIn("$digestAllowed", text)
        self.assertIn("& $python $digest render", text)
        # The digest branch must return before the sync retry loop.
        self.assertLess(text.index("& $python $digest render"),
                        text.index("for ($attempt=1;"))


if __name__ == "__main__":
    unittest.main()
