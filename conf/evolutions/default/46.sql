-- !Ups

ALTER TABLE report_files ADD COLUMN av_output VARCHAR;
UPDATE report_files SET av_output = '—' WHERE av_output IS NULL;

-- !Downs

ALTER TABLE report_files DROP COLUMN av_output;
