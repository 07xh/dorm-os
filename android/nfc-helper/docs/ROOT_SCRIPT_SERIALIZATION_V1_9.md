# DORM NFC Manager V1.9 — Root execution serialization

## Why

An NFC tap, an in-app Run action, and a script/binding edit can arrive close together. Concurrent Root scripts may race on shared files or device/network state. A separate resolve-then-run sequence also permits a tag to be rebound between lookup and execution.

## Lock strategy

- Prefer a kernel-managed `flock` lock when a compatible `flock` command is available.
- Probe `flock` on a unique temporary file before relying on it. The real lock uses one stable file and a non-blocking exclusive lock.
- The lock descriptor is inherited by the timeout/script process tree. This keeps the lock held while inherited children remain alive and lets the kernel release it after process death.
- If `flock` is unavailable or incompatible, use an atomic `mkdir` lock directory. A second request fails closed. A stale fallback lock is never automatically stolen; inspect active processes and remove the lock directory manually only after confirming no DORM script or child process remains.
- The lock is also used for script save/rename/delete, tag bind/unbind, and log clearing.

## Atomic NFC resolution

The dispatcher calls `script-manager.sh --run-bound TAG_UUID`. That command acquires the lock first, validates the tag UUID, resolves the current binding, validates the script ID and script file, then executes it without releasing the lock between those steps.

## Timeout and output

Scripts still run through the 20-second `timeout` command. The app bridge retains a bounded output tail. The event log stores only timestamp, script ID and result.

## Tests

- Two overlapping manual runs: second request is rejected with `script_busy` or the fallback lock's busy marker.
- A concurrent delete is rejected while a script is running.
- Two overlapping fallback-lock runs: second request is rejected with `script_busy_or_stale_lock`.
- A stale fallback lock is not silently stolen.
- The dispatcher test exercises the atomic NFC resolve-and-run path and checks the script ID and success marker.

## Device verification still required

Confirm the Android 16 KernelSU environment's `flock` support, NFC tap behavior, child-process handling, lock-screen dispatch and idle power on the Ace 6. CI shell tests cannot substitute for those device checks.
