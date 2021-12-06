-- !Ups
DROP INDEX IF EXISTS no_similar_report;

ALTER TABLE companies DROP COLUMN address_old_version;
ALTER TABLE companies DROP COLUMN postal_code_old_version;
ALTER TABLE companies DROP COLUMN department_old_version;
ALTER TABLE companies DROP COLUMN done;

ALTER TABLE reports DROP COLUMN company_address_old_version;
ALTER TABLE reports DROP COLUMN company_postal_code_old_version;
ALTER TABLE reports DROP COLUMN done;

create unique index no_similar_report on reports (
    email,
    last_name,
    first_name,
    details,
    my_date_trunc('day'::text, creation_date),
    company_postal_code,
    company_street_number,
    company_street,
    company_address_supplement,
    company_city
);



-- !Downs

DROP INDEX IF EXISTS no_similar_report;

ALTER TABLE companies ADD COLUMN address_old_version VARCHAR;
ALTER TABLE companies ADD COLUMN postal_code_old_version VARCHAR;
ALTER TABLE companies ADD COLUMN department_old_version VARCHAR;
ALTER TABLE companies ADD COLUMN done VARCHAR;

ALTER TABLE reports ADD COLUMN company_address_old_version VARCHAR;
ALTER TABLE reports ADD COLUMN company_postal_code_old_version VARCHAR;
ALTER TABLE reports ADD COLUMN done VARCHAR;

create unique index no_similar_report
    on reports (email, last_name, first_name, details, my_date_trunc('day'::text, creation_date), company_address_old_version);
