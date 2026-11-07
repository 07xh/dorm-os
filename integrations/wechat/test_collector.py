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

    def test_full_redacted_message_keeps_long_details_and_marks_overflow(self):
        body = "11月7日提交实验报告\\n" + "需要包含原理、步骤和数据。" * 180
        task = bridge.classify(body, 1793764800)
        self.assertLessEqual(len(task["title"].encode("utf-16-le")) // 2, 120)
        self.assertIn("需要包含原理", task["excerpt"])
        self.assertGreater(len(task["excerpt"]), 240)
        self.assertEqual(task["excerpt"], body)

        secret = "13812345678"
        protected = bridge.classify(
            "提交实验报告\\n" + secret + " example@school.edu", 1793764800)
        self.assertNotIn(secret, protected["excerpt"])
        self.assertNotIn("example@school.edu", protected["excerpt"])
        too_long = bridge.classify("提交实验报告" + "说明" * 3000, 1793764800)
        self.assertLessEqual(len(too_long["excerpt"].encode("utf-16-le")) // 2,
                             bridge.MAX_EXCERPT_UNITS)
        self.assertIn("剩余部分请在微信查看", too_long["excerpt"])

    def test_extract_https_links_basics(self):
        self.assertEqual(
            bridge.extract_https_links(
                "实验报告模板 https://course.example/hw/template.zip 请下载"),
            ["https://course.example/hw/template.zip"])
        # Chinese punctuation directly after the URL must not join the URL,
        # and the title keeps the redacted placeholder instead of the link.
        links = bridge.extract_https_links(
            "详情见https://x.example/a，截止前提交。备用：https://x.example/b。")
        self.assertEqual(links, ["https://x.example/a", "https://x.example/b"])
        # Unbalanced markdown-style paren is not part of the URL.
        self.assertEqual(
            bridge.extract_https_links("参考(https://x.example/c)"),
            ["https://x.example/c"])
        self.assertEqual(
            bridge.extract_https_links("括号内(https://x.example/d)后缀"),
            ["https://x.example/d"])
        # http is never accepted, only https.
        self.assertEqual(bridge.extract_https_links("看 http://x.example/e 提交"), [])
        # Embedded credentials make the URL invalid.
        self.assertEqual(
            bridge.extract_https_links("看 https://user:pw@x.example/f 提交"), [])
        self.assertEqual(bridge.extract_https_links("没有链接也要提交"), [])

    def test_extract_https_links_caps_and_dedups(self):
        four = " ".join(f"https://x.example/{i}" for i in range(4))
        self.assertEqual(len(bridge.extract_https_links(four)), 3)
        dup = "https://x.example/same https://x.example/same"
        self.assertEqual(bridge.extract_https_links(dup),
                         ["https://x.example/same"])
        self.assertEqual(bridge.extract_https_links("x" * 600), [])

    def test_classify_keeps_links_out_of_title(self):
        ts = 1793764800
        task = bridge.classify(
            "11月6日前提交报告，模板 https://school.example/hw1 提交", ts)
        self.assertNotIn("school.example", task["title"])
        self.assertIn("[链接]", task["title"])
        self.assertEqual(task["links"], ["https://school.example/hw1"])

    def test_records_carry_links_without_changing_identity(self):
        ts = 1793764800
        secret = b"link-test-secret"
        rows = [("shard/Msg", 7, ts,
                 "11月6日提交实验报告 https://lab.example/report 逾期不收")]
        def reader(name, cutoff):
            yield from rows
        records, _, _ = bridge.extract_groups(
            {"chats": ["群"]}, secret, {}, reader=reader, now_ts=ts + 10)
        self.assertEqual(records[0]["links"], ["https://lab.example/report"])
        self.assertNotIn("lab.example", records[0]["title"])
        base = {**records[0], "links": None}
        def reader_plain(name, cutoff):
            yield ("shard/Msg", 7, ts, "11月6日提交实验报告 逾期不收")
        plain, _, _ = bridge.extract_groups(
            {"chats": ["群"]}, secret, {}, reader=reader_plain, now_ts=ts + 10)
        self.assertEqual(records[0]["candidateKey"], plain[0]["candidateKey"],
                         "links must not change the stable candidate identity")

    def test_cloud_schema_utf16_limits_and_control_characters(self):
        # Python length differs from JS UTF-16 length for emoji/non-BMP glyphs.
        text = "提交报告" + "📘"*85 + chr(1) + "请按时提交"
        task = bridge.classify(text, 1793764800)
        self.assertLessEqual(len(task["title"].encode("utf-16-le")) // 2, 120)
        self.assertNotIn(chr(1), task["title"])

        chat = "专业课程" + "🎓"*42
        def reader(name, cutoff):
            self.assertEqual(name, chat, "look up full chat name, never truncated name")
            yield ("shard", 11, 1793764800, "提交电工报告")

        records, _, _ = bridge.extract_groups(
            {"chats": [chat]}, b"test-device-secret", {},
            reader=reader, now_ts=1793765000
        )
        self.assertEqual(len(records), 1)
        self.assertLessEqual(len(records[0]["chatLabel"].encode("utf-16-le")) // 2, 80)
        self.assertEqual(records[0]["title"], "提交电工报告")

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

    def test_sensitive_link_params_rejected(self):
        # Credential-style query/fragment parameters never reach the cloud.
        self.assertEqual(
            bridge.extract_https_links("填表 https://school.example/form?token=abc 提交"), [])
        self.assertEqual(
            bridge.extract_https_links(
                "看 https://school.example/a?id=1&api_key=x "
                "和 https://school.example/b#access_token=xyz 提交"), [])
        self.assertEqual(
            bridge.extract_https_links(
                "看 https://school.example/a?id=1&next=2 提交"),
            ["https://school.example/a?id=1&next=2"])
        # Word-boundary matching: classcode is a plain value, invite_code is
        # conservatively treated as sensitive.
        self.assertEqual(
            bridge.extract_https_links("提交 https://school.example/d?classcode=7 提交"),
            ["https://school.example/d?classcode=7"])
        self.assertEqual(
            bridge.extract_https_links("提交 https://school.example/d?invite_code=7 提交"), [])
        # Malformed percent-encoding is treated as unvalidatable.
        self.assertEqual(
            bridge.extract_https_links("提交 https://school.example/e?na%2me=1 提交"), [])
        self.assertEqual(
            bridge.extract_https_links("提交 https://school.example/e?na%2me=%zz 提交"), [])
        # camelCase composites hit the substring tier (regression for V1.1
        # review: authToken / clientSecret slipped past boundary matching).
        self.assertEqual(
            bridge.extract_https_links("提交 https://school.example/f?authToken=x 提交"), [])
        self.assertEqual(
            bridge.extract_https_links("提交 https://school.example/f?clientSecret=y 提交"), [])
        self.assertEqual(
            bridge.extract_https_links("提交 https://school.example/f?secretKey=z 提交"), [])
        # Percent-encoded bytes that are not valid UTF-8 must be rejected the
        # same way JavaScript decodeURIComponent throws (V1.1 review P0):
        # unquote(errors="replace") used to silently accept ?%FF=1.
        self.assertEqual(
            bridge.extract_https_links("提交 https://school.example/g?%FFx=1 提交"), [])
        self.assertEqual(
            bridge.extract_https_links("提交 https://school.example/g?%C3%A9d=1 提交"),
            ["https://school.example/g?%C3%A9d=1"])

    def test_link_port_validation_matches_js(self):
        # WHATWG new URL() rejects non-numeric and out-of-range ports; the
        # collector must agree so one bad link cannot sink a whole batch.
        self.assertEqual(
            bridge.extract_https_links("看 https://school.example:abc/report 提交"), [])
        self.assertEqual(
            bridge.extract_https_links("看 https://school.example:99999/x 提交"), [])
        self.assertEqual(
            bridge.extract_https_links("看 https://school.example:8443/x 提交"),
            ["https://school.example:8443/x"])

    def test_http_422_reports_safe_contract_code(self):
        import io
        from urllib.error import HTTPError
        for body, expected in [
            (b'{"error":"title_invalid"}', "title_invalid"),
            (b'{"error":"chat_label_invalid"}', "chat_label_invalid"),
            (b'{"error":"links_invalid"}', "links_invalid"),
            (b'{"error":"secret_or_untrusted_response"}', "unknown_schema_error"),
        ]:
            with self.subTest(expected=expected), patch.object(bridge.urllib.request, "build_opener") as opener:
                opener.return_value.open.side_effect = HTTPError(
                    "https://dorm-os-dk9.pages.dev/api/v1/wechat/ingest",
                    422, "Unprocessable", {"Content-Type":"application/json"}, io.BytesIO(body)
                )
                with self.assertRaises(bridge.BridgeError) as caught:
                    bridge.submit("https://dorm-os-dk9.pages.dev",
                                  "wxu_" + "x"*43, {"records":[]})
                self.assertIn(expected, str(caught.exception))
                self.assertNotIn("secret_or_untrusted_response", str(caught.exception))
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
