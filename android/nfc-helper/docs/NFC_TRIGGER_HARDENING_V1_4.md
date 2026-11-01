# DORM NFC Manager V1.4 — NFC trigger hardening

## Threat addressed

The NFC activity is exported so Android can dispatch physical NDEF tags to it. An exported activity can also be launched explicitly by another app. The previous implementation parsed `NfcAdapter.EXTRA_NDEF_MESSAGES` from the launch Intent, so it relied on caller-provided extras instead of verifying the tag's actual stored data.

## Change

- Continue to require a real `Tag` extra.
- Read NDEF records from `Ndef.get(tag)` after connecting to the discovered tag.
- Ignore launch-intent NDEF message extras.
- Accept only the new DORM NFC MIME or the legacy MIME, a 1–80 byte payload, and the supported UUID payload format.
- Close the NDEF connection in a finally block.

This prevents a simple forged Intent with a fabricated NDEF payload from selecting an arbitrary existing script binding. It does not prevent cloning/relay of a legitimate tag: NFC UUIDs are bearer triggers, not authentication secrets.

## Output and timeout handling

The root bridge reads subprocess output continuously but retains only a bounded 42 KiB tail. This keeps memory bounded while preserving the dispatcher's final result marker when a user's script emits verbose output. NFC success requires exit code 0 and the exact `DORM_NFC_RESULT=success` marker.

The user-script dispatcher still applies its own 20-second timeout. The app bridge has a 26-second outer timeout. These are independent guardrails; Android device testing must confirm that the child process is terminated on timeout in the actual KernelSU environment.

## Tests

- Supported MIME + valid UUID payload accepted.
- Wrong MIME, oversized payload and malformed UUID payload rejected.
- Long script output does not invalidate a valid final dispatcher success marker.
- Non-zero exit and failure marker remain failures.

## Still not final-release verified

- Real Ace 6 / ColorOS Android 16 NFC dispatch and Root prompt.
- Lock-screen and screen-off behavior under actual device settings.
- Process-tree termination under KernelSU when a user script times out.
- Idle power A/B measurement.
