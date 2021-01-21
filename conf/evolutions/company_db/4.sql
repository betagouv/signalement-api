# --- !Ups

ALTER TABLE etablissements ADD etatadministratifetablissement VARCHAR;


# --- !Downs

ALTER TABLE etablissements DROP etatadministratifetablissement;