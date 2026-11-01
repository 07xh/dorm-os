#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
export DORM_NFC_TESTING=1
export DORM_NFC_TEST_ROOT="$TMP/data"
RUN="$ROOT/run.sh"
ID="123e4567-e89b-12d3-a456-426614174000"
NAME_B64="$(printf '校园网检查' | base64 -w0)"
CONFIG="$(printf 'DORM_NFC_SCENES_V1\n%s|ncwu-check||%s\n' "$ID" "$NAME_B64" | base64 -w0)"

sh "$RUN" --save-b64 "$CONFIG" | grep -q 'DORM_CONFIG=saved'
sh "$RUN" --status | grep -q 'NFC_ENABLED=0'
if sh "$RUN" --nfc "$ID" >/dev/null 2>&1; then
  echo "NFC dispatch must be disabled by default" >&2
  exit 1
fi
sh "$RUN" --enable 1 | grep -q 'NFC_ENABLED=1'
OUT="$(sh "$RUN" --nfc "$ID")"
printf '%s' "$OUT" | grep -q 'DORM_NFC_RESULT=success'
printf '%s' "$OUT" | grep -q 'ACTION=ncwu-check'
if sh "$RUN" --nfc 'not-a-uuid' >/dev/null 2>&1; then
  echo "Malformed UUID must be rejected" >&2
  exit 1
fi
if sh "$RUN" --save-b64 'not-base64!' >/dev/null 2>&1; then
  echo "Malformed config must be rejected" >&2
  exit 1
fi
BAD_URL="$(printf 'https://example.org/?access_token=secret' | base64 -w0)"
BAD_CONFIG="$(printf 'DORM_NFC_SCENES_V1\n%s|open-url|%s|%s\n' "$ID" "$BAD_URL" "$NAME_B64" | base64 -w0)"
if sh "$RUN" --save-b64 "$BAD_CONFIG" >/dev/null 2>&1; then
  echo "Credential-bearing HTTPS target must be rejected" >&2
  exit 1
fi
PRIVATE_URL="$(printf 'https://192.168.1.1/admin' | base64 -w0)"
PRIVATE_CONFIG="$(printf 'DORM_NFC_SCENES_V1\n%s|open-url|%s|%s\n' "$ID" "$PRIVATE_URL" "$NAME_B64" | base64 -w0)"
if sh "$RUN" --save-b64 "$PRIVATE_CONFIG" >/dev/null 2>&1; then
  echo "Private-network HTTPS target must be rejected" >&2
  exit 1
fi
sh "$RUN" --list | grep -q "$ID"
sh "$RUN" --logs | grep -q "$ID"
sh "$RUN" --clear-logs | grep -q 'LOGS=CLEARED'
if sh "$RUN" --logs | grep -q "$ID"; then
  echo "Logs should be empty after clear" >&2
  exit 1
fi
echo "dispatcher tests passed"
