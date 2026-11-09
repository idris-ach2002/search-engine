#!/usr/bin/env python3
"""Offline verification of local catalog and >=10,000-word UTF-8 texts (no network)."""
import argparse
import csv
from pathlib import Path
import re
import sys

WORD = re.compile(r"[^\W\d_]+(?:['’\-][^\W\d_]+)*", re.UNICODE)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data-dir", type=Path, default=Path(__file__).resolve().parents[1]/"backend/data")
    parser.add_argument("--minimum", type=int, default=10000)
    parser.add_argument("--target", type=int, default=600, help="Minimum required number of valid books")
    args = parser.parse_args()
    root = args.data_dir.resolve()/"books"
    bad = []
    with (args.data_dir/"catalog.csv").open(encoding="utf-8", newline="") as fd:
        reader = csv.DictReader(fd)
        if reader.fieldnames != ["storage_key", "title", "author", "language", "source_url"]:
            raise ValueError("catalog.csv header incorrect")
        rows = list(reader)
    if len({r["storage_key"] for r in rows}) != len(rows):
        bad.append("duplicate storage keys")
    for row in rows:
        key = row["storage_key"]
        path = (root/key).resolve()
        if not path.is_relative_to(root) or not path.is_file():
            bad.append(f"missing/outside root: {key}")
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeError:
            bad.append(f"not UTF-8: {key}")
            continue
        words = sum(1 for _ in WORD.finditer(text))
        if words < args.minimum:
            bad.append(f"too short: {key} ({words} words)")
        if not row["title"].strip():
            bad.append(f"no title: {key}")
    if len(rows) < args.target:
        bad.append(f"only {len(rows)} books; required {args.target}")
    print(f"Catalog entries: {len(rows)} | errors: {len(bad)} | minimum words: {args.minimum} | target: {args.target}")
    for failure in bad[:30]:
        print("ERROR:",failure)
    return 0 if not bad and rows else 1

if __name__ == "__main__":
    sys.exit(main())
