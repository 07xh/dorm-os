"""仅使用合成消息测试校序电脑桥接，不接触真实微信数据或账号。"""
from datetime import datetime, timezone
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, main
from unittest.mock import patch
import importlib.util
import json

path = Path(__file__).parent / "collector.py"
spec = importlib.util.spec_from_file_location("xiaoxu_bridge_test_target", path)
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)


class CollectorTests(TestCase):
    def test_classify_and_date(self):
        ts = int(datetime(2026, 11, 4, 4, tzinfo=timezone.utc).timestamp())
        task = bridge.classify("本周提交电工实验报告，11月6日截止", ts)
        self.assertEqual(task["deadline"], {"precision": "date", "date": "2026-11-06"})
        self.assertEqual(task["kind"], "homework")
        self.assertEqual(bridge.classify("今天在教室正常上课", ts), None)
        self.assertEqual(bridge.classify("考试安排另行通知", ts)["kind"], "exam")
        self.assertEqual(bridge.classify("报名信息请查看群公告", ts)["kind"], "signup")
        self.assertEqual(bridge.due_date("月底前提交", ts), {"precision": "unknown"})
        self.assertEqual(bridge.due_date("2026-02-30 提交", ts), {"precision": "unknown"})
        self.assertEqual(bridge.due_date("2026年11月8日提交", ts)["date"], "2026-11-08")

    def test_privacy_redaction(self):
        ts = 1793764800
        task = bridge.classify(
            "提交报告 https://school.example/private?id=123 学号请发 13812345678 "
            "或 example@school.edu", ts)
        self.assertNotIn("13812345678", task["title"])
        self.assertNotIn("example@school.edu", task["title"])
        self.assertNotIn("school.example", task["title"])
        self.assertLessEqual(len(task["title"]), 120)

    def test_selected_groups_only_deterministic(self):
        ts = 1793764800
        secret = b"only-local-hmac-test-key"
        cfg = {"chats": ["课程群一"]}
        calls = []
        rows = [
            ("shard-a/Msg_abc", 10, ts, "明天上课正常"),
            ("shard-a/Msg_abc", 11, ts + 1, "11月6日提交电路实验报告"),
            ("shard-b/Msg_abc", 11, ts + 1, "11月7日提交电路实验报告"),
        ]

        def reader(name, cutoff):
            calls.append((name, cutoff))
            yield from rows

        a, cursors, _ = bridge.extract_groups(cfg, secret, {}, reader=reader, now_ts=ts+90)
        b, _, _ = bridge.extract_groups(cfg, secret, {}, reader=reader, now_ts=ts+90)
        self.assertEqual(len(a), 2, "same local ID in distinct source shards must remain distinct")
        self.assertEqual(a, b, "repeat scan generates stable dedup keys")
        self.assertEqual(cursors["课程群一"], ts + 1)
        self.assertEqual(len(calls), 2)
        self.assertTrue(all(c["chatLabel"] == "课程群一" for c in a))
        self.assertTrue(all(len(c["candidateKey"]) == 43 for c in a))
        self.assertTrue(all("sender" not in c and "rawMessage" not in c for c in a))

    def test_watermark_overlap(self):
        ts = 1793764800
        def reader(name, cutoff):
            self.assertEqual(cutoff, ts - bridge.OVERLAP_SECONDS)
            return iter([])
        _, states, _ = bridge.extract_groups(
            {"chats": ["选中群"]}, b"key", {"选中群": ts},
            reader=reader, now_ts=ts + 3600)
        self.assertEqual(states["选中群"], ts)

    def test_setup_validation(self):
        self.assertEqual(bridge.validate_site("https://dorm-os-dk9.pages.dev/"),
                         "https://dorm-os-dk9.pages.dev")
        for bad in ("http://not-secure", "https://user:pw@example.com",
                    "https://example.com/path", "https://example.com/?key=123"):
            with self.assertRaises(bridge.BridgeError):
                bridge.validate_site(bad)

    def test_checkpoint_only_after_success(self):
        with TemporaryDirectory() as temp:
            old_state = bridge.STATE
            bridge.STATE = Path(temp) / "checkpoints.json"
            cfg = {"chats": ["课程群"], "site": "https://dorm-os-dk9.pages.dev",
                   "deviceId": "wxd_" + "a"*24,
                   "tokenDpapi": "ignored", "hmacDpapi": "ignored"}
            row = {"candidateKey": "a"*43, "sentAt": "2026-11-04T04:00:00Z"}
            try:
                with patch.object(bridge, "load_config", return_value=cfg), \
                     patch.object(bridge, "opened", side_effect=[b"private-hmac", ("wxu_" + "b"*43).encode()]), \
                     patch.object(bridge, "extract_groups",
                         return_value=([row], {"课程群": 1000}, ["synthetic"])) ,\
                     patch.object(bridge, "submit", side_effect=bridge.BridgeError("offline")):
                    with self.assertRaises(bridge.BridgeError):
                        bridge.main(["sync"])
                self.assertFalse(bridge.STATE.exists(),
                                 "network failure must not advance the local checkpoint")
                with patch.object(bridge, "load_config", return_value=cfg), \
                     patch.object(bridge, "opened", side_effect=[b"private-hmac", ("wxu_" + "b"*43).encode()]), \
                     patch.object(bridge, "extract_groups",
                         return_value=([row], {"课程群": 1000}, ["synthetic"])) ,\
                     patch.object(bridge, "submit", return_value={"reviewStatus":"needs_review"}):
                    bridge.main(["sync"])
                self.assertEqual(json.loads(bridge.STATE.read_text()), {"课程群": 1000})
            finally:
                bridge.STATE = old_state


if __name__ == "__main__":
    main()
