CREATE TABLE IF NOT EXISTS pro_connect_session
(
    id            UUID                     NOT NULL PRIMARY KEY,
    state         VARCHAR                  NOT NULL UNIQUE,
    nonce         VARCHAR                  NOT NULL,
    creation_date TIMESTAMP WITH TIME ZONE NOT NULL
);

