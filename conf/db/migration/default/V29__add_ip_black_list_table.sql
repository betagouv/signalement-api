CREATE TABLE IF NOT EXISTS ip_black_list
(
    ip       VARCHAR NOT NULL PRIMARY KEY,
    comment  VARCHAR NOT NULL,
    critical BOOLEAN NOT NULL
);