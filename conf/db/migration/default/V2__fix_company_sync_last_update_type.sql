-- Fix last_updated column. Slick expects a timestamp WITH time zone
ALTER TABLE company_sync
ALTER COLUMN last_updated TYPE timestamp with time zone;

ALTER TABLE company_sync
ALTER COLUMN last_updated SET DEFAULT '1970-01-01 00:00:00+00'::timestamp with time zone;