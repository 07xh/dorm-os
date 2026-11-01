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
