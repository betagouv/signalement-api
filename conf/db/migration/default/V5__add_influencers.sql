CREATE TABLE IF NOT EXISTS social_networks
(
    slug       text    NOT NULL  PRIMARY KEY,
    siret      text    NOT NULL
);

INSERT INTO social_networks VALUES
    ('Facebook', '53008580200037'),
    ('Instagram', '53008580200037'),
    ('YouTube', '44306184100047'),
    ('TikTok', '88253060300027'),
    ('Twitter', '78930559600015'),
    ('LinkedIn', '51905928100021'),
    ('Snapchat', '82092005600026'),
    ('Twitch', '48777332700027');

ALTER TABLE reports
    ADD social_network  text,
    ADD influencer_name text;