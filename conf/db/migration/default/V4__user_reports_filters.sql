CREATE TABLE IF NOT EXISTS user_reports_filters
(
    user_id          uuid                                 NOT NULL,
    name             varchar                              NOT NULL,
    filters          json,
    default_filters  boolean DEFAULT false                NOT NULL,

    PRIMARY KEY (user_id, name),
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);