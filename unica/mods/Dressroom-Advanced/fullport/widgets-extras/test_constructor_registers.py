#!/usr/bin/env python3
"""Regression coverage for constructor parameter aliases and obfuscated names."""
import tempfile
import unittest
from pathlib import Path

from generate_patches import E, Patcher


class ConstructorRegistersTest(unittest.TestCase):
    def patch_constructor(self, instructions):
        with tempfile.TemporaryDirectory(prefix='dressroom-register-test-') as root:
            source = Path(root) / 'smali/aod/fa2.smali'
            source.parent.mkdir(parents=True)
            source.write_text(
                '.class public Laod/fa2;\n.super Ljava/lang/Object;\n'
                '.method public constructor <init>(I)V\n'
                '    .locals 8\n' + instructions +
                '    return-void\n.end method\n')
            patcher = Patcher(root)
            patcher.callback('aod/fa2', '<init>(I)V',
                             E + '->artworkHolder(Ljava/lang/Object;)V', ['p0'], 'O')
            return patcher.files['smali/aod/fa2.smali'][1]

    def test_preserves_class_member_literal_label_and_debug_names(self):
        instructions = (
            '    new-instance v0, Laod/v92;\n'
            '    invoke-direct {v0, p0, v1}, Laod/v92;-><init>(Laod/fa2;I)V\n'
            '    iget v1, p0, Laod/fa2;->v92:I\n'
            '    const-string v2, "v92, v8, {v9}"\n'
            '    const-string v3, "escaped \\"v92\\""\n'
            '    .local v0, "v92":Laod/v92;\n'
            '    # v92 must remain in this comment\n'
            '    goto :v92\n'
            '    :v92\n')
        result = self.patch_constructor(instructions)
        self.assertIn(instructions, result)
        self.assertNotIn('Laod/p84;', result)
        self.assertIn('.locals 9', result)

    def test_converts_real_parameter_aliases_before_adding_local(self):
        result = self.patch_constructor(
            '    move-object/from16 v0, v8\n'
            '    move/from16 v1, v9\n'
            '    invoke-direct {v8}, Ljava/lang/Object;-><init>()V\n'
            '    invoke-static/range {v8 .. v9}, Ltest/Bridge;->call(Laod/fa2;I)V\n')
        for instruction in (
            'move-object/from16 v0, p0', 'move/from16 v1, p1',
            'invoke-direct {p0}, Ljava/lang/Object;-><init>()V',
            'invoke-static/range {p0 .. p1}, Ltest/Bridge;->call(Laod/fa2;I)V',
            'move-object/from16 v8, p0',
            'invoke-static/range {v8 .. v8}, ' + E + '->artworkHolder(Ljava/lang/Object;)V',
        ):
            self.assertIn(instruction, result)


if __name__ == '__main__':
    unittest.main()
