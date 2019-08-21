# --- !Ups

create index no_similar_report on signalement (email, details, date_trunc('day', date_creation), adresse_etablissement);

# --- !Downs

drop index no_similar_report;