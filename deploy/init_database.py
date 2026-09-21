"""Import the handoff SQL into an EMPTY, pre-created database; activate its first admin."""
import argparse
import getpass
import os
from pathlib import Path
import re
import subprocess
import sys
import bcrypt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', required=True)
    parser.add_argument('--defaults-file', required=True, type=Path)
    parser.add_argument('--mysql', default='mysql')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_]+', args.database):
        parser.error('Database name must contain only letters, numbers or underscores')
    config = args.defaults_file.resolve(strict=True)
    # Some Windows MySQL clients cannot decode non-ASCII absolute option-file paths.
    # Resolve the parent with Python and pass only the filename from that directory.
    command = [args.mysql, '--defaults-extra-file=' + config.name, '--default-character-set=utf8mb4',
               '--batch', '--skip-column-names', args.database]

    def execute(sql):
        result = subprocess.run(command, cwd=config.parent, input=sql.encode('utf-8'), capture_output=True)
        if result.returncode:
            raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))
        return result.stdout.decode('utf-8').strip()

    count = execute('SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE();')
    if count != '0':
        raise RuntimeError('Refusing import: database is not empty. No existing data was modified.')
    password = os.environ.get('SHOP_ADMIN_PASSWORD')
    if not password:
        password = getpass.getpass('New admin password (12-20 characters): ')
        if password != getpass.getpass('Repeat password: '):
            raise RuntimeError('Passwords do not match')
    if not 12 <= len(password) <= 20 or len(password.encode('utf-8')) > 72:
        raise RuntimeError('Use a password of 12-20 characters, at most 72 UTF-8 bytes')
    hashed = bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt(rounds=12)).decode('ascii')
    database_dir = Path(__file__).resolve().parents[1] / 'database'
    for name in ['01-schema.sql', '02-system.sql', '03-catalog.sql']:
        execute((database_dir / name).read_text(encoding='utf-8-sig'))
        print('Imported ' + name)
    execute("UPDATE sys_user SET password='" + hashed + "',status='0',pwd_update_date=NOW() WHERE user_id=1 AND user_name='admin';")
    print('Database ready. Admin: admin. Password is the one you supplied; it is not saved to a file.')
    print('Stock starts at 0. Configure actual inventory, domains and WeChat credentials before selling.')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
