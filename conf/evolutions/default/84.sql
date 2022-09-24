-- !Ups

ALTER TABLE companies ADD COLUMN is_public BOOLEAN not null default true ;

-- !Downs