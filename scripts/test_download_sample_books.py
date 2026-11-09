#!/usr/bin/env python3
"""Offline unit tests: no downloads, no RapidAPI."""
from __future__ import annotations

import argparse
import csv
import gzip
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError

import download_sample_books as ds


def catalog(*entries):
    out = io.StringIO()
    writer = csv.DictWriter(out, fieldnames=[
        "Text#", "Type", "Issued", "Title", "Language", "Authors", "Subjects", "LoCC", "Bookshelves"
    ])
    writer.writeheader()
    for n, title, language, ty in entries:
        writer.writerow({"Text#": n, "Type": ty, "Title": title,
                         "Language": language, "Authors": "Example, Test, 1800-1850"})
    return gzip.compress(out.getvalue().encode("utf-8"))


def body(words=10_050):
    return ("Header with no title\n*** START OF THE PROJECT GUTENBERG EBOOK SAMPLE ***\n"
            + "violet " * words
            + "\n*** END OF THE PROJECT GUTENBERG EBOOK SAMPLE ***\nSome copyright notice")


def args(path, **kw):
    fields = dict(data_dir=Path(path), target=1, min_words=10_000, delay=2.0, timeout=3,
                  max_text_bytes=25_000_000, max_catalog_bytes=30_000_000,
                  max_candidates=0, mirror=None, skip_probe=True, probe=False,
                  refresh_catalog=False)
    fields.update(kw)
    return argparse.Namespace(**fields)


class LocalDownloaderTest(unittest.TestCase):
    def test_bulk_catalog_filters_english_texts_and_preserves_csv_title(self):
        rows = ds.catalog_candidates(catalog(
            (42, "A Novel, with comma", "en", "Text"),
            (14, "French", "fr", "Text"),
            (16, "Sound", "en", "Sound")))
        self.assertEqual([42], [r["id"] for r in rows])
        self.assertEqual("A Novel, with comma", rows[0]["title"])
        self.assertEqual("Example, Test, 1800-1850", rows[0]["author"])

    def test_body_strips_boilerplate_and_word_limit(self):
        text = ds.body_only(body())
        self.assertIsNotNone(text)
        self.assertEqual(10_050, ds.count_words(text))
        self.assertNotIn("copyright", text)
        self.assertIsNone(ds.body_only("not a project gutenberg text"))

    def test_mirror_urls_not_a_robot_harvest(self):
        urls = ds.book_urls(1342, ds.MIRRORS)
        self.assertEqual("https://gutenberg.pglaf.org/cache/epub/1342/pg1342.txt", urls[0])
        self.assertIn("https://gutenberg.pglaf.org/1/3/4/1342/1342-0.txt", urls)
        self.assertFalse(any("robot/harvest" in url for url in urls))

    def test_mirror_404_fallback_and_write_and_resume(self):
        with tempfile.TemporaryDirectory() as tmp:
            payloads = []
            def fake_get(_fetcher, url, _max_bytes):
                payloads.append(url)
                if url == ds.CATALOG_URL:
                    return catalog((4321, "My Unrelated Title", "en", "Text"))
                if "gutenberg.pglaf.org/cache/epub/4321/" in url:
                    raise HTTPError(url, 404, "Not Found", {}, None)
                if "mirror.cs.odu.edu/gutenberg-epub/4321/" in url:
                    return body().encode("utf-8")
                raise AssertionError(url)
            with patch.object(ds.Fetcher, "get", fake_get):
                self.assertEqual(0, ds.run(args(tmp)))
                self.assertEqual(0, ds.run(args(tmp)))
            with (Path(tmp) / "catalog.csv").open(newline="", encoding="utf-8") as fp:
                rows = list(csv.DictReader(fp))
            self.assertEqual(1, len(rows))
            self.assertEqual("My Unrelated Title", rows[0]["title"])
            self.assertTrue((Path(tmp) / "books" / rows[0]["storage_key"]).is_file())
            self.assertNotIn("My Unrelated Title", rows[0]["storage_key"])
            self.assertEqual(1, payloads.count(ds.CATALOG_URL))
            state = (Path(tmp) / "download_state.json").read_text(encoding="utf-8")
            self.assertIn('"bulk-catalog-v2"', state)

    def test_existing_harvest_cursor_is_ignored_on_upgrade(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "download_state.json").write_text(
                '{"page_url": "https://www.gutenberg.org/robot/harvest", "link_index": 10}',
                encoding="utf-8")
            def fake_get(_fetcher, url, _max_bytes):
                if url == ds.CATALOG_URL:
                    return catalog((4321, "New path", "en", "Text"))
                return body().encode("utf-8")
            with patch.object(ds.Fetcher, "get", fake_get):
                self.assertEqual(0, ds.run(args(tmp)))
            self.assertEqual(1, len(ds.load_manifest(root / "catalog.csv")))


if __name__ == "__main__":
    unittest.main()
