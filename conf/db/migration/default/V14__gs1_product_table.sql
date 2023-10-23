ALTER TABLE reports
    ADD gtin   varchar;

CREATE TABLE IF NOT EXISTS gs1_product (
    id UUID NOT NULL PRIMARY KEY,
    gtin varchar NOT NULL UNIQUE,
    siren varchar NOT NULL,
    creation_date TIMESTAMP WITH TIME ZONE NOT NULL
);

