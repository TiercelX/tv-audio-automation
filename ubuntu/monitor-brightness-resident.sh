#!/usr/bin/env bash
set -euo pipefail

# Resident wrapper. Any detection/write/readback error creates a fault flag
# and stops until the operator removes it and restarts the service.

BASE_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
DAILY="$BASE_DIR/monitor-brightness-today.sh"
STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/ddc-brightness-control"
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
        reason="daily brightness monitor failed"
        printf '%s\n' "$reason" >"$FAULT_FILE"
        log "$reason; service is stopping"
        exit 1
    fi

    next_day="$(date -d 'tomorrow 05:59:00' +%s)"
    wait_seconds=$((next_day - $(date +%s)))
    (( wait_seconds > 0 )) && sleep "$wait_seconds"
done
