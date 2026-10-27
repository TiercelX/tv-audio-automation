#!/usr/bin/python3
"""Select a working DDC path for the TV bound by EDID identity."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys


TARGET_FILE = Path(__file__).with_name('tv-edid-target.json')


def identity(edid):
    if len(edid) < 128 or edid[:8] != b'\x00\xff\xff\xff\xff\xff\xff\x00' or sum(edid[:128]) % 256:
        raise ValueError('invalid EDID base block')
    manufacturer = int.from_bytes(edid[8:10], 'big')
    return {
        'manufacturer': ''.join(chr(((manufacturer >> shift) & 31) + 64) for shift in (10, 5, 0)),
        'product_code': int.from_bytes(edid[10:12], 'little'),
        'serial_number': int.from_bytes(edid[12:16], 'little'),
    }


def discover(output, drm_root=Path('/sys/class/drm'), target=None):
    if target is None:
        target = json.loads(TARGET_FILE.read_text())
    if target.get('serial_number') in (None, 0, 0xffffffff):
        raise ValueError('target must have a usable EDID serial number')
    candidates = {}
    for block in re.split(r'(?m)^(?=(?:Display|Invalid display|Phantom display)\b)', output):
        if not re.match(r'Display \d+\b', block):
            continue
        bus = re.search(r'(?m)^\s*I2C bus:\s*/dev/i2c-(\d+)\s*$', block)
        connector = re.search(r'(?m)^\s*DRM connector:\s*(card\d+-[A-Za-z0-9-]+)\s*$', block)
        if not bus or not connector:
            continue
        enabled = False
        path = drm_root / connector[1]
        try:
            if identity((path / 'edid').read_bytes()) != target:
                continue
            if (path / 'status').read_text().strip() == 'disconnected':
                continue
        except (OSError, ValueError):
            continue
        try:
            enabled = (path / 'enabled').read_text().strip() == 'enabled'
        except OSError:
            pass
        candidates[bus[1]] = candidates.get(bus[1], False) or enabled
    if not candidates:
        raise ValueError('target EDID not detected (asleep, powered off or link changing)')
    # Same identity over multiple links is allowed. Never write through all links.
    return sorted(candidates, key=lambda bus: (not candidates[bus], int(bus)))


def select_bus(candidates, vcp):
    for bus in candidates:
        try:
            result = subprocess.run(['ddcutil', 'getvcp', vcp, '--bus', bus, '--terse'],
                                    capture_output=True, text=True, timeout=12, check=True)
            fields = result.stdout.split()
            if vcp == '10':
                valid = len(fields) == 5 and fields[:3] == ['VCP', '10', 'C'] and all(s.isdigit() for s in fields[3:])
            else:
                valid = len(fields) == 7 and fields[:3] == ['VCP', '62', 'CNC'] and all(re.fullmatch(r'x[0-9a-fA-F]+', s) for s in fields[3:])
            if valid:
                return bus
        except (subprocess.SubprocessError, OSError):
            pass
        print(f'ddc-discover: bus={bus} VCP={vcp} unreadable; trying next matching EDID link', file=sys.stderr)
    raise ValueError(f'no matching EDID link can read VCP {vcp}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--vcp', choices=('10', '62'))
    mode.add_argument('--bind', metavar='CONNECTOR', help='bind EDID from a DRM connector, e.g. card1-DP-1')
    args = parser.parse_args()
    try:
        if args.bind:
            if not re.fullmatch(r'card\d+-[A-Za-z0-9-]+', args.bind):
                raise ValueError('invalid DRM connector name')
            target = identity((Path('/sys/class/drm') / args.bind / 'edid').read_bytes())
            if target['serial_number'] in (0, 0xffffffff):
                raise ValueError('EDID has no usable serial number; refusing model-only binding')
            TARGET_FILE.write_text(json.dumps(target, indent=2) + '\n')
            print('Target EDID saved locally; do not commit tv-edid-target.json')
            return 0
        output = subprocess.run(['ddcutil', 'detect', '--terse'], capture_output=True,
                                text=True, timeout=20, check=True).stdout
        print(select_bus(discover(output), args.vcp))
    except (subprocess.SubprocessError, OSError, ValueError) as exc:
        print(f'ddc-discover: {exc}', file=sys.stderr)
        return 10
    return 0


if __name__ == '__main__':
    sys.exit(main())
