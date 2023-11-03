ALTER TABLE reports
    ADD gs1_product_id varchar;

CREATE TABLE IF NOT EXISTS gs1_product (
    id UUID NOT NULL PRIMARY KEY,
    gtin VARCHAR NOT NULL UNIQUE,
    product JSONB NOT NULL,
    creation_date TIMESTAMP WITH TIME ZONE NOT NULL
);