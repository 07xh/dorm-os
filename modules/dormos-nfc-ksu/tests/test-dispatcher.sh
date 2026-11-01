#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
export DORM_NFC_TESTING=1
export DORM_NFC_TEST_ROOT="$TMP/data"
RUN="$ROOT/run.sh"
MANAGER="$ROOT/script-manager.sh"
SCRIPT_ID=123e4567-e89b-12d3-a456-426614174000
TAG_ID=123e4567-e89b-12d3-a456-426614174001
NAME_B64="$(printf '校园网.sh' | base64 -w0)"
BODY_B64="$(printf '#!/system/bin/sh\necho campus-script-ran\n' | base64 -w0)"

OUT="$(sh "$RUN" --status)"
[[ "$OUT" == *DORM_MODULE=ready* ]]
[[ "$OUT" == *NFC_ENABLED=0* ]]
[[ "$OUT" == *BACKGROUND_SERVICE=none* ]]
if sh "$RUN" --nfc "$TAG_ID" >/dev/null 2>&1; then
  echo "NFC must be disabled by default" >&2
  exit 1
fi

sh "$MANAGER" --save "$SCRIPT_ID" "$NAME_B64" "$BODY_B64" | grep -q DORM_SCRIPT_SAVED
sh "$RUN" --script-bind "$TAG_ID" "$SCRIPT_ID" | grep -q DORM_SCRIPT_BOUND
sh "$RUN" --enable 1 | grep -q NFC_ENABLED=1
OUT="$(sh "$RUN" --nfc "$TAG_ID")"
[[ "$OUT" == *campus-script-ran* ]]
[[ "$OUT" == *DORM_NFC_RESULT=success* ]]
[[ "$OUT" == *DORM_ACTION=personal-script* ]]
[[ "$OUT" == *"DORM_SCRIPT_ID=$SCRIPT_ID"* ]]

if sh "$RUN" --nfc "bad" >/dev/null 2>&1; then
  echo "Malformed tag UUID must be rejected" >&2
  exit 1
fi
if sh "$RUN" --legacy-ncwu >/dev/null 2>&1; then
  echo "No dedicated campus-network action may remain" >&2
  exit 1
fi
sh "$RUN" --enable 0 >/dev/null
if sh "$RUN" --nfc "$TAG_ID" >/dev/null 2>&1; then
  echo "Disabled NFC must reject execution" >&2
  exit 1
fi
OUT="$(sh "$RUN" --script-logs)"
[[ "$OUT" == *"$SCRIPT_ID"* ]]
echo "generic NFC dispatcher tests passed"
