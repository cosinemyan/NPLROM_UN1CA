# NPL ROM (UN1CA fork)

This tree is **[UN1CA](https://github.com/salvogiangri/UN1CA) / sixteen** with an NPL skin.

- **Engine:** keep `scripts/`, `external/`, `unica/` folder names so you can `git fetch upstream && git rebase upstream/sixteen`.
- **Skin:** branding in `unica/configs/version.sh`, NPL Settings, extra modules (`csc`, `dfe`, `knoxpatch`, `playintegrity`), `npl_menu.sh`.
- **Devices:** S23 family — `target/dm1q` (SM-S911B), `target/dm2q` (SM-S916B), `target/dm3q` (SM-S918B).

```bash
./npl_menu.sh
# or
source buildenv.sh dm1q
npl make_rom -z    # alias of unica
```

Do **not** copy patches from a second tree by hand. Pull UN1CA, then add/edit modules under `unica/patches` or `unica/mods` (`./scripts/new_module.sh`).
