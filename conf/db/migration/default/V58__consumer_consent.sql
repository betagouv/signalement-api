CREATE TABLE IF NOT EXISTS consumer_consent
(
    id            UUID                     NOT NULL,
    email         VARCHAR                  NOT NULL,
    creation_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deletion_date TIMESTAMP WITH TIME ZONE,

    PRIMARY KEY (id, email)
);