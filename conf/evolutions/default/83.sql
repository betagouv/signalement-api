-- !Ups

-- Nullable at first
ALTER TABLE users ADD COLUMN creation_date TIMESTAMP WITH TIME ZONE;

-- Set the creation_date if we can based on the events
-- This should work for most users but not DGCCRF, they dont have the event
-- This also won't work for a minority of users, they don't have the event either (probably created before we had it)
WITH users_creations AS (
	SELECT
	users.id,
	events.creation_date
	FROM users
	 JOIN events
	ON events.user_id = users.id
	WHERE events.action = 'Activation d''un compte'
)
UPDATE users
SET creation_date = users_creations.creation_date
FROM users_creations
WHERE users.id = users_creations.id
;

-- Then for the others, set the creation date at 1970-01-01
UPDATE users
SET creation_date =  TIMESTAMP 'epoch'
WHERE creation_date IS NULL;

-- Now set the column to non-nullable, with now() as default
ALTER TABLE users ALTER COLUMN creation_date SET NOT NULL;
ALTER TABLE users ALTER COLUMN creation_date SET DEFAULT NOW();

-- !Downs
