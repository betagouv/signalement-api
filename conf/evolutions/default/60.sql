-- !Ups

ALTER TABLE websites ADD COLUMN company_country VARCHAR;

-- !Downs

ALTER TABLE websites DROP COLUMN company_country;