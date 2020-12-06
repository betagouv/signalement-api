-- !Ups

UPDATE websites
SET kind = 'DEFAULT'
WHERE kind = 'EXCLUSIVE';

CREATE TYPE WebsiteKind AS ENUM ('DEFAULT','MARKETPLACE','PENDING');

ALTER TABLE websites alter kind drop default;

ALTER TABLE websites
ALTER COLUMN kind TYPE WebsiteKind USING kind::WebsiteKind;

ALTER TABLE websites ALTER kind SET DEFAULT 'DEFAULT'::WebsiteKind;

-- !Downs

ALTER TABLE websites alter kind drop default;

ALTER TABLE websites
ALTER COLUMN kind TYPE varchar;

ALTER TABLE websites ALTER kind SET DEFAULT 'DEFAULT';