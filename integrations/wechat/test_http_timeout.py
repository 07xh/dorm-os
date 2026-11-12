"""Timeout configuration tests; no network, no WeChat reader, no cursor write."""
import os
from unittest import TestCase
from unittest.mock import patch

import collector


class HttpTimeoutTests(TestCase):
    def test_default_is_longer_than_the_old_20s(self):
        with patch.dict(os.environ, {}, clear=False):
            os.environ.pop("XIAOXU_HTTP_TIMEOUT_SEC", None)
            value = collector.http_timeout()
        self.assertGreater(value, 20.0)
        self.assertEqual(value, 45.0)

    def test_environment_override(self):
        with patch.dict(os.environ, {"XIAOXU_HTTP_TIMEOUT_SEC": "60"}):
            self.assertEqual(collector.http_timeout(), 60.0)

    def test_malformed_value_fails_closed(self):
        for bad in ("abc", "0", "4", "121", "-5"):
            with patch.dict(os.environ, {"XIAOXU_HTTP_TIMEOUT_SEC": bad}):
                with self.assertRaises(collector.BridgeError):
                    collector.http_timeout()
