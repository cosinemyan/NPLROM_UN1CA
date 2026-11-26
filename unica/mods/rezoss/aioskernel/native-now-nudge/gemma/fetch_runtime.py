#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Fetch pinned public SDK artifacts into a caller-owned build directory."""
from pathlib import Path
import hashlib,json,sys,urllib.request,shutil
root=Path(__file__).resolve().parent
dest=Path(sys.argv[1]);dest.mkdir(parents=True,exist_ok=True)
cache=Path(sys.argv[2]) if len(sys.argv)>2 else None
for entry in json.loads((root/'runtime-lock.json').read_text()):
    target=dest/entry['file']
    if not target.exists():
        candidates=[] if cache is None else [cache/entry['file'],cache/'deps'/entry['file'],cache/'libs'/entry['file']]
        source=next((p for p in candidates if p.is_file()),None)
        if source:shutil.copy2(source,target)
        else:
            with urllib.request.urlopen(entry['url'],timeout=120) as src,target.open('xb') as dst:shutil.copyfileobj(src,dst,1024*1024)
    with target.open('rb') as f:digest=hashlib.file_digest(f,'sha256').hexdigest()
    if target.stat().st_size!=entry['size'] or digest!=entry['sha256']:raise SystemExit('SDK artifact integrity failure: '+entry['file'])
print('Pinned runtime artifacts verified')
