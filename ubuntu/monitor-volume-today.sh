#!/usr/bin/env bash
set -euo pipefail

# One-day supervisor for the Mi Home IR schedule plus DDC correction.
# It exits after the 22:14 final correction and never runs tomorrow.

BASE_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
CONTROL="$BASE_DIR/ddc-volume-control.sh"
STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/ddc-volume-control"
LOG_FILE="$STATE_DIR/monitor-today.log"

mkdir -p "$STATE_DIR"
exec 8>>"$LOG_FILE"
log() { printf '%s %s\n' "$(date --iso-8601=seconds)" "$*" | tee -a /dev/fd/8; }

[[ -x "$CONTROL" ]] || { log "ERROR control script is not executable"; exit 1; }

day="$(date +%F)"
now="$(date +%s)"

# Time is the DDC check time, one minute after the corresponding IR action.
# Target values are absolute VCP 0x62 values, not relative increments.
events=(
  '08:51|correct|45'
  '11:31|correct|50'
  '14:41|correct|55'
  '15:01|correct|60'
  '17:01|correct|65'
  '17:11|correct|70'
  '17:21|correct|75'
  '17:31|correct|80'
  '19:01|correct|75'
  '19:31|correct|70'
  '20:01|correct|65'
  '20:31|correct|60'
  '20:51|correct|55'
  '21:01|correct|50'
  '21:11|correct|45'
  '21:21|correct|40'
  '21:31|correct|35'
  '21:36|correct|30'
  '21:41|correct|25'
  '21:46|correct|20'
  '21:51|correct|15'
  '21:56|correct|10'
  '22:01|correct|5'
  '22:06|guard|'
  '22:11|guard|'
  '22:14|correct|1'
)

run_event() {
    local at="$1" action="$2" value="${3:-}" result rc logged_unavailable=0 write_failures=0
    log "event=$at action=$action target=${value:-guard}"
    while :; do
        if [[ "$action" == guard ]]; then
            if result="$("$CONTROL" --apply guard-low 2>&1)"; then rc=0; else rc=$?; fi
        else
            if result="$("$CONTROL" --apply correct "$value" 2>&1)"; then rc=0; else rc=$?; fi
        fi
        if (( rc == 0 )); then log "result=$result"; return 0; fi
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
        log "WARN event=$at attempt=$write_failures/3 failed; retrying same action in 5 seconds: $result"
        sleep 5
    done
}

# The service normally starts after 07:00 today. Catch up that already-passed
# checkpoint once, but do not replay the morning IR burst.
if (( now < $(date -d "$day 08:51:00" +%s) )); then
    if (( now >= $(date -d "$day 07:01:00" +%s) )); then
        run_event '07:00-catch-up' correct 40
    fi
fi

for event in "${events[@]}"; do
    IFS='|' read -r at action value <<<"$event"
    event_epoch="$(date -d "$day $at:00" +%s)"
    (( event_epoch > now )) || continue
    wait_seconds=$((event_epoch - $(date +%s)))
    (( wait_seconds > 0 )) && sleep "$wait_seconds"
    run_event "$at" "$action" "$value"
done

log 'completed today'
