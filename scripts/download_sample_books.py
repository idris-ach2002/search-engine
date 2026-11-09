#!/usr/bin/env python3
"""Download a verified local book corpus from Project Gutenberg mirrors.

Uses the official BULK metadata catalog (one cached CSV.gz file), then downloads
plain text from mirrors. No RapidAPI, no /robot/harvest, no Java-side network.

Outputs:
    backend/data/catalog.csv
    backend/data/books/<SHA256>.txt
    backend/data/gutenberg_catalog.csv.gz (cached metadata)
    backend/data/download_state.json (restart cursor)

Before distributing texts outside the US, verify applicable copyright rules.
"""
from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import io
import json
import logging
from pathlib import Path
import re
import socket
import ssl
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen

LOG = logging.getLogger("local-corpus")
HEADERS = ["storage_key", "title", "author", "language", "source_url"]
CATALOG_URL = "https://www.gutenberg.org/cache/epub/feeds/pg_catalog.csv.gz"
# Both mirrors are explicitly listed by Project Gutenberg.
MIRRORS = (
    "https://gutenberg.pglaf.org/cache/epub",
    "https://mirror.cs.odu.edu/gutenberg-epub",
)
WORD = re.compile(r"[^\W\d_]+(?:['’\-][^\W\d_]+)*", re.UNICODE)
START = re.compile(r"(?mi)^\*{3}\s*START OF (?:THE |THIS )?PROJECT GUTENBERG .*?\*{3}\s*$")
END = re.compile(r"(?mi)^\*{3}\s*END OF (?:THE |THIS )?PROJECT GUTENBERG .*?\*{3}\s*$")


def trusted_url(url: str) -> bool:
    part = urlparse(url)
    host = (part.hostname or "").lower()
    return part.scheme == "https" and host in {
        "www.gutenberg.org", "gutenberg.pglaf.org", "mirror.cs.odu.edu"
    }


class Fetcher:
    def __init__(self, delay: float, timeout: int):
        self.delay = delay
        self.timeout = timeout
        self.last_request = 0.0

    def get(self, url: str, max_bytes: int) -> bytes:
        if not trusted_url(url):
            raise ValueError(f"Untrusted download URL: {url}")
        for attempt in range(4):
            remaining = self.delay - (time.monotonic() - self.last_request)
            if remaining > 0:
                time.sleep(remaining)
            self.last_request = time.monotonic()
            try:
                with urlopen(Request(url, headers={
                    "User-Agent": "DAAR-course-project/2.0 (single-file, rate-limited; contact: academic project)"
                }), timeout=self.timeout) as response:
                    if not trusted_url(response.geturl()):
                        raise ValueError(f"Untrusted redirect: {response.geturl()}")
                    content = response.read(max_bytes + 1)
                    if len(content) > max_bytes:
                        raise ValueError(f"File exceeds {max_bytes} bytes")
                    return content
            except HTTPError as error:
                if error.code not in (429, 500, 502, 503, 504) or attempt == 3:
                    raise
                pause = min(90, max(2.0, self.delay) * (2 ** (attempt + 1)))
                LOG.warning("HTTP %d from %s; retrying in %.0fs", error.code, url, pause)
                time.sleep(pause)
            except (URLError, socket.timeout, TimeoutError, ssl.SSLError) as error:
                if attempt == 3:
                    raise
                LOG.warning("Network error %s; retrying %s", error, url)
                time.sleep(min(60, max(2.0, self.delay) * 2 ** attempt))
        raise RuntimeError("Retries exhausted")


def atomic_write(path: Path, content: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    tmp.write_bytes(content)
    tmp.replace(path)


def load_manifest(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        return []
    with path.open(newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != HEADERS:
            raise ValueError(f"Invalid catalog.csv header: expected {HEADERS}")
        return list(reader)


def write_manifest(path: Path, rows: list[dict[str, str]]) -> None:
    stream = io.StringIO(newline="")
    writer = csv.DictWriter(stream, fieldnames=HEADERS, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    atomic_write(path, stream.getvalue().encode("utf-8"))


def catalog_candidates(compressed: bytes) -> list[dict[str, str]]:
    """Return deterministically ordered English text entries, with real metadata."""
    raw = gzip.decompress(compressed)
    reader = csv.DictReader(io.StringIO(raw.decode("utf-8-sig"), newline=""))
    needed = {"Text#", "Type", "Title", "Language", "Authors"}
    if not reader.fieldnames or not needed.issubset(reader.fieldnames):
        raise ValueError(f"Unexpected official metadata columns: {reader.fieldnames}")
    candidates = []
    seen_ids = set()
    for row in reader:
        identifier = (row.get("Text#") or "").strip()
        language = (row.get("Language") or "").lower()
        title = " ".join((row.get("Title") or "").split())
        if not identifier.isdecimal() or (row.get("Type") or "").strip().lower() != "text":
            continue
        if "en" not in re.split(r"[;,\s]+", language) or not title:
            continue
        number = int(identifier)
        if number in seen_ids:
            continue
        seen_ids.add(number)
        author = " ".join((row.get("Authors") or "").split()).split(";")[0]
        candidates.append({"id": number, "title": title, "author": author})
    # Newer editions usually supply UTF-8 generated text; stable on resumed runs
    # because the compressed catalog is cached on disk.
    candidates.sort(key=lambda item: item["id"], reverse=True)
    return candidates


def body_only(source: str) -> str | None:
    start = START.search(source)
    if not start:
        return None
    end = END.search(source, start.end())
    if not end:
        return None
    body = source[start.end():end.start()].strip()
    return body or None


def decode_text(content: bytes) -> str:
    try:
        return content.decode("utf-8-sig")
    except UnicodeDecodeError:
        return content.decode("latin-1")


def count_words(body: str) -> int:
    return sum(1 for _ in WORD.finditer(body))


def book_urls(number: int, mirrors: tuple[str, ...]) -> list[str]:
    result = []
    for mirror in mirrors:
        if not trusted_url(mirror):
            raise ValueError(f"Invalid mirror: {mirror}")
        root = mirror.rstrip("/")
        # Official generated flat-per-book layout.
        result.append(f"{root}/{number}/pg{number}.txt")
    # Official main-collection hierarchy; try only when the generated text
    # is missing on all configured mirrors.
    digits = "/".join(str(number)[:-1])
    path = f"{digits}/{number}/" if digits else ""
    result.append(f"https://gutenberg.pglaf.org/{path}{number}-0.txt")
    result.append(f"https://gutenberg.pglaf.org/{path}{number}.txt")
    return list(dict.fromkeys(result))


def fetch_plaintext(fetcher: Fetcher, number: int, mirrors: tuple[str, ...],
                    max_bytes: int) -> tuple[str, str] | None:
    for url in book_urls(number, mirrors):
        try:
            raw = fetcher.get(url, max_bytes)
            return decode_text(raw), url
        except HTTPError as exc:
            if exc.code in (404, 410):
                continue
            # 403/429/5xx: do NOT silently advance the cursor and retry forever.
            raise
    return None


def probe(fetcher: Fetcher, mirrors: tuple[str, ...], max_bytes: int) -> str:
    # A known long public-domain English book, explicitly available in text.
    last_error: Exception | None = None
    for url in book_urls(1342, mirrors):
        try:
            raw = fetcher.get(url, max_bytes)
            if body_only(decode_text(raw)):
                LOG.info("Mirror probe OK: %s (%d bytes)", url, len(raw))
                return url
            last_error = ValueError("Missing Gutenberg body markers")
        except (HTTPError, URLError, TimeoutError, ValueError, OSError) as error:
            last_error = error
            LOG.warning("Mirror probe failed: %s (%s)", url, error)
    raise RuntimeError(
        "No mirror returned a valid plain-text book (#1342). "
        "Check internet / DNS, try --mirror https://mirror.cs.odu.edu/gutenberg-epub, "
        f"or verify the official mirror list. Last error: {last_error}"
    )


def run(args: argparse.Namespace) -> int:
    data = args.data_dir.resolve()
    books = data / "books"
    manifest = data / "catalog.csv"
    cache = data / "gutenberg_catalog.csv.gz"
    state_file = data / "download_state.json"
    mirrors = tuple(args.mirror) if args.mirror else MIRRORS
    fetcher = Fetcher(args.delay, args.timeout)

    if args.probe:
        probe(fetcher, mirrors, args.max_text_bytes)
        return 0

    books.mkdir(parents=True, exist_ok=True)
    rows = load_manifest(manifest)
    # Do not count missing books. Never silently remove existing metadata rows.
    missing = [r["storage_key"] for r in rows if not (books / r["storage_key"]).is_file()]
    if missing:
        raise ValueError(f"{len(missing)} manifest files are missing; repair them before continuing")
    if len({r["storage_key"] for r in rows}) != len(rows):
        raise ValueError("Duplicate storage keys in catalog.csv")
    known_sources = {row["source_url"] for row in rows}
    known_keys = {row["storage_key"] for row in rows}
    LOG.info("Already have %d local books; target %d", len(rows), args.target)
    if len(rows) >= args.target:
        return 0

    # Fail early if the configured mirrors cannot serve a known book.
    if not args.skip_probe:
        probe(fetcher, mirrors, args.max_text_bytes)

    if args.refresh_catalog or not cache.is_file():
        LOG.info("Downloading official BULK metadata: %s", CATALOG_URL)
        compressed = fetcher.get(CATALOG_URL, args.max_catalog_bytes)
        # Verify its structure before caching any download cursor.
        catalog_candidates(compressed)
        atomic_write(cache, compressed)
    compressed = cache.read_bytes()
    candidates = catalog_candidates(compressed)
    if not candidates:
        raise ValueError("No English text titles in the official catalog")
    LOG.info("Eligible catalog entries: %d", len(candidates))

    state = {}
    if state_file.exists() and not args.refresh_catalog:
        state = json.loads(state_file.read_text(encoding="utf-8"))
    if state.get("mode") != "bulk-catalog-v2":
        if state:
            LOG.warning("Old harvest cursor ignored; starting bulk-catalog cursor at zero")
        state = {"mode": "bulk-catalog-v2", "catalog_index": 0}
    index = int(state["catalog_index"])
    if not (0 <= index <= len(candidates)):
        raise ValueError("Invalid download cursor; inspect download_state.json")

    attempted = 0
    successes = 0
    short = 0
    missing_text = 0
    while index < len(candidates) and len(rows) < args.target:
        if args.max_candidates and attempted >= args.max_candidates:
            break
        candidate = candidates[index]
        number = candidate["id"]
        source_url = f"https://www.gutenberg.org/ebooks/{number}"
        if source_url not in known_sources:
            try:
                downloaded = fetch_plaintext(fetcher, number, mirrors, args.max_text_bytes)
            except ValueError as error:
                LOG.warning("Skip book #%d: %s", number, error)
                downloaded = None
            if downloaded is None:
                missing_text += 1
            else:
                raw_text, actual_url = downloaded
                body = body_only(raw_text)
                if body is None:
                    LOG.debug("Skip %d: no complete body delimiters (%s)", number, actual_url)
                else:
                    nwords = count_words(body)
                    if nwords < args.min_words:
                        short += 1
                    else:
                        payload = (body + "\n").encode("utf-8")
                        if len(payload) > args.max_text_bytes:
                            LOG.warning("Skip %d: UTF-8 text too large", number)
                        else:
                            key = hashlib.sha256(payload).hexdigest() + ".txt"
                            if key not in known_keys:
                                atomic_write(books / key, payload)
                                rows.append({
                                    "storage_key": key,
                                    "title": candidate["title"],
                                    "author": candidate["author"],
                                    "language": "en",
                                    "source_url": source_url,
                                })
                                write_manifest(manifest, rows)
                                known_sources.add(source_url)
                                known_keys.add(key)
                                successes += 1
                                LOG.info("[%d/%d] %d words | #%d | %s", len(rows),
                                         args.target, nwords, number, candidate["title"][:100])
        attempted += 1
        index += 1
        atomic_write(state_file, json.dumps({
            "mode": "bulk-catalog-v2", "catalog_index": index,
        }, indent=2).encode("utf-8"))
        if attempted % 50 == 0:
            LOG.info("Progress: accepted=%d, processed=%d, too-short=%d, no-text=%d",
                     len(rows), attempted, short, missing_text)
    LOG.info("Finished: accepted=%d/%d; attempted=%d; new=%d; short=%d; no-text=%d",
             len(rows), args.target, attempted, successes, short, missing_text)
    return 0 if len(rows) >= args.target else 2


def main() -> int:
    repo = Path(__file__).resolve().parents[1]
    ap = argparse.ArgumentParser(
        description="Acquire 600+ local books using official bulk catalog + mirrors (no RapidAPI)")
    ap.add_argument("--target", type=int, default=600)
    ap.add_argument("--min-words", type=int, default=10000)
    ap.add_argument("--data-dir", type=Path, default=repo / "backend/data")
    ap.add_argument("--delay", type=float, default=2.0, help="Min seconds between requests")
    ap.add_argument("--timeout", type=int, default=90)
    ap.add_argument("--max-text-bytes", type=int, default=25_000_000)
    ap.add_argument("--max-catalog-bytes", type=int, default=30_000_000)
    ap.add_argument("--max-candidates", type=int, default=0, help="Pilot run; 0 means unlimited")
    ap.add_argument("--mirror", action="append", help="Override mirror root (repeatable)")
    ap.add_argument("--probe", action="store_true", help="Test known book #1342 without writing files")
    ap.add_argument("--skip-probe", action="store_true", help="For offline tests only")
    ap.add_argument("--refresh-catalog", action="store_true", help="Redownload metadata and restart cursor")
    ap.add_argument("--verbose", action="store_true")
    args = ap.parse_args()
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO,
                        format="%(asctime)s %(levelname)s %(message)s")
    if args.min_words < 10000 or args.target < 1 or args.delay < 2 or args.max_candidates < 0:
        ap.error("Require target>=1, min-words>=10000, delay>=2, max-candidates>=0")
    try:
        return run(args)
    except (ValueError, RuntimeError, OSError, HTTPError, URLError,
            socket.timeout, TimeoutError, gzip.BadGzipFile) as error:
        LOG.error("Stopped: %s. Fix the issue and rerun to resume.", error)
        return 1


if __name__ == "__main__":
    sys.exit(main())
