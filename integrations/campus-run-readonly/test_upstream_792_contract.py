"""Optional contract smoke test against pinned upstream *real* read-only classes.

Import only three source modules; never initialize a real network connection or
run login/running/telemetry/ads code. Upstream execute_request is stubbed.
"""
import os
from pathlib import Path
import sys
import unittest
from urllib.parse import urlsplit

from sport_world_readonly_bridge import (
    ReadOnlySession, SportWorldReadOnlyBridge, authorize_readonly_call,
)

root = os.environ.get("SPORT_WORLD_UPSTREAM_ROOT", "").strip()
if root:
    upstream_root = Path(root).resolve()
    if not upstream_root.is_dir():
        raise RuntimeError("upstream read-only source checkout directory missing")
    sys.path.insert(0, str(upstream_root))


@unittest.skipUnless(root, "Pinned external source not provided; synthetic contract still runs")
class PinnedUpstreamReadMethodTests(unittest.TestCase):
    def test_actual_oss_792_methods_match_guarded_adapter(self):
        # Imports happen only in the isolated CI runner that provides a pinned
        # public upstream checkout. We don't construct or call login/running.
        from profile.client import ProfileClient
        from policy.client import PolicyClient
        from history.client import HistoryClient

        routes = []

        def offline_execute(method, url, body_plain=None, **kwargs):
            authorize_readonly_call(method, url)
            path = urlsplit(url).path
            routes.append((method, path))
            if path == "/api/v70390/user/info":
                return {"error": 10000, "data": {"uid": 12345}}
            if path == "/api/v55/runnings/recordssummary/semester":
                return {"error": 10000, "data": {
                    "sname": "Synthetic only",
                    "semesterDis": 15_200,
                    "semesterValidDis": 11_000,
                }}
            if path == "/api/v41/running/getPersonalSemesterInfo":
                return {"error": 10000, "data": {
                    "semesterName": "Synthetic only",
                    "targetDistance": 100_000,
                }}
            if path == "/api/v70230/runnings/records":
                return {"error": 10000, "data": {
                    "recordList": [{
                        "rrid": 9, "totalDis": 1_900,
                        "complete": True, "isValid": True,
                    }],
                    "hasNext": False,
                }}
            raise AssertionError("unexpected method or endpoint")

        # __new__ is intentional for this *offline signature test* only:
        # it avoids the upstream constructor's device config/crypto setup.
        # Real clients must arrive from an existing normal authorized session.
        clients = []
        for cls in (ProfileClient, PolicyClient, HistoryClient):
            instance = object.__new__(cls)
            instance.uid = "12345"
            instance.token = "OFFLINE_FAKE_SAME_SESSION"
            instance.execute_request = offline_execute
            clients.append(instance)

        adapter = SportWorldReadOnlyBridge(
            *clients, ReadOnlySession("12345", b"p" * 32)
        )
        summary = adapter.read_semester()
        page = adapter.read_history_page()

        self.assertEqual(summary["fields"]["summary.semesterValidDis"], 11_000)
        self.assertEqual(summary["fields"]["info.targetDistance"], 100_000)
        self.assertFalse(summary["verifiedFieldSemantics"])
        self.assertTrue(page["isLastPage"])
        self.assertEqual(page["recordsPayload"][0]["rrid"], 9)
        self.assertTrue(all((method, host, path) for method, path in routes
            for host in ("run.gxapp.iydsj.com",)))
        self.assertGreaterEqual(len(routes), 6)


if __name__ == "__main__":
    unittest.main()
