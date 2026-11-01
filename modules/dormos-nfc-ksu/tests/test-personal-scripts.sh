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
if sh "$R" --script-save "$ID" "$(printf 'not-shell.txt' | base64 -w0)" "$BODY" >/dev/null 2>&1; then
  echo "Non-SH file name was accepted" >&2
  exit 1
fi
if sh "$R" --script-bind "$TAG" '../bad'; then
  echo "Bad script id was accepted" >&2
  exit 1
fi
BAD_BODY=$(printf 'if then syntax-error\n' | base64 -w0)
if sh "$R" --script-save "$ID" "$NAME" "$BAD_BODY" >/dev/null 2>&1; then
  echo "Bad script syntax was accepted" >&2
  exit 1
fi
sh "$R" --script-clear-logs | grep -q DORM_SCRIPT_LOGS=CLEARED
[[ -z "$(sh "$R" --script-logs)" ]]
sh "$R" --script-unbind "$TAG" | grep -q DORM_SCRIPT_UNBOUND
if sh "$R" --nfc "$TAG" >/dev/null 2>&1; then
  echo "Unbound NFC tag ran a script" >&2
  exit 1
fi
sh "$R" --script-delete "$ID" | grep -q DORM_SCRIPT_DELETED
[[ -z "$(sh "$R" --script-list)" ]]
# Concurrent app/NFC triggers and mutations must be serialized.
BUSY_ID=123e4567-e89b-12d3-a456-426614174002
BUSY_NAME=$(printf '并发测试.sh' | base64 -w0)
BUSY_BODY=$(printf '#!/system/bin/sh\nsleep 3\necho slow-script-finished\n' | base64 -w0)
sh "$R" --script-save "$BUSY_ID" "$BUSY_NAME" "$BUSY_BODY" | grep -q DORM_SCRIPT_SAVED
sh "$R" --script-run "$BUSY_ID" > "$TMP/first-run.out" 2>&1 &
FIRST_PID=$!
sleep 0.25
if sh "$R" --script-run "$BUSY_ID" > "$TMP/second-run.out" 2>&1; then
  echo "Concurrent Root script execution was not blocked" >&2
  exit 1
fi
grep -Eq 'DORM_SCRIPT_ERROR=(script_busy|script_busy_or_stale_lock)' "$TMP/second-run.out"
if sh "$R" --script-delete "$BUSY_ID" > "$TMP/concurrent-delete.out" 2>&1; then
  echo "Script deletion was allowed during execution" >&2
  exit 1
fi
grep -Eq 'DORM_SCRIPT_ERROR=(script_busy|script_busy_or_stale_lock)' "$TMP/concurrent-delete.out"
if sh "$R" --script-bind "$TAG" "$BUSY_ID" > "$TMP/concurrent-bind.out" 2>&1; then
  echo "Tag rebinding was allowed during execution" >&2
  exit 1
fi
grep -Eq 'DORM_SCRIPT_ERROR=(script_busy|script_busy_or_stale_lock)' "$TMP/concurrent-bind.out"
wait "$FIRST_PID"
grep -q 'DORM_SCRIPT_RESULT=success' "$TMP/first-run.out"

# Exercise the portable mkdir fallback and its fail-closed stale-lock behavior.
DORM_NFC_FORCE_MKDIR_LOCK=1 sh "$R" --script-run "$BUSY_ID" > "$TMP/fallback-run.out" 2>&1 &
FALLBACK_PID=$!
sleep 0.25
if DORM_NFC_FORCE_MKDIR_LOCK=1 sh "$R" --script-run "$BUSY_ID" > "$TMP/fallback-second.out" 2>&1; then
  echo "Concurrent fallback lock execution was not blocked" >&2
  exit 1
fi
grep -q 'DORM_SCRIPT_ERROR=script_busy_or_stale_lock' "$TMP/fallback-second.out"
wait "$FALLBACK_PID"
grep -q 'DORM_SCRIPT_RESULT=success' "$TMP/fallback-run.out"
[[ ! -e "$DORM_NFC_TEST_ROOT/script-run.lock.d" ]]

mkdir "$DORM_NFC_TEST_ROOT/script-run.lock.d"
if DORM_NFC_FORCE_MKDIR_LOCK=1 sh "$R" --script-run "$BUSY_ID" > "$TMP/stale-lock.out" 2>&1; then
  echo "Stale fallback lock was silently stolen" >&2
  exit 1
fi
grep -q 'DORM_SCRIPT_ERROR=script_busy_or_stale_lock' "$TMP/stale-lock.out"
rm -rf "$DORM_NFC_TEST_ROOT/script-run.lock.d"
sh "$R" --script-delete "$BUSY_ID" | grep -q DORM_SCRIPT_DELETED

echo "personal script manager tests passed"
