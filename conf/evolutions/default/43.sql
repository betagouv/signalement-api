-- !Ups

ALTER TABLE reports ADD COLUMN tags VARCHAR[] DEFAULT '{}'::varchar[];

-- !Downs

ALTER TABLE reports DROP COLUMN tags;
