CREATE TABLE IF NOT EXISTS consumer_consent
(
    id              uuid                  NOT NULL,
    email           varchar               NOT NULL,

    PRIMARY KEY (id, email)
);