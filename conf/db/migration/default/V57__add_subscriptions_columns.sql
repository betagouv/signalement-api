ALTER TABLE subscriptions
    ADD websites        character varying[]      default '{}'::character varying[],
    ADD phones          character varying[]      default '{}'::character varying[];