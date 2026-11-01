"""Repository source-level invariants; hardware and Gradle build are separate gates."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/com/dormos/nfc'

class StructureTests(unittest.TestCase):
    def test_manifest_nfc_and_no_network(self):
        root = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
        android = '{http://schemas.android.com/apk/res/android}'
        permissions = [p.get(android+'name') for p in root.findall('uses-permission')]
        self.assertIn('android.permission.NFC', permissions)
        self.assertNotIn('android.permission.INTERNET', permissions)
    def test_legacy_ndef(self):
        code = (SRC/'NfcEngine.kt').read_text()
        self.assertIn('dormos://scene/', code)
        self.assertIn('ACTION_NDEF_DISCOVERED', code)
        self.assertIn('getParcelableExtra(NfcAdapter.EXTRA_TAG', code)
    def test_whitelist(self):
        code = (SRC/'ModuleGateway.kt').read_text()
        self.assertIn('if (action !in AllowedActions.items)', code)
        self.assertIn('waitFor(timeout, TimeUnit.SECONDS)', code)
        self.assertNotIn('Runtime.getRuntime().exec(user', code)
    def test_no_legacy_webview(self):
        ui = (SRC/'MainActivity.kt').read_text()
        self.assertIn('Scaffold(', ui)
        self.assertIn('NavigationBar(', ui)
        self.assertNotIn('WebView', ui)

if __name__ == '__main__':
    unittest.main()
