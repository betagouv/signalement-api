CREATE INDEX companies_name_trgm_idx ON companies USING gist (name public.gist_trgm_ops);
CREATE INDEX companies_commercial_name_trgm_idx ON companies USING gist (commercial_name public.gist_trgm_ops);
CREATE INDEX companies_establishment_commercial_name_trgm_idx ON companies USING gist (establishment_commercial_name public.gist_trgm_ops);
CREATE INDEX companies_brand_trgm_idx ON companies USING gist (brand public.gist_trgm_ops);