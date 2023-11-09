CREATE TABLE IF NOT EXISTS barcode_product (
    id UUID NOT NULL PRIMARY KEY,
    gtin VARCHAR NOT NULL UNIQUE,
    gs1_product JSONB NOT NULL,
    open_food_facts_product JSONB,
    open_beauty_facts_product JSONB,
    creation_date TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE reports
    ADD barcode_product_id UUID,
    ADD CONSTRAINT fk_barcode_product FOREIGN KEY (barcode_product_id) REFERENCES barcode_product(id);