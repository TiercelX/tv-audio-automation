# TV and audio volume automation

Host-local automation for Philips TV/audio controls on Ubuntu, Sound BlasterX G1 output volume on macOS, and a standalone Samsung Android media-volume scheduler. Each platform has its own implementation and control API.

## Android / Samsung Galaxy A7 and Note9

The [Android app](android/README.md) provides a Chinese card-based interface for scheduled media volume, designed for continuous music playback through a wired speaker. It includes the original TV 1–80 checkpoints mapped to phone output 10%–80%, editable mapping limits, custom time/weekday rules, execution logs, and Samsung fine-volume control (0–150). It does not play music itself or require root.

Tested on Galaxy A7 SM-A7100 (Android 7.1.1) and Note9 SM-N9600 (Android 10), including short screen-off timed execution. Other devices and long-duration operation have not been verified. The master switch starts off; allow background operation before long-term use.

**[Download the Android APK](https://github.com/TiercelX/tv-audio-automation/releases/tag/android-v0.2.0)** · [Chinese instructions and build details](android/README.md)

## Ubuntu

The `ubuntu/` scripts provide:

- Philips display brightness and volume control over DDC/CI (`ddcutil`).
- SA9023 SPDIF volume control through PipeWire (`wpctl`).
- `tvctl` for status, manual level changes, and independent service switches.

The current SA9023 schedule maps TV volume 1–80 linearly to SA9023 30–100:

```text
30 + round((TV volume - 1) * 70 / 79)
```

It follows the daily TV volume checkpoints, including the 05:50–07:00 morning rise in five-point steps. The SA9023 sink is resolved by its PipeWire node name and USB product ID, not a transient sink number. The target display is bound by EDID manufacturer, product code, and serial number. Same-identity links are allowed: enabled connectors are preferred and unreadable paths fall back to another matching link. Only one path is used for each operation. A different serial number is excluded even when the model name matches. Missing or unreadable identities are retried without permanently stopping the schedule.

### Install

Requirements: Bash, Python 3, `ddcutil`, `wpctl`/PipeWire, and a user session with systemd.

Copy the project into the path used by the unit files, then install and start the services:

```bash
mkdir -p "$HOME/.local/lib"
cp -R . "$HOME/.local/lib/tv-audio-automation"
# Inspect ddcutil detect --terse and replace the example connector below.
python3 "$HOME/.local/lib/tv-audio-automation/ubuntu/ddc-discover-tv.py" --bind card1-DP-1
mkdir -p "$HOME/.config/systemd/user"
cp ubuntu/systemd/*.service "$HOME/.config/systemd/user/"
systemctl --user daemon-reload
systemctl --user enable --now tv-volume-monitor.service tv-brightness-monitor.service sa9023-volume-monitor.service
mkdir -p "$HOME/.local/bin"
ln -sf "$HOME/.local/lib/tv-audio-automation/ubuntu/tvctl" "$HOME/.local/bin/tvctl"
```

Binding writes `tv-edid-target.json` beside the installed helper. Keep this machine-specific file private; it is ignored by Git. Rebind when replacing the target TV. The example JSON uses an invalid serial placeholder deliberately; it cannot select a real display. Only EDID identity fields are compared, so input-specific timing or extension data can differ. Devices with missing, inconsistent, or cloned serial numbers cannot be reliably distinguished by this mechanism.

Tests (no hardware writes):

```bash
python3 -m unittest discover -s ubuntu -p 'test_ddc_discovery.py'
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

### Sound Blaster Z speaker output (Ubuntu)

`ubuntu/sbz-speakers-volume-monitor.py` applies the G1 time checkpoints to the
Sound Blaster Z analog stereo **speaker output** on the configured host. It only
adjusts the output when the default PipeWire sink is
`alsa_output.pci-0000_03_00.0.analog-stereo`; this PCI-specific node name may
need to be changed for another machine. It is independent of the SA9023 service.

The original G1 mapping produces an integer `g1` between 7 and 100. The speaker
output uses `round(13 + 87 * ((g1 - 7) / 93) ** 1.5)` (rounding halves up),
so the endpoints are 13% and 100%. The morning and evening checkpoints follow
the G1 schedule, including the minute-by-minute evening fade from 21:30 to
22:00. On startup the latest checkpoint is applied; subsequent manual changes
last until the next checkpoint. If the target is not the default output or a
write fails, the service retries. Volume percentages do not guarantee equal
perceived loudness across different audio devices.

To install this service after copying the repository to
`~/.local/lib/tv-audio-automation`:

```bash
mkdir -p ~/.config/systemd/user
cp ubuntu/systemd/sbz-speakers-volume-monitor.service ~/.config/systemd/user/
systemctl --user daemon-reload
systemctl --user enable --now sbz-speakers-volume-monitor.service
```

Use `systemctl --user status sbz-speakers-volume-monitor.service` to inspect it,
and `systemctl --user disable --now sbz-speakers-volume-monitor.service` to stop
and disable it. Avoid running another scheduler for the same output at the same
time. Logs are stored in `~/.local/state/creative-g1-schedule/monitor.log`.
