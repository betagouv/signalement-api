-- !Ups

CREATE TABLE emails_validation
(
    id                   UUID        NOT NULL PRIMARY KEY,
    creation_date        timestamptz NOT NULL,
    email                VARCHAR     NOT NULL UNIQUE,
    last_validation_date timestamptz
);

-- !Downs

DROP TABLE emails_validation