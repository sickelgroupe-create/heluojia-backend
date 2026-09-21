import importlib.util
import io
import os
from pathlib import Path
import stat
import tarfile
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('update_existing', Path(__file__).resolve().parents[1] / 'update_existing.py')
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)


class DeploymentSafetyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def archive(self, name, kind=tarfile.REGTYPE):
        blob = io.BytesIO()
        with tarfile.open(fileobj=blob, mode='w') as archive:
            item = tarfile.TarInfo(name)
            item.type = kind
            item.linkname = '/etc/passwd' if kind == tarfile.SYMTYPE else ''
            item.size = 3 if kind == tarfile.REGTYPE else 0
            archive.addfile(item, io.BytesIO(b'abc') if item.size else None)
        return blob.getvalue()

    def test_source_rejects_traversal_absolute_paths_and_links(self):
        for name, kind in [('../outside', tarfile.REGTYPE), ('/etc/file', tarfile.REGTYPE),
                           ('C:/file', tarfile.REGTYPE), ('nested\\outside', tarfile.REGTYPE),
                           ('link', tarfile.SYMTYPE), ('device', tarfile.CHRTYPE)]:
            with self.subTest(name=name, kind=kind), self.assertRaises(RuntimeError):
                deploy.safe_extract_source(self.archive(name, kind), self.root)
        self.assertEqual([], list(self.root.iterdir()))

    def test_tracked_source_extracts_normally(self):
        deploy.safe_extract_source(self.archive('database/test.sql'), self.root)
        self.assertEqual(b'abc', (self.root / 'database/test.sql').read_bytes())

    def test_atomic_copy_preserves_destination_on_read_failure(self):
        target = self.root / 'live.jar'
        target.write_bytes(b'old')
        with self.assertRaises(FileNotFoundError):
            deploy.atomic_copy(self.root / 'missing.jar', target)
        self.assertEqual(b'old', target.read_bytes())
        self.assertEqual(['live.jar'], [p.name for p in self.root.iterdir()])

    def test_atomic_copy_preserves_existing_permissions(self):
        source, target = self.root / 'new.jar', self.root / 'live.jar'
        source.write_bytes(b'new')
        target.write_bytes(b'old')
        target.chmod(0o640)
        deploy.atomic_copy(source, target)
        self.assertEqual(b'new', target.read_bytes())
        if os.name == 'posix':
            self.assertEqual(0o640, stat.S_IMODE(target.stat().st_mode))

    def test_static_publish_keeps_old_assets_and_rollback_keeps_unrelated_changes(self):
        sources, live = [], []
        for name in ('admin', 'h5'):
            source, destination = self.root / ('new-' + name), self.root / name
            source.mkdir(); destination.mkdir()
            (source / 'index.html').write_text('new-index')
            (source / 'new-hash.js').write_text('new-js')
            (destination / 'index.html').write_text('old-index')
            (destination / 'old-hash.js').write_text('old-js')
            (destination / 'challenge.txt').write_text('old-acme')
            sources.append(source); live.append(destination)
        backup = self.root / 'websites.tar.gz'
        with tarfile.open(backup, 'w:gz') as archive:
            for name, directory in zip(('admin', 'h5'), live):
                archive.add(directory, arcname=name)
        for source, destination in zip(sources, live):
            deploy.publish_static(source, destination)
            self.assertEqual('old-js', (destination / 'old-hash.js').read_text())
            (destination / 'challenge.txt').write_text('renewed-acme')
        deploy.restore_websites(backup, sources, live)
        for destination in live:
            self.assertEqual('old-index', (destination / 'index.html').read_text())
            self.assertEqual('renewed-acme', (destination / 'challenge.txt').read_text())
            self.assertTrue((destination / 'new-hash.js').exists())

    def test_private_files_never_enter_website(self):
        (self.root / 'index.html').write_text('index')
        for name in ('.env.local', 'merchant.pem', 'merchant.key'):
            path = self.root / name
            path.write_text('test')
            with self.assertRaises(RuntimeError):
                deploy.static_files(self.root)
            path.unlink()


if __name__ == '__main__':
    unittest.main()
