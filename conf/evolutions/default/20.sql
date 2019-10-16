-- !Ups

CREATE TABLE "companies" (
    "id" UUID NOT NULL PRIMARY KEY,
    "siret" VARCHAR NOT NULL UNIQUE,
    "creation_date" timestamptz NOT NULL,
    "name" VARCHAR NOT NULL,
    "address" VARCHAR NOT NULL,
    "postal_code" VARCHAR
);

ALTER TABLE "signalement" ADD COLUMN "company_id" UUID REFERENCES companies(id);

-- !Downs

ALTER TABLE "signalement" DROP COLUMN "company_id";

DROP TABLE "companies";
