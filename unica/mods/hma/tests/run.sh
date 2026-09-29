#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
set -euo pipefail
if [[ $# != 2 ]]; then
    echo "Usage: bash unica/mods/hma/tests/run.sh <android.jar> <json.jar>" >&2
    exit 2
fi
HMA_ANDROID=$(realpath "$1")
HMA_JSON=$(realpath "$2")
HMA_MOD=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
HMA_TEST=$(mktemp -d /tmp/unica-hma-tests.XXXXXXXX)
mkdir -p "$HMA_TEST/stubs/android/content/pm" "$HMA_TEST/stubs/android/os" "$HMA_TEST/classes" "$HMA_TEST/test-classes"
cat > "$HMA_TEST/stubs/android/content/pm/ApplicationInfo.java" <<'EOF'
package android.content.pm; public class ApplicationInfo {public String packageName,sourceDir; public String[] splitSourceDirs; public int flags;}
EOF
cat > "$HMA_TEST/stubs/android/os/SystemClock.java" <<'EOF'
package android.os; public class SystemClock {public static long elapsedRealtime(){return 0;}}
EOF
javac --release 8 -cp "$HMA_ANDROID:$HMA_JSON" -d "$HMA_TEST/classes" "$HMA_MOD"/src/io/mesalabs/unica/*.java
javac --release 8 -cp "$HMA_ANDROID:$HMA_JSON:$HMA_TEST/classes" -d "$HMA_TEST/test-classes" \
    "$HMA_TEST/stubs/android/content/pm/ApplicationInfo.java" "$HMA_TEST/stubs/android/os/SystemClock.java" "$HMA_MOD/tests/PolicyHostTest.java"
java -Djava.io.tmpdir="$HMA_TEST" -cp "$HMA_TEST/test-classes:$HMA_TEST/classes:$HMA_ANDROID:$HMA_JSON" io.mesalabs.unica.PolicyHostTest
echo "Host test artifacts: $HMA_TEST"
