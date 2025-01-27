CREATE TABLE IF NOT EXISTS siret_extractions (
    host        TEXT NOT NULL PRIMARY KEY,
    status      TEXT NOT NULL,
    error       TEXT,
    extractions JSONB
    );