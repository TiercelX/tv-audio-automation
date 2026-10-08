#!/usr/bin/env bash
set -euo pipefail

# Absolute-value DDC correction for the TV volume.
# Dry-run is the default. Add --apply to write.

MODEL_PATTERN='PHL:PHL 436M6VBP:'
STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/ddc-volume-control"
LOG_FILE="$STATE_DIR/events.log"
LOCK_FILE="$STATE_DIR/lock"

usage() {
    cat <<'EOF'
Usage:
  ddc-volume-control.sh status
  ddc-volume-control.sh [--apply] correct TARGET
  ddc-volume-control.sh [--apply] guard-low

Commands:
  status          Read and report the current TV volume.
  correct TARGET  Set the TV directly to TARGET (0..100) if different.
  guard-low       0 -> 1, 1..5 -> no-op, above 5 -> 5.

The TV is rediscovered on every invocation by EDID/model, so the I2C bus
number may change after a display-link re-enumeration.
EOF
}

die() { echo "ddc-volume: $*" >&2; exit 1; }
unavailable() { echo "ddc-volume: $*" >&2; exit 10; }
command -v ddcutil >/dev/null || die "ddcutil not found"
command -v flock >/dev/null || die "flock not found"

mkdir -p "$STATE_DIR"
exec 9>"$LOCK_FILE"
flock -n 9 || die "another DDC operation is already running"

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

read_volume() {
    local bus="$1" line current_hex max_hex
    line="$(ddcutil getvcp 62 --bus "$bus" --terse 2>/dev/null)" || return 1
    current_hex="$(awk '{print $7}' <<<"$line")"
    max_hex="$(awk '{print $5}' <<<"$line")"
    [[ "$current_hex" == x* && "$max_hex" == x* ]] || return 1
    printf '%s %s\n' "$((16#${current_hex#x}))" "$((16#${max_hex#x}))"
}

log_event() {
    printf '%s action=%s bus=%s before=%s target=%s after=%s\n' \
        "$(date --iso-8601=seconds)" "$1" "$2" "$3" "$4" "$5" >>"$LOG_FILE"
}

show_status() {
    local bus current max
    bus="$(discover_bus)"
    read -r current max < <(read_volume "$bus") || unavailable "cannot read VCP 0x62 (TV may be asleep or powered off)"
    echo "tv=PHL 436M6VBP bus=$bus volume=$current/$max"
}

[[ $# -ge 1 ]] || { usage; exit 2; }
APPLY=0
if [[ "${1:-}" == "--apply" ]]; then APPLY=1; shift; fi
ACTION="${1:-}"
[[ "$ACTION" == status ]] && { show_status; exit 0; }

bus="$(discover_bus)"
read -r before max < <(read_volume "$bus") || unavailable "cannot read VCP 0x62 (TV may be asleep or powered off)"
[[ "$max" == 100 ]] || die "unexpected TV volume scale: max=$max"

case "$ACTION" in
    correct)
        target="${2:-}"
        [[ "$target" =~ ^[0-9]+$ && "$target" -le 100 ]] || die "target must be 0..100"
        ;;
    guard-low)
        if (( before == 0 )); then target=1
        elif (( before <= 5 )); then target="$before"
        else target=5
        fi
        ;;
    *) usage; exit 2 ;;
esac

echo "observed: bus=$bus volume=$before/$max target=$target"
if (( before == target )); then
    echo "no correction needed"
    log_event "$ACTION" "$bus" "$before" "$target" "$before"
    exit 0
fi
if (( APPLY == 0 )); then
    echo "dry-run: add --apply to write the absolute target"
    exit 0
fi

ddcutil setvcp 62 "$target" --bus "$bus" --verify >/dev/null || die "DDC write failed"
read -r after _ < <(read_volume "$bus") || die "verification read failed"
[[ "$after" == "$target" ]] || die "verification mismatch: expected $target, got $after"
log_event "$ACTION" "$bus" "$before" "$target" "$after"
echo "applied and verified: volume=$after/100"
