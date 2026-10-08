#!/usr/bin/env bash
set -euo pipefail

# Mirrors the Philips TV volume checkpoints, controlling only the SA9023 sink.
BASE_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/sa9023-volume-control"
LOG_FILE="$STATE_DIR/monitor-today.log"
NODE_NAME='alsa_output.usb-SAVITECH_Bravo-X_USB_Audio-01.iec958-stereo'
mkdir -p "$STATE_DIR"
exec 8>>"$LOG_FILE"
log() { printf '%s %s\n' "$(date --iso-8601=seconds)" "$*" | tee -a /dev/fd/8; }

find_sink() {
    local status ids id info
    status="$(wpctl status -n)"
    mapfile -t ids < <(awk -v name="$NODE_NAME" 'index($0,name) && index($0,"[vol:") { line=$0; match(line, /[0-9]+[.] /); line=substr(line,RSTART,RLENGTH); sub(/[.] .*/, "", line); if (line ~ /^[0-9]+$/) print line }' <<<"$status")
    if ((${#ids[@]} == 0)); then return 10; fi
    ((${#ids[@]} == 1)) || { log "ERROR expected one SA9023 sink, found ${#ids[@]}"; return 1; }
    id="${ids[0]}"
    info="$(wpctl inspect "$id")"
    grep -Fq 'alsa.components = "USB262a:9023"' <<<"$info" || { log "ERROR sink $id does not identify as SA9023"; return 1; }
    printf '%s\n' "$id"
}

read_value() {
    local id="$1" output scalar
    output="$(wpctl get-volume "$id")"
    scalar="${output#Volume: }"
    awk -v v="$scalar" 'BEGIN { if (v !~ /^[0-9]+([.][0-9]+)?$/ || v < 0 || v > 1.0001) exit 1; printf "%d\n", (v * 100 + 0.5) }'
}

set_value() {
    local target="$1" id observed rc attempt
    [[ "$target" =~ ^[0-9]+$ ]] && (( target >= 0 && target <= 100 )) || { log "ERROR target outside 0-100: $target"; return 1; }
    for ((attempt = 1; attempt <= 18; attempt++)); do
        if id="$(find_sink)"; then break; else rc=$?; fi
        if (( rc != 10 )); then return 1; fi
        if (( attempt == 1 )); then log 'SA9023 SPDIF sink not ready; retrying every 10 seconds for up to 3 minutes'; fi
        if (( attempt == 18 )); then log 'ERROR SA9023 SPDIF sink still unavailable after 3 minutes'; return 1; fi
        sleep 10
    done
    wpctl set-volume "$id" "${target}%"
    observed="$(read_value "$id")"
    (( observed == target )) || { log "ERROR readback=$observed target=$target"; return 1; }
    log "SA9023 sink=$id volume=$observed/100 target=$target verified"
}

guard_low() {
    # TV low-volume guard target is 5; map it to the 30-100 SA9023 range.
    set_value 34
}

run_event() {
    local at="$1" action="$2" value="${3:-}"
    log "event=$at action=$action target=${value:-guard}"
    if [[ "$action" == guard ]]; then guard_low; else set_value "$value"; fi
}

day="$(date +%F)"
now="$(date +%s)"
events=(
  '05:51|set|34' '06:01|set|38' '06:11|set|42' '06:21|set|47'
  '06:31|set|51' '06:41|set|56' '06:51|set|60' '07:01|set|65'
  '08:51|set|69' '11:31|set|73' '14:41|set|78' '15:01|set|82'
  '17:01|set|87' '17:11|set|91' '17:21|set|96' '17:31|set|100'
  '19:01|set|96' '19:31|set|91' '20:01|set|87' '20:31|set|82'
  '20:51|set|78' '21:01|set|73' '21:11|set|69' '21:21|set|65'
  '21:31|set|60' '21:36|set|56' '21:41|set|51' '21:46|set|47'
  '21:51|set|42' '21:56|set|38' '22:01|set|34' '22:06|guard|'
  '22:11|guard|' '22:14|set|30'
)

if (( now >= $(date -d "$day 07:01:00" +%s) && now < $(date -d "$day 08:51:00" +%s) )); then
    run_event '07:00-catch-up' set 65
else
    # Align immediately on startup/restart with the most recent scheduled level.
    catchup_target=30
    for event in "${events[@]}"; do
        IFS='|' read -r at action value <<<"$event"
        event_epoch="$(date -d "$day $at:00" +%s)"
        (( event_epoch <= now )) || continue
        if [[ "$action" == guard ]]; then catchup_target=34; else catchup_target="$value"; fi
    done
    run_event startup-catch-up set "$catchup_target"
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
