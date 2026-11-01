#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
export DORM_NFC_TESTING=1
export DORM_NFC_TEST_ROOT="$TMP/data"
R="$ROOT/run.sh"
SCRIPT=123e4567-e89b-12d3-a456-426614174000
TAG=123e4567-e89b-12d3-a456-426614174001
A=123e4567-e89b-12d3-a456-426614174002
B=123e4567-e89b-12d3-a456-426614174003
C=123e4567-e89b-12d3-a456-426614174004
enc() { printf '%s' "$1" | base64 -w0; }
sh "$R" --folder-create "$A" root "$(enc '学习')" | grep -q DORM_FOLDER_CREATED
sh "$R" --folder-create "$B" "$A" "$(enc '校园')" | grep -q DORM_FOLDER_CREATED
out=$(sh "$R" --folder-list)
[[ "$out" == *"$A|root|"* ]]
[[ "$out" == *"$B|$A|"* ]]
if sh "$R" --folder-create "$C" root "$(enc '学习')" >/dev/null 2>&1; then
  echo "Duplicate folder accepted" >&2; exit 1
fi
if sh "$R" --folder-move "$A" "$B" >/dev/null 2>&1; then
  echo "Circular folder hierarchy accepted" >&2; exit 1
fi
sh "$R" --script-save "$SCRIPT" "$(enc '校园网.sh')" "$(enc '#!/bin/sh
echo SAFE_OUTPUT')" | grep -q DORM_SCRIPT_SAVED
sh "$R" --script-move "$SCRIPT" "$B" | grep -q DORM_SCRIPT_MOVED
sh "$R" --script-rename "$SCRIPT" "$(enc '校园网连接.sh')" | grep -q DORM_SCRIPT_RENAMED
[[ "$(sh "$R" --script-list)" == *"$(enc '校园网连接.sh')"* ]]
[[ "$(sh "$R" --script-folders)" == *"$SCRIPT|$B"* ]]
sh "$R" --script-bind "$TAG" "$SCRIPT" | grep -q DORM_SCRIPT_BOUND
sh "$R" --enable 1 >/dev/null
out=$(sh "$R" --nfc "$TAG")
[[ "$out" == *SAFE_OUTPUT* ]]
[[ "$out" == *DORM_NFC_RESULT=success* ]]
if sh "$R" --folder-delete "$B" >/dev/null 2>&1; then
  echo "Deleted a nonempty folder" >&2; exit 1
fi
if sh "$R" --folder-delete "$A" >/dev/null 2>&1; then
  echo "Deleted a folder with a subfolder" >&2; exit 1
fi
sh "$R" --folder-rename "$A" "$(enc '大学学习')" | grep -q DORM_FOLDER_RENAMED
sh "$R" --script-move "$SCRIPT" root | grep -q DORM_SCRIPT_MOVED
sh "$R" --folder-move "$B" root | grep -q DORM_FOLDER_MOVED
sh "$R" --folder-delete "$A" | grep -q DORM_FOLDER_DELETED
sh "$R" --folder-delete "$B" | grep -q DORM_FOLDER_DELETED
[[ "$(sh "$R" --script-folders)" == "" ]]
out=$(sh "$R" --nfc "$TAG")
[[ "$out" == *SAFE_OUTPUT* ]]
if sh "$R" --folder-create "$C" root "$(enc '../bad')" >/dev/null 2>&1; then
  echo "Path traversal folder accepted" >&2; exit 1
fi
echo "folder manager tests passed"
