# DORM NFC Manager V1.8 — atomic stale-lock recovery

## Race fixed

The earlier stale-lock path checked that the recorded manager and timeout PIDs were no longer alive, then removed the lock directory and recreated it. Two simultaneous invocations could both pass the stale-PID check; one could remove the other invocation's newly created lock, allowing both to run.

## New recovery protocol

- Validate both recorded PID fields.
- If either manager or timeout process is alive, fail with script_busy.
- If both are dead, atomically rename the stale lock directory to a process-unique sibling path.
- Only the process that successfully renames the stale directory may remove it and create a new lock. Competing reclaimers fail or acquire the now-free lock through the normal atomic mkdir path; they cannot both own it.
- The shell test pre-creates a stale lock with dead PIDs, verifies one run can recover it, and verifies the lock is removed after completion. The active-run concurrency test remains.
