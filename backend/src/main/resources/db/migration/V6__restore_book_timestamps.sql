-- Be defensive on legacy databases whose timestamp defaults differ from V1.
-- Application INSERT statements also explicitly set both values.
ALTER TABLE books ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE books ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;
