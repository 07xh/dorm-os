# DORM NFC Manager V1.5 — historical serialization design

> Superseded by V1.9. Do not use this document as the current lock implementation specification.

V1.5 introduced the initial PID-based `mkdir` lock. Its automatic stale-lock reclamation was replaced because concurrent reclaimers could race. V1.9 uses a probed kernel-managed `flock` lock where available and a fail-closed `mkdir` fallback. See `ROOT_SCRIPT_SERIALIZATION_V1_9.md`.
