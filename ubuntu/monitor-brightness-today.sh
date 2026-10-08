#!/usr/bin/env bash
set -euo pipefail

# One-day absolute DDC brightness schedule.
# Values are intentionally discrete 5-minute checkpoints; every write is
# verified by ddc-brightness-control.sh.

BASE_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
CONTROL="$BASE_DIR/ddc-brightness-control.sh"
STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/ddc-brightness-control"
LOG_FILE="$STATE_DIR/monitor-today.log"

mkdir -p "$STATE_DIR"
exec 8>>"$LOG_FILE"
log() { printf '%s %s\n' "$(date --iso-8601=seconds)" "$*" | tee -a /dev/fd/8; }

[[ -x "$CONTROL" ]] || { log "ERROR control script is not executable"; exit 1; }

day="$(date +%F)"
now="$(date +%s)"
events=()

add_ramp() {
    local start="$1" end_minutes="$2" from="$3" to="$4"
    local minute target at
    for minute in $(seq 0 5 "$end_minutes"); do
        target=$((from + (to - from) * minute / end_minutes))
        at="$(date -d "$day $start:00 + $minute minutes" +%H:%M)"
        events+=("$at|$target")
    done
}

# 07:30 -> 08:00: 0 to 30.
add_ramp '07:30' 30 0 30
# 08:00 -> 08:30: 30 to 40.
add_ramp '08:00' 30 30 40
# 15:30 -> 16:30: 40 to 30.
add_ramp '15:30' 60 40 30
# 22:00 -> 23:00: 30 to 0.
add_ramp '22:00' 60 30 0

run_event() {
    local at="$1" target="$2" result rc logged_unavailable=0 write_failures=0
    log "event=$at target=$target"
    while :; do
        if result="$($CONTROL --apply correct "$target" 2>&1)"; then
            log "result=$result"
            return 0
        else
            rc=$?
        fi
        if (( rc == 10 )); then
            if (( logged_unavailable == 0 )); then
                log "TV unavailable at event=$at; retrying every 30 seconds: $result"
                logged_unavailable=1
            fi
            sleep 30
            continue
        fi

        write_failures=$((write_failures + 1))
        if (( write_failures >= 3 )); then
            log "ERROR event=$at failed after 3 attempts: $result"
            return 1
        fi
        log "WARN event=$at attempt=$write_failures/3 failed; retrying same absolute target in 5 seconds: $result"
        sleep 5
    done
}

# Catch up exactly once to the latest checkpoint when the service starts late.
last_at=""
last_target=""
for event in "${events[@]}"; do
    IFS='|' read -r at target <<<"$event"
    event_epoch="$(date -d "$day $at:00" +%s)"
    if (( event_epoch <= now )); then
        last_at="$at"
        last_target="$target"
    fi
done
if [[ -n "$last_at" ]]; then
    run_event "$last_at-catch-up" "$last_target"
fi

for event in "${events[@]}"; do
    IFS='|' read -r at target <<<"$event"
    event_epoch="$(date -d "$day $at:00" +%s)"
    (( event_epoch > now )) || continue
    wait_seconds=$((event_epoch - $(date +%s)))
    (( wait_seconds > 0 )) && sleep "$wait_seconds"
    run_event "$at" "$target"
done

log 'completed today'
