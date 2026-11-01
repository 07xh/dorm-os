#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
export DORM_NFC_TESTING=1
export DORM_NFC_TEST_ROOT="$TMP/data"
ID=123e4567-e89b-12d3-a456-426614174000
TAG=123e4567-e89b-12d3-a456-426614174001
NAME=$(printf '校园网.sh' | base64 -w0)
BODY=$(printf '#!/bin/sh\necho test-script-ok\n' | base64 -w0)
S="$ROOT/script-manager.sh"
R="$ROOT/run.sh"
out=$(sh "$S" --save "$ID" "$NAME" "$BODY")
[[ "$out" == *DORM_SCRIPT_SAVED=1* ]]
out=$(sh "$R" --script-list)
[[ "$out" == *"$ID"* ]]
[[ "$out" == *"$NAME"* ]]
out=$(sh "$R" --script-read "$ID")
[[ "$out" == *SCRIPT_BODY64=* ]]
sh "$R" --script-bind "$TAG" "$ID" | grep -q DORM_SCRIPT_BOUND
sh "$R" --enable 1 >/dev/null
out=$(sh "$R" --nfc "$TAG")
[[ "$out" == *test-script-ok* ]]
[[ "$out" == *DORM_NFC_RESULT=success* ]]
out=$(sh "$R" --script-run "$ID")
[[ "$out" == *DORM_SCRIPT_RESULT=success* ]]
out=$(sh "$R" --script-logs)
[[ "$out" == *"$ID"* ]]
if sh "$R" --script-bind "$TAG" '../bad'; then
  echo "Bad script id was accepted" >&2
  exit 1
fi
BAD_BODY=$(printf 'if then syntax-error\n' | base64 -w0)
if sh "$R" --script-save "$ID" "$NAME" "$BAD_BODY" >/dev/null 2>&1; then
  echo "Bad script syntax was accepted" >&2
  exit 1
fi
sh "$R" --script-unbind "$TAG" | grep -q DORM_SCRIPT_UNBOUND
if sh "$R" --nfc "$TAG" >/dev/null 2>&1; then
  echo "Unbound NFC tag ran a script" >&2
  exit 1
fi
sh "$R" --script-delete "$ID" | grep -q DORM_SCRIPT_DELETED
[[ -z "$(sh "$R" --script-list)" ]]
echo "personal script manager tests passed"
