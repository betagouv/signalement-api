CREATE TABLE IF NOT EXISTS subcategory_labels (
    category text NOT NULL,
    subcategories character varying[] NOT NULL, -- character varying because of legacy reports table which uses this instead of text making it impossible to join on text
    category_label text NOT NULL,
    subcategory_labels text[] NOT NULL,
    PRIMARY KEY (category, subcategories)
);