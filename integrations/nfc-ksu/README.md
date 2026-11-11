# DORM OS NFC + KernelSU v0.1.0

**开发测试版**，未在 OnePlus Ace 6 Android 16 真机验证。本模块为独立设计，酷安 feed 70833813 正文当前无法直接读取，不能声称精确复刻。

## Architecture

Custom NDEF URI `dormos://scene/study` → Android `ACTION_NDEF_DISCOVERED` → Activity (no resident service) → KernelSU root authorization → allowlisted SH scripts. No passive SH-only NFC receiver, no logcat polling, no dangerous NFC service patch.

## Build and install

1. GitHub Actions workflow `.github/workflows/nfc-build.yml` builds the debug APK and KernelSU ZIP on PR or dispatch.
2. Install ZIP with KernelSU Manager; reboot if needed. Install the debug APK from the workflow artifact.
3. Launch app, choose a scene, write it to your NFC tag. On a new scan, allow Root for **DORM OS NFC** only.
4. Test with the phone unlocked, NFC on. Test one tag and observe output before binding more.
5. If needed, remove module in KSU Manager and uninstall APK.

Scenes: `study` opens learning website, `dingtalk` opens DingTalk without automatic punch-in, `ncwu-check` runs read-only Wi-Fi checks (no login), `dorm` reserved for verified website URL.

Only fixed scene IDs are accepted. No credentials or arbitrary commands are stored on the tag. Tags are cloneable; do not treat NFC as identity proof.

Local dry run: `DORMOS_TEST_MODE=1 sh module/bin/dispatch.sh study`.
