CREATE TABLE IF NOT EXISTS blacklisted_emails (
    id UUID NOT NULL PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    comments TEXT,
    creation_date TIMESTAMP WITH TIME ZONE NOT NULL
);

