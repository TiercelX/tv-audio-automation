#!/usr/bin/env python3
"""G1-based daily volume schedule for the Sound Blaster Z speaker output."""
from datetime import datetime
import subprocess
import time
from pathlib import Path

STATE = Path.home() / ".local/state/creative-g1-schedule"
LOG = STATE / "monitor.log"
EVENTS = [
    ("05:51", 5), ("06:01", 10), ("06:11", 15), ("06:21", 20),
    ("06:31", 25), ("06:41", 30), ("06:51", 35), ("07:01", 40),
    ("08:51", 45), ("11:31", 50), ("14:41", 55), ("15:01", 60),
    ("17:01", 65), ("17:11", 70), ("17:21", 75), ("17:31", 80),
    ("19:01", 75), ("19:31", 70),
    ("20:01", 65), ("20:11", 63), ("20:21", 62), ("20:31", 60),
    ("20:41", 58), ("20:51", 57), ("21:01", 55), ("21:11", 53),
    ("21:21", 52),
]

def log(message):
    STATE.mkdir(parents=True, exist_ok=True)
    line = f"{datetime.now().astimezone().isoformat(timespec='seconds')} {message}"
    with LOG.open("a") as stream:
        stream.write(line + "\n")
    print(line, flush=True)

def set_volume(target):
    # Match the persistent node name before using the default sink alias.
    output = subprocess.check_output(["wpctl", "inspect", "@DEFAULT_AUDIO_SINK@"], text=True)
    if 'node.name = "alsa_output.pci-0000_03_00.0.analog-stereo"' not in output:
        raise RuntimeError("Creative output is not the default sink")
    subprocess.run(["wpctl", "set-volume", "@DEFAULT_AUDIO_SINK@", f"{target}%"], check=True)
    output = subprocess.check_output(["wpctl", "get-volume", "@DEFAULT_AUDIO_SINK@"], text=True)
    observed = round(float(output.split()[1]) * 100)
    if abs(observed - target) > 2:
        raise RuntimeError(f"readback={observed} target={target}")
    return observed

def mapped(tv, evening=False):
    # Map the normalized original G1 curve using x^1.5 onto 13–100.
    if evening:
        original = (50 + int((tv - 50) * 50 / 30 + 0.5)
                    if tv >= 50 else 7 + int(tv * 43 / 50 + 0.5))
    else:
        original = 7 + int(tv * 93 / 80 + 0.5)
    return 13 + int(87 * ((original - 7) / 93) ** 1.5 + 0.5)

def apply_event(label, tv, evening=False):
    target = mapped(tv, evening)
    try:
        value = set_volume(target)
        log(f"event={label} tv_target={tv} output={value}/100 target={target} verified")
        return True
    except Exception as error:
        log(f"WARN event={label}: {error}")
        return False

def daily_events():
    events = list(EVENTS)
    # Begin a smooth one-minute ramp at 21:30 and reach the TV floor at 22:00.
    for elapsed in range(31):
        tv = int(50 * (30 - elapsed) / 30 + 0.5)
        minute_of_day = 21 * 60 + 30 + elapsed
        events.append((f"{minute_of_day // 60:02d}:{minute_of_day % 60:02d}", tv))
    events.extend([("22:06", 0), ("22:11", 0), ("22:14", 0)])
    return sorted(events, key=lambda item: item[0])

def main():
    log("Sound Blaster Z speaker schedule started")
    last_key = None
    preserve = STATE / "preserve-current-once"
    if preserve.exists():
        now = datetime.now().astimezone()
        current = [(clock, tv) for clock, tv in daily_events() if clock <= now.strftime("%H:%M")]
        clock, _ = current[-1] if current else daily_events()[-1]
        last_key = (now.date(), clock)
        preserve.unlink()
        log("Preserving current volume until the next scheduled event")
    while True:
        now = datetime.now().astimezone()
        events = daily_events()
        current = [(clock, tv) for clock, tv in events if clock <= now.strftime("%H:%M")]
        clock, tv = current[-1] if current else events[-1]
        key = (now.date(), clock)
        if key != last_key:
            if apply_event(clock, tv, clock >= "19:01"):
                last_key = key
        time.sleep(10)

if __name__ == "__main__":
    main()
