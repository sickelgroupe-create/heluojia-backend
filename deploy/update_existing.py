"""Build and update the existing Linux server without initializing its database.

Run with --check for read-only preflight, --build-only to build, or --apply to
build, back up, deploy and verify. Uses the standard library only. No credentials
are supplied by this repository; the existing service and MySQL files are reused.
"""
import argparse
import contextlib
import datetime
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import platform
import re
import shutil
import stat
import subprocess
import sys
import tarfile
import tempfile
import time
import urllib.request
import zipfile

REPO = Path(__file__).resolve().parents[1]


def say(message):
    print(time.strftime('%H:%M:%S ') + message, flush=True)


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def run(args, *, cwd=None, env=None, log=None, timeout=1200):
    """Do not echo build output: it may include environment-specific details."""
    if log:
        with open(log, 'ab') as stream:
            result = subprocess.run(args, cwd=cwd, env=env, stdout=stream,
                                    stderr=subprocess.STDOUT, timeout=timeout)
        require(result.returncode == 0, '%s failed; inspect %s' % (args[0], log))
        return b''
    result = subprocess.run(args, cwd=cwd, env=env, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, timeout=timeout)
    require(result.returncode == 0, '%s failed (exit %s)' % (args[0], result.returncode))
    return result.stdout


def sha(path):
    digest = hashlib.sha256()
    with open(path, 'rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def safe_extract_source(blob, destination):
    """Only ordinary tracked files/directories; never extract links or devices."""
    with tarfile.open(fileobj=io.BytesIO(blob)) as archive:
        members = archive.getmembers()
        for item in members:
            path = PurePosixPath(item.name)
            require(not path.is_absolute() and '..' not in path.parts
                    and '\\' not in item.name and ':' not in item.name,
                    'Unsafe source archive path')
            require(item.isfile() or item.isdir(), 'Source archive contains a link/device')
            require(not (destination / item.name).is_symlink(), 'Source destination is a link')
        for item in members:
            target = destination / item.name
            if item.isdir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with archive.extractfile(item) as source, target.open('wb') as output:
                    shutil.copyfileobj(source, output)
                target.chmod(0o644)


def atomic_copy(source, target, mode=None):
    require(not target.is_symlink(), 'Refusing to overwrite a symbolic link: ' + str(target))
    previous = target.stat() if target.exists() else None
    handle, name = tempfile.mkstemp(prefix='.deploy-', dir=str(target.parent))
    try:
        with os.fdopen(handle, 'wb') as output, source.open('rb') as incoming:
            shutil.copyfileobj(incoming, output)
            output.flush()
            os.fsync(output.fileno())
        os.chmod(name, mode if mode is not None else stat.S_IMODE(previous.st_mode) if previous else 0o644)
        if previous and hasattr(os, 'chown'):
            os.chown(name, previous.st_uid, previous.st_gid)
        os.replace(name, target)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def static_files(directory):
    files = []
    for path in sorted(directory.rglob('*')):
        require(not path.is_symlink(), 'Static build contains a symbolic link')
        if path.is_file():
            require(path.suffix not in {'.pem', '.key', '.p12', '.pfx'}
                    and not path.name.startswith('.env'), 'Private file in static build')
            files.append(path)
    require((directory / 'index.html').is_file(), 'Static index.html is missing')
    return files


def publish_static(source, destination):
    files = static_files(source)
    # Keep old hashed assets for active browser sessions; switch index last.
    files.sort(key=lambda p: p.relative_to(source).as_posix() == 'index.html')
    for incoming in files:
        target = destination / incoming.relative_to(source)
        require(target.resolve().is_relative_to(destination.resolve()), 'Static destination escapes website')
        target.parent.mkdir(parents=True, exist_ok=True)
        for directory in (target.parent, *target.parent.parents):
            if directory == destination.parent:
                break
            directory.chmod(0o755)
        atomic_copy(incoming, target, 0o644)


def restore_websites(backup, sources, destinations):
    # Restore only files this release could have replaced. Unrelated website
    # files (ACME challenges, control-panel files) remain untouched.
    with tarfile.open(backup, 'r:gz') as archive:
        for name, source, destination in zip(('admin', 'h5'), sources, destinations):
            for incoming in static_files(source):
                relative = incoming.relative_to(source)
                try:
                    member = archive.getmember(name + '/' + relative.as_posix())
                except KeyError:
                    continue  # New hashed assets may stay; old index never uses them.
                require(member.isfile(), 'A replaced website file was not an ordinary file')
                with tempfile.TemporaryDirectory(dir=str(backup.parent)) as temporary:
                    restored = Path(temporary) / 'content'
                    with archive.extractfile(member) as stream, restored.open('wb') as output:
                        shutil.copyfileobj(stream, output)
                    atomic_copy(restored, destination / relative, member.mode & 0o777)


def get_json(url):
    with urllib.request.urlopen(url, timeout=15) as response:
        value = json.load(response)
    require(value.get('code') == 200, 'API did not return success')
    return value['data']


def health(cfg, expected=None, wait=False):
    for attempt in range(60 if wait else 1):
        try:
            data = get_json(cfg['local_api_url'] + '/shop/app/bootstrap')
            flags = {key: data.get(key) for key in ('demo', 'wechatPayReady')}
            require(flags['demo'] is False, 'Production service is in demo mode')
            require(expected is None or flags == expected, 'Payment/demo configuration changed')
            return flags
        except Exception:
            if not wait or attempt == 59:
                raise
            time.sleep(2)


def configuration_hashes(root):
    paths = list(root.glob('*.properties')) + list(root.glob('server.env'))
    paths += [p for p in (root / 'wechat-pay').rglob('*') if p.is_file()]
    return {p.relative_to(root).as_posix(): sha(p) for p in paths}


def preflight(cfg):
    require(sys.version_info >= (3, 9), 'Python 3.9+ is required')
    require(sys.platform == 'linux' and os.geteuid() == 0, 'Run on the existing server as root/sudo')
    require(platform.machine() == 'x86_64', 'This deployment profile is for the existing x86_64 server')
    for name in ('git', 'mvn', 'tar', 'xz', 'systemctl'):
        require(shutil.which(name) is not None, 'Missing existing tool: ' + name)
    for key in ('root', 'admin_directory', 'h5_directory'):
        path = Path(cfg[key])
        require(path.is_absolute() and path.is_dir() and path.resolve() == path,
                'Missing directory or symbolic link: ' + str(path))
    root = Path(cfg['root'])
    require(root == Path('/opt/meir-shop'), 'This entry is only for /opt/meir-shop')
    require(cfg['service'] == 'meir-shop' and cfg['database'] == 'meir_shop', 'Unexpected service/database')
    require(re.fullmatch(r'[0-9a-f]{40}', cfg['admin_revision']) is not None
            and re.fullmatch(r'[0-9a-f]{40}', cfg['miniapp_revision']) is not None, 'Pin frontend Git commits')
    for path in (root / 'app/ruoyi-admin.jar', root / 'mysql-client.cnf',
                 root / 'wechat-pay/application-wechat-pay.properties',
                 Path(cfg['mysql']), Path(cfg['mysqldump']),
                 Path(cfg['build_java_home']) / 'bin/javac'):
        require(path.is_file(), 'Required existing file missing: ' + str(path))
    require((root / 'app').resolve() == root / 'app', 'Application directory is a symbolic link')
    for name in ('incoming', 'backups', 'build-tools'):
        require(not (root / name).is_symlink(), 'Managed directory is a symbolic link')
    require(shutil.disk_usage(root).free >= 4 * 1024 ** 3, 'At least 4 GiB free disk space is required')
    run(['systemctl', 'is-active', '--quiet', cfg['service']], timeout=20)
    mysql = [cfg['mysql'], '--defaults-extra-file=' + str(root / 'mysql-client.cnf'),
             '--batch', '--skip-column-names', cfg['database']]
    tables = int(run(mysql + ['-e', 'select count(*) from information_schema.tables where table_schema=database()'], timeout=30))
    require(tables >= 112, 'Existing business database is missing; never initialize it here')
    require(not run(['git', '-C', str(REPO), 'status', '--porcelain']), 'Commit or stash source changes first')
    health(cfg)
    say('Preflight OK: existing service, business database, website paths and payment configuration')


def node_environment(cfg, root, stage):
    name = 'node-v' + cfg['node_version'] + '-linux-x64'
    tools = root / 'build-tools'
    tools.mkdir(mode=0o700, exist_ok=True)
    node_root = tools / name
    require(not node_root.is_symlink(), 'Node installation directory is a symbolic link')
    if not (node_root / 'bin/node').exists():
        archive = stage / (name + '.tar.xz')
        say('Installing checksum-verified Node in the private build-tools directory')
        url = 'https://nodejs.org/dist/v' + cfg['node_version'] + '/' + archive.name
        with urllib.request.urlopen(url, timeout=120) as response, archive.open('wb') as output:
            shutil.copyfileobj(response, output)
        require(sha(archive) == cfg['node_linux_x64_sha256'], 'Node download checksum mismatch')
        run(['tar', '-xJf', str(archive), '--no-same-owner', '-C', str(tools)])
    env = os.environ.copy()
    env['JAVA_HOME'] = cfg['build_java_home']
    env['PATH'] = str(node_root / 'bin') + ':' + cfg['build_java_home'] + '/bin:' + env['PATH']
    env['MAVEN_OPTS'] = '-Xmx768m -Dfile.encoding=UTF-8'
    env['NODE_OPTIONS'] = '--max-old-space-size=1536'
    env['VITE_SHOP_API_BASE'] = cfg['api_url']
    require(run(['node', '--version'], env=env).decode().strip() == 'v' + cfg['node_version'], 'Unexpected Node version')
    return env


def checkout_frontend(cfg, name, stage):
    path = stage / name
    path.mkdir()
    run(['git', 'init', '--quiet', str(path)])
    run(['git', 'remote', 'add', 'origin', cfg[name + '_repository']], cwd=path)
    run(['git', 'fetch', '--quiet', '--depth=1', 'origin', cfg[name + '_revision']], cwd=path, timeout=180)
    run(['git', 'checkout', '--quiet', '--detach', 'FETCH_HEAD'], cwd=path)
    require(run(['git', 'rev-parse', 'HEAD'], cwd=path).decode().strip() == cfg[name + '_revision'], 'Unexpected frontend revision')
    return path


def build(cfg, stage):
    env = node_environment(cfg, Path(cfg['root']), stage)
    source = stage / 'backend'
    source.mkdir()
    blob = run(['git', '-C', str(REPO), 'archive', '--format=tar', 'HEAD'])
    safe_extract_source(blob, source)
    say('Building backend with JDK 8 and running default tests')
    run(['mvn', '-q', 'clean', 'verify'], cwd=source, env=env, log=stage / 'backend-build.log')
    jar = source / 'ruoyi-admin/target/ruoyi-admin.jar'
    require(jar.is_file(), 'Backend artifact missing')
    with zipfile.ZipFile(jar) as archive:
        sql = list((source / 'database/bootstrap').glob('*.sql'))
        require(len(sql) == 24, 'Missing bootstrap SQL files')
        for path in sql:
            require(archive.read('BOOT-INF/classes/' + path.name) == path.read_bytes(), 'Packaged SQL mismatch')
    admin = checkout_frontend(cfg, 'admin', stage)
    miniapp = checkout_frontend(cfg, 'miniapp', stage)
    shutil.copyfile(admin / '.env.production.example', admin / '.env.production.local')
    for name, path in [('admin', admin), ('miniapp', miniapp)]:
        say('Installing locked ' + name + ' dependencies')
        run(['npm', 'ci', '--no-audit', '--no-fund'], cwd=path, env=env, log=stage / (name + '-install.log'))
    say('Building admin, H5 and WeChat mini program')
    run(['npm', 'test'], cwd=admin, env=env, log=stage / 'admin-tests.log')
    run(['npm', 'run', 'build:prod'], cwd=admin, env=env, log=stage / 'admin-build.log')
    for target in ('h5', 'mp-weixin'):
        run(['npm', 'run', 'build:' + target], cwd=miniapp, env=env, log=stage / (target + '-build.log'))
    static_files(admin / 'dist')
    static_files(miniapp / 'dist/build/h5')
    mp = miniapp / 'dist/build/mp-weixin'
    require(cfg['api_url'] in (mp / 'services/shop.js').read_text(), 'Wrong mini program API domain')
    hashes = {'backend': sha(jar), 'admin': sha(admin / 'dist/index.html'), 'h5': sha(miniapp / 'dist/build/h5/index.html')}
    result = {'backend_commit': run(['git', '-C', str(REPO), 'rev-parse', 'HEAD']).decode().strip(),
              'admin_commit': cfg['admin_revision'], 'miniapp_commit': cfg['miniapp_revision'], 'artifact_sha256': hashes}
    (stage / 'build-result.json').write_text(json.dumps(result, indent=2) + '\n')
    with tarfile.open(stage / 'miniapp-upload.tar.gz', 'w:gz') as archive:
        archive.add(mp, arcname='mp-weixin')
    say('Build verified; WeChat upload artifact: ' + str(stage / 'miniapp-upload.tar.gz'))
    return jar, admin / 'dist', miniapp / 'dist/build/h5'


def order_snapshot(cfg, maximum=None):
    mysql = [cfg['mysql'], '--defaults-extra-file=' + cfg['root'] + '/mysql-client.cnf',
             '--batch', '--skip-column-names', cfg['database']]
    if maximum is None:
        maximum = int(run(mysql + ['-e', 'select coalesce(max(id),0) from shop_order']))
    sql = ('select id,order_no,channel,business_type,amount,subtotal,discount,shipping_amount '
           'from shop_order where id<=%d order by id; '
           'select id,order_id,price,quantity,standard_price from shop_order_item '
           'where order_id<=%d order by id') % (maximum, maximum)
    return maximum, run(mysql + ['-e', sql])


def deploy(cfg, stage, artifacts):
    root = Path(cfg['root'])
    backup = root / 'backups' / stage.name
    backup.mkdir(mode=0o700, parents=True, exist_ok=False)
    admin, h5, live_jar = Path(cfg['admin_directory']), Path(cfg['h5_directory']), root / 'app/ruoyi-admin.jar'
    before_flags = health(cfg)
    before_config = configuration_hashes(root)
    for source, destination in zip(artifacts[1:], (admin, h5)):
        for incoming in static_files(source):
            target = destination / incoming.relative_to(source)
            require(target.resolve().is_relative_to(destination), 'Website target escapes its root')
            require(not target.is_symlink(), 'Website target is a symbolic link')
    say('Backing up database, application, websites, uploads and private configuration')
    shutil.copy2(live_jar, backup / 'ruoyi-admin.jar')
    with tarfile.open(backup / 'websites.tar.gz', 'w:gz') as archive:
        archive.add(admin, arcname='admin')
        archive.add(h5, arcname='h5')
    with tarfile.open(backup / 'business-files.private.tar.gz', 'w:gz') as archive:
        for name in ['runtime/assets', 'runtime/profile', 'runtime/private', 'wechat-pay', *before_config]:
            path = root / name
            if path.exists() and not (name.startswith('wechat-pay/') and name != 'wechat-pay'):
                archive.add(path, arcname=name)
    with (backup / 'database.private.sql').open('wb') as sql, (backup / 'backup.log').open('wb') as errors:
        dumped = subprocess.run([cfg['mysqldump'], '--defaults-extra-file=' + str(root / 'mysql-client.cnf'),
                                 '--single-transaction', '--no-tablespaces', cfg['database']],
                                stdout=sql, stderr=errors, timeout=600)
    require(dumped.returncode == 0 and (backup / 'database.private.sql').stat().st_size > 0,
            'Database backup failed; live service was not changed')
    maximum, before_orders = order_snapshot(cfg)
    (backup / 'order-prices-before.tsv').write_bytes(before_orders)
    (backup / 'configuration-sha256.json').write_text(json.dumps(before_config, indent=2))
    changed = False
    try:
        say('Backup complete. Briefly restarting the existing backend, then switching website indexes')
        changed = True
        run(['systemctl', 'stop', cfg['service']], timeout=90)
        atomic_copy(artifacts[0], live_jar)
        run(['systemctl', 'start', cfg['service']], timeout=90)
        health(cfg, before_flags, wait=True)
        publish_static(artifacts[1], admin)
        publish_static(artifacts[2], h5)
        require(configuration_hashes(root) == before_config, 'Existing configuration changed')
        _, after_orders = order_snapshot(cfg, maximum)
        (backup / 'order-prices-after.tsv').write_bytes(after_orders)
        require(before_orders == after_orders, 'Historical order price snapshot changed')
        for url, expected in [(cfg['api_url'] + '/', artifacts[1] / 'index.html'), (cfg['h5_url'] + '/', artifacts[2] / 'index.html')]:
            with urllib.request.urlopen(url, timeout=30) as response:
                require(response.read() == expected.read_bytes(), 'Public website differs from new artifact')
        public = get_json(cfg['api_url'] + '/shop/app/bootstrap')
        require({k: public.get(k) for k in before_flags} == before_flags, 'Public payment health changed')
        require(sha(live_jar) == sha(artifacts[0]), 'Backend artifact differs after deployment')
    except BaseException:
        if changed:
            say('Deployment failed. Restoring previous application/websites; current database is preserved')
            with contextlib.suppress(Exception):
                run(['systemctl', 'stop', cfg['service']], timeout=90)
            try:
                atomic_copy(backup / 'ruoyi-admin.jar', live_jar)
                restore_websites(backup / 'websites.tar.gz', artifacts[1:], (admin, h5))
            finally:
                run(['systemctl', 'start', cfg['service']], timeout=90)
            health(cfg, before_flags, wait=True)
            say('Previous application restored. No database rollback was performed')
        raise
    result = json.loads((stage / 'build-result.json').read_text())
    result.update({'status': 'deployed', 'backup': str(backup), 'historical_order_prices': 'preserved',
                   'existing_configuration': 'preserved', 'public_websites': 'verified', 'payment_health': before_flags})
    (stage / 'deployment-result.json').write_text(json.dumps(result, indent=2) + '\n')
    (root / 'last-deployment.json').write_text(json.dumps(result, indent=2) + '\n')
    say('DEPLOYED. Backup: ' + str(backup))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--check', action='store_true')
    mode.add_argument('--build-only', action='store_true')
    mode.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    cfg = json.loads((REPO / 'deploy/existing-server.json').read_text())
    preflight(cfg)
    if args.check:
        return
    import fcntl
    os.umask(0o077)
    root = Path(cfg['root'])
    with (root / 'deploy.lock').open('a') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError('Another deployment is already running') from None
        incoming = root / 'incoming'
        incoming.mkdir(mode=0o700, exist_ok=True)
        stage = Path(tempfile.mkdtemp(prefix='github-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S') + '-', dir=incoming))
        say('Build and deployment logs: ' + str(stage))
        artifacts = build(cfg, stage)
        if args.apply:
            deploy(cfg, stage, artifacts)
        else:
            say('BUILD ONLY passed; live application and business data unchanged')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('FAILED: ' + str(error), file=sys.stderr)
        sys.exit(1)
