-- !Ups

ALTER TABLE events ALTER COLUMN report_id DROP NOT NULL;

-- !Downs

ALTER TABLE events ALTER COLUMN report_id SET NOT NULL;
