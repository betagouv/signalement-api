-- NULL for now
ALTER TABLE reports
ADD visible_to_pro BOOLEAN;

UPDATE reports
SET visible_to_pro = NOT (
    status = 'LanceurAlerte'
    OR tags && ARRAY['Bloctel', 'ProduitDangereux', 'ReponseConso']
);

-- From now on, it's NOT NULL
ALTER TABLE reports
ALTER COLUMN visible_to_pro SET NOT NULL;






