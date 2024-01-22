ALTER TABLE access_tokens
    ADD user_id UUID,
    ADD CONSTRAINT fk_user_id FOREIGN KEY (user_id) REFERENCES users(id);