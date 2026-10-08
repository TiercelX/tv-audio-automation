#!/usr/bin/env python3
"""Daily G1 schedule, mapping TV volume 0-80 to G1 volume 7-100."""
from datetime import datetime, timedelta
import os
import subprocess
import time
from pathlib import Path

STATE = Path.home() / ".local/state/g1-volume-control"
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

def g1_is_default_output():
    result = subprocess.run(["/usr/sbin/system_profiler", "SPAudioDataType", "-detailLevel", "mini"],
                            check=True, capture_output=True, text=True).stdout
    lines = result.splitlines()
    for index, line in enumerate(lines):
        if line.strip() == "Sound BlasterX G1:":
            if any("Default Output Device: Yes" in following
                   for following in lines[index + 1:index + 8]):
                return True
    return False

def read_volume():
    output = subprocess.check_output(["/usr/bin/osascript", "-e",
                                       "output volume of (get volume settings)"], text=True).strip()
    value = int(output)
    if not 0 <= value <= 100:
        raise RuntimeError(f"invalid output volume readback: {output}")
    return value

def set_volume(target):
    if not g1_is_default_output():
        raise RuntimeError("Sound BlasterX G1 is not the default output device")
    subprocess.run(["/usr/bin/osascript", "-e", f"set volume output volume {target}"], check=True)
    observed = read_volume()
    # macOS rounds some requested percentages to hardware steps up to 2 points wide.
    if abs(observed - target) > 2:
        raise RuntimeError(f"G1 readback={observed} target={target}")
    return observed

def mapped(tv, evening=False):
    if evening:
        if tv >= 50:
            return 50 + int((tv - 50) * 50 / 30 + 0.5)
        return 7 + int(tv * 43 / 50 + 0.5)
    return 7 + int(tv * 93 / 80 + 0.5)

def apply_event(label, tv, evening=False):
    target = mapped(tv, evening)
    log(f"event={label} tv_target={tv} g1_target={target}")
    for attempt in range(1, 4):
        try:
            value = set_volume(target)
            log(f"G1 output volume={value}/100 verified")
            return
        except Exception as error:
            log(f"WARN event={label} attempt={attempt}/3 failed: {error}")
            if attempt < 3:
                time.sleep(5)
    STATE.mkdir(parents=True, exist_ok=True)
    (STATE / "fault").write_text(f"event={label} failed after 3 attempts\n")
    raise RuntimeError(f"event {label} failed; fault marker written")

def event_time(day, clock):
    hour, minute = map(int, clock.split(":"))
    return day.replace(hour=hour, minute=minute, second=0, microsecond=0)

def daily_events():
    events = list(EVENTS)
    # Begin a smooth one-minute ramp at 21:30 and reach the TV floor at 22:00.
    for elapsed in range(31):
        tv = int(50 * (30 - elapsed) / 30 + 0.5)
        minute_of_day = 21 * 60 + 30 + elapsed
        events.append((f"{minute_of_day // 60:02d}:{minute_of_day % 60:02d}", tv))
    events.extend([("22:06", 0), ("22:11", 0), ("22:14", 0)])
    return sorted(events, key=lambda item: item[0])

def run_day():
    now = datetime.now().astimezone()
    day = now.date()
    timed = [(event_time(now, clock), clock, tv) for clock, tv in daily_events()]
    passed = [(when, clock, tv) for when, clock, tv in timed if when <= now]
    if passed:
        _, clock, tv = passed[-1]
        apply_event("startup-catch-up", tv, clock >= "19:01")
    for when, clock, tv in timed:
        if when <= now:
            continue
        delay = (when - datetime.now().astimezone()).total_seconds()
        if delay > 0:
            time.sleep(delay)
        apply_event(clock, tv, clock >= "19:01")
    tomorrow = datetime.combine(day + timedelta(days=1), datetime.min.time()).astimezone()
    wake = tomorrow.replace(hour=5, minute=49)
    time.sleep(max(1, (wake - datetime.now().astimezone()).total_seconds()))

def main():
    if (STATE / "fault").exists():
        raise RuntimeError(f"fault marker exists: {STATE / 'fault'}")
    log("G1 volume schedule started")
    while True:
        run_day()

if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        log(f"ERROR {error}")
        raise
