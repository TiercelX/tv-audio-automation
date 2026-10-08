#!/usr/bin/env bash
set -euo pipefail
BASE_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
DAILY="$BASE_DIR/monitor-sa9023-volume-today.sh"
STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/sa9023-volume-control"
FAULT_FILE="$STATE_DIR/fault"
LOG_FILE="$STATE_DIR/resident.log"
mkdir -p "$STATE_DIR"
log() { printf '%s %s\n' "$(date --iso-8601=seconds)" "$*" | tee -a "$LOG_FILE"; }
if [[ -e "$FAULT_FILE" ]]; then log "fault flag exists; refusing to start: $(<"$FAULT_FILE")"; exit 1; fi
while :; do
    if ! "$DAILY"; then
        printf '%s\n' 'daily SA9023 volume monitor failed' >"$FAULT_FILE"
        log 'daily monitor failed; service stopping'
        exit 1
    fi
    next_day="$(date -d 'tomorrow 05:49:00' +%s)"
    wait_seconds=$((next_day - $(date +%s)))
    (( wait_seconds > 0 )) && sleep "$wait_seconds"
done
