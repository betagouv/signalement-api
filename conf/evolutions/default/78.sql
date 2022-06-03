-- !Ups

create table IF NOT EXISTS website_investigation
(
    id            uuid                     not null
        constraint website_investigation_pkey primary key,
    website_id    uuid                     not null,
    creation_date timestamp with time zone not null,
    last_updated   timestamp with time zone not null,
    investigation_status varchar                  not null,
    practice      varchar,
    attribution      varchar
);

ALTER TABLE website_investigation
    DROP CONSTRAINT IF EXISTS fk_website_investigation;
ALTER TABLE website_investigation
    ADD CONSTRAINT fk_website_investigation FOREIGN KEY (website_id) REFERENCES websites (id);

-- !Downs

ALTER TABLE website_investigation
    DROP CONSTRAINT fk_website_investigation;
