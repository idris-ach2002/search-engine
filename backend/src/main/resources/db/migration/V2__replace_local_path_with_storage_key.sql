-- Persist a portable storage identifier instead of a machine-specific absolute path.
-- Existing READY rows such as
-- /Users/alice/project/backend/data/books/10084.txt
-- become simply
-- 10084.txt

ALTER TABLE books
    RENAME COLUMN local_path TO storage_key;

UPDATE books
SET storage_key = regexp_replace(
        replace(storage_key, E'\\', '/'),
        '^.*/',
        ''
    )
WHERE storage_key IS NOT NULL;
