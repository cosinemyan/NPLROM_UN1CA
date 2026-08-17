#!/usr/bin/env bash
# Galaxy S23+ (dm2q) Debloat List

LOGI "Executing Galaxy S23+ debloat cleanup..."

debloat_apps=(
  "system/priv-app/SamsungPay"
  "system/app/MetaAppManager"
  "product/priv-app/Facebook"
)

for app in "${debloat_apps[@]}"; do
  DELETE_FROM_WORK_DIR "$(cut -d "/" -f 1 <<< "$app")" "$(cut -d "/" -f 2- <<< "$app")" || true
done

LOGI "Galaxy S23+ debloat completed."
