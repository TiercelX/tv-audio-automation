#!/usr/bin/env bash
set -euo pipefail

# Resident wrapper: run one day's schedule, sleep until the next day.
# A control/detection error creates a fault flag and stops permanently until
# the operator removes the flag and restarts the service.

BASE_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
DAILY="$BASE_DIR/monitor-volume-today.sh"
STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/ddc-volume-control"
LOG_FILE="$STATE_DIR/resident.log"
FAULT_FILE="$STATE_DIR/fault"
mkdir -p "$STATE_DIR"

log() { printf '%s %s\n' "$(date --iso-8601=seconds)" "$*" | tee -a "$LOG_FILE"; }

if [[ -e "$FAULT_FILE" ]]; then
    log "fault flag exists; refusing to start: $(<"$FAULT_FILE")"
    exit 1
fi

while :; do
    if ! "$DAILY"; then
        reason="daily volume monitor failed"
        printf '%s\n' "$reason" >"$FAULT_FILE"
        log "$reason; service is stopping"
        exit 1
    fi

    # The daily script ends after 22:14. Sleep until the next day's first
    # morning checkpoint, without polling the TV overnight.
    next_day="$(date -d 'tomorrow 05:49:00' +%s)"
    wait_seconds=$((next_day - $(date +%s)))
    (( wait_seconds > 0 )) && sleep "$wait_seconds"
done
