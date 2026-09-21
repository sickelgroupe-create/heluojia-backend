"""Verify portable source, complete SQL and referenced public assets. No network or DB writes."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
EXCLUDED = {'node_modules', 'target', 'dist', 'unpackage', 'runtime', '__pycache__', '.git', '.local'}
MANIFEST = ROOT / 'deploy/source-manifest.json'


def source_hash(blob):
    # Git stores text with LF; Windows working copies may still contain CRLF.
    try:
        blob.decode('utf-8')
        if b'\0' not in blob:
            blob = blob.replace(b'\r\n', b'\n')
    except UnicodeDecodeError:
        pass
    return hashlib.sha256(blob).hexdigest()


def source_files():
    for directory in ['ruoyi-admin', 'ruoyi-common', 'ruoyi-framework', 'ruoyi-system', 'assets', 'database', 'deploy']:
        for p in (ROOT / directory).rglob('*'):
            if not p.is_file() or any(part in EXCLUDED for part in p.relative_to(ROOT).parts):
                continue
            if p == MANIFEST or p.name == '.env.local' or p.name.endswith('.local') or '.private.' in p.name:
                continue
            yield p
    for name in ['README.md', '.gitignore', '.gitattributes', 'pom.xml', 'LICENSE']:
        yield ROOT / name


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write-manifest', action='store_true')
    args = parser.parse_args()
    required = ['pom.xml', 'ruoyi-admin/pom.xml', 'database/01-schema.sql', 'database/02-system.sql',
                'database/03-catalog.sql', 'deploy/application-server.properties.example',
                'deploy/server.env.example', 'deploy/nginx.conf.example', 'deploy/meir-shop.service']
    for name in required:
        assert (ROOT / name).is_file(), 'Missing ' + name
    schema = (ROOT / 'database/01-schema.sql').read_text(encoding='utf-8')
    tables = re.findall(r'CREATE TABLE `([^`]+)`', schema)
    assert len(tables) == len(set(tables)) == 112, 'Unexpected schema table inventory'
    service = (ROOT / 'ruoyi-admin/src/main/java/com/ruoyi/web/controller/shop/ShopService.java').read_text(encoding='utf-8')
    references = set(re.findall(r'ClassPathResource\("([^"\n]+\.sql)"\)', service))
    bootstrap = {p.name for p in (ROOT / 'database/bootstrap').glob('*.sql')}
    assert references <= bootstrap and len(bootstrap) == 24, 'Missing startup SQL'
    assets = json.loads((ROOT / 'assets/manifest.json').read_text(encoding='utf-8'))
    for entry in assets:
        p = ROOT / entry['file']
        assert p.is_file() and hashlib.sha256(p.read_bytes()).hexdigest() == entry['sha256'], 'Missing/changed asset: ' + entry['file']
    hashes = {}
    for p in sorted(source_files()):
        relative = p.relative_to(ROOT).as_posix()
        assert p.suffix not in {'.pem', '.p12', '.pfx', '.key'}, 'Private material: ' + relative
        assert not (p.suffix == '.sql' and not relative.startswith('database/')), 'SQL outside database: ' + relative
        blob = p.read_bytes()
        assert b'-----BEGIN PRIVATE KEY-----\nM' not in blob, 'Embedded private key: ' + relative
        assert p.stat().st_size < 100 * 1024 * 1024, 'File exceeds GitHub limit: ' + relative
        hashes[relative] = source_hash(blob)
    if args.write_manifest:
        MANIFEST.write_text(json.dumps({'release': '3.20260921.19', 'files': hashes}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    elif MANIFEST.exists():
        assert json.loads(MANIFEST.read_text(encoding='utf-8'))['files'] == hashes, 'Source changed; review then regenerate manifest with --write-manifest'
    print(f'PASS: {len(hashes)} source/config files, {len(tables)} tables, {len(bootstrap)} bootstrap scripts, {len(assets)} public assets')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('FAIL: ' + str(error), file=sys.stderr)
        sys.exit(1)
