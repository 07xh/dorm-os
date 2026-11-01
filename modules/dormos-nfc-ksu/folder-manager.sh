#!/system/bin/sh
# Personal virtual folders for scripts: UUID-based IDs remain stable across moves.
# Each folder has a root-owned metadata file, not a user-controlled filesystem path.
set -eu
umask 077
if [ "${DORM_NFC_TESTING:-0}" = "1" ] && [ "$(id -u)" != "0" ]; then
  BASE="${DORM_NFC_TEST_ROOT:?}"
else
  BASE=/data/adb/dormos_nfc_bridge
fi
FOLDERS="$BASE/folders"
SCRIPTS="$BASE/scripts"
die() { echo "DORM_FOLDER_ERROR=$1"; exit "${2:-1}"; }
uuid() { printf '%s' "$1" | grep -Eq '^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$'; }
valid_folder_id() { [ "$1" = root ] || uuid "$1"; }
check_folder() {
  valid_folder_id "$1" || die invalid_folder_id 64
  if [ "$1" != root ]; then
    [ -f "$FOLDERS/$1.meta" ] && [ ! -L "$FOLDERS/$1.meta" ] || die folder_not_found 66
  fi
}
valid_name64() {
  value="$1"
  [ -n "$value" ] && [ "${#value}" -le 360 ] || die invalid_name 64
  printf '%s' "$value" | grep -Eq '^[A-Za-z0-9+/]+={0,2}$' || die invalid_name 64
  name=$(printf '%s' "$value" | base64 -d 2>/dev/null) || die invalid_base64 65
  [ -n "$name" ] && [ "$(printf '%s' "$name" | wc -c | tr -d ' ')" -le 120 ] || die invalid_name 64
  case "$name" in .|..|*/*|*\\*|*'|'*) die invalid_name 64 ;; esac
  if printf '%s' "$name" | grep -q '[[:cntrl:]]'; then die invalid_name 64; fi
}
no_duplicate() {
  _check_parent="$1"; _check_value="$2"; _check_except="$3"
  for _check_meta in "$FOLDERS"/*.meta; do
    [ -f "$_check_meta" ] && [ ! -L "$_check_meta" ] || continue
    _check_id="${_check_meta##*/}"; _check_id="${_check_id%.meta}"
    [ "$_check_id" = "$_check_except" ] && continue
    IFS='|' read -r _check_existing_parent _check_existing_name < "$_check_meta" || :
    if [ "$_check_existing_parent" = "$_check_parent" ] && [ "$_check_existing_name" = "$_check_value" ]; then die duplicate_folder 65; fi
  done
}
folder_parent() {
  IFS='|' read -r parent _ < "$FOLDERS/$1.meta" || :
  printf '%s' "$parent"
}
no_cycle() {
  moving="$1"; target="$2"; hops=0
  while [ "$target" != root ]; do
    [ "$target" != "$moving" ] || die folder_cycle 65
    target=$(folder_parent "$target")
    hops=$((hops + 1))
    [ "$hops" -le 40 ] || die folder_cycle 65
    check_folder "$target"
  done
}
[ ! -L "$BASE" ] && [ ! -L "$FOLDERS" ] && [ ! -L "$SCRIPTS" ] || die unsafe_storage 70
mkdir -p "$FOLDERS" "$SCRIPTS"
chmod 700 "$BASE" "$FOLDERS" "$SCRIPTS" 2>/dev/null || :
case "${1:-}" in
  --list)
    for meta in "$FOLDERS"/*.meta; do
      [ -f "$meta" ] && [ ! -L "$meta" ] || continue
      id="${meta##*/}"; id="${id%.meta}"
      uuid "$id" || continue
      IFS='|' read -r parent title < "$meta" || :
      valid_folder_id "$parent" || continue
      printf '%s|%s|%s\n' "$id" "$parent" "$title"
    done
    ;;
  --create)
    id="${2:-}"; parent="${3:-}"; title="${4:-}"
    uuid "$id" || die invalid_folder_id 64
    check_folder "$parent"; valid_name64 "$title"
    [ ! -e "$FOLDERS/$id.meta" ] && [ ! -L "$FOLDERS/$id.meta" ] || die folder_exists 65
    no_duplicate "$parent" "$title" ""
    printf '%s|%s\n' "$parent" "$title" > "$FOLDERS/$id.meta"
    chmod 600 "$FOLDERS/$id.meta"
    echo "DORM_FOLDER_CREATED=1"
    ;;
  --rename)
    id="${2:-}"; title="${3:-}"
    uuid "$id" || die invalid_folder_id 64
    check_folder "$id"; valid_name64 "$title"
    parent=$(folder_parent "$id")
    no_duplicate "$parent" "$title" "$id"
    printf '%s|%s\n' "$parent" "$title" > "$FOLDERS/$id.tmp"
    chmod 600 "$FOLDERS/$id.tmp"; mv "$FOLDERS/$id.tmp" "$FOLDERS/$id.meta"
    echo "DORM_FOLDER_RENAMED=1"
    ;;
  --move)
    id="${2:-}"; parent="${3:-}"
    uuid "$id" || die invalid_folder_id 64
    check_folder "$id"; check_folder "$parent"; no_cycle "$id" "$parent"
    IFS='|' read -r _ title < "$FOLDERS/$id.meta" || :
    no_duplicate "$parent" "$title" "$id"
    printf '%s|%s\n' "$parent" "$title" > "$FOLDERS/$id.tmp"
    chmod 600 "$FOLDERS/$id.tmp"; mv "$FOLDERS/$id.tmp" "$FOLDERS/$id.meta"
    echo "DORM_FOLDER_MOVED=1"
    ;;
  --delete)
    id="${2:-}"
    uuid "$id" || die invalid_folder_id 64
    check_folder "$id"
    for meta in "$FOLDERS"/*.meta; do
      [ -f "$meta" ] || continue
      IFS='|' read -r parent _ < "$meta" || :
      [ "$parent" != "$id" ] || die folder_not_empty 65
    done
    for link in "$SCRIPTS"/*.folder; do
      [ -f "$link" ] || continue
      [ "$(cat "$link")" != "$id" ] || die folder_not_empty 65
    done
    rm -f "$FOLDERS/$id.meta"
    echo "DORM_FOLDER_DELETED=1"
    ;;
  --script-folders)
    for link in "$SCRIPTS"/*.folder; do
      [ -f "$link" ] && [ ! -L "$link" ] || continue
      id="${link##*/}"; id="${id%.folder}"
      uuid "$id" || continue
      [ -f "$SCRIPTS/$id.sh" ] || continue
      folder=$(cat "$link")
      check_folder "$folder"
      printf '%s|%s\n' "$id" "$folder"
    done
    ;;
  --script-move)
    id="${2:-}"; target="${3:-}"
    uuid "$id" || die invalid_script_id 64
    [ -f "$SCRIPTS/$id.sh" ] && [ ! -L "$SCRIPTS/$id.sh" ] || die script_not_found 66
    check_folder "$target"
    [ ! -L "$SCRIPTS/$id.folder" ] || die unsafe_storage 70
    if [ "$target" = root ]; then rm -f "$SCRIPTS/$id.folder"; else
      printf '%s\n' "$target" > "$SCRIPTS/$id.folder.tmp"
      chmod 600 "$SCRIPTS/$id.folder.tmp"
      mv "$SCRIPTS/$id.folder.tmp" "$SCRIPTS/$id.folder"
    fi
    echo "DORM_SCRIPT_MOVED=1"
    ;;
  *)
    die invalid_action 64
    ;;
esac
