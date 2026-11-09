-- Migrate legacy databases in place. Keep READY books and their ids/index postings.
DELETE FROM books WHERE status <> 'READY' OR storage_key IS NULL OR TRIM(storage_key) = '';
DROP TABLE IF EXISTS gutenberg_import_checkpoint;
ALTER TABLE books DROP COLUMN gutenberg_id;
ALTER TABLE books ALTER COLUMN storage_key SET NOT NULL;
ALTER TABLE books ADD CONSTRAINT uq_books_storage_key UNIQUE (storage_key);
