ALTER TABLE reports
    DROP COLUMN admin_search_column,
    DROP COLUMN pro_search_column,
    DROP COLUMN pro_search_column_without_consumer;

ALTER TABLE reports
    ADD admin_search_column tsvector GENERATED ALWAYS AS (to_tsvector(
            CASE lang
                WHEN 'en' THEN 'english'::regconfig
                ELSE 'french'::regconfig
                END,
            category
                || ' '
                || immutable_array_to_string(subcategories, ',', '')
                || ' '
                || immutable_array_to_string(details, ',', '')
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
                || ' '
                || coalesce(vendor, '')
                                                          )) STORED,
     ADD pro_search_column tsvector GENERATED ALWAYS AS (to_tsvector(
            CASE lang
                WHEN 'en' THEN 'english'::regconfig
                ELSE 'french'::regconfig
                END,
            immutable_array_to_string(details, ',', '')
                || ' '
                || coalesce(first_name, '')
                || ' '
                || coalesce(last_name, '')
                || ' '
                || coalesce(consumer_reference_number, '')
                || ' '
                || coalesce(vendor, '')
                                                        )) STORED,
     ADD pro_search_column_without_consumer tsvector GENERATED ALWAYS AS (to_tsvector(
            CASE lang
                WHEN 'en' THEN 'english'::regconfig
                ELSE 'french'::regconfig
                END,
            immutable_array_to_string(details, ',', '')
                || ' '
                || coalesce(vendor, '')
                                                                         )) STORED;

CREATE INDEX IF NOT EXISTS reports_admin_search_idx ON reports USING gin (admin_search_column);
CREATE INDEX IF NOT EXISTS reports_pro_search_idx ON reports USING gin (pro_search_column);
CREATE INDEX IF NOT EXISTS reports_pro_search_without_consumer_idx ON reports USING gin (pro_search_column_without_consumer);