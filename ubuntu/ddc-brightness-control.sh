#!/usr/bin/env bash
set -euo pipefail

# Absolute-value DDC brightness control for the Philips TV.
# Dry-run is the default. Add --apply to write.

MODEL_PATTERN='PHL:PHL 436M6VBP:'
STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/ddc-brightness-control"
LOCK_FILE="$STATE_DIR/lock"

usage() {
    cat <<'EOF'
Usage:
  ddc-brightness-control.sh status
  ddc-brightness-control.sh [--apply] correct TARGET

TARGET is an absolute brightness value from 0 to 100.
The TV is rediscovered on every invocation by EDID/model.
EOF
}

die() { echo "ddc-brightness: $*" >&2; exit 1; }
unavailable() { echo "ddc-brightness: $*" >&2; exit 10; }
command -v ddcutil >/dev/null || die "ddcutil not found"
command -v flock >/dev/null || die "flock not found"

mkdir -p "$STATE_DIR"
exec 9>"$LOCK_FILE"
flock -n 9 || die "another brightness operation is already running"

discover_bus() {
    local output buses
    output="$(ddcutil detect --terse 2>/dev/null)" || unavailable "DDC detection failed (TV may be asleep or powered off)"
    buses="$(awk -v pattern="$MODEL_PATTERN" '
        /I2C bus:/ { bus=$3; sub("/dev/i2c-", "", bus) }
        index($0, pattern) { print bus }
    ' <<<"$output")"
    [[ -n "$buses" ]] || unavailable "target TV not detected (TV may be asleep or powered off)"
    [[ "$(wc -l <<<"$buses" | tr -d ' ')" == 1 ]] || die "multiple matching TVs detected"
    [[ "$buses" =~ ^[0-9]+$ ]] || die "invalid detected bus: $buses"
    printf '%s\n' "$buses"
}

read_brightness() {
    local bus="$1" line
    line="$(ddcutil getvcp 10 --bus "$bus" --terse 2>/dev/null)" || return 1
    awk '{print $4, $5}' <<<"$line"
}

[[ $# -ge 1 ]] || { usage; exit 2; }
APPLY=0
if [[ "${1:-}" == "--apply" ]]; then APPLY=1; shift; fi
ACTION="${1:-}"
bus="$(discover_bus)"
read -r before max < <(read_brightness "$bus") || unavailable "cannot read VCP 0x10 (TV may be asleep or powered off)"
[[ "$max" == 100 ]] || die "unexpected TV brightness scale: max=$max"

case "$ACTION" in
    status)
        echo "tv=PHL 436M6VBP bus=$bus brightness=$before/$max"
        exit 0
        ;;
    correct)
        target="${2:-}"
        [[ "$target" =~ ^[0-9]+$ && "$target" -le 100 ]] || die "target must be 0..100"
        ;;
    *) usage; exit 2 ;;
esac

echo "observed: bus=$bus brightness=$before/$max target=$target"
if (( before == target )); then echo "no correction needed"; exit 0; fi
if (( APPLY == 0 )); then
    echo "dry-run: add --apply to write the absolute target"
    exit 0
fi

ddcutil setvcp 10 "$target" --bus "$bus" --verify >/dev/null || die "DDC write failed"
read -r after _ < <(read_brightness "$bus") || die "verification read failed"
[[ "$after" == "$target" ]] || die "verification mismatch: expected $target, got $after"
echo "applied and verified: brightness=$after/100"
