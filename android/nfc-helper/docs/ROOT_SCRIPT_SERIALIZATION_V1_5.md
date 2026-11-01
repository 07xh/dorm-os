# DORM NFC Manager V1.5 — Root script serialization

## Why

A tag tap and an in-app Run action can arrive close together. Without serialization, two user scripts may run concurrently as Root, race on shared files, or overlap network/device operations.

## Behavior

- script-manager.sh --run obtains an atomic mkdir lock before starting a script.
- The lock records both the manager PID and the active timeout PID.
- A second run while either owner is alive fails fast with DORM_SCRIPT_ERROR=script_busy (exit 75); it does not queue or silently rerun.
- The dispatcher surfaces the failure as an NFC execution failure.
- Normal exit and handled termination remove the lock; after an uncatchable process death, the next invocation can reclaim the lock only after both recorded PIDs are gone.
- Log rotation uses a unique temporary file per process instead of a shared fixed .tmp name.

## Test

The shell test starts a deliberately slow test script, attempts a second execution, asserts the second run is rejected with script_busy, and confirms the first run completes successfully.

## Limits

A user script can intentionally spawn detached child processes; this manager does not attempt to police every process a user-created Root script may start. Review scripts before binding them to NFC. The NFC tag is still a clonable bearer trigger.
