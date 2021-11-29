-- !Ups

ALTER TABLE companies DROP COLUMN address_old_version;
ALTER TABLE companies DROP COLUMN postal_code_old_version;
ALTER TABLE companies DROP COLUMN department_old_version;
ALTER TABLE companies DROP COLUMN done;

ALTER TABLE reports DROP COLUMN company_address_old_version;
ALTER TABLE reports DROP COLUMN company_postal_code_old_version;
ALTER TABLE reports DROP COLUMN done;

-- !Downs

ALTER TABLE companies ADD COLUMN address_old_version VARCHAR;
ALTER TABLE companies ADD COLUMN postal_code_old_version VARCHAR;
ALTER TABLE companies ADD COLUMN department_old_version;
ALTER TABLE companies ADD COLUMN done VARCHAR;

ALTER TABLE reports ADD COLUMN company_address_old_version VARCHAR;
ALTER TABLE reports ADD COLUMN company_postal_code_old_version VARCHAR;
ALTER TABLE reports ADD COLUMN done VARCHAR;