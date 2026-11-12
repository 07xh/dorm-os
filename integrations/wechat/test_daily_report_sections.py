"""Six-section daily report tests; local fixtures only, no cloud or chat data."""
from datetime import datetime, timezone
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase

import sync_digest as digest

CLOCK = datetime(2026, 11, 9, 15, 0, 0, tzinfo=timezone.utc)
DAY = "2026-11-09"
RECORDS = [
    {"candidateKey": "a" * 43, "kind": "homework",
     "deadline": {"precision": "date", "date": "2026-11-10"}},
    {"candidateKey": "b" * 43, "kind": "signup",
     "deadline": {"precision": "unknown"}},
]
SECTIONS = [
    "## 1. 今日新增候选", "## 2. 待审核任务", "## 3. 已确认完成的任务",
    "## 4. 已取消、改期任务", "## 5. 最近三天截止任务",
    "## 6. 同步失败及漏读风险",
]


class DailyReportSectionsTests(TestCase):
    def _audit(self, tmp):
        path = Path(tmp) / "audit.json"
        digest.record_success(RECORDS, "rules", at=CLOCK, path=path)
        return digest.read_audit(path)

    def test_all_six_sections_and_no_fabricated_status(self):
        with TemporaryDirectory() as tmp:
            result = digest.render_report(self._audit(tmp), DAY, failures=[])
        for section in SECTIONS:
            self.assertIn(section, result)
        self.assertIn("不得判断任何任务已完成", result)
        self.assertIn("没有证据", result)
        self.assertIn("可确认 0 条", result)

    def test_failure_counts_come_from_real_log_entries(self):
        with TemporaryDirectory() as tmp:
            failures = [
                {"at": "2026-11-09T22:00:12+08:00", "command": "sync", "error": "x"},
                {"at": "2026-11-09T22:00:13+08:00", "source": "scheduler",
                 "command": "sync", "error": "y"},
                {"at": "2026-11-10T09:00:00+08:00", "command": "sync", "error": "other day"},
            ]
            result = digest.render_report(self._audit(tmp), DAY, failures=failures[:2])
        self.assertIn("采集 1，调度 1", result)
        self.assertIn("最后一次 22:00:13", result)
        self.assertIn("漏读风险", result)

    def test_unreadable_log_is_unknown_not_zero(self):
        with TemporaryDirectory() as tmp:
            result = digest.render_report(self._audit(tmp), DAY, failures=None)
        self.assertIn("无法统计失败次数", result)
        self.assertNotIn("当日本地失败记录", result)

    def test_read_failures_filters_days_and_bad_lines(self):
        with TemporaryDirectory() as tmp:
            path = Path(tmp) / "run.jsonl"
            path.write_text("\n".join([
                "not json",
                '{"at":"2026-11-09T21:30:43+08:00","command":"sync","error":"timeout"}',
                '{"at":"2026-11-10T08:00:00+08:00","command":"sync","error":"next day"}',
                '{"at":"2026-11-09T21:31:00+08:00","source":"scheduler","error":"retry"}',
            ]), encoding="utf-8")
            self.assertEqual(len(digest.read_failures(DAY, path)), 2)
            self.assertEqual(digest.read_failures(DAY, Path(tmp) / "missing.jsonl"), [])

    def test_report_never_leaks_keys_or_titles(self):
        with TemporaryDirectory() as tmp:
            result = digest.render_report(self._audit(tmp), DAY, failures=[])
        self.assertNotIn("a" * 43, result)
        self.assertNotIn("b" * 43, result)

    def test_no_evidence_report_still_has_six_sections(self):
        result = digest.render_report({"schema": digest.SCHEMA, "days": {}}, DAY, failures=[])
        for section in SECTIONS:
            self.assertIn(section, result)
        self.assertIn("不能据此判断", result)
        self.assertNotIn("云端确认的同步运行", result)
