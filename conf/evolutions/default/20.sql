# --- !Ups

ALTER TABLE events ALTER COLUMN detail TYPE JSONB USING json_build_object('description', detail);
ALTER TABLE events RENAME COLUMN detail TO details;

# --- !Downs

ALTER TABLE events RENAME COLUMN details TO detail;
ALTER TABLE events ALTER COLUMN detail TYPE VARCHAR USING detail::jsonb->>'description';

