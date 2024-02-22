ALTER TABLE influencers
    ADD creation_date timestamp with time zone default now() not null;