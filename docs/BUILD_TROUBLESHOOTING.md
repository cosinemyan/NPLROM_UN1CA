# Build log: UN1CA vs NPL

NPL is now meant to be the **UN1CA build engine** with an **NPL skin** (`unica/patches`, `unica/mods`, branding). This doc explains the failure modes that showed up when the skin was present but the engine was not.

`[INFO] All modules applied successfully` after red `[ERROR]` lines is **not** a good build.

---

## Engine layout (aligned)

| Piece | UN1CA | NPL (current) |
| :--- | :--- | :--- |
| Toolchain | `build_dependencies.sh` → `out/tools/bin` | Same + `tools/setup.sh` fast path (apktool **3.0.2**, `mkuserimg_mke2fs`) |
| `TOOLS_DIR` | `$OUT_DIR/tools` | `$OUT_DIR/tools` via `buildenv.sh` |
| Work dir | SOURCE system over TARGET device | Same (`create_work_dir.sh`) |
| Modules | Magisk-style `module.prop` + `customize.sh` | Same under `unica/patches` / `unica/mods` |
| `apply_modules.sh` | `set -e` only | Same (UN1CA copy) |
| Debloat | `unica/debloat.sh` | `npl/debloat.sh` |
| New module | copy a folder | `./scripts/new_module.sh patches\|mods <id>` |

Skin stays in `npl/`; do not rename it to `unica/`. Props like `ro.unica.version` remain for compatibility with settings smali.

---

## Firmware modes (menu Step 1)

| Mode | SOURCE | TARGET | When to use |
| :--- | :--- | :--- | :--- |
| **qssi** (default) | `npl/configs/qssi.sh` (SM-S911B base) | Device firmware | UN1CA-style: shared system for S23 / + / Ultra |
| **native** | Same as TARGET | Device firmware | Per-device only — max compatibility, no base overlay |

### How download knows the model

Each device has `target/<codename>/config.sh` with:

```bash
TARGET_FIRMWARE="SM-S91xB/EUX/<IMEI_or_TAC>"
```

`download_fw` / `extract_fw` read **SOURCE_FIRMWARE** + **TARGET_FIRMWARE** from `out/config.sh` (built by `gen_config` from `qssi.sh` + that target file). Format is `MODEL/CSC/IMEI` (samloader accepts a TAC = first 8 digits).

| Codename | Model | Config |
| :--- | :--- | :--- |
| dm1q | SM-S911B | `target/dm1q/config.sh` |
| dm2q | SM-S916B | `target/dm2q/config.sh` |
| dm3q | SM-S918B | `target/dm3q/config.sh` |

Step 2 “all S23 models” loops those configs and downloads each `TARGET_FIRMWARE`, plus the QSSI base once.

---

## Error classes (historical)

### 1. Bluetooth APEX — `mkuserimg_mke2fs: command not found`

Hex patch applied; APEX repack needs `out/tools/bin/mkuserimg_mke2fs` (and `mke2fs.android` from android-tools submodule).

`npl_menu.sh` **Step 0** runs submodule init + `./tools/setup.sh`. CLI equivalent:

```bash
git submodule update --init --recursive
./tools/setup.sh
# or: scripts/build_dependencies.sh
```

### 2. `services.jar` — incomplete decode / wrong apktool

DEX 041 multi-dex needs **apktool ≥ 3.0.2**. Stock 2.10 only emits `smali/` so UN1CA paths like `smali_classes2/.../InstallPackageHelper.smali` never exist. Do not retarget patches until a 3.x decode creates those files.

### 3. `SMALI_PATCH` / `METHOD: unbound variable`

`replaceall` has no method name. NPL’s old `set -u` + a buggy method grep (`grep … "$METHOD" "$file"` under `pipefail`) caused false “method not found” and crashes. Fixed in `scripts/utils/smali_utils.sh`.

### 4. Expected noise when SOURCE == TARGET

Camera/saiv `File not found` for some TARGET overlay blobs is normal on native dm1q. “Already applied / reversed” often means leftover `out/target/*/apktool` — use menu Step 4 **option 2 or 3**, or:

```bash
source buildenv.sh dm1q && npl make_rom -f -z
```

---

## Dev: add or edit a patch

See [PATCHING_GUIDE.md](PATCHING_GUIDE.md). Short version:

```bash
./scripts/new_module.sh patches my_thing "My Thing"
# edit unica/patches/my_thing/customize.sh
source buildenv.sh dm1q && npl make_rom
```
