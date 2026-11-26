#!/usr/bin/env python3
"""Refresh the local kernel/CI cache; network failures retain cached inputs."""
from functools import lru_cache
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

KERNEL_REPO = 'Rezoss-Reza/s23-ksu-next'
CI_REPO = 'KernelSU-Next/KernelSU-Next'
ARTIFACTS = {'aarch64-android13-5.15-ksu-lkm': 'android13-5.15_kernelsu.ko',
             'ksud-x86_64-unknown-linux-musl': 'x86_64-unknown-linux-musl/release/ksud'}


@lru_cache(maxsize=1)
def github_token():
    token = os.environ.get('GH_TOKEN') or os.environ.get('GITHUB_TOKEN')
    if token:
        return token
    try:
        result = subprocess.run(
            ['gh', 'auth', 'token', '--hostname', 'github.com'],
            capture_output=True, text=True, timeout=10,
        )
    except (OSError, subprocess.TimeoutExpired):
        return None
    return result.stdout.strip() if result.returncode == 0 else None


def fetch(url, output):
    args = ['curl', '-fsSL', '--connect-timeout', '10', '--max-time', '120', '-o', str(output)]
    token = github_token() if url.startswith('https://api.github.com/') else None
    # Credentials are only sent to the official GitHub API.
    config = 'header = ' + json.dumps('Authorization: Bearer ' + token) + '\n' if token else ''
    # Pass the credential on stdin, keeping it out of process arguments/errors.
    subprocess.run(args + ['--config', '-', url], input=config, text=True,
                   check=True, stderr=subprocess.PIPE)


def api(path, scratch):
    output = scratch / 'metadata.json'
    fetch('https://api.github.com/repos/' + path, output)
    return json.loads(output.read_text())


def validate(path, member):
    with zipfile.ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise ValueError('ZIP checksum failure: ' + path.name)
        matches = [item for item in archive.infolist() if item.filename == member]
        if len(matches) != 1 or matches[0].file_size == 0:
            raise ValueError('Missing or ambiguous ZIP member: ' + member)
        if member.endswith('ksud') or member.endswith('.ko'):
            with archive.open(matches[0]) as stream:
                header = stream.read(20)
            machine = 62 if member.endswith('ksud') else 183
            if header[:6] != b'\x7fELF\x02\x01' or int.from_bytes(header[18:20], 'little') != machine:
                raise ValueError('Wrong ELF architecture: ' + member)


def commit(cache, files, version_text):
    # Keep rollback copies until both payloads and metadata have been installed.
    with tempfile.TemporaryDirectory(prefix='.update-', dir=cache) as staging:
        staging = Path(staging)
        replacements = dict(files)
        new_version = staging / 'version.new'
        new_version.write_text(version_text)
        replacements['version'] = new_version
        installed = []
        try:
            for name, source in replacements.items():
                target = cache / name
                backup = staging / (name + '.old')
                if target.exists():
                    shutil.copy2(target, backup)
                pending = staging / (name + '.new')
                if source != pending:
                    shutil.copy2(source, pending)
                os.replace(pending, target)
                installed.append((target, backup))
        except Exception:
            for target, backup in reversed(installed):
                if backup.exists():
                    os.replace(backup, target)
                else:
                    target.unlink()
            raise


def replace_value(text, key, value):
    return re.sub(r'^' + re.escape(key) + r'=.*$', key + '=' + value, text, flags=re.M)


def error_message(error):
    if isinstance(error, subprocess.CalledProcessError):
        return 'download failed (curl exit ' + str(error.returncode) + ')'
    return str(error).split('\n')[0]


def refresh(cache):
    text = (cache / 'version').read_text()
    kernel = re.search(r'^kernel=(?:5\.15\.)?(\d+)\s*$', text, re.M)
    ci = re.search(r'^ksu-next-CI=#?(\d+)\s*$', text, re.M)
    if not kernel or not ci:
        raise ValueError('Expected kernel=5.15.N (or N) and ksu-next-CI=#N')
    with tempfile.TemporaryDirectory(prefix='rezoss-ksu-update-') as scratch:
        scratch = Path(scratch)
        try:
            releases = api(KERNEL_REPO + '/releases?per_page=100', scratch)
            candidates = []
            for release in releases:
                if release.get('draft'):
                    continue
                for asset in release['assets']:
                    match = re.fullmatch(r'common-5\.15\.(\d+)-.*\.zip', asset['name'])
                    if match:
                        candidates.append((int(match[1]), asset))
            if not candidates:
                raise ValueError('No common-5.15 kernel release asset')
            number, asset = max(candidates, key=lambda item: item[0])
            if number > int(kernel[1]):
                payload = scratch / asset['name']
                fetch(asset['browser_download_url'], payload)
                validate(payload, 'Image.gz')
                text_new = replace_value(text, 'kernel', '5.15.' + str(number))
                commit(cache, {asset['name']: payload}, text_new)
                text = text_new
                print('Updated cached kernel to 5.15.' + str(number))
            else:
                print('Cached kernel is current: 5.15.' + kernel[1])
        except Exception as error:
            print('WARNING: Kernel update unavailable; using local cache (' + error_message(error) + ')')
        try:
            runs = api(CI_REPO + '/actions/workflows/build-manager-ci.yml/runs?status=success&per_page=1', scratch)['workflow_runs']
            if not runs:
                raise ValueError('No successful CI run')
            run = runs[0]
            if (run['run_number'] <= int(ci[1])
                    and all((cache / (name + '.zip')).is_file() for name in ARTIFACTS)):
                print('Cached KSU-Next CI is current: #' + ci[1])
                return
            assets = api(CI_REPO + '/actions/runs/' + str(run['id']) + '/artifacts?per_page=100', scratch)['artifacts']
            files = {}
            for name, member in ARTIFACTS.items():
                matches = [a for a in assets if a['name'] == name and not a['expired']]
                if len(matches) != 1:
                    raise ValueError('Missing or ambiguous CI artifact: ' + name)
                artifact = matches[0]
                payload = scratch / (name + '.zip')
                try:
                    fetch(artifact['archive_download_url'], payload)
                except subprocess.CalledProcessError:
                    fetch('https://nightly.link/' + CI_REPO + '/actions/artifacts/' + str(artifact['id']) + '.zip', payload)
                validate(payload, member)
                files[name + '.zip'] = payload
            text_new = replace_value(text, 'ksu-next-CI', '#' + str(run['run_number']))
            commit(cache, files, text_new)
            print('Updated cached KSU-Next CI to #' + str(run['run_number']))
        except Exception as error:
            print('WARNING: CI update unavailable; using local cache (' + error_message(error) + ')')


if __name__ == '__main__':
    refresh(Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent)
