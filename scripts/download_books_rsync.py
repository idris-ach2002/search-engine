#!/usr/bin/env python3
"""Acquire PG text files in rsync batches (preferred for bulk by Project Gutenberg).

Shares the local catalog + resume cursor with download_sample_books.py.
Uses official pg_catalog.csv.gz metadata; never queries RapidAPI or PG main
website for the text files. Requires rsync and a reachable mirror rsync port.

Run only ONE downloader at a time; both write the same catalog and cursor.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import logging
from pathlib import Path
import shutil
import subprocess
import sys
from urllib.error import HTTPError, URLError

from download_sample_books import (
    CATALOG_URL, Fetcher, atomic_write, body_only, catalog_candidates,
    count_words, decode_text, load_manifest, write_manifest,
)

LOG = logging.getLogger("bulk-rsync")
SOURCES = (
    "gutenberg.pglaf.org::gutenberg-epub",
    "rsync.ibiblio.org::gutenberg-epub",
)


def generated_path(number: int) -> str:
    return f"{number}/pg{number}.txt"


def rsync_command(source: str, destination: Path) -> list[str]:
    if source not in SOURCES:
        raise ValueError(f"Source rsync non approuvée: {source}")
    return ["rsync", "-az", "--partial", "--timeout=120",
            "--files-from=-", source + "/", str(destination) + "/"]


def transfer_batch(numbers: list[int], source: str, destination: Path,
                   *, runner=subprocess.run) -> None:
    """Download candidate files in one rsync session; missing files are normal."""
    destination.mkdir(parents=True, exist_ok=True)
    paths = "\n".join(generated_path(n) for n in numbers) + "\n"
    completed = runner(rsync_command(source, destination), input=paths,
                       text=True, capture_output=True, check=False)
    # 23: remote file missing or partial transfer; process files that arrived.
    # 24: file vanished during transfer (rare live mirror race).
    if completed.returncode not in (0, 23, 24):
        msg = (completed.stderr or completed.stdout or "").strip()[-1500:]
        raise RuntimeError(f"rsync code {completed.returncode}: {msg}")
    received = sum((destination / generated_path(n)).is_file() for n in numbers)
    if completed.returncode and received == 0:
        msg = (completed.stderr or completed.stdout or "").strip()[-1500:]
        raise RuntimeError(f"rsync transferred no files (code {completed.returncode}): {msg}")
    if completed.returncode:
        LOG.warning("rsync code %s (some files unavailable); %s/%s present",
                    completed.returncode, received, len(numbers))


def process_book(candidate: dict, path: Path, books: Path, rows: list[dict],
                 sources: set[str], keys: set[str], minimum: int,
                 max_bytes: int, target: int) -> str:
    """Process staged local file. Independent metadata from catalog, not filename."""
    source = f"https://www.gutenberg.org/ebooks/{candidate['id']}"
    if source in sources:
        return "existing"
    if not path.is_file():
        return "missing"
    if path.stat().st_size > max_bytes:
        return "oversized"
    text = decode_text(path.read_bytes())
    body = body_only(text)
    if body is None:
        return "invalid"
    count = count_words(body)
    if count < minimum:
        return "short"
    payload = (body + "\n").encode("utf-8")
    if len(payload) > max_bytes:
        return "oversized"
    key = hashlib.sha256(payload).hexdigest() + ".txt"
    if key in keys:
        return "duplicate"
    atomic_write(books / key, payload)
    rows.append({"storage_key": key, "title": candidate["title"],
                 "author": candidate["author"], "language": "en", "source_url": source})
    sources.add(source)
    keys.add(key)
    LOG.info("[%d/%d] %d words | #%s | %s", len(rows), target, count,
             candidate["id"], candidate["title"][:95])
    return "accepted"


def run(args, *, runner=subprocess.run) -> int:
    if shutil.which("rsync") is None:
        raise RuntimeError("rsync est absent; installe-le avec 'brew install rsync'")
    data = args.data_dir.resolve()
    books = data / "books"
    manifest = data / "catalog.csv"
    cache = data / "gutenberg_catalog.csv.gz"
    state_file = data / "download_state.json"
    staging = data / ".rsync-staging"
    if args.probe:
        LOG.info("Checking rsync mirror %s with book #1342", args.source)
        transfer_batch([1342], args.source, staging, runner=runner)
        sample = staging / generated_path(1342)
        if not sample.exists() or body_only(decode_text(sample.read_bytes())) is None:
            raise RuntimeError("Mirror did not provide a valid text for #1342")
        LOG.info("rsync probe OK (%d bytes)", sample.stat().st_size)
        sample.unlink(missing_ok=True)
        return 0

    books.mkdir(parents=True, exist_ok=True)
    rows = load_manifest(manifest)
    if len({r["storage_key"] for r in rows}) != len(rows):
        raise ValueError("Duplicate storage keys in catalog.csv")
    missing = [r["storage_key"] for r in rows if not (books / r["storage_key"]).is_file()]
    if missing:
        raise ValueError(f"Missing {len(missing)} existing files from catalog.csv")
    LOG.info("Existing local books: %d; target: %d", len(rows), args.target)
    if len(rows) >= args.target:
        return 0

    if not cache.exists():
        LOG.info("Caching the official Gutenberg metadata catalog (one request)")
        compressed = Fetcher(delay=2, timeout=90).get(CATALOG_URL, 30_000_000)
        catalog_candidates(compressed)
        atomic_write(cache, compressed)
    candidates = catalog_candidates(cache.read_bytes())
    LOG.info("Eligible metadata records: %d", len(candidates))
    state = json.loads(state_file.read_text()) if state_file.exists() else {}
    if state.get("mode") != "bulk-catalog-v2":
        LOG.warning("Unknown old resume cursor; beginning from catalog index zero")
        state = {"mode": "bulk-catalog-v2", "catalog_index": 0}
    index = int(state["catalog_index"])
    if not (0 <= index <= len(candidates)):
        raise ValueError("Invalid cursor in download_state.json")
    sources = {r["source_url"] for r in rows}
    keys = {r["storage_key"] for r in rows}
    batches = 0
    counts: dict[str, int] = {}
    while index < len(candidates) and len(rows) < args.target:
        if args.max_batches and batches >= args.max_batches:
            break
        needed = args.target - len(rows)
        take = min(args.batch_size, max(6, int(needed * 1.4) + 3))
        batch = candidates[index:index + take]
        if not batch:
            break
        numbers = [c["id"] for c in batch]
        LOG.info("rsync batch %d: %d candidate files (%d books remaining)",
                 batches + 1, len(batch), needed)
        transfer_batch(numbers, args.source, staging, runner=runner)
        for candidate in batch:
            path = staging / generated_path(candidate["id"])
            result = process_book(candidate, path, books, rows, sources, keys,
                                  args.min_words, args.max_text_bytes, args.target)
            counts[result] = counts.get(result, 0) + 1
            if result == "accepted":
                write_manifest(manifest, rows)
            path.unlink(missing_ok=True)
            index += 1
            # Update the cursor AFTER processing each file for safe restart.
            atomic_write(state_file, json.dumps({
                "mode": "bulk-catalog-v2", "catalog_index": index,
            }, indent=2).encode("utf-8"))
            if len(rows) >= args.target:
                break
        # All requested files were processed or are no longer needed. Clean up
        # the extra staged candidates of the final batch to avoid wasting disk.
        for candidate in batch:
            (staging / generated_path(candidate["id"])).unlink(missing_ok=True)
        batches += 1
        LOG.info("Progress: %d/%d books; batch=%d; reasons=%s", len(rows),
                 args.target, batches, counts)
    LOG.info("Finished %d/%d books after %d rsync batches", len(rows), args.target, batches)
    # In pilot mode, a partial corpus is an expected successful outcome.
    return 0 if len(rows) >= args.target or args.max_batches else 2


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    ap = argparse.ArgumentParser(description="Fast bulk Gutenberg download via official rsync mirror")
    ap.add_argument("--target", type=int, default=600)
    ap.add_argument("--data-dir", type=Path, default=root / "backend/data")
    ap.add_argument("--min-words", type=int, default=10_000)
    ap.add_argument("--max-text-bytes", type=int, default=25_000_000)
    ap.add_argument("--batch-size", type=int, default=80)
    ap.add_argument("--max-batches", type=int, default=0, help="Pilot batch limit, 0 = all")
    ap.add_argument("--source", choices=SOURCES, default=SOURCES[0])
    ap.add_argument("--probe", action="store_true")
    args = ap.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    if args.target < 1 or args.min_words < 10000 or not (1 <= args.batch_size <= 200) or args.max_batches < 0:
        ap.error("Invalid target/min-words/batch-size/max-batches")
    try:
        return run(args)
    except (ValueError, OSError, RuntimeError, HTTPError, URLError) as error:
        LOG.error("Stopped: %s", error)
        return 1


if __name__ == "__main__":
    sys.exit(main())
