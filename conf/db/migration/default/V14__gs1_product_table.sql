ALTER TABLE reports
    ADD gs1_product_id varchar;

CREATE TABLE IF NOT EXISTS gs1_product (
    id UUID NOT NULL PRIMARY KEY,
    gtin VARCHAR NOT NULL UNIQUE,
    siren VARCHAR,
    description VARCHAR,
    creation_date TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS gs1_net_content (
    unit_code VARCHAR,
    quantity VARCHAR,
    gs1_product_id UUID NOT NULL,
    CONSTRAINT fk_gs1_product FOREIGN KEY (gs1_product_id) REFERENCES gs1_product (id) ON DELETE CASCADE
)