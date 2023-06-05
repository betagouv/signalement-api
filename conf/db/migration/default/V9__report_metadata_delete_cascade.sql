ALTER TABLE reports_metadata
DROP CONSTRAINT fk_reports,
ADD CONSTRAINT fk_reports FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE CASCADE