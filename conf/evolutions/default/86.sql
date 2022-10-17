-- !Ups

CREATE TABLE IF NOT EXISTS company_sync
(
    id UUID PRIMARY KEY DEFAULT UUID_GENERATE_V4(),
    last_updated TIMESTAMP not null default '1970-01-01'::timestamp
);


-- !Downs

