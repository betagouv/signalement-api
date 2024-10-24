CREATE TABLE IF NOT EXISTS bookmarks (
    report_id UUID NOT NULL,
    user_id UUID NOT NULL,
    creation_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (report_id, user_id),
    CONSTRAINT fk_report_id FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS bookmarks_user_id_idx ON bookmarks (user_id);
