CREATE TABLE IF NOT EXISTS company_activation_attempts (
    id UUID NOT NULL PRIMARY KEY,
    siret TEXT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL
);


