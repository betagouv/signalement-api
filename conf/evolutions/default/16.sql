# --- !Ups

ALTER TABLE signalement ALTER date_creation TYPE timestamptz USING date_creation AT TIME ZONE 'UTC';

# --- !Downs

ALTER TABLE signalement ALTER date_creation TYPE timestamp;
