import os
from datasets import load_dataset
import re

BOOKS_DIR = "backend/data/books"
os.makedirs(BOOKS_DIR, exist_ok=True)

print("Connexion à Hugging Face (mode streaming)...")
ds = load_dataset("AdhyanshVerma/pg-en", split="train", streaming=True)

WORD = re.compile(r"[^\W\d_]+(?:['’\-][^\W\d_]+)*", re.UNICODE)
saved = 0
target = 1664

print(f"Extraction des {target} livres (minimum 10 000 mots)...")

for row in ds:
    if saved >= target:
        break

    text = row.get("text", "")
    if not text:
        continue

    if len(WORD.findall(text)) < 10000:
        continue

    filepath = os.path.join(BOOKS_DIR, f"book_{saved+1:04d}.txt")
    with open(filepath, "w", encoding="utf-8") as f:
        f.write(text)

    saved += 1
    if saved % 100 == 0:
        print(f"-> {saved}/{target} livres sauvegardés")

print(f"Terminé ! {saved} livres prêts dans {BOOKS_DIR}.")