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
            "或 example@school.edu 学号202612345678", ts)
        self.assertNotIn("13812345678", task["title"])
        self.assertNotIn("example@school.edu", task["title"])
        self.assertNotIn("school.example", task["title"])
        self.assertNotIn("202612345678", task["title"])
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
        for bad in ("http://not-secure", "https://example.com", "https://user:pw@example.com",
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


    def test_empty_result_still_verifies_server_before_checkpoint(self):
        with TemporaryDirectory() as temp:
            original = bridge.STATE
            bridge.STATE = Path(temp) / "sync-state.json"
            config = {
                "site": "https://dorm-os-dk9.pages.dev",
                "deviceId": "wxd_" + "a"*24,
                "chats": ["已允许的课程群"],
                "tokenDpapi": "encrypted",
                "hmacDpapi": "encrypted",
            }
            try:
                with patch.object(bridge, "load_config", return_value=config), \
                     patch.object(bridge, "opened", side_effect=[
                         b"local-hmac", ("wxu_" + "b"*43).encode()
                     ]), \
                     patch.object(bridge, "extract_groups", return_value=(
                         [], {"已允许的课程群": 12345}, ["合成数据"]
                     )), \
                     patch.object(bridge, "submit", side_effect=bridge.BridgeError("offline")) as send:
                    with self.assertRaises(bridge.BridgeError):
                        bridge.main(["sync"])
                    self.assertEqual(send.call_count, 1)
                    self.assertEqual(send.call_args.args[2]["records"], [])
                    self.assertFalse(bridge.STATE.exists(),
                                     "failed heartbeat must not advance watermark")
                with patch.object(bridge, "load_config", return_value=config), \
                     patch.object(bridge, "opened", side_effect=[
                         b"local-hmac", ("wxu_" + "b"*43).encode()
                     ]), \
                     patch.object(bridge, "extract_groups", return_value=(
                         [], {"已允许的课程群": 12345}, ["合成数据"]
                     )), \
                     patch.object(bridge, "submit", return_value={
                         "reviewStatus": "needs_review", "received": 0
                     }) as send:
                    bridge.main(["sync"])
                    self.assertEqual(send.call_count, 1)
                    payload = send.call_args.args[2]
                    self.assertEqual(payload["records"], [])
                    self.assertTrue(payload["batchId"])
                    self.assertEqual(json.loads(bridge.STATE.read_text()),
                                     {"已允许的课程群": 12345})
            finally:
                bridge.STATE = original

    def test_server_must_ack_exact_received_count(self):
        payload = {"records": [{"messageKey": "synthetic-record"}]}
        with patch.object(bridge.urllib.request, "build_opener") as build:
            response = build.return_value.open.return_value.__enter__.return_value
            response.status = 202
            for returned in (0, 2, None, True):
                response.read.return_value = json.dumps({
                    "reviewStatus": "needs_review", "received": returned
                }).encode("utf-8")
                with self.assertRaises(bridge.BridgeError):
                    bridge.submit("https://dorm-os-dk9.pages.dev", "wxu_"+"x"*43, payload)
            response.read.return_value = b'{"reviewStatus":"needs_review","received":1}'
            self.assertEqual(bridge.submit(
                "https://dorm-os-dk9.pages.dev", "wxu_"+"x"*43, payload
            )["received"], 1)

    def test_verify_checks_device_without_reading_messages_or_moving_cursor(self):
        with TemporaryDirectory() as temp:
            old_state = bridge.STATE
            bridge.STATE = Path(temp) / "checkpoints.json"
            cfg = {
                "site": "https://dorm-os-dk9.pages.dev",
                "deviceId": "wxd_" + "a"*24,
                "chats": ["合成群聊"],
                "tokenDpapi": "protected-token",
                "hmacDpapi": "protected-hmac",
            }
            try:
                with patch.object(bridge, "load_config", return_value=cfg), \
                     patch.object(bridge, "opened", side_effect=[
                         ("wxu_"+"b"*43).encode(), b"local-hmac"
                     ]), \
                     patch.object(bridge, "extract_groups") as scan, \
                     patch.object(bridge, "submit", return_value={"received": 0}) as send:
                    bridge.main(["verify"])
                    scan.assert_not_called()
                    payload = send.call_args.args[2]
                    self.assertEqual(payload["records"], [])
                    self.assertEqual(payload["deviceId"], cfg["deviceId"])
                    self.assertFalse(bridge.STATE.exists())
            finally:
                bridge.STATE = old_state
    def test_post_has_explicit_client_identity_and_scoped_credentials(self):
        payload = {"records": []}
        with patch.object(bridge.urllib.request, "build_opener") as opener:
            response = opener.return_value.open.return_value.__enter__.return_value
            response.status = 202
            response.read.return_value = b'{"reviewStatus":"needs_review","received":0}'
            bridge.submit("https://dorm-os-dk9.pages.dev", "wxu_" + "x"*43, payload)
            req = opener.return_value.open.call_args.args[0]
            self.assertEqual(req.get_header("User-agent"), "Xiaoxu-Desktop-Connector/1.0")
            self.assertEqual(req.get_header("Authorization"), "Bearer " + "wxu_" + "x"*43)
            self.assertEqual(req.full_url, "https://dorm-os-dk9.pages.dev/api/v1/wechat/ingest")
    def test_network_error_category_does_not_leak_details(self):
        from urllib.error import URLError
        import socket
        import ssl
        cases = [
            (socket.gaierror(11001, "private.dns.internal"), "DNS 域名解析失败"),
            (ssl.SSLCertVerificationError(1, "private certificate"), "HTTPS 证书验证失败"),
            (TimeoutError("private internal endpoint"), "HTTPS 连接超时"),
            (ConnectionRefusedError("private local port"), "HTTPS 连接被拒绝"),
            (OSError("Tunnel connection failed: private proxy"), "代理隧道连接失败"),
            (OSError("secret proxy username:password"), "网络连接失败"),
        ]
        for reason, expected in cases:
            with self.subTest(expected=expected), patch.object(bridge.urllib.request, "build_opener") as opener:
                opener.return_value.open.side_effect = URLError(reason)
                with self.assertRaises(bridge.BridgeError) as caught:
                    bridge.submit("https://dorm-os-dk9.pages.dev", "wxu_" + "x"*43, {"records": []})
                self.assertIn(expected, str(caught.exception))
                self.assertNotIn("private", str(caught.exception))
                self.assertNotIn("secret", str(caught.exception))
                self.assertNotIn("wxu_", str(caught.exception))

    def test_http_403_reports_api_code_without_exposing_sensitive_response(self):
        import io
        from urllib.error import HTTPError
        payload = {"records": []}
        cases = [
            ({"Content-Type": "application/json"}, b'{"error":"device_mismatch"}', "device_mismatch"),
            ({"Content-Type": "application/json"}, b'{"error":"forbidden_origin_or_format"}', "forbidden_origin_or_format"),
            ({"Content-Type": "text/html"}, b"<html>hidden challenge details</html>", "非校序已知错误"),
            ({"Content-Type": "application/json"}, b'{"error":"unknown_private_value"}', "非校序已知错误"),
        ]
        for headers, response_body, expected in cases:
            with self.subTest(expected=expected), patch.object(bridge.urllib.request, "build_opener") as opener:
                opener.return_value.open.side_effect = HTTPError(
                    "https://dorm-os-dk9.pages.dev/api/v1/wechat/ingest",
                    403, "Forbidden", headers, io.BytesIO(response_body)
                )
                with self.assertRaises(bridge.BridgeError) as caught:
                    bridge.submit("https://dorm-os-dk9.pages.dev", "wxu_" + "x"*43, payload)
                error = str(caught.exception)
                self.assertIn(expected, error)
                self.assertNotIn("hidden challenge", error)
                self.assertNotIn("unknown_private_value", error)
                self.assertNotIn("wxu_", error)


if __name__ == "__main__":
    main()
