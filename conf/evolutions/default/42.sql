-- !Ups

ALTER TABLE access_tokens ADD COLUMN creation_date TIMESTAMPTZ;
UPDATE access_tokens SET creation_date = expiration_date - INTERVAL '60 days' WHERE creation_date IS NULL;
ALTER TABLE access_tokens ALTER COLUMN creation_date SET NOT NULL;

-- !Downs

ALTER TABLE access_tokens DROP COLUMN creation_date;
