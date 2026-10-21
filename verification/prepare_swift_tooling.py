"""Prepare the published tool's separate SDK and verify its immutable inputs."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import urllib.request
import zipfile

LOCK = json.loads(Path(__file__).with_name('swift-tooling-lock.json').read_text())

def sha(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()

def require(ok, message):
    if not ok:
        raise ValueError(message)

def download(url, path, expected):
    if not path.exists():
        part = path.with_suffix(path.suffix + '.partial')
        with urllib.request.urlopen(url, timeout=120) as response, part.open('wb') as out:
            shutil.copyfileobj(response, out)
        require(sha(part) == expected, 'Download checksum mismatch: ' + url)
        part.rename(path)
    require(sha(path) == expected, 'Cached input checksum mismatch: ' + str(path))

def helper_module(root):
    spec = importlib.util.spec_from_file_location('published_sdk_helper', root / 'helper/scripts/prepare_native_sdk.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

def verify(root):
    receipt = json.loads((root / 'sdk-audit.json').read_text())
    require(receipt['changed_files'] == ['konan/swift_export/kotlin_runtime/KotlinBase.h'] and
            receipt['unchanged_native_binaries'] and not receipt['installed_source_modified'], 'SDK helper scope mismatch')
    expected = dict(receipt['source_files'])
    expected['konan/swift_export/kotlin_runtime/KotlinBase.h'] = LOCK['native_header_candidate_sha256']
    for name, value in LOCK['helper_source_sha256'].items():
        require(sha(root / 'helper' / name) == value, 'Published SDK helper changed: ' + name)
    require(helper_module(root).inventory(root / 'native-sdk') == expected, 'Prepared SDK changed')
    worker = json.loads((root / 'resolved-worker.json').read_text())
    require(len(worker) == len(LOCK['worker_components']) and
            {row['coordinate']: sha(row['path']) for row in worker} == LOCK['worker_components'],
            'Published exporter worker inputs changed')
    print('Verified complete prepared SDK inventory: ' + str(len(expected)) + ' files', flush=True)

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--helper', type=Path)
parser.add_argument('--worker', type=Path)
parser.add_argument('--verify-only', action='store_true')
args = parser.parse_args()
root = args.output.resolve()
root.mkdir(parents=True, exist_ok=True)
if args.verify_only:
    verify(root)
    raise SystemExit(0)
require(args.helper and args.worker, 'Resolved Maven helper and worker manifest required')
worker = json.loads(args.worker.read_text())
actual = {}
for row in worker:
    require(row['coordinate'] not in actual, 'Duplicate exporter worker component')
    actual[row['coordinate']] = sha(row['path'])
require(actual == LOCK['worker_components'], 'Published exporter worker closure differs from verified release')
(root / 'verified-worker.json').write_text(json.dumps(actual, indent=2) + '\n')
helper_name = 'swift-export-embeddable-2.4.20-km.1-native-sdk-helper.zip'
require(sha(args.helper) == LOCK['files'][helper_name], 'Published helper ZIP checksum mismatch')
if not (root / 'helper').exists():
    with zipfile.ZipFile(args.helper) as archive:
        for member in archive.infolist():
            path = Path(member.filename)
            require(not path.is_absolute() and '..' not in path.parts and
                    (member.external_attr >> 16) & 0o170000 != 0o120000, 'Unsafe helper entry')
        archive.extractall(root / 'helper')
for name, value in LOCK['helper_source_sha256'].items():
    require(sha(root / 'helper' / name) == value, 'Published helper source checksum mismatch')
if (root / 'native-sdk').exists():
    verify(root)
    raise SystemExit(0)
archive = root / 'kotlin-native-prebuilt-2.4.20-macos-aarch64.tar.gz'
# An optional trusted archive path saves network traffic; its full digest is still required.
seed = os.environ.get('KOTLIN_NATIVE_ARCHIVE')
if seed and not archive.exists():
    require(sha(seed) == LOCK['native_sdk_archive_sha256'], 'Official SDK seed checksum mismatch')
    shutil.copyfile(seed, archive)
download(LOCK['native_sdk_archive_url'], archive, LOCK['native_sdk_archive_sha256'])
source = root / 'official-sdk'
require(not source.exists(), 'Incomplete previous SDK preparation; use a fresh output directory')
source.mkdir()
with tarfile.open(archive, 'r:gz') as bundle:
    for member in bundle.getmembers():
        path = Path(member.name)
        require(not path.is_absolute() and '..' not in path.parts and
                (member.isfile() or member.isdir()), 'Unsafe SDK archive entry')
    bundle.extractall(source)
children = list(source.iterdir())
require(len(children) == 1 and children[0].is_dir(), 'Unexpected official SDK archive layout')
subprocess.run(['python3', str(root / 'helper/scripts/prepare_native_sdk.py'),
                '--source', str(children[0]), '--destination', str(root / 'native-sdk'),
                '--receipt', str(root / 'sdk-audit.json')], check=True)
verify(root)
