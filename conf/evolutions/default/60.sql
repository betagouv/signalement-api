-- !Ups

ALTER TABLE websites ADD COLUMN country VARCHAR;

-- !Downs

ALTER TABLE websites DROP COLUMN country;