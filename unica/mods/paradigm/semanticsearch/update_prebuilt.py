#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Cache newer Galaxy Store SemanticSearchCore APKs before ROM patching."""
import argparse
import fcntl
import re
import shutil
import subprocess
import tempfile
import zipfile
from datetime import datetime
from pathlib import Path

PACKAGE = 'com.samsung.android.ssco'
NAME = 'SemanticSearchCore.apk'
CHUNK_SIZE = 52428800


def version(apk):
    with zipfile.ZipFile(apk) as archive:
        if archive.testzip() is not None:
            raise ValueError('APK ZIP integrity check failed')
    result = subprocess.run(['aapt2', 'dump', 'badging', str(apk)],
                            check=True, capture_output=True, text=True)
    match = re.search(r"^package: name='([^']+)' versionCode='(\d+)'", result.stdout, re.M)
    if not match or match[1] != PACKAGE:
        raise ValueError('APK package is not ' + PACKAGE)
    major = re.search(r"versionCodeMajor='(\d+)'", result.stdout)
    return ((int(major[1]) if major else 0) << 32) | int(match[2])


def update(folder, url, backups):
    # Serialize cache changes across concurrent builds.
    backups.mkdir(parents=True, exist_ok=True)
    with (backups / '.update.lock').open('w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        with tempfile.TemporaryDirectory(prefix='semanticsearch-update-') as scratch:
            scratch = Path(scratch)
            current = folder / NAME
            chunks = sorted(folder.glob(NAME + '.[0-9][0-9]'))
            if not current.exists():
                if not chunks or chunks[0].suffix != '.00':
                    raise ValueError('No SemanticSearchCore prebuilt available')
                current = scratch / NAME
                with current.open('wb') as output:
                    for index, chunk in enumerate(chunks):
                        if chunk.suffix != f'.{index:02d}':
                            raise ValueError('Prebuilt chunks are not contiguous')
                        with chunk.open('rb') as source:
                            shutil.copyfileobj(source, output)
            old_version = version(current)
            if not url or url.startswith('galaxy-store-source:'):
                print(f'SemanticSearchCore: Store unavailable; using prebuilt {old_version}')
                return
            candidate = scratch / 'download.apk'
            try:
                subprocess.run(['curl', '--fail', '--location', '--retry', '2',
                                '--connect-timeout', '30', '--max-time', '1800',
                                '--output', str(candidate), url], check=True)
                new_version = version(candidate)
            except (subprocess.CalledProcessError, ValueError, zipfile.BadZipFile) as error:
                print(f'SemanticSearchCore: Store download invalid/unavailable ({type(error).__name__}); using prebuilt {old_version}')
                return
            if new_version <= old_version:
                print(f'SemanticSearchCore: Store {new_version} is not newer than prebuilt {old_version}')
                return
            # Prepare all files before changing the cache. Keep the old cache
            # for rollback; the full APK remains locally ignored by Git.
            with tempfile.TemporaryDirectory(prefix='.ssco-stage-', dir=folder.parent) as staging:
                staging = Path(staging)
                shutil.copy2(candidate, staging / NAME)
                if candidate.stat().st_size > CHUNK_SIZE:
                    with candidate.open('rb') as source:
                        index = 0
                        while data := source.read(CHUNK_SIZE):
                            (staging / f'{NAME}.{index:02d}').write_bytes(data)
                            index += 1
                backup = backups / datetime.now().strftime('%Y%m%d_%H%M%S_%f')
                backup.mkdir(parents=True)
                previous = ([folder / NAME] if (folder / NAME).exists() else []) + chunks
                for path in previous:
                    shutil.copy2(path, backup / path.name)
                try:
                    for path in previous:
                        path.unlink()
                    for path in staging.iterdir():
                        path.replace(folder / path.name)
                except OSError:
                    for path in folder.glob(NAME + '*'):
                        if path.name == NAME or re.fullmatch(re.escape(NAME) + r'\.\d{2}', path.name):
                            path.unlink()
                    for path in backup.iterdir():
                        shutil.copy2(path, folder / path.name)
                    raise
                print(f'SemanticSearchCore: prebuilt updated {old_version} -> {new_version}; backup: {backup}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('url')
    parser.add_argument('backups', type=Path)
    args = parser.parse_args()
    update(args.folder, args.url, args.backups)
