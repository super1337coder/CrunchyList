#!/usr/bin/env bash
#
# Build a signed release APK and, if the GitHub CLI is available and logged in,
# publish it as a release.
#
# Signing comes from ~/.crunchylist/keystore.properties, which lives outside this
# repository so it cannot be committed. Without it the build still succeeds but
# produces an UNSIGNED apk, which Android will refuse to install — the script
# checks for that rather than letting you upload a dud.
#
# Usage:
#   bash tools/release.sh              # build only
#   bash tools/release.sh v0.1.0       # build, tag, and publish if gh is present
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TAG="${1:-}"
APK="$HERE/tv-app/app/build/outputs/apk/release/app-release.apk"
BT="$LOCALAPPDATA/Android/Sdk/build-tools/36.1.0"
DIST="$HERE/dist"

echo "==> building release"
bash "$HERE/tv-app/build.sh" assembleRelease >/dev/null
[ -f "$APK" ] || { echo "no APK produced"; exit 1; }

echo "==> verifying signature"
export JAVA_HOME="C:\\Program Files\\Android\\Android Studio\\jbr"
if ! "$BT/apksigner.bat" verify "$APK" >/dev/null 2>&1; then
    echo "APK IS NOT SIGNED — is ~/.crunchylist/keystore.properties present?"
    exit 1
fi
"$BT/apksigner.bat" verify --print-certs "$APK" 2>/dev/null | grep "certificate DN" | sed 's/^/    /'

VER=$(grep -oE 'versionName *= *"[^"]+"' "$HERE/tv-app/app/build.gradle.kts" | grep -oE '"[^"]+"' | tr -d '"')
mkdir -p "$DIST"
OUT="$DIST/crunchylist-tv-$VER.apk"
cp "$APK" "$OUT"
echo "==> $OUT  ($(du -h "$OUT" | cut -f1))"

[ -z "$TAG" ] && { echo "no tag given — build only. Pass a tag to publish."; exit 0; }

echo "==> tagging $TAG"
git -C "$HERE" tag -a "$TAG" -m "CrunchyList TV $VER" 2>/dev/null || echo "    tag exists, reusing"
git -C "$HERE" push origin "$TAG" 2>&1 | tail -1

if command -v gh >/dev/null 2>&1; then
    echo "==> publishing release via gh"
    gh release create "$TAG" "$OUT" \
        --title "CrunchyList TV $VER" \
        --notes-file "$HERE/docs/RELEASE-NOTES.md"
else
    cat <<EOF

The GitHub CLI isn't installed, so the release wasn't published automatically.
Either:

  winget install GitHub.cli   &&  gh auth login  &&  bash tools/release.sh $TAG

or do it once in the browser — the tag is already pushed:

  https://github.com/super1337coder/CrunchyList/releases/new?tag=$TAG

  Title:  CrunchyList TV $VER
  Notes:  docs/RELEASE-NOTES.md
  Attach: $OUT
EOF
fi
