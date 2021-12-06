-- !Ups

CREATE TABLE consumer(
    id UUID PRIMARY KEY,
    name VARCHAR NOT NULL,
    creation_date TIMESTAMP NOT NULL DEFAULT now(),
    api_key VARCHAR NOT NULL,
    delete_date TIMESTAMP
);

-- !Downs

DROP TABLE consumer;
