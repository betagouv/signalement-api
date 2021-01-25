-- !Ups

CREATE TABLE "reported_phones" (
    "id" UUID NOT NULL PRIMARY KEY,
    "creation_date" timestamptz NOT NULL,
    "phone" VARCHAR NOT NULL UNIQUE,
    "company_id" UUID REFERENCES companies(id),
    "status" VARCHAR
);

ALTER TABLE "reports" ADD COLUMN "phone" VARCHAR;


-- !Downs

ALTER TABLE "reports" DROP COLUMN "phone";

DROP TABLE "reported_phones"
