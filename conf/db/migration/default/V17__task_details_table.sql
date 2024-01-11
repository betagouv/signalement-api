CREATE TABLE IF NOT EXISTS task_details (
                                               id UUID NOT NULL PRIMARY KEY,
                                               name VARCHAR NOT NULL,
                                               start_time TIMESTAMP WITH TIME ZONE NOT NULL,
                                               interval BIGINT NOT NULL,
                                               last_run_date TIMESTAMP WITH TIME ZONE NOT NULL,
                                               last_run_status VARCHAR NOT NULL
);