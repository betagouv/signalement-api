-- !Ups

ALTER TABLE async_files ADD COLUMN kind VARCHAR;

-- !Downs

ALTER TABLE async_files DROP COLUMN kind;
