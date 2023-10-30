ALTER TABLE reports
    ADD gs1_product_id varchar;

CREATE TABLE IF NOT EXISTS gs1_product (
    id UUID NOT NULL PRIMARY KEY,
    gtin varchar NOT NULL UNIQUE,
    siren varchar,
    description varchar,
    creation_date TIMESTAMP WITH TIME ZONE NOT NULL
);
