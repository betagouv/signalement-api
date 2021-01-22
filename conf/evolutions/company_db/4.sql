# --- !Ups

ALTER TABLE etablissements ADD etatadministratifetablissement VARCHAR;
CREATE INDEX IF NOT EXISTS etatadministratifetablissement_idx ON etablissements USING GIN (etatadministratifetablissement);

# --- !Downs

ALTER TABLE etablissements DROP etatadministratifetablissement;