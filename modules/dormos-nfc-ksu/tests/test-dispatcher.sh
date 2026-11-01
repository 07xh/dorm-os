#!/usr/bin/env bash
set -euxo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
export DORM_NFC_TESTING=1
export DORM_NFC_TEST_ROOT="$TMP/data"
RUN="$ROOT/run.sh"
ID="123e4567-e89b-12d3-a456-426614174000"
NAME_B64="$(printf '校园网检查' | base64 -w0)"
CONFIG="$(printf 'DORM_NFC_SCENES_V1\n%s|ncwu-check||%s\n' "$ID" "$NAME_B64" | base64 -w0)"

OUT="$(sh "$RUN" --save-b64 "$CONFIG")"
case "$OUT" in *DORM_CONFIG=saved*) ;; *) echo "Config save marker missing: $OUT" >&2; exit 1 ;; esac
OUT="$(sh "$RUN" --status)"
case "$OUT" in *NFC_ENABLED=0*) ;; *) echo "NFC must be disabled by default" >&2; exit 1 ;; esac
if sh "$RUN" --nfc "$ID" >/dev/null 2>&1; then
  echo "NFC dispatch must be disabled by default" >&2
  exit 1
fi
OUT="$(sh "$RUN" --enable 1)"
case "$OUT" in *NFC_ENABLED=1*) ;; *) echo "NFC enable marker missing" >&2; exit 1 ;; esac
OUT="$(sh "$RUN" --nfc "$ID")"
case "$OUT" in *DORM_NFC_RESULT=success*) ;; *) echo "NFC dispatch did not succeed: $OUT" >&2; exit 1 ;; esac
case "$OUT" in *ACTION=ncwu-check*) ;; *) echo "Wrong fixed action: $OUT" >&2; exit 1 ;; esac
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
OUT="$(sh "$RUN" --list)"
case "$OUT" in *"$ID"*) ;; *) echo "Saved scene missing from list" >&2; exit 1 ;; esac
OUT="$(sh "$RUN" --logs)"
case "$OUT" in *"$ID"*) ;; *) echo "Scene dispatch missing from logs" >&2; exit 1 ;; esac
OUT="$(sh "$RUN" --clear-logs)"
case "$OUT" in *LOGS=CLEARED*) ;; *) echo "Log clear marker missing" >&2; exit 1 ;; esac
OUT="$(sh "$RUN" --logs)"
case "$OUT" in *"$ID"*) echo "Logs should be empty after clear" >&2; exit 1 ;; esac
echo "dispatcher tests passed"
