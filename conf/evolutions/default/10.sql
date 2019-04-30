# --- !Ups

ALTER TABLE events ALTER COLUMN result_action DROP NOT NULL;
ALTER TABLE events ALTER COLUMN detail DROP NOT NULL;
ALTER TABLE events ALTER COLUMN creation_date TYPE timestamp without time zone;
ALTER TABLE events ALTER COLUMN creation_date SET DEFAULT now();

# --- !Downs

ALTER TABLE events ALTER COLUMN creation_date TYPE Date;
ALTER TABLE events ALTER COLUMN creation_date DROP DEFAULT;
