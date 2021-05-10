# --- !Ups

--- Needed because of: SlickException("InsertOrUpdateAll is not supported on a table without PK.")
--- insertOrUpdateAll won't work without a primary ket set
ALTER TABLE etablissements
    ADD CONSTRAINT etablissements_pk PRIMARY KEY (id);

CREATE TABLE IF NOT EXISTS files_sync_info
(
    id          UUID PRIMARY KEY,
    file_name   VARCHAR                     NOT NULL,
    file_url    VARCHAR                     NOT NULL,
    lines_count INTEGER                     NOT NULL,
    lines_done  INTEGER                     NOT NULL,
    started_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    ended_at    TIMESTAMP WITHOUT TIME ZONE,
    errors      VARCHAR
);

# --- !Downs

ALTER TABLE etablissements
    DROP CONSTRAINT etablissements_pk;

DROP TABLE files_sync_info