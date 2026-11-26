#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Regression checks for startup-hook adaptation using the build's decode."""
import sys
import unittest
from pathlib import Path
from unittest.mock import Mock

from prepare_patches import adapt_startup_patch


class StartupAdaptationTests(unittest.TestCase):
    def setUp(self):
        self.root = Path(sys.argv[1])
        self.provider = next(self.root.glob(
            'smali*/com/samsung/android/app/aodservice/provider/AODContentProvider.smali'))
        self.source = self.provider.read_text()
        self.patch = (Path(__file__).parent / 'AODService_v80.apk'
                      / '0003-Backfill-AOD-stretch-font-type-on-startup.patch').read_text()

    def adapt(self, source, count=1):
        provider = Mock()
        provider.read_text.return_value = source
        provider.relative_to.return_value = Path(
            'smali_classes5/com/samsung/android/app/aodservice/provider/AODContentProvider.smali')
        decoded = Mock()
        decoded.glob.return_value = [provider] * count
        return adapt_startup_patch(self.patch, decoded)

    def test_field_renames_and_dex_relocation(self):
        for field in ('p', 'q', 'renamedDb123'):
            with self.subTest(field=field):
                source = self.source.replace('q:Lcom/samsung/android/app/aodservice/common/provider/DB/DBAdapter;',
                                             field + ':Lcom/samsung/android/app/aodservice/common/provider/DB/DBAdapter;')
                result = self.adapt(source)
                self.assertIn('->' + field + ':Lcom/samsung/android/app/aodservice/common/provider/DB/DBAdapter;', result)
                self.assertIn('smali_classes5/com/samsung/android/app/aodservice/provider/', result)
                self.assertEqual(result.count('+    invoke-static {v1}'), 2)

    def test_changed_context_register_rejected(self):
        with self.assertRaisesRegex(ValueError, 'live Context'):
            self.adapt(self.source.replace('move-result-object v1', 'move-result-object v0'))

    def test_changed_register_layout_rejected(self):
        with self.assertRaisesRegex(ValueError, 'register layout'):
            self.adapt(self.source.replace('.method public final onCreate()Z\n    .locals 2',
                                          '.method public final onCreate()Z\n    .locals 3'))

    def test_ambiguous_provider_rejected(self):
        with self.assertRaisesRegex(ValueError, 'expected one match, found 2'):
            self.adapt(self.source, count=2)


if __name__ == '__main__':
    unittest.main(argv=[sys.argv[0]])
