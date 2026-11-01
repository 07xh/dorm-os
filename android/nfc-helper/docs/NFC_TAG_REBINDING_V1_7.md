# DORM NFC Manager V1.7 — NFC tag rebinding cleanup

## Problem

Rebinding a previously managed NFC tag generated a fresh UUID and overwrote the old NDEF payload. The old UUID-to-script mapping could remain in the Root registry as an orphaned binding.

## Updated flow

1. When the physical tag is already NDEF-formatted, read its existing DORM MIME record before writing.
2. Accept the previous ID only when the record is MIME-media with a supported DORM MIME and a valid UUID payload.
3. Write the new UUID and verify the NDEF read-back.
4. Return both the verified new ID and the validated previous ID to the manager.
5. The manager commits the new UUID-to-script binding first. Only after that succeeds does it unbind the previous UUID.
6. Refresh the binding list regardless of cleanup outcome. If cleanup fails, report that explicitly.

A newly formatted tag has no previous ID. An unrelated NDEF tag is overwritten only after the user explicitly chose Bind and Write; no unrelated record is treated as a DORM binding.

## Tests

Pure unit tests cover: removing a distinct valid old UUID; not removing the new ID itself; ignoring malformed previous IDs; refusing cleanup when the new ID is invalid or missing. Existing cancellation/read-back gates remain in place.

## Limitation

If the new binding fails after the physical tag has been rewritten, the tag may contain an unbound UUID; the prior Root binding is left untouched. The UI refreshes and reports the failure so the user can repeat the bind operation. This avoids silently deleting the old binding before the new one exists.
