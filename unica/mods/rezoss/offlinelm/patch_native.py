#!/usr/bin/env python3
"""Guard the exact 4.0.01.19 JNI library; never overwrite the input."""
import hashlib
import struct
import sys
from pathlib import Path
src, dst = map(Path, sys.argv[1:])
b = bytearray(src.read_bytes())
if hashlib.sha256(b).hexdigest() != '19099df54b866c825a74f612b2a1bd95d5d26c7201b0b66c502b922fadcb063b':
    raise SystemExit('Unexpected JNI input; re-audit required')
# Missing-context branch, replaces the error log call, before any JNI refs/C++ locals:
# mov w19, #1; b existing stack-canary/restore epilogue. Nonzero = failure.
# Preserve 0x3b798: the valid-context path branches there from 0x3bba0.
# Executable ELF segment maps virtual addresses to file offsets minus 0x4000.
off = 0x3b790 - 0x4000
assert b[off:off+8].hex() == '2cb50094f4031faa'
b[off:off+8] = struct.pack('<II', 0x52800033, 0x14000000 | ((0x3ba4c-0x3b794)//4))
with dst.open('xb') as f:
    f.write(b)
