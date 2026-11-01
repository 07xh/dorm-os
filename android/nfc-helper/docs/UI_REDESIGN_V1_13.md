# DORM NFC Manager V1.13 — Native UI redesign

## Why a new APK is required

Previous V1.12 successful artifacts were built before the ManagerActivity UI overhaul. The native manager screen consequently still showed the old dashboard even when the separate tag reader/writer had changed.

## Changes in this revision

- New main dashboard: prominent status hero (KernelSU connected/not connected), current SH count, bound NFC count, and last result.
- Four quick operations: read tag, write ordinary NDEF content, manage SH, and view NFC bindings. No campus-network-specific screen.
- Native Material 3 navigation with clear selected states; same four top-level areas only.
- New SH list card hierarchy: script name/size, two primary actions (run, bind NFC), one More menu containing edit, preview, rename, move, delete. Keep all existing backend actions intact.
- NFC area shows the script-trigger switch once, two foreground read/write shortcuts and the list of existing tag bindings. SH binding still begins in the script card.
- Cleaner light design: blue gradient hero, white cards, subtler borders, consistent 12/14/20 dp corner radii and spacing.
- No new background service, side-key hooks, or extra KSU WebUI. Existing local SH library and NFC-to-SH contract unchanged.

## Build and verification

- Android application versionCode 22, versionName 1.13.0-ui-preview.
- Build the **PR HEAD**, run testDebugUnitTest and assembleDebug and verify APK was produced from that exact SHA.
- Do not hand out the earlier 1.12 debug artifact as if it contained the new UI.
- In absence of an NFC tag, manual checks can cover home, scripts, folder management, editing, settings/logs, Root connection, and read/write pages' empty/waiting states.
- With a physical tag, verify read, write, protected-DORM overwrite prevention, user-authorized SH binding, repeat taps, and KernelSU execution.
- Release remains a Draft until real-device acceptance; this is a debug preview rather than signed stable release.
