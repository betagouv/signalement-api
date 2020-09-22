-- !Ups

ALTER TABLE reports ADD COLUMN contractual_dispute BOOLEAN NOT NULL DEFAULT false;

-- !Downs

ALTER TABLE reports DROP COLUMN contractual_dispute;