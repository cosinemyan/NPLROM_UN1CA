#!/usr/bin/env bash
# Scaffold a Magisk-style NPL module (UN1CA apply_modules layout).
# Usage: scripts/new_module.sh patches|mods <id> ["Name"] ["Description"]

set -e

SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KIND="${1:-}"
ID="${2:-}"
NAME="${3:-$ID}"
DESC="${4:-NPL module $ID.}"

if [[ "$KIND" != "patches" && "$KIND" != "mods" ]] || [ -z "$ID" ]; then
    echo "Usage: $(basename "$0") patches|mods <id> [\"Display name\"] [\"Description\"]" >&2
    exit 1
fi

if ! [[ "$ID" =~ ^[a-zA-Z0-9_][a-zA-Z0-9_-]*$ ]]; then
    echo "Invalid id: use letters, numbers, _ or -" >&2
    exit 1
fi

DIR="$SRC_DIR/unica/$KIND/$ID"
if [ -e "$DIR" ]; then
    echo "Already exists: $DIR" >&2
    exit 1
fi

mkdir -p "$DIR"
cat > "$DIR/module.prop" <<EOF
id=$ID
name=$NAME
author=${NPL_MAINTAINER:-Cosine}
description=$DESC
EOF

cat > "$DIR/customize.sh" <<EOF
#!/usr/bin/env bash
# $NAME — edit this file; optional system/ and smali/ live beside it.

LOGI "Applying $NAME..."

# Examples:
# SET_PROP "system" "ro.example.enabled" "true"
# SMALI_PATCH "system" "system/framework/services.jar" "smali_classes2/com/example/Foo.smali" "return" "isEnabled()Z" "true"
# DELETE_FROM_WORK_DIR "system" "system/app/SomeApp"

LOGI "$NAME applied."
EOF
chmod +x "$DIR/customize.sh"

echo "Created $DIR"
echo "  module.prop   required by apply_modules"
echo "  customize.sh  your patch logic"
echo "  (optional) system/  copied into work_dir unless SKIPUNZIP=1"
echo "  (optional) smali/*.patch  applied after customize.sh"
echo "  (optional) touch disable  to skip this module"
echo ""
echo "Rebuild with: source buildenv.sh <target> && npl make_rom"
