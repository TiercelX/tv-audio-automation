#!/bin/bash
set -euo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "$0")/.." && pwd -P)"
LABEL="com.example.tv-g1-volume-monitor"
AGENT_DIR="$HOME/Library/LaunchAgents"
STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/g1-volume-control"
PLIST="$AGENT_DIR/$LABEL.plist"

mkdir -p "$AGENT_DIR" "$STATE_DIR"
/usr/bin/python3 - "$PROJECT_DIR" "$PLIST" "$STATE_DIR" "$LABEL" <<'PY'
import os
import plistlib
import sys

project_dir, plist_path, state_dir, label = sys.argv[1:]
document = {
    "Label": label,
    "ProgramArguments": [
        "/usr/bin/python3",
        os.path.join(project_dir, "macos", "g1-volume-monitor.py"),
    ],
    "RunAtLoad": True,
    "StandardOutPath": os.path.join(state_dir, "launchd.log"),
    "StandardErrorPath": os.path.join(state_dir, "launchd.err"),
}
with open(plist_path, "wb") as stream:
    plistlib.dump(document, stream)
PY

DOMAIN="gui/$(id -u)"
/bin/launchctl enable "$DOMAIN/$LABEL"
if /bin/launchctl print "$DOMAIN/$LABEL" >/dev/null 2>&1; then
    /bin/launchctl bootout "$DOMAIN" "$PLIST"
fi
/bin/launchctl bootstrap "$DOMAIN" "$PLIST"
/bin/launchctl kickstart -k "$DOMAIN/$LABEL"
echo "Installed and started $LABEL"
