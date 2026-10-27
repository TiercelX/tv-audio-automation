import importlib.util
import json
import os
from pathlib import Path
import subprocess
import shutil
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('discovery', Path(__file__).with_name('ddc-discover-tv.py'))
discovery = importlib.util.module_from_spec(spec)
spec.loader.exec_module(discovery)
TARGET = {'manufacturer': 'PHL', 'product_code': 49529, 'serial_number': 123456789}


def edid(serial=123456789, product=49529, extra=0):
    data = bytearray(128)
    data[:8] = b'\x00\xff\xff\xff\xff\xff\xff\x00'
    data[8:10] = bytes.fromhex('410c')
    data[10:12] = product.to_bytes(2, 'little')
    data[12:16] = serial.to_bytes(4, 'little')
    data[20] = extra
    data[127] = (-sum(data)) % 256
    return bytes(data)


def display(bus, header='Display 1'):
    return f'{header}\n I2C bus: /dev/i2c-{bus}\n DRM connector: card1-DP-{bus}\n Monitor: PHL:PHL 436M6VBP:\n\n'


class DiscoveryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def connector(self, bus, enabled='enabled', status='connected', data=None):
        path = self.root / f'card1-DP-{bus}'
        path.mkdir()
        (path / 'enabled').write_text(enabled)
        (path / 'status').write_text(status)
        (path / 'edid').write_bytes(edid() if data is None else data)

    def find(self, output):
        return discovery.discover(output, self.root, TARGET)

    def test_bound_identity_and_changed_bus(self):
        self.connector(19)
        self.assertEqual(self.find(display(19)), ['19'])

    def test_same_model_different_serial_excluded(self):
        self.connector(6, data=edid(serial=9999))
        self.connector(7)
        self.assertEqual(self.find(display(6) + display(7)), ['7'])

    def test_same_serial_different_product_excluded(self):
        self.connector(6, data=edid(product=1234))
        with self.assertRaisesRegex(ValueError, 'target EDID not detected'):
            self.find(display(6))

    def test_matching_identity_multiple_links_allowed(self):
        self.connector(6, 'disabled')
        self.connector(7, data=edid(extra=1))
        self.assertEqual(self.find(display(6) + display(7)), ['7', '6'])

    def test_both_enabled_and_duplicate_records(self):
        self.connector(6)
        self.connector(7)
        self.assertEqual(self.find(display(7) + display(6) * 2), ['6', '7'])

    def test_disconnected_and_invalid_edid_skipped(self):
        self.connector(6, status='disconnected')
        self.connector(7, data=b'broken')
        with self.assertRaises(ValueError):
            self.find(display(6) + display(7))

    def test_bad_checksum_skipped(self):
        self.connector(6, data=edid()[:-1] + b'\x00')
        with self.assertRaises(ValueError):
            self.find(display(6))

    def test_unknown_identity_never_falls_back_to_model(self):
        with self.assertRaises(ValueError):
            self.find(display(6))

    def test_invalid_and_phantom_displays_skipped(self):
        self.connector(6)
        with self.assertRaises(ValueError):
            self.find(display(6, 'Invalid display') + display(6, 'Phantom display'))

    def test_first_readable_link_selected_no_fanout(self):
        with patch.object(discovery.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, 'VCP 10 C 30 100')) as run:
            self.assertEqual(discovery.select_bus(['7', '6'], '10'), '7')
            self.assertEqual(run.call_count, 1)

    def test_failed_link_falls_back_to_same_identity(self):
        failure = subprocess.CalledProcessError(1, 'ddcutil')
        ok = subprocess.CompletedProcess([], 0, 'VCP 62 CNC x00 x64 x00 x3c')
        with patch.object(discovery.subprocess, 'run', side_effect=[failure, ok]) as run:
            self.assertEqual(discovery.select_bus(['7', '6'], '62'), '6')
            self.assertEqual(run.call_count, 2)

    def test_malformed_probe_not_accepted(self):
        with patch.object(discovery.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, 'VCP 10 ERR')):
            with self.assertRaises(ValueError):
                discovery.select_bus(['7'], '10')

    def test_controls_return_retry_code_for_unverified_target(self):
        fake = self.root / 'ddcutil'
        fake.write_text('#!/usr/bin/python3\nprint(' + repr(display(6).replace('card1-', 'card999-')) + ')\n')
        fake.chmod(0o755)
        controls = self.root / 'controls'
        controls.mkdir()
        for name in ('ddc-discover-tv.py', 'ddc-brightness-control.sh', 'ddc-volume-control.sh'):
            shutil.copy2(Path(__file__).with_name(name), controls / name)
        (controls / 'tv-edid-target.json').write_text(json.dumps(TARGET))
        env = dict(os.environ, PATH=str(self.root) + ':' + os.environ['PATH'], XDG_STATE_HOME=str(self.root / 'state'))
        for name in ('ddc-brightness-control.sh', 'ddc-volume-control.sh'):
            result = subprocess.run([str(controls / name), 'status'], capture_output=True, text=True, env=env, timeout=10)
            self.assertEqual(result.returncode, 10, result.stderr)
            self.assertIn('target EDID not detected', result.stderr)

    def test_bind_saves_local_identity(self):
        target_path = self.root / 'tv-edid-target.json'
        with patch.object(discovery.sys, 'argv', ['helper', '--bind', 'card1-DP-1']), \
             patch.object(discovery.Path, 'read_bytes', return_value=edid()), \
             patch.object(discovery, 'TARGET_FILE', target_path):
            self.assertEqual(discovery.main(), 0)
        self.assertEqual(json.loads(target_path.read_text()), TARGET)

    def test_bind_rejects_path_traversal(self):
        with patch.object(discovery.sys, 'argv', ['helper', '--bind', '../other']):
            self.assertEqual(discovery.main(), 10)


if __name__ == '__main__':
    unittest.main()
