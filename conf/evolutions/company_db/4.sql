# --- !Ups

--- Needed because of: SlickException("InsertOrUpdateAll is not supported on a table without PK.")
--- insertOrUpdateAll won't work without a primary ket set
alter table etablissements add constraint etablissements_pk primary key (id);

# --- !Downs

alter table etablissements drop constraint etablissements_pk;
