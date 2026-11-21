"""Fully offline fixtures for archived ydsj_toolkit.py JSON contract."""
import unittest

from sport_world_readonly_bridge import SchemaNotVerified
from toolkit_readonly_contract import (
    normalize_toolkit_score,
    normalize_toolkit_history_page,
)


SUBJECT = "a" * 64


class ToolkitReadOnlyContractTests(unittest.TestCase):
    def test_semester_total_and_credited_distances_never_get_collapsed(self):
        raw = {
            "semester_completed": {
                "error": 10000,
                "data": {
                    "sname": "semester",
                    "semesterDis": 16000,
                    "semesterValidDis": 12000,
                    "semesterCount": 10,
                    "semesterValidCount": 8,
                    "unrelatedPersonalData": "must-not-propagate",
                },
            },
            "semester_info": {
                "error": 10000,
                "data": {"semesterName": "semester", "targetDistance": 50000},
            },
            "rank": {"data": [{"order": 1, "uid": "other-person"}]},
        }
        normalized = normalize_toolkit_score(raw, SUBJECT)
        self.assertEqual(normalized["fields"]["summary.semesterDis"], 16000)
        self.assertEqual(normalized["fields"]["summary.semesterValidDis"], 12000)
        self.assertEqual(normalized["fields"]["summary.semesterCount"], 10)
        self.assertEqual(normalized["fields"]["summary.semesterValidCount"], 8)
        self.assertNotIn("rank", normalized["fields"])
        self.assertNotIn("summary.unrelatedPersonalData", normalized["fields"])
        self.assertFalse(normalized["verifiedFieldSemantics"])

    def test_bare_page_never_claims_complete_history_even_when_short(self):
        raw = {"error": 10000, "data": [
            {"rrid": 17, "totalDis": 2000, "complete": True, "isValid": False,
             "secret": "discard"},
        ]}
        result = normalize_toolkit_history_page(raw, SUBJECT)
        self.assertEqual(result["bridgeKind"], "history")
        self.assertEqual(len(result["recordsPayload"]), 1)
        self.assertIsNone(result["isLastPage"])
        self.assertFalse(result["paginationVerified"])
        self.assertFalse(result["verifiedFieldSemantics"])
        self.assertNotIn("secret", result["recordsPayload"][0])

    def test_metadata_allows_structural_pagination_but_not_verified_semantics(self):
        raw = {"error": 10000, "data": {
            "recordList": [{"rrid": "r1", "totalDis": 1200}],
            "hasNext": True,
        }}
        first = normalize_toolkit_history_page(raw, SUBJECT)
        self.assertFalse(first["isLastPage"])
        self.assertEqual("2", first["nextCursor"])
        self.assertTrue(first["paginationVerified"])
        self.assertFalse(first["verifiedFieldSemantics"])

        raw["data"]["hasNext"] = False
        last = normalize_toolkit_history_page(raw, SUBJECT, page=2)
        self.assertTrue(last["isLastPage"])
        self.assertIsNone(last["nextCursor"])

    def test_gateway_401_http200_and_encrypted_shell_are_not_valid_reads(self):
        for response in [
            {"status": 401, "error": "Unauthorized"},
            {"error": 10003, "message": "not authorized"},
            {"r": "cipher", "s": "signature", "v": 101},
            {"error": 10000},
        ]:
            with self.subTest(response_keys=list(response)):
                with self.assertRaises(SchemaNotVerified):
                    normalize_toolkit_history_page(response, SUBJECT)
                with self.assertRaises(SchemaNotVerified):
                    normalize_toolkit_score({"semester_completed": response}, SUBJECT)

    def test_rejects_arbitrary_subject_and_unproven_history_termination(self):
        for fingerprint in ("", "uid-123", "A"*64, "a"*63):
            with self.assertRaises(SchemaNotVerified):
                normalize_toolkit_score({
                    "semester_completed": {"error": 10000, "data": {"semesterDis": 1}}
                }, fingerprint)
        with self.assertRaises(SchemaNotVerified):
            normalize_toolkit_history_page({"error": 10000, "data": []}, SUBJECT, page=0)
        with self.assertRaises(SchemaNotVerified):
            normalize_toolkit_history_page({"error": 10000, "data": []}, SUBJECT, page_size=1000)


if __name__ == "__main__":
    unittest.main()
