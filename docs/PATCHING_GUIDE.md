# Custom Patching & Module Guide

NPL uses the **same Magisk-style modules as UN1CA**. The engine applies top-level dirs under `unica/patches/` then `unica/mods/`. You only edit the skin.

---

## 1. Create a module (fast path)

```bash
./scripts/new_module.sh patches my_feature "My Feature" "Short description"
# or: ./scripts/new_module.sh mods my_mod ...
```

That creates:

```
unica/patches/my_feature/
├── module.prop      # required (id, name, author, description)
└── customize.sh     # your logic
```

Optional next to those files:

| Path | Purpose |
|------|---------|
| `system/`, `vendor/`, … | Copied into work_dir unless `SKIPUNZIP=1` in `customize.sh` |
| `smali/**/*.patch` | Applied after `customize.sh` |
| `disable` (empty file) | Skip this module |

---

## 2. Helpers inside `customize.sh`

* `SET_PROP` / `GET_PROP` — build.prop
* `DELETE_FROM_WORK_DIR` / `ADD_TO_WORK_DIR`
* `SMALI_PATCH` — null / remove / replace / replaceall / return / strip
* `LOGI` / `LOG` / `LOGW` / `LOGE`

Example:

```bash
#!/usr/bin/env bash
LOGI "Applying My Feature..."
SET_PROP "system" "ro.my_feature.enabled" "true"
```

---

## 3. Smali paths

UN1CA/NPL patches hardcode dex folders, e.g.:

```
smali_classes2/com/android/server/pm/InstallPackageHelper.smali
```

One UI 8 `services.jar` needs **apktool ≥ 3.0.2** so those folders exist. Do not retarget to `smali/` until a decode actually has the class there. See [BUILD_TROUBLESHOOTING.md](BUILD_TROUBLESHOOTING.md).

---

## 4. Build after editing

```bash
source buildenv.sh dm1q
npl make_rom -z
```

Wipe stale apktool output if patches look “already applied”:

```bash
rm -rf out/target/dm1q/apktool out/target/dm1q/work_dir/.completed
```
