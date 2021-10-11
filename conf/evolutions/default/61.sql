-- !Ups

DROP TABLE report_data

-- !Downs

CREATE TABLE report_data (
    report_id UUID NOT NULL PRIMARY KEY REFERENCES reports(id),
    read_delay BIGINT,
    response_delay BIGINT
);
