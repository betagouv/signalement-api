-- !Ups

ALTER TABLE subscriptions ADD COLUMN creation_date TIMESTAMPTZ DEFAULT NOW();

-- !Downs

ALTER TABLE subscriptions DROP COLUMN creation_date;