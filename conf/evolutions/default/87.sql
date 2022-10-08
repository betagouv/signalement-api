-- !Ups

ALTER TABLE reports ADD COLUMN IF NOT EXISTS expiration_date TIMESTAMP WITH TIME ZONE;

-- TODO voir comment gérer le non null, que faire pour les reports existants ? peut-être faudra mettre la colonne NOT NULL dans un second temps après avoir fait tourner un script
ALTER TABLE reports ALTER COLUMN expiration_date SET NOT NULL;


-- !Downs

