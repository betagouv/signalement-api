ALTER TABLE users
    ADD auth_provider VARCHAR default 'SignalConso' not null;

ALTER TABLE users
    ADD auth_provider_id VARCHAR;