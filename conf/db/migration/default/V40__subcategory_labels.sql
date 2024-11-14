CREATE TABLE IF NOT EXISTS subcategory_labels (
    category text NOT NULL,
    subcategories character varying[] NOT NULL, -- character varying because of legacy reports table which uses this instead of text making it impossible to join on text
    category_label_fr text,
    category_label_en text,
    subcategory_labels_fr text[],
    subcategory_labels_en text[],
    PRIMARY KEY (category, subcategories)
);