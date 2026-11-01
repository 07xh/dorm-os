# DORM NFC Manager V1.8 — historical stale-lock recovery

> Superseded by V1.9. Do not use this document as the current lock specification.

V1.8 used PID-based stale-lock reclamation by renaming the old lock directory. A later concurrency review found that a second contender could rename a replacement lock created by the first contender. V1.9 replaces this with a probed kernel-managed `flock` lock where supported and a fail-closed atomic `mkdir` fallback. See `ROOT_SCRIPT_SERIALIZATION_V1_9.md`.
