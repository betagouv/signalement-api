-- !Ups

ALTER TABLE users
    ADD COLUMN accept_notifications BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE report_notification_blocklist
(
    user_id       UUID      NOT NULL,
    company_id    UUID      NOT NULL,
    date_creation TIMESTAMP NOT NULL
);

ALTER TABLE report_notification_blocklist
    ADD CONSTRAINT unique_row UNIQUE (user_id, company_id);

ALTER TABLE report_notification_blocklist
    ADD CONSTRAINT fk_report_notification_blocklist_user
        FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE report_notification_blocklist
    ADD CONSTRAINT fk_report_notification_blocklist_company
        FOREIGN KEY (company_id) REFERENCES companies (id);

-- !Downs

ALTER TABLE users
    DROP COLUMN accept_notifications;

DROP TABLE report_notification_blocklist;