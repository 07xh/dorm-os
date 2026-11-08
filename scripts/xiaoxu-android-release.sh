#!/usr/bin/env bash
# Only the protected main build invokes this after compiling/testing its exact SHA.
set -euo pipefail
: "${GH_TOKEN:?}" "${GITHUB_REPOSITORY:?}" "${GITHUB_SHA:?}" "${ANDROID_HOME:?}" "${RUNNER_TEMP:?}"
[[ "${GITHUB_REF:-}" == refs/heads/main ]]
version=0.8.13
code=34
tag="xiaoxu-android-v$version"
appid=io.github.x07xh.xiaoxu
out="$RUNNER_TEMP/xiaoxu-release"
secret_dir=$(mktemp -d)
trap 'rm -rf "$secret_dir"' EXIT
mkdir -p "$out"
# A new main commit must receive its own complete checks before publication.
[[ $(gh api "repos/$GITHUB_REPOSITORY/git/ref/heads/main" --jq .object.sha) == "$GITHUB_SHA" ]]
# Only an explicit GitHub 404 establishes absence; permission/network errors stop.
assert_absent() {
  local endpoint="$1" status
  if gh api --include "$endpoint" > "$secret_dir/probe-response" 2> "$secret_dir/probe-error"; then
    echo "Release/tag already exists; refusing to overwrite: $endpoint" >&2
    exit 1
  fi
  status=$(awk '/^HTTP\// {value=$2} END {print value}' "$secret_dir/probe-response")
  [[ "$status" == 404 ]] || { echo 'Unable to verify release/tag absence.' >&2; exit 1; }
}
assert_absent "repos/$GITHUB_REPOSITORY/releases/tags/$tag"
assert_absent "repos/$GITHUB_REPOSITORY/git/ref/tags/$tag"
if [[ -n ${XIAOXU_KEYSTORE_BASE64:-} ]]; then
  : "${XIAOXU_STORE_PASSWORD:?}" "${XIAOXU_KEY_PASSWORD:?}" "${XIAOXU_KEY_ALIAS:?}"
  printf '%s' "$XIAOXU_KEYSTORE_BASE64" | base64 -d > "$secret_dir/release.p12"
  printf '%s' "$XIAOXU_STORE_PASSWORD" > "$secret_dir/store-password.txt"
  printf '%s' "$XIAOXU_KEY_PASSWORD" > "$secret_dir/key-password.txt"
  alias_name="$XIAOXU_KEY_ALIAS"
  gh release download xiaoxu-android-v0.8.13 --repo "$GITHUB_REPOSITORY" \
    --pattern xiaoxu-release-certificate.pem --dir "$secret_dir/pinned"
else
  # First release only. Atomically reserve the key generation attempt BEFORE keygen.
  # A failed run cannot silently replace an issued signing identity on rerun.
  [[ "$version" == 0.8.13 && "$code" == 34 ]]
  gh api --method POST "repos/$GITHUB_REPOSITORY/git/refs" \
    -f ref=refs/tags/xiaoxu-signing-v1-bootstrap -f sha="$GITHUB_SHA" >/dev/null
  alias_name=xiaoxu-release
  password=$(openssl rand -hex 32)
  echo "::add-mask::$password"
  printf '%s' "$password" > "$secret_dir/store-password.txt"
  cp "$secret_dir/store-password.txt" "$secret_dir/key-password.txt"
  keytool -genkeypair -storetype PKCS12 -keystore "$secret_dir/release.p12" \
    -storepass:file "$secret_dir/store-password.txt" -keypass:file "$secret_dir/key-password.txt" \
    -alias "$alias_name" -keyalg RSA -keysize 3072 -validity 10000 \
    -dname 'CN=Xiaoxu Android Release' >/dev/null
fi
keytool -exportcert -rfc -keystore "$secret_dir/release.p12" \
  -storepass:file "$secret_dir/store-password.txt" -alias "$alias_name" \
  -file "$out/xiaoxu-release-certificate.pem" >/dev/null
if [[ -d "$secret_dir/pinned" ]]; then
  cmp "$out/xiaoxu-release-certificate.pem" "$secret_dir/pinned/xiaoxu-release-certificate.pem"
fi
cert_sha=$(openssl x509 -in "$out/xiaoxu-release-certificate.pem" -noout -fingerprint -sha256 | cut -d= -f2 | tr -d ':')
printf '%s' "$alias_name" > "$secret_dir/alias.txt"
python3 - "$out/release-manifest.json" "$version" "$code" "$appid" "$GITHUB_SHA" "$cert_sha" <<'PY'
import json,sys
path,version,code,appid,source,cert=sys.argv[1:]
with open(path,'w') as f: json.dump(dict(version=version,versionCode=int(code),applicationId=appid,sourceCommit=source,certificateSha256=cert),f,indent=2)
PY
cp "$out/release-manifest.json" "$secret_dir/manifest.json"
cp "$out/xiaoxu-release-certificate.pem" "$secret_dir/certificate.pem"
tar -C "$secret_dir" -cf "$secret_dir/signing-backup.tar" release.p12 store-password.txt key-password.txt alias.txt certificate.pem manifest.json
openssl cms -encrypt -binary -aes-256-cbc -in "$secret_dir/signing-backup.tar" \
  -outform DER -out "$out/signing-backup.cms" .github/xiaoxu-release/recovery-certificate.pem
apk="$out/Xiaoxu-$version.apk"
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -f -p 4 \
  apps/xiaoxu-android/app/build/outputs/apk/release/app-release-unsigned.apk "$secret_dir/aligned.apk"
"$ANDROID_HOME/build-tools/36.0.0/apksigner" sign --ks "$secret_dir/release.p12" \
  --ks-key-alias "$alias_name" --ks-pass "file:$secret_dir/store-password.txt" \
  --key-pass "file:$secret_dir/key-password.txt" --out "$apk" "$secret_dir/aligned.apk"
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs "$apk" > "$out/apk-signature.txt"
"$ANDROID_HOME/build-tools/36.0.0/aapt" dump badging "$apk" > "$out/apk-manifest.txt"
grep -Fq "package: name='$appid' versionCode='$code' versionName='$version'" "$out/apk-manifest.txt"
if grep -q 'application-debuggable' "$out/apk-manifest.txt"; then
  echo 'Refusing a debuggable formal APK.' >&2
  exit 1
fi
(cd "$out" && sha256sum "Xiaoxu-$version.apk" signing-backup.cms xiaoxu-release-certificate.pem release-manifest.json > SHA256SUMS)
# Draft assets are durable before the release becomes visible as latest.
gh api --method POST "repos/$GITHUB_REPOSITORY/git/refs" \
  -f ref="refs/tags/$tag" -f sha="$GITHUB_SHA" >/dev/null
[[ $(gh api "repos/$GITHUB_REPOSITORY/git/ref/tags/$tag" --jq .object.sha) == "$GITHUB_SHA" ]]
gh release create "$tag" --repo "$GITHUB_REPOSITORY" --verify-tag --draft \
  --title "校序 $version" --notes-file .github/xiaoxu-release/notes.md
for file in "$out"/*; do
  gh release upload "$tag" "$file" --repo "$GITHUB_REPOSITORY"
done
gh release download "$tag" --repo "$GITHUB_REPOSITORY" --pattern signing-backup.cms --dir "$secret_dir/download-check"
cmp "$out/signing-backup.cms" "$secret_dir/download-check/signing-backup.cms"
[[ $(gh api "repos/$GITHUB_REPOSITORY/git/ref/heads/main" --jq .object.sha) == "$GITHUB_SHA" ]]
gh release edit "$tag" --repo "$GITHUB_REPOSITORY" --draft=false --prerelease=false --latest
