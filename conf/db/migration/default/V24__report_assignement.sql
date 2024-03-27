ALTER TABLE reports_metadata
    ADD assigned_user_id UUID,
    ADD CONSTRAINT fk_assigned_user
        FOREIGN KEY (assigned_user_id)
        REFERENCES users(id)
        ON DELETE SET NULL;