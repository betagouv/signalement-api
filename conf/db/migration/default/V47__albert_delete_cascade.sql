ALTER TABLE albert_classification
DROP CONSTRAINT fk_reportId;

ALTER TABLE albert_classification
ADD CONSTRAINT fk_reportId FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE CASCADE;