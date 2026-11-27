"""No network, no real identities. Tests only a synthetic score/all fixture."""
import json
import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from offline_score_export import SCHEMA, extract_score_only


class OfflineScoreExportTests(unittest.TestCase):
    def test_keeps_only_explicit_semester_fields_and_units(self):
        raw = {
            "score": {"semester_completed": {
                "error": 10000, "data": {
                    "sname": "synthetic-autumn",
                    "semesterDis": 12345, "semesterValidDis": 10003,
                    "semesterCount": 5, "semesterValidCount": 3,
                    "hiddenLocation": "secret", "personalId": "private"
                }}},
            "token": "synthetic-token",
            "rank": {"data": [{"uid": "somebody", "rank": 1}]},
            "history": {"data": [{"gps": "sensitive"}]},
        }
        result = extract_score_only(json.dumps(raw))
        self.assertEqual(result["schema"], SCHEMA)
        self.assertEqual(result["unit"], "m")
        self.assertEqual(result["semester_completed"]["data"],
            {"sname": "synthetic-autumn",
             "semesterDis": 12345, "semesterValidDis": 10003,
             "semesterCount": 5, "semesterValidCount": 3})
        serialized = json.dumps(result)
        for forbidden in ("synthetic-token", "hiddenLocation", "private", "somebody", "sensitive", "gps"):
            self.assertNotIn(forbidden, serialized)

    def test_shared_kotlin_python_golden_fixture_matches_sanitizer(self):
        repo_root = pathlib.Path(__file__).resolve().parents[2]
        fixture = repo_root / (
            "apps/xiaoxu-android/app/src/test/resources/"
            "campus-run-safe-score-fixture.json"
        )
        expected = json.loads(fixture.read_text(encoding="utf-8"))
        raw = {"score": {
            "semester_completed": {"error": 10000, "data": {
                "sname": "synthetic-autumn", "semesterDis": 12345,
                "semesterValidDis": 10003, "semesterCount": 5,
                "semesterValidCount": 3,
                "unusedSensitiveField": "must-not-export",
            }}
        }, "token": "must-not-export", "history": {"data": ["secret"]}}
        self.assertEqual(extract_score_only(json.dumps(raw)), expected)

    def test_handles_direct_score_without_hypothesized_history(self):
        data = {"semester_completed": {
            "error": 10000, "data": {"semesterDis": 0,
                                    "semesterCount": 0}}}
        result = extract_score_only(json.dumps(data))
        self.assertEqual(result["semester_completed"]["data"]["semesterDis"], 0)
        self.assertNotIn("history", result)

    def test_bad_status_missing_metrics_or_inconsistent_values_rejected(self):
        invalids = [
            {"semester_completed": {"error": 401, "data": {"semesterDis": 100}}},
            {"semester_completed": {"error": True, "data": {"semesterDis": 100}}},
            {"semester_completed": {"error": 10000, "data": {"semesterDis": 5, "semesterValidDis": 6}}},
            {"semester_completed": {"error": 10000, "data": {"semesterCount": 2, "semesterValidCount": 3}}},
            {"semester_completed": {"error": 10000, "data": {"semesterDis": 1.4}}},
            {"semester_completed": {"error": 10000, "data": {"semesterCount": 1.5}}},
            {"semester_completed": {"error": 10000, "data": {"semesterDis": True}}},
            {"semester_completed": {"error": 10000, "data": {"sname": "only a term"}}},
            {"semester_completed": {"error": 10000, "r": "secret", "s": "secret", "v": 1}},
            {"score": {"semester_completed": {"error": 10000,
                "data": {"semesterDis": 1, "semesterCount": 1_000_001}}}},
        ]
        for fixture in invalids:
            with self.subTest(fixture=fixture):
                with self.assertRaises(ValueError):
                    extract_score_only(json.dumps(fixture))

    def test_rejects_oversized_input_and_no_trailing_data(self):
        with self.assertRaises(ValueError):
            extract_score_only(" ".join(["{}"] * 120_000))
        with self.assertRaises(json.JSONDecodeError):
            extract_score_only('{"score": 1}{"score": 1}')


if __name__ == "__main__":
    unittest.main()
