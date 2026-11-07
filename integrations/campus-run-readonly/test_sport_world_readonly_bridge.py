"""Offline regressions for the deliberately narrow OSS query bridge.

No account data, tokens, HAR bodies, real network or workout submissions.
Run: python -m unittest discover -s integrations/campus-run-readonly -p 'test_*.py'
"""
import hashlib
import hmac
import unittest
from types import SimpleNamespace
from urllib.parse import urlsplit

from sport_world_readonly_bridge import (
    ReadOnlyAccessError,
    ReadOnlySession,
    SchemaNotVerified,
    SportWorldReadOnlyBridge,
    authorize_readonly_call,
)


class FakeUpstream:
    def __init__(self, results=None):
        self.uid = "12345"
        self.token = "FAKE_AUTHORIZED_SESSION_FOR_OFFLINE_TESTS"
        self.results = results or {}
        self.called = []

    def execute_request(
        self, method, url, body_plain=None, custom_headers=None,
        sign_params=None, timeout=20
    ):
        path = urlsplit(url).path
        self.called.append((method, path))
        return self.results.get(path)

    def get_user_info(self):
        result = self.execute_request(
            "GET", "https://run.gxapp.iydsj.com/api/v70390/user/info"
        )
        if result is None:
            return None
        return SimpleNamespace(uid=result)

    def get_semester_summary(self):
        return self.execute_request(
            "POST",
            "https://run.gxapp.iydsj.com/api/v55/runnings/recordssummary/semester",
            body_plain={"sid": 0},
        )

    def get_semester_info(self):
        result = self.execute_request(
            "POST",
            "https://run.gxapp.iydsj.com/api/v41/running/getPersonalSemesterInfo",
            body_plain={"runMode": 1},
        )
        return SimpleNamespace(raw=result) if result is not None else None

    def get_run_records(self, page_num=1, page_size=20, sid=0, sport_type=1):
        return self.execute_request(
            "POST", "https://run.gxapp.iydsj.com/api/v70230/runnings/records",
            body_plain={
                "pageNum": page_num, "pageSize": page_size,
                "sid": sid, "type": sport_type,
            },
        )

    def submit_run(self):
        return self.execute_request(
            "POST", "https://run.gxapp.iydsj.com/api/v70260/runnings/save/record",
            body_plain={"distance": 0},
        )


class SportWorldReadOnlyBridgeTests(unittest.TestCase):
    def setUp(self):
        self.subject = ReadOnlySession("12345", b"k" * 32)
        self.summary = {
            "error": 10000,
            "data": {
                "sname": "Synthetic term",
                "semesterDis": 12_300,
                "semesterValidDis": 10_500,
                "semesterCount": 7,
                "semesterValidCount": 6,
                "secret": "not copied",
            },
        }
        self.records = {
            "error": 10000,
            "data": {
                "recordList": [
                    {
                        "rrid": 17, "totalDis": 2300, "validDis": 2100,
                        "totalTime": 870, "complete": True, "isValid": True,
                        "phone": "not copied",
                    }
                ],
                "hasNext": False,
            },
        }
        self.profile = FakeUpstream({
            "/api/v70390/user/info": "12345",
        })
        self.policy = FakeUpstream({
            "/api/v55/runnings/recordssummary/semester": self.summary,
            "/api/v41/running/getPersonalSemesterInfo": {
                "semesterName": "Synthetic term",
                "targetDistance": 100_000,
            },
        })
        self.history = FakeUpstream({
            "/api/v70230/runnings/records": self.records,
        })
        self.bridge = SportWorldReadOnlyBridge(
            self.profile, self.policy, self.history, self.subject
        )

    def test_allowlist_is_exact_and_excludes_write_operations(self):
        authorize_readonly_call(
            "POST", "https://run.gxapp.iydsj.com/api/v70230/runnings/records"
        )
        for method, url in [
            ("POST", "https://run.gxapp.iydsj.com/api/v70260/runnings/save/record"),
            ("POST", "https://run.gxapp.iydsj.com/api/obs/temporary/url"),
            ("POST", "https://run.gxapp.iydsj.com/api/v70100/run/start"),
            ("GET", "https://run.gxapp.iydsj.com/api/v70390/user/info?uid=2222"),
            ("GET", "http://run.gxapp.iydsj.com/api/v70390/user/info"),
            ("GET", "https://evil.example/api/v70390/user/info"),
            ("GET", "https://run.gxapp.iydsj.com@evil.example/api/v70390/user/info"),
            ("get", "https://run.gxapp.iydsj.com/api/v70390/user/info"),
        ]:
            with self.subTest(url=url):
                with self.assertRaises(ReadOnlyAccessError):
                    authorize_readonly_call(method, url)
        with self.assertRaises(ReadOnlyAccessError):
            self.history.submit_run()
        self.assertEqual(self.history.called, [])

    def test_block_mutable_headers_and_sign_overrides(self):
        with self.assertRaises(ReadOnlyAccessError):
            self.policy.execute_request(
                "POST", "https://run.gxapp.iydsj.com/api/v55/runnings/recordssummary/semester",
                custom_headers={"uid": "other"},
            )
        with self.assertRaises(ReadOnlyAccessError):
            self.history.execute_request(
                "POST", "https://run.gxapp.iydsj.com/api/v70230/runnings/records",
                sign_params={"other": "value"},
            )

    def test_versioned_subject_and_candidate_outputs_match_android_contract(self):
        identity = self.bridge.read_subject()
        semester = self.bridge.read_semester()
        page = self.bridge.read_history_page()
        self.assertEqual(identity["bridgeSchemaVersion"], 1)
        self.assertEqual(identity["bridgeKind"], "subject")
        self.assertEqual(semester["bridgeSchemaVersion"], 1)
        self.assertEqual(semester["bridgeKind"], "semester")
        self.assertEqual(page["bridgeSchemaVersion"], 1)
        self.assertEqual(page["bridgeKind"], "history")
        self.assertEqual(identity["subjectFingerprint"], semester["subjectFingerprint"])
        self.assertEqual(identity["subjectFingerprint"], page["subjectFingerprint"])
        self.assertIs(semester["verifiedFieldSemantics"], False)
        self.assertIs(page["verifiedFieldSemantics"], False)
        self.assertIs(page["paginationVerified"], True)

    def test_subject_is_hmac_and_not_raw_identifier(self):
        expected = hmac.new(b"k" * 32, b"sport-world-campus:12345", hashlib.sha256).hexdigest()
        self.assertEqual(self.bridge.current_subject_fingerprint(), expected)
        self.assertNotIn("12345", expected)
        with self.assertRaises(ReadOnlyAccessError):
            ReadOnlySession("12345", b"short")

    def test_summary_reads_both_existing_methods_and_removes_other_fields(self):
        result = self.bridge.read_semester()
        self.assertEqual(result["subjectFingerprint"], self.subject.fingerprint())
        self.assertFalse(result["verifiedFieldSemantics"])
        self.assertEqual(result["fields"]["summary.semesterValidDis"], 10_500)
        self.assertEqual(result["fields"]["info.targetDistance"], 100_000)
        self.assertNotIn("secret", str(result))
        self.assertTrue(any("/recordssummary/semester" in p for _, p in self.policy.called))
        self.assertTrue(any("/getPersonalSemesterInfo" in p for _, p in self.policy.called))
        self.assertEqual(sum(1 for m, _ in self.profile.called if m == "GET"), 2)

    def test_identity_mismatch_rejects_result(self):
        self.profile.results["/api/v70390/user/info"] = "999"
        with self.assertRaises(ReadOnlyAccessError):
            self.bridge.read_semester()

    def test_changing_session_during_read_cannot_publish_results(self):
        original = self.policy.get_semester_summary

        def switch_token_mid_request():
            result = original()
            self.policy.token = "REPLACED_SYNTHETIC_SESSION"
            return result

        self.policy.get_semester_summary = switch_token_mid_request
        with self.assertRaises(ReadOnlyAccessError):
            self.bridge.read_semester()

    def test_subsequent_subject_or_session_change_is_rejected(self):
        self.history.uid = "67890"
        with self.assertRaises(ReadOnlyAccessError):
            self.bridge.read_history_page()
        self.history.uid = "12345"
        self.history.token = "REPLACED_SYNTHETIC_SESSION"
        with self.assertRaises(ReadOnlyAccessError):
            self.bridge.read_history_page()

    def test_opaque_or_business_error_not_success(self):
        self.policy.results["/api/v55/runnings/recordssummary/semester"] = {
            "r": "cipher", "s": "sign", "v": 3
        }
        with self.assertRaises(SchemaNotVerified):
            self.bridge.read_semester()
        self.policy.results["/api/v55/runnings/recordssummary/semester"] = {
            "error": 20002, "data": {"semesterDis": 2000}
        }
        with self.assertRaises(SchemaNotVerified):
            self.bridge.read_semester()

    def test_history_fields_and_explicit_pagination_are_safe(self):
        page = self.bridge.read_history_page()
        self.assertEqual(len(page["recordsPayload"]), 1)
        self.assertEqual(page["recordsPayload"][0]["rrid"], 17)
        self.assertNotIn("phone", str(page))
        self.assertTrue(page["isLastPage"])
        self.assertTrue(page["paginationVerified"])
        self.assertIsNone(page["nextCursor"])
        self.assertFalse(page["verifiedFieldSemantics"])

    def test_list_payload_without_metadata_is_not_marked_complete(self):
        self.history.results["/api/v70230/runnings/records"] = {
            "error": 10000,
            "data": [{"rrid": 18, "totalDis": 900}],
        }
        page = self.bridge.read_history_page("2")
        self.assertIsNone(page["isLastPage"])
        self.assertFalse(page["paginationVerified"])
        self.assertIsNone(page["nextCursor"])

    def test_next_page_when_explicit_has_next(self):
        self.history.results["/api/v70230/runnings/records"]["data"]["hasNext"] = True
        page = self.bridge.read_history_page("2")
        self.assertFalse(page["isLastPage"])
        self.assertEqual(page["nextCursor"], "3")

    def test_empty_missing_cursor_and_bad_data_rejected(self):
        with self.assertRaises(SchemaNotVerified):
            self.bridge.read_history_page("abc")
        with self.assertRaises(SchemaNotVerified):
            self.bridge.read_history_page(page_size=100)
        self.history.results["/api/v70230/runnings/records"] = {
            "error": 10000, "data": ["bad-row"]
        }
        with self.assertRaises(SchemaNotVerified):
            self.bridge.read_history_page()

    def test_separate_clients_are_required(self):
        with self.assertRaises(ReadOnlyAccessError):
            SportWorldReadOnlyBridge(
                self.profile, self.profile, self.history, self.subject
            )

    def test_all_source_clients_must_share_a_legitimate_session(self):
        wrong_history = FakeUpstream()
        wrong_history.uid = "999"
        with self.assertRaises(ReadOnlyAccessError):
            SportWorldReadOnlyBridge(
                FakeUpstream(), FakeUpstream(), wrong_history, self.subject
            )
        wrong_token = FakeUpstream()
        wrong_token.token = "DIFFERENT_SYNTHETIC_SESSION"
        with self.assertRaises(ReadOnlyAccessError):
            SportWorldReadOnlyBridge(
                FakeUpstream(), FakeUpstream(), wrong_token, self.subject
            )



if __name__ == "__main__":
    unittest.main()
