CREATE TABLE IF NOT EXISTS promises_of_action (
                                           id UUID NOT NULL PRIMARY KEY,
                                           report_id UUID NOT NULL,
                                           promise_event_id UUID NOT NULL,
                                           resolution_event_id UUID,

                                           CONSTRAINT fk_report_id FOREIGN KEY (report_id) REFERENCES reports(id),
                                           CONSTRAINT fk_promise_event_id FOREIGN KEY (promise_event_id) REFERENCES events(id),
                                           CONSTRAINT fk_resolution_event_id FOREIGN KEY (resolution_event_id) REFERENCES events(id)
);
