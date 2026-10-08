# TV and audio volume automation

Small, host-local automation scripts for Philips TV controls on Ubuntu and Sound BlasterX G1 output volume on macOS. The project keeps the two host implementations separate; they use different control APIs and schedules.

## Ubuntu

The `ubuntu/` scripts provide:

- Philips display brightness and volume control over DDC/CI (`ddcutil`).
- SA9023 SPDIF volume control through PipeWire (`wpctl`).
- `tvctl` for status, manual level changes, and independent service switches.

The current SA9023 schedule maps TV volume 1–80 linearly to SA9023 30–100:

```text
30 + round((TV volume - 1) * 70 / 79)
```

It follows the daily TV volume checkpoints, including the 05:50–07:00 morning rise in five-point steps. The SA9023 sink is resolved by its PipeWire node name and USB product ID, not a transient sink number. The Philips EDID pattern is configured in `ddc-volume-control.sh` and `ddc-brightness-control.sh`; edit it for a different display.

### Install

Requirements: Bash, `ddcutil`, `wpctl`/PipeWire, and a user session with systemd.

Copy the project into the path used by the unit files, then install and start the services:

```bash
mkdir -p "$HOME/.local/lib"
cp -R . "$HOME/.local/lib/tv-audio-automation"
mkdir -p "$HOME/.config/systemd/user"
cp ubuntu/systemd/*.service "$HOME/.config/systemd/user/"
systemctl --user daemon-reload
systemctl --user enable --now tv-volume-monitor.service tv-brightness-monitor.service sa9023-volume-monitor.service
mkdir -p "$HOME/.local/bin"
ln -sf "$HOME/.local/lib/tv-audio-automation/ubuntu/tvctl" "$HOME/.local/bin/tvctl"
```

Examples:

```bash
tvctl --status
tvctl --volume 40
tvctl --sa9023-volume 60
tvctl --volume-auto on
tvctl --sa9023-volume-auto on
```

## macOS / Hackintosh

The `macos/` scripts control the macOS output volume only when **Sound BlasterX G1** is the default output device. The TV schedule values and times remain the source checkpoints; only G1 output targets are mapped. Daytime maps TV 0–80 to G1 7–100. The evening curve maps TV 80 to G1 100, TV 50 at 21:30 to G1 50, and TV 0 at 22:00 to G1 7; earlier evening reductions use the upper half of this curve. Manual G1 volume accepts 7–100. macOS may round volume to nearby hardware steps; readback allows a two-point difference.

### Install

Requirements: macOS, Python 3 at `/usr/bin/python3`, and Sound BlasterX G1 selected as the default output.

Run from the project directory:

```bash
./macos/install-launch-agent.sh
./macos/g1ctl status
```

Manual control and service commands:

```bash
./macos/g1ctl volume 50
./macos/g1ctl auto off
./macos/g1ctl auto on
./macos/g1ctl log 50
```

The launch agent and logs are created under the current user's home directory. No host address, account name, or machine-specific absolute path is embedded in the repository.
