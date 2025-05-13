-- temporary table to keep track of a migration task
CREATE TABLE IF NOT EXISTS companies_access_inheritance_migration (
   company_id INTEGER NOT NULL PRIMARY KEY,
   processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_companies_access_inheritance_migration_processed_at
ON companies_access_inheritance_migration (processed_at);
