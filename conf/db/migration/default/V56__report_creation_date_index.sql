-- Index descendant pour le tri des signalements
CREATE INDEX IF NOT EXISTS report_creation_date_idx_desc ON reports (creation_date DESC);