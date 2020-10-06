-- !Ups

ALTER TABLE users ADD COLUMN last_email_validation timestamptz;

-- !Downs

ALTER TABLE users DROP COLUMN last_email_validation;
