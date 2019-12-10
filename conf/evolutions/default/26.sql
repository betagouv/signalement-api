-- !Ups

ALTER TABLE USERS DROP COLUMN ACTIVATION_KEY;
ALTER TABLE USERS DROP COLUMN LOGIN;

-- !Downs

ALTER TABLE USERS ADD COLUMN LOGIN VARCHAR;
ALTER TABLE USERS ADD CONSTRAINT LOGIN_UNIQUE UNIQUE(LOGIN);
ALTER TABLE USERS ADD COLUMN ACTIVATION_KEY VARCHAR;
