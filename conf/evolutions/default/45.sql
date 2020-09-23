-- !Ups

ALTER TABLE reports ADD COLUMN consumer_actions_id VARCHAR;

-- !Downs

ALTER TABLE reports DROP COLUMN consumer_actions_id;