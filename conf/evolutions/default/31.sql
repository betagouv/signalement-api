-- !Ups

ALTER TABLE company_access_tokens ADD COLUMN kind VARCHAR;
ALTER TABLE company_access_tokens ALTER COLUMN company_id DROP NOT NULL;
ALTER TABLE company_access_tokens ALTER COLUMN level DROP NOT NULL;

-- !Downs

ALTER TABLE company_access_tokens DROP COLUMN kind;
ALTER TABLE company_access_tokens ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE company_access_tokens ALTER COLUMN level SET NOT NULL;
