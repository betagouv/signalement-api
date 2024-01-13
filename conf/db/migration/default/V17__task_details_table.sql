CREATE TABLE IF NOT EXISTS task_details (
                                               id INTEGER NOT NULL PRIMARY KEY,
                                               name VARCHAR NOT NULL,
                                               start_time TIME WITHOUT TIME ZONE NOT NULL,
                                               interval INTERVAL NOT NULL,
                                               last_run_date TIMESTAMP WITH TIME ZONE NOT NULL,
                                               last_run_error VARCHAR
);