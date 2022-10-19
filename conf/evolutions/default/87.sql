-- !Ups

ALTER TABLE reports ADD COLUMN IF NOT EXISTS expiration_date TIMESTAMP WITH TIME ZONE;

-- Set the expiration_date to a safe value (J+60) for all existing reports
UPDATE reports
SET expiration_date = creation_date + INTERVAL '60 days'
WHERE expiration_date IS NULL;

-- Then set the column to not null
-- From now on, all reports created will have an expiration_date set at creation time
ALTER TABLE reports ALTER COLUMN expiration_date SET NOT NULL;


-- !Downs

