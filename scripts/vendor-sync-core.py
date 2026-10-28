#!/usr/bin/env python3
import argparse
import hashlib
import json
from pathlib import Path
import shutil

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
parser.add_argument('--source', type=Path, default=root.parent / 'src/toolkit/components/midori-sync/midori_sync_core')
parser.add_argument('--check', action='store_true')
args = parser.parse_args()
destination = root / 'native/sync/core'
files = [Path('Cargo.toml'), Path('tests/fixtures/native-sodium.json'), *sorted(path.relative_to(args.source) for path in (args.source / 'src').glob('*.rs'))]
hashes = {str(path): hashlib.sha256((args.source / path).read_bytes()).hexdigest() for path in files}
if args.check:
    for path in files:
        if (destination / path).read_bytes() != (args.source / path).read_bytes():
            raise SystemExit(f'Sync core differs: {path}')
    if json.loads((destination / 'source.json').read_text()) != hashes:
        raise SystemExit('Sync core source manifest differs')
else:
    for path in files:
        (destination / path).parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(args.source / path, destination / path)
    (destination / 'source.json').write_text(json.dumps(hashes, indent=2) + '\n')
