#!/usr/bin/env bash
set -euo pipefail

NODE_NAME='alsa_output.usb-SAVITECH_Bravo-X_USB_Audio-01.iec958-stereo'

find_sink() {
    local status ids id info
    status="$(wpctl status -n)"
    mapfile -t ids < <(awk -v name="$NODE_NAME" 'index($0,name) && index($0,"[vol:") { line=$0; match(line, /[0-9]+[.] /); line=substr(line,RSTART,RLENGTH); sub(/[.] .*/, "", line); if (line ~ /^[0-9]+$/) print line }' <<<"$status")
    ((${#ids[@]} == 1)) || { echo "SA9023 SPDIF sink detection expected one match, found ${#ids[@]}" >&2; return 1; }
    id="${ids[0]}"
    info="$(wpctl inspect "$id")"
    grep -Fq 'alsa.components = "USB262a:9023"' <<<"$info" || { echo "sink $id is not SA9023" >&2; return 1; }
    printf '%s\n' "$id"
}

read_value() {
    local id="$1" output scalar
    output="$(wpctl get-volume "$id")"
    scalar="${output#Volume: }"
    awk -v v="$scalar" 'BEGIN { if (v !~ /^[0-9]+([.][0-9]+)?$/ || v < 0 || v > 1.0001) exit 1; printf "%d\n", (v * 100 + 0.5) }'
}

case "${1:-}" in
    status)
        [[ $# -eq 1 ]] || { echo "Usage: sa9023-volume-control.sh status | set VALUE" >&2; exit 2; }
        id="$(find_sink)"
        printf 'SA9023 SPDIF volume=%s/100 sink=%s\n' "$(read_value "$id")" "$id"
        ;;
    set)
        [[ $# -eq 2 && "$2" =~ ^[0-9]+$ ]] && (( $2 >= 0 && $2 <= 100 )) || { echo "Usage: sa9023-volume-control.sh set VALUE (0-100)" >&2; exit 2; }
        target="$2"
        id="$(find_sink)"
        wpctl set-volume "$id" "${target}%"
        observed="$(read_value "$id")"
        (( observed == target )) || { echo "SA9023 readback=$observed target=$target" >&2; exit 1; }
        printf 'SA9023 SPDIF volume=%s/100 verified\n' "$observed"
        ;;
    *) echo "Usage: sa9023-volume-control.sh status | set VALUE" >&2; exit 2 ;;
esac
