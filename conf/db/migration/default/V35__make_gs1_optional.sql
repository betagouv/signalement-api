ALTER TABLE barcode_product
    ALTER gs1_product DROP NOT NULL,
    ADD update_date TIMESTAMP WITH TIME ZONE;

UPDATE barcode_product
    SET update_date = creation_date;

ALTER TABLE barcode_product
    ALTER update_date SET NOT NULL;