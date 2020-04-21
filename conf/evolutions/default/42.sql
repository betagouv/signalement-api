-- !Ups

ALTER TABLE subscriptions ADD COLUMN sirets VARCHAR[] DEFAULT '{}'::varchar[];

-- !Downs

ALTER TABLE subscriptions DROP COLUMN sirets;

