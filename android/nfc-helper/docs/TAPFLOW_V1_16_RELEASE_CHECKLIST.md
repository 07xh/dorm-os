# 触序 · TapFlow V1.16.1 Preview Release Checklist

- Product: 触序 · TapFlow
- Version: 1.16.1-inline-ndef-sheet-lifecycle (versionCode 29)
- Package: xyz.dormos.nfc (unchanged)
- KernelSU module ID: dormos_nfc_actions (unchanged)
- NDEF protocol, UUID payload, SH library paths and DORM OS App Links: unchanged

## Included in this preview

1. Ordinary NDEF text and HTTP(S) URL writing is now hosted in the ManagerActivity tools-page bottom sheet.
2. The standalone NdefWriterActivity manifest route and Kotlin source are removed.
3. A single foreground one-shot NDEF write session validates writability and capacity, requires explicit consent before replacing existing ordinary NDEF records, always blocks application/vnd.dormos.* automation records, and requires byte-for-byte full-message readback.
4. An interrupted/unknown write result does not expose an immediate retry action; the user is directed to scan and verify the physical tag first. A generation guard also handles the race between NFC callback claim and the main-thread state update when the Activity leaves the foreground.
5. Management icons use a consistent 24dp fine-line vector grid. Each management row is one clickable Material Surface with a unified ripple target.
6. The NFC reader remains a read-only flow; scene binding retains its separate verified write/readback + KernelSU registration sequence.

## Automated verification

- Previous same-code preview build: GitHub Actions run 41304988631, commit 78e217ce15fd1ae9f2f9d86fe3e9399995447960.
- Android testDebugUnitTest, assembleDebug, assembleRelease: passed.
- KernelSU dispatcher, personal script and folder tests: passed.
- This release commit triggers a fresh exact-HEAD verification; the GitHub prerelease job runs only after both APK and KernelSU jobs succeed.

## Still requires physical device verification

- OnePlus Ace 6 / Android 16 NDEF text and URL write, readback, read-only tags, low capacity, pre-existing ordinary data and DORM automation-tag protection.
- NFC off/on recovery, app pause/resume during an armed session and write interruption.
- Scene write → full readback → KernelSU binding → actual tag-triggered script execution.
- UI at user font scaling, keyboard open, short display and gesture navigation.

This is a Debug preview, not a stable-signed production release. PR #12 remains Draft until physical NFC and device UX verification are complete.
