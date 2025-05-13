-- drop and recreate with the right type for the id column
DROP TABLE IF EXISTS companies_access_inheritance_migration;
CREATE TABLE IF NOT EXISTS companies_access_inheritance_migration (
   company_id UUID NOT NULL PRIMARY KEY,
   processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_companies_access_inheritance_migration_processed_at
ON companies_access_inheritance_migration (processed_at);
