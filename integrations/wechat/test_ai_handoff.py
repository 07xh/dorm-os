"""Synthetic-only AI handoff tests: no WeChat or Cloudflare account access."""
from copy import deepcopy
from datetime import datetime, timezone, timedelta
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase
from unittest.mock import patch
import json

import ai_handoff as ai
import collector


class AiHandoffTests(TestCase):
    def setUp(self):
        self.ts = int(datetime(2026, 11, 9, 10, tzinfo=timezone.utc).timestamp())
        self.now = datetime.fromtimestamp(self.ts, timezone.utc)
        self.cfg = {"deviceId": "wxd_" + "Z" * 24, "chats": ["课程群A"]}
        self.secret = b"local-test-hmac-secret"
        self.base = {"课程群A": self.ts - 100}
        self.rows = [
            ("shardA/Msg", 101, self.ts, "新通知：今晚改到新教室见面"),
            ("shardA/Msg", 102, self.ts + 1,
             "11月10日提交实验报告，详情 https://school.example/task ，联系13812345678"),
        ]

        def reader(chat, cutoff):
            self.assertEqual(chat, "课程群A")
            yield from self.rows

        self.snapshot, self.notes = ai.build_snapshot(
            self.cfg, self.secret, self.base, reader=reader, now_ts=self.ts + 2
        )

    def decisions(self):
        return {
            "schema": ai.DECISION_SCHEMA,
            "snapshotSignature": self.snapshot["signature"],
            "tasks": [
                {"messageKey": self.snapshot["messages"][0]["messageKey"],
                 "slot": 0, "kind": "other", "title": "核对临时上课教室安排",
                 "deadline": {"precision": "unknown"}},
                {"messageKey": self.snapshot["messages"][1]["messageKey"],
                 "slot": 0, "kind": "homework",
                 "title": "提交实验报告，联系 13812345678",
                 "deadline": {"precision": "exact",
                              "date": "2026-11-10",
                              "at": "2026-11-10T17:00:00+08:00"}},
            ],
        }

    def validate(self, snapshot=None, decisions=None, **kwargs):
        return ai.validate_decisions(
            self.cfg, self.secret, self.base, snapshot or self.snapshot,
            decisions or self.decisions(), now=self.now, **kwargs
        )

    def test_ai_can_handle_non_keyword_message_without_replacing_reader(self):
        self.assertEqual(len(self.snapshot["messages"]), 2)
        self.assertIn("临时", self.snapshot["messages"][0]["text"])
        self.assertNotIn("13812345678", json.dumps(self.snapshot, ensure_ascii=False))
        self.assertIn("[电话]", self.snapshot["messages"][1]["text"])
        self.assertNotIn("https://school.example", self.snapshot["messages"][1]["text"])
        self.assertEqual(self.snapshot["messages"][1]["links"], ["https://school.example/task"])
        self.assertNotIn("all_keys", json.dumps(self.snapshot))

    def test_valid_ai_result_reuses_original_cloud_identity_and_redaction(self):
        records, next_state = self.validate()
        self.assertEqual(len(records), 2)
        self.assertEqual(next_state["课程群A"], self.ts + 1)
        self.assertNotIn("13812345678", records[1]["title"])
        self.assertEqual(records[1]["deadline"]["precision"], "exact")
        self.assertEqual(records[1]["links"], ["https://school.example/task"])
        self.assertEqual(records[1]["candidateKey"], collector.fingerprint(
            self.secret, "candidate", records[1]["messageKey"], 0))
        self.assertNotIn("text", records[1])  # raw/redacted input stays local

    def test_tampered_snapshot_and_unauthorized_group_are_rejected(self):
        tampered = deepcopy(self.snapshot)
        tampered["messages"][0]["text"] = "forged"
        with self.assertRaises(collector.BridgeError):
            self.validate(snapshot=tampered)
        wrong_scope = {**self.cfg, "chats": ["别的群"]}
        with self.assertRaises(collector.BridgeError):
            ai.validate_decisions(wrong_scope, self.secret, self.base, self.snapshot,
                                  self.decisions(), now=self.now)

    def test_unknown_message_or_duplicate_slot_is_rejected(self):
        result = self.decisions()
        result["tasks"][0]["messageKey"] = "made-up-message"
        with self.assertRaises(collector.BridgeError):
            self.validate(decisions=result)
        result = self.decisions()
        result["tasks"].append(deepcopy(result["tasks"][0]))
        with self.assertRaises(collector.BridgeError):
            self.validate(decisions=result)

    def test_wrong_china_date_invalid_exact_and_stale_snapshot(self):
        result = self.decisions()
        result["tasks"][1]["deadline"]["date"] = "2026-11-11"
        with self.assertRaises(collector.BridgeError):
            self.validate(decisions=result)
        with self.assertRaises(collector.BridgeError):
            ai.validate_decisions(
                self.cfg, self.secret, self.base, self.snapshot, self.decisions(),
                now=self.now + timedelta(hours=49))
        with self.assertRaises(collector.BridgeError):
            ai.validate_decisions(
                self.cfg, self.secret, {"课程群A": self.ts},
                self.snapshot, self.decisions(), now=self.now)

    def test_submit_uses_existing_uploader_and_never_advances_on_failure(self):
        with TemporaryDirectory() as tmp:
            a = Path(tmp) / "input.json"
            b = Path(tmp) / "decisions.json"
            a.write_text(json.dumps(self.snapshot), encoding="utf-8")
            b.write_text(json.dumps(self.decisions()), encoding="utf-8")
            with patch.object(ai, "INPUT", a), patch.object(ai, "DECISIONS", b), \
                 patch.object(ai, "datetime", wraps=datetime) as clock:
                clock.now.return_value = self.now
                with patch.object(collector, "upload_candidates",
                                  side_effect=collector.BridgeError("network")) as upload:
                    with self.assertRaises(collector.BridgeError):
                        ai.submit_ai(self.cfg, self.secret, self.base)
                    upload.assert_called_once()
                self.assertTrue(a.exists())
                self.assertTrue(b.exists())
                with patch.object(collector, "upload_candidates") as upload:
                    ai.submit_ai(self.cfg, self.secret, self.base)
                    upload.assert_called_once()
                    self.assertEqual(len(upload.call_args.args[2]), 2)
                self.assertFalse(a.exists())
                self.assertFalse(b.exists())

    def test_empty_decisions_still_allow_verified_heartbeat(self):
        result = self.decisions()
        result["tasks"] = []
        records, state = self.validate(decisions=result)
        self.assertEqual(records, [])
        self.assertEqual(state["课程群A"], self.ts + 1)
