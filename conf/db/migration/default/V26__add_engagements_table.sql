CREATE TABLE IF NOT EXISTS engagements (
                                           id UUID NOT NULL PRIMARY KEY,
                                           report_id UUID NOT NULL,
                                           promise_event_id UUID NOT NULL,
                                           resolution_event_id UUID,
                                           expiration_date timestamp with time zone not null,

                                           CONSTRAINT fk_report_id FOREIGN KEY (report_id) REFERENCES reports(id),
                                           CONSTRAINT fk_promise_event_id FOREIGN KEY (promise_event_id) REFERENCES events(id),
                                           CONSTRAINT fk_resolution_event_id FOREIGN KEY (resolution_event_id) REFERENCES events(id)
);

create table if not exists engagement_reviews
(
    id            uuid                     not null
    primary key,
    report_id     uuid                     not null
    constraint fk_report_engagement_review
    references reports,
    creation_date timestamp with time zone not null,
    evaluation    varchar                  not null,
    details       varchar
);