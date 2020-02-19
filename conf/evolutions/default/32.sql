-- !Ups

ALTER TABLE subscriptions ADD COLUMN email VARCHAR UNIQUE;

-- !Downs

ALTER TABLE subscriptions DROP COLUMN email;
