-- !Ups

ALTER TABLE subscriptions ADD COLUMN creation_date TIMESTAMPTZ DEFAULT NOW();

-- !Downs

ALTER TABLE access_tokens DROP COLUMN creation_date;