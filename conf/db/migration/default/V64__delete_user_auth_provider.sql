ALTER TABLE users
    DROP COLUMN auth_provider;

ALTER TABLE users
    DROP COLUMN auth_provider_id;

DROP TABLE pro_connect_session;