CREATE TABLE IF NOT EXISTS user_settings
(
    user_id          uuid                                 NOT NULL,
    reports_filters  json,

    PRIMARY KEY (user_id),
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);