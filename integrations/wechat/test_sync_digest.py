"""Synthetic privacy and date-boundary tests; never read a WeChat database."""
from datetime import datetime, timedelta, timezone
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase
import json

import sync_digest as digest


class LocalDigestTests(TestCase):
    def setUp(self):
        self.clock = datetime(2026, 11, 9, 15, 0, 0, tzinfo=timezone.utc)
        self.records = [
            {
                "candidateKey": "a" * 43, "kind": "homework",
                "title": "私人课程作业 - should never enter local audit",
                "chatLabel": "private example class",
                "links": ["https://private.example/?id=123"],
                "deadline": {"precision": "date", "date": "2026-11-10"},
            },
            {
                "candidateKey": "b" * 43, "kind": "signup",
                "deadline": {"precision": "unknown"}
            },
        ]

    def test_success_journal_keeps_only_keys_counts_and_due_dates(self):
        with TemporaryDirectory() as tmp:
            path = Path(tmp) / "audit.json"
            digest.record_success(self.records, "rules", at=self.clock, path=path)
            digest.record_success(self.records, "rules", at=self.clock + timedelta(minutes=15), path=path)
            data = digest.read_audit(path)
            entry = data["days"]["2026-11-09"]
            self.assertEqual(entry["runs"], 2)
            self.assertEqual(len(entry["candidates"]), 2)
            self.assertEqual(entry["modeRuns"], {"rules": 2, "ai": 0})
            raw = path.read_text(encoding="utf-8")
            for secret in ("私人课程", "private example class", "private.example"):
                self.assertNotIn(secret, raw)
            self.assertEqual(entry["candidates"]["a" * 43]["due"], "2026-11-10")

    def test_day_boundary_and_overlap_do_not_inflate_new_candidates(self):
        with TemporaryDirectory() as tmp:
            path = Path(tmp) / "audit.json"
            digest.record_success(self.records[:1], "rules", at=self.clock, path=path)
            next_day = datetime(2026, 11, 9, 16, 0, 0, tzinfo=timezone.utc)
            digest.record_success(self.records, "ai", at=next_day, path=path)
            audit = digest.read_audit(path)
            self.assertEqual(len(audit["days"]["2026-11-09"]["candidates"]), 1)
            self.assertEqual(len(audit["days"]["2026-11-10"]["candidates"]), 1)
            self.assertEqual(audit["days"]["2026-11-10"]["modeRuns"]["ai"], 1)

    def test_render_mentions_only_acknowledged_candidates_and_no_completion_claim(self):
        with TemporaryDirectory() as tmp:
            path = Path(tmp) / "audit.json"
            digest.record_success(self.records, "ai", at=self.clock, path=path)
            result = digest.render_report(digest.read_audit(path), "2026-11-09")
            self.assertIn("**2** 条", result)
            self.assertIn("2026-11-10：1 条待核对候选", result)
            self.assertIn("AI 1", result)
            self.assertIn("不代表已审核任务", result)
            self.assertNotIn("私人课程", result)

    def test_no_evidence_is_not_success(self):
        result = digest.render_report({"schema": digest.SCHEMA, "days": {}}, "2026-11-09")
        self.assertIn("不能据此判断", result)
        self.assertNotIn("云端确认的同步运行", result)

    def test_corrupt_evidence_is_preserved_not_overwritten(self):
        with TemporaryDirectory() as tmp:
            path = Path(tmp) / "audit.json"
            path.write_text("{not valid json", encoding="utf-8")
            before = path.read_bytes()
            with self.assertRaises(digest.AuditError):
                digest.record_success(self.records, "rules", at=self.clock, path=path)
            self.assertEqual(path.read_bytes(), before)

    def test_daily_report_is_idempotent(self):
        with TemporaryDirectory() as tmp:
            path = Path(tmp) / "audit.json"
            directory = Path(tmp) / "daily"
            digest.record_success(self.records, "rules", at=self.clock, path=path)
            first = digest.write_report(
                at=self.clock + timedelta(minutes=10), path=path, directory=directory
            )
            self.assertEqual(first.name, "2026-11-09.md")
            existing = first.read_bytes()
            second = digest.write_report(
                at=self.clock + timedelta(minutes=35), path=path, directory=directory
            )
            self.assertEqual(first, second)
            self.assertEqual(first.read_bytes(), existing)

    def test_no_raw_text_or_keys_in_report(self):
        with TemporaryDirectory() as tmp:
            path = Path(tmp) / "audit.json"
            digest.record_success(self.records, "rules", at=self.clock, path=path)
            result = digest.render_report(digest.read_audit(path), "2026-11-09")
            self.assertNotIn("a" * 43, result)
            self.assertNotIn("私人课程", result)
            self.assertNotIn("private.example", result)

    def test_reject_invalid_candidates_and_dates(self):
        with TemporaryDirectory() as tmp:
            path = Path(tmp) / "audit.json"
            bad = [dict(self.records[0], candidateKey="not a stable ID")]
            with self.assertRaises(digest.AuditError):
                digest.record_success(bad, "rules", at=self.clock, path=path)
            bad = [dict(self.records[0], deadline={"precision": "date", "date": "2026-02-30"})]
            with self.assertRaises(digest.AuditError):
                digest.record_success(bad, "rules", at=self.clock, path=path)
            self.assertFalse(path.exists())

    def test_prunes_old_days_and_retains_current(self):
        with TemporaryDirectory() as tmp:
            path = Path(tmp) / "audit.json"
            for i in range(40):
                moment = self.clock + timedelta(days=i)
                records = [dict(self.records[0], candidateKey=f"{i:043d}")]
                digest.record_success(records, "rules", at=moment, path=path)
            audit = digest.read_audit(path)
            self.assertEqual(len(audit["days"]), digest.MAX_DAYS)
            self.assertIn((self.clock + timedelta(days=39)).astimezone(digest.CHINA).date().isoformat(), audit["days"])
