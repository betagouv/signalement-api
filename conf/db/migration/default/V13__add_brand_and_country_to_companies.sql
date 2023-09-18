ALTER TABLE companies
    ADD brand   varchar,
    ADD country varchar;

ALTER TABLE reports
    ADD company_brand   varchar;