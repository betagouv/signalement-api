CREATE TABLE IF NOT EXISTS reports_metadata (
    report_id UUID NOT NULL PRIMARY KEY,
    is_mobile_app BOOLEAN NOT NULL,
    os TEXT CHECK (os IN ('Android', 'Ios') OR os IS NULL),
    CONSTRAINT fk_reports FOREIGN KEY (report_id) REFERENCES reports(id)
);