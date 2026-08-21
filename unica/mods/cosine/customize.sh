SKIPUNZIP=1

# Cosine pack: wallpapers + ThemeCenter trial gate (Extra settings toggle).
# Touch npl_wallpapers/disable or theme_trial/disable to skip one piece.

COSINE_ROOT="$MODPATH"

npl_run_cosine_mod() {
  local sub="$1"
  local dir="$COSINE_ROOT/$sub"
  [ -d "$dir" ] || return 0
  [ -f "$dir/disable" ] && return 0
  [ -f "$dir/customize.sh" ] || return 0
  local saved="$MODPATH"
  MODPATH="$dir"
  # shellcheck source=/dev/null
  . "$dir/customize.sh"
  MODPATH="$saved"
}

npl_run_cosine_mod npl_wallpapers
npl_run_cosine_mod theme_trial
