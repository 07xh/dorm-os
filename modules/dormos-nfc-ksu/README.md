# DORM NFC Manager 1.3.0 Preview — KernelSU only

One dedicated Android Compose app + one minimal KernelSU module. This is a personal SH manager, not a campus-network product.

## App

- Four compact areas: home/status, SH library, NFC bindings, settings/logs.
- Create, edit, import, search, execute and delete personal `.sh` files.
- Import uses Android's Storage Access Framework; no broad all-files permission.
- Save performs Shell syntax validation. Import never executes a script.
- Run and NFC binding both require an explicit confirmation.
- NFC tags contain only an opaque UUID; the actual script remains in protected local storage.

## KernelSU module

- KernelSU only. No Magisk, LSPosed or KernelSU WebUI.
- No `service.sh`, boot receiver, daemon, polling loop or permanent wake lock.
- Scripts live outside the module at `/data/adb/dormos_nfc_bridge/scripts/<UUID>.sh`; module updates do not overwrite them.
- Metadata, bindings and logs are root-only. Script size is limited to 16 KiB.
- NFC is disabled by default. When enabled, only a previously bound UUID can resolve to a script ID and execute it.
- No dedicated campus-network action, screen or hard-coded login logic. A campus script is simply another user-owned `校园网.sh` in the library.

## Safety

Imported scripts execute with Root only after explicit user action or a deliberately bound NFC tap. Review each script before running or binding it. The module does not fetch remote scripts or install APKs.

## Verification

CI checks Shell syntax, import/save/read/delete, invalid names and UUIDs, syntax errors, disabled/unbound NFC rejection, generic script dispatch and Android unit tests. Ace 6 / Android 16 real-device NFC, Root permission and idle-power checks are still required before stable release.

## V1.3 NFC safe-binding protocol

The native app writes a new NDEF tag and verifies the read-back **before** it records a Root script binding. Canceling a write, presenting a read-only tag, or receiving a mismatched read-back leaves the tag unbound. A newly formatted tag without confirmed read-back must be presented again for verification. New tags use `application/vnd.dormos.nfc-script`; the receiver still accepts the legacy `application/vnd.dormos.ncwu` tag MIME for migration. This is a compatibility mechanism, not a dedicated campus-login feature. NFC IDs are not security tokens: copied tags may trigger the same approved script.


## V1.4 NFC trigger hardening

- The exported NFC activity no longer trusts `EXTRA_NDEF_MESSAGES` supplied by the launching Intent. It reads the NDEF message from the discovered physical `Tag` via `Ndef.get(tag)`, then validates the MIME type, payload length, version prefix and UUID.
- Supports the new DORM NFC MIME and the legacy MIME for already written tags.
- Root command output is kept as a bounded 42 KiB tail rather than the first 42 KiB, so verbose user-script output cannot hide the dispatcher's final success marker.
- NFC execution success is determined by exit code plus the exact dispatcher marker, not by an arbitrary output-size limit.
- Added unit tests for MIME/payload validation and verbose-output success-marker handling.

This hardens forged-Intent handling but does not make NFC tags secret or unclonable. A copied tag still acts as a bearer trigger for its bound script.


## V1.5 concurrent Root execution guard

The module now uses an atomic per-device run lock so an NFC tap and an in-app Run action cannot launch two registered Root scripts at the same time. A second request receives `DORM_SCRIPT_ERROR=script_busy` and does not run. The lock records both the manager PID and active timeout PID, cleans up on normal exit/signals, and can recover a stale lock after both owners have exited. Log rotation uses unique temporary files. The personal-script test suite includes a concurrent-run positive/negative case.


## V1.6 manager reliability and readable logs

- Module refresh and Root actions now recover from unexpected exceptions instead of leaving the UI permanently busy; coroutine cancellation is still propagated correctly.
- Recent execution output displays its tail so the final result is visible.
- Execution history converts Unix timestamps into local time and script IDs into script names, skips malformed rows, and shows at most the latest 80 valid events.
- The home page surfaces the actual module/Root status message when connection fails.


## V1.7 NFC tag rebinding cleanup

When overwriting a previously DORM-managed NDEF tag, the app reads and validates the old DORM UUID from the physical tag before writing. It writes and verifies the new UUID, commits the new Root binding, and then removes the old UUID mapping. Unrelated NDEF records are ignored. If old-binding cleanup fails, the app reports that the new binding was created but cleanup needs attention; it does not silently claim a clean replacement.


## V1.8 stale run-lock recovery

Stale run-lock recovery now atomically renames the old lock directory before creating a replacement. This closes a race where two simultaneous requests could both observe a stale lock and both delete/recreate it. The shell test now verifies stale-owner recovery in addition to rejecting a second active Root run.
