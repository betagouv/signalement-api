CREATE TABLE IF NOT EXISTS signalconso_review
(
    id            uuid                     not null,
    evaluation    text                     NOT NULL,
    details       text,
    creation_date timestamp with time zone not null,
    platform      text                     NOT NULL
);