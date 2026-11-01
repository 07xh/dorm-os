#!/bin/sh
set -eu
DIR="${0%/*}"
SCRIPT="$DIR/../module/bin/dispatch.sh"
sh -n "$SCRIPT" "$DIR/../module/action.sh" "$DIR/../module/service.sh"
for scene in status study dingtalk ncwu-check dorm; do
 DORMOS_TEST_MODE=1 sh "$SCRIPT" "$scene" >/dev/null
done
if DORMOS_TEST_MODE=1 sh "$SCRIPT" 'study;id' >/dev/null 2>&1; then
 echo "Invalid scene accepted" >&2; exit 1
fi
echo "Module shell tests passed"
