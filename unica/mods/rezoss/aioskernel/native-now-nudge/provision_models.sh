#!/system/bin/sh
set -eu
APP=/data/user/0/com.samsung.android.aioskernelservice
SOURCE=/data/local/tmp/rezoss-nudge-install
mkdir -p "$APP/files/models"
cp "$SOURCE/hybrid_llama_qnn.pte" "$APP/files/models/hybrid_llama_qnn.pte"
cp "$SOURCE/tokenizer.json" "$APP/files/models/tokenizer.json"
chown -R "$(stat -c %u "$APP"):$(stat -c %g "$APP")" "$APP/files/models"
chmod 700 "$APP/files/models"
chmod 600 "$APP/files/models/hybrid_llama_qnn.pte" "$APP/files/models/tokenizer.json"
restorecon -RF "$APP/files/models"
