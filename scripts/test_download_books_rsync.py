#!/usr/bin/env python3
"""Offline unit tests for the rsync bulk mode."""
import argparse
import csv
import gzip
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import download_books_rsync as bulk
def body(words=10_050):
    return ("Header\n*** START OF THE PROJECT GUTENBERG EBOOK SAMPLE ***\n"
            + "violet " * words
            + "\n*** END OF THE PROJECT GUTENBERG EBOOK SAMPLE ***\nNotice")


def catalog(*entries):
    buffer = io.StringIO()
    writer = csv.DictWriter(buffer, fieldnames=[
        "Text#", "Type", "Issued", "Title", "Language", "Authors",
        "Subjects", "LoCC", "Bookshelves",
    ])
    writer.writeheader()
    for number, title, language, kind in entries:
        writer.writerow({"Text#": number, "Type": kind, "Title": title,
                         "Language": language, "Authors": "Example, Test, 1800-1850"})
    return gzip.compress(buffer.getvalue().encode('utf-8'))


class TestRsyncBatch(unittest.TestCase):
    def args(self, root, **kwargs):
        options = dict(data_dir=Path(root), target=3, min_words=10000,
                       max_text_bytes=25000000, batch_size=6, max_batches=0,
                       source=bulk.SOURCES[0], probe=False)
        options.update(kwargs)
        return argparse.Namespace(**options)

    def test_rsync_files_from_safe_source(self):
        cmd = bulk.rsync_command(bulk.SOURCES[0], Path('/tmp/test-books'))
        self.assertIn('--files-from=-', cmd)
        self.assertIn('gutenberg.pglaf.org::gutenberg-epub/', cmd)
        with self.assertRaises(ValueError):
            bulk.rsync_command('example.org::other', Path('/tmp/test-books'))

    def test_probe_single_book(self):
        with tempfile.TemporaryDirectory() as tmp:
            def fake_runner(cmd, input, text, capture_output, check):
                self.assertEqual('1342/pg1342.txt\n', input)
                f = Path(cmd[-1]) / '1342/pg1342.txt'
                f.parent.mkdir(parents=True, exist_ok=True)
                f.write_text(body(), encoding='utf-8')
                return argparse.Namespace(returncode=0, stdout='', stderr='')
            with patch.object(bulk.shutil, 'which', return_value='/usr/bin/rsync'):
                self.assertEqual(0, bulk.run(self.args(tmp, probe=True), runner=fake_runner))

    def test_resumes_from_shared_http_cursor_and_keeps_manifest(self):
        with tempfile.TemporaryDirectory() as tmp:
            data = Path(tmp)
            # Simulate previous valid HTTP-mode download (id 12). The source is
            # already in the manifest; cursor points after that candidate.
            (data / 'books').mkdir()
            (data / 'books/existing.txt').write_text(body(), encoding='utf-8')
            bulk.write_manifest(data / 'catalog.csv', [{
                'storage_key':'existing.txt', 'title':'Existing', 'author':'A',
                'language':'en', 'source_url':'https://www.gutenberg.org/ebooks/12',
            }])
            (data / 'download_state.json').write_text(json.dumps({
                'mode':'bulk-catalog-v2', 'catalog_index':1,
            }), encoding='utf-8')
            (data / 'gutenberg_catalog.csv.gz').write_bytes(catalog(*[
                (n, f'Title {n}', 'en', 'Text') for n in range(12, 1, -1)
            ]))
            calls = []
            def fake_runner(cmd, input, text, capture_output, check):
                calls.append(input)
                for path in input.splitlines():
                    number = int(path.split('/')[0])
                    if number == 9:  # unavailable on this mirror
                        continue
                    f = Path(cmd[-1]) / path
                    f.parent.mkdir(parents=True, exist_ok=True)
                    f.write_text(body(words=6000 if number == 10 else 10050).replace('violet ', f'word{number} '), encoding='utf-8')
                return argparse.Namespace(returncode=23, stdout='', stderr='some missing')
            with patch.object(bulk.shutil, 'which', return_value='/usr/bin/rsync'):
                self.assertEqual(0, bulk.run(self.args(tmp), runner=fake_runner))
                self.assertEqual(0, bulk.run(self.args(tmp), runner=fake_runner))
            rows = bulk.load_manifest(data / 'catalog.csv')
            self.assertEqual(3, len(rows))
            self.assertEqual(1, len(calls))
            self.assertEqual('Existing', rows[0]['title'])
            self.assertTrue(all((data / 'books' / r['storage_key']).is_file() for r in rows))
            self.assertIn('"bulk-catalog-v2"', (data/'download_state.json').read_text())
            # Candidate 11 is accepted, 10 too short, 9 missing, 8 accepted.
            self.assertEqual(['Existing','Title 11','Title 8'], [r['title'] for r in rows])

    def test_transfer_failure_does_not_advance_cursor(self):
        with tempfile.TemporaryDirectory() as tmp:
            def fail_runner(cmd, input, text, capture_output, check):
                return argparse.Namespace(returncode=10, stdout='', stderr='Connection refused')
            with self.assertRaises(RuntimeError):
                bulk.transfer_batch([1234], bulk.SOURCES[0], Path(tmp), runner=fail_runner)

if __name__ == '__main__':
    unittest.main()
