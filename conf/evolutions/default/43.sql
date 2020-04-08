-- !Ups

CREATE TABLE "websites" (
    "id" UUID NOT NULL PRIMARY KEY,
    "creation_date" timestamptz NOT NULL,
    "url" VARCHAR NOT NULL UNIQUE,
    "company_id" UUID REFERENCES companies(id)
);

ALTER TABLE "reports" ADD COLUMN "website_id" UUID REFERENCES websites(id);

-- !Downs

ALTER TABLE "reports" DROP COLUMN "website_id";

DROP TABLE "websites";
