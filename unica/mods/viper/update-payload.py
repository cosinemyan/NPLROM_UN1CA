#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
import hashlib
import json
from pathlib import Path

here = Path(__file__).resolve().parent
pinned = dict(line.split(None, 1)[::-1] for line in (here / 'upstream.sha256').read_text().splitlines())
for name, expected in pinned.items():
    if hashlib.sha256((here / name).read_bytes()).hexdigest() != expected:
        raise SystemExit(f'Upstream checksum mismatch: {name}')
manifest = {
    'app': {'repository': 'https://github.com/likelikeslike/ViPER4Android',
            'commit': '1f0c824f3f28040f64790a4fd2c22543059152a4', 'version': '2.1.1'},
    'driver': {'repository': 'https://github.com/likelikeslike/ViPERFX_RE',
               'release': 'v2.1.1', 'variant': 'non-AIDL'},
    'sha256': {name: hashlib.sha256((here / name).read_bytes()).hexdigest()
               for name in ('ViPER4Android.apk', 'ViPER4Android-RE-v2.1.1.zip', 'upstream-app-2.1.1.tar.gz')},
}
(here / 'payload.json').write_text(json.dumps(manifest, indent=2) + '\n')
