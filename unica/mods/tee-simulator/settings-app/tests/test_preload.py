#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Samsung scan allowlist and explicit Settings intent must be installed together."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

APP = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('preload', APP / 'preload.py')
preload = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preload)

class PreloadTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='tee-preload-', dir='/tmp')
        self.work = Path(self.temp.name)
        self.path = self.work / preload.RELATIVE
        self.path.parent.mkdir(parents=True)
        self.original = '<config>\n    <!-- preserve -->\n    <allowed-system-preload package="existing.app"/>\n</config>\n'
        self.path.write_text(self.original)
    def tearDown(self):
        self.temp.cleanup()
    def test_add_preserves_existing_and_is_idempotent(self):
        preload.install(self.work)
        first = self.path.read_bytes()
        preload.install(self.work)
        self.assertEqual(first, self.path.read_bytes())
        self.assertIn('<!-- preserve -->', first.decode())
        self.assertIn('package="existing.app"', first.decode())
        preload.verify(self.work)
    def test_missing_malformed_duplicate_rejected(self):
        for text in ('<other/>', '<config>', '<config><allowed-system-preload package="io.mesalabs.unica.teesettings"/><allowed-system-preload package="io.mesalabs.unica.teesettings"/></config>'):
            self.path.write_text(text)
            with self.assertRaises(Exception):
                preload.install(self.work)
            self.assertEqual(text, self.path.read_text())
        self.path.unlink()
        with self.assertRaises(FileNotFoundError):
            preload.install(self.work)
    def test_route_rejected_before_entry_and_dependencies(self):
        screen = self.work / 'screen.xml'
        original = '<PreferenceScreen><intent android:targetPackage="com.android.settings" android:targetClass="io.mesalabs.unica.keybox.KeyboxActivity" xmlns:android="http://schemas.android.com/apk/res/android"/></PreferenceScreen>'
        screen.write_text(original)
        command = ['python3', str(APP / 'route_settings.py'), str(screen), str(self.work)]
        self.assertNotEqual(subprocess.run(command, capture_output=True).returncode, 0)
        self.assertEqual(original, screen.read_text())
        preload.install(self.work)
        self.assertNotEqual(subprocess.run(command, capture_output=True).returncode, 0)
        self.assertEqual(original, screen.read_text())
        for path in ('system/system/priv-app/TeeSimulatorSettings/TeeSimulatorSettings.apk', 'system/system/etc/permissions/privapp-permissions-io.mesalabs.unica.teesettings.xml', 'system/system/etc/sysconfig/preinstalled-packages-io.mesalabs.unica.teesettings.xml'):
            p = self.work / path
            p.parent.mkdir(parents=True, exist_ok=True)
            p.touch()
        for _ in range(2):
            subprocess.run(command, capture_output=True, check=True)
        self.assertIn('android:targetPackage="io.mesalabs.unica.teesettings"', screen.read_text())

if __name__ == '__main__':
    unittest.main()
