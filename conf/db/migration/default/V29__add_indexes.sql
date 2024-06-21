CREATE INDEX IF NOT EXISTS reports_company_siret_idx ON reports (company_siret);
CREATE INDEX IF NOT EXISTS reports_company_siren_idx ON reports (substr(company_siret, 0, 10));
CREATE INDEX IF NOT EXISTS reports_email_idx ON reports (email);

DROP INDEX companies_name_trgm_idx;
DROP INDEX companies_commercial_name_trgm_idx;
DROP INDEX companies_establishment_commercial_name_trgm_idx;
DROP INDEX companies_brand_trgm_idx;

ALTER TABLE companies
    ADD search_column_trgm TEXT GENERATED ALWAYS AS (
        name
            || ' '
            || coalesce(brand, '')
            || ' '
            || coalesce(commercial_name, '')
            || ' '
            || coalesce(establishment_commercial_name, '')
        ) STORED;
CREATE INDEX IF NOT EXISTS companies_search_column_trgm_gin_idx ON companies USING gin (search_column_trgm public.gin_trgm_ops);
CREATE INDEX IF NOT EXISTS companies_siren_idx ON companies (substr(siret, 0, 10));


ALTER TABLE reports
    ADD admin_search_column tsvector GENERATED ALWAYS AS (to_tsvector(
            CASE lang
                WHEN 'en' THEN 'english'
                ELSE 'french'
                END,
            category
                || ' '
                || array_to_string(subcategories, ',', '')
                || ' '
                || array_to_string(details, ',', '')
                || ' '
                || coalesce(social_network, '')
                || ' '
                || coalesce(other_social_network, '')
                || ' '
                || coalesce(influencer_name, '')
                || ' '
                || coalesce(company_name, '')
                || ' '
                || coalesce(company_commercial_name, '')
                || ' '
                || coalesce(company_establishment_commercial_name, '')
                || ' '
                || coalesce(company_brand, '')
                || ' '
                || coalesce(first_name, '')
                || ' '
                || coalesce(last_name, '')
                || ' '
                || coalesce(consumer_reference_number, '')
                || ' '
                || coalesce(train, '')
                || ' '
                || coalesce(ter, '')
                || ' '
                || coalesce(night_train, '')
                || ' '
                || coalesce(station, '')
                                                          )) STORED;

ALTER TABLE reports
    ADD pro_search_column tsvector GENERATED ALWAYS AS (to_tsvector(
            CASE lang
                WHEN 'en' THEN 'english'
                ELSE 'french'
                END,
            array_to_string(details, ',', '')
                || ' '
                || coalesce(first_name, '')
                || ' '
                || coalesce(last_name, '')
                || ' '
                || coalesce(consumer_reference_number, '')
                                                        )) STORED;

ALTER TABLE reports
    ADD pro_search_column_without_consumer tsvector GENERATED ALWAYS AS (to_tsvector(
            CASE lang
                WHEN 'en' THEN 'english'
                ELSE 'french'
                END,
            array_to_string(details, ',', '')
                                                        )) STORED;