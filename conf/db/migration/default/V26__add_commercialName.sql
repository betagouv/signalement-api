ALTER TABLE companies
    ADD commercial_name   varchar,
    ADD establishment_commercial_name varchar;

ALTER TABLE reports
    ADD company_commercial_name   varchar,
    ADD company_establishment_commercial_name   varchar;