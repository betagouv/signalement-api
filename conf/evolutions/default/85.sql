-- !Ups
ALTER TABLE users ADD COLUMN deletion_date TIMESTAMP WITH TIME ZONE;

-- If an account is marked as deleted, we want to make it possible
-- to recreate an account with the same email
-- So the unique constraint should apply only for non-deleted users
CREATE UNIQUE INDEX email_unique_if_not_deleted
ON users(email)
WHERE deletion_date IS NULL;
-- Remove the existing constraint
ALTER TABLE users DROP CONSTRAINT email_unique;

-- !Downs