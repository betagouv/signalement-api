UPDATE reports SET category = (
    CASE category
        WHEN 'Retrait-Rappel spécifique' THEN 'RetraitRappelSpecifique'
        WHEN 'COVID-19 (coronavirus)' THEN 'Coronavirus'
        WHEN 'Café / Restaurant' THEN 'CafeRestaurant'
        WHEN 'Achat / Magasin' THEN 'AchatMagasinLegacy'
        WHEN 'Achat (Magasin ou Internet)' THEN 'AchatMagasinInternet'
        WHEN 'Achat en Magasin' THEN 'AchatMagasin'
        WHEN 'Achat sur internet' THEN 'AchatInternet'
        WHEN 'Services aux particuliers' THEN 'ServicesAuxParticuliers'
        WHEN 'Téléphonie / Eau-Gaz-Electricité' THEN 'TelEauGazElec'
        WHEN 'Eau / Gaz / Electricité' THEN 'EauGazElectricite'
        WHEN 'Téléphonie / Fournisseur d''accès internet / médias' THEN 'TelephonieFaiMedias'
        WHEN 'Banque / Assurance / Mutuelle' THEN 'BanqueAssuranceMutuelle'
        WHEN 'Intoxication alimentaire' THEN 'IntoxicationAlimentaire'
        WHEN 'Produits / Objets' THEN 'ProduitsObjets'
        WHEN 'Internet (hors achats)' THEN 'Internet'
        WHEN 'Travaux / Rénovation' THEN 'TravauxRenovations'
        WHEN 'Voyage / Loisirs' THEN 'VoyageLoisirs'
        WHEN 'Immobilier' THEN 'Immobilier'
        WHEN 'Secteur de la santé' THEN 'Sante'
        WHEN 'Voiture / Véhicule' THEN 'VoitureVehicule'
        WHEN 'Animaux' THEN 'Animaux'
        WHEN 'Démarches administratives' THEN 'DemarchesAdministratives'
        WHEN 'Voiture / Véhicule / Vélo' THEN 'VoitureVehiculeVelo'
        WHEN 'Démarchage abusif' THEN 'DemarchageAbusif'
        ELSE category
        END
    );

UPDATE subscriptions SET categories = (
    array (
        SELECT CASE category
           WHEN 'Retrait-Rappel spécifique' THEN 'RetraitRappelSpecifique'
           WHEN 'COVID-19 (coronavirus)' THEN 'Coronavirus'
           WHEN 'Café / Restaurant' THEN 'CafeRestaurant'
           WHEN 'Achat / Magasin' THEN 'AchatMagasinLegacy'
           WHEN 'Achat (Magasin ou Internet)' THEN 'AchatMagasinInternet'
           WHEN 'Achat en Magasin' THEN 'AchatMagasin'
           WHEN 'Achat sur internet' THEN 'AchatInternet'
           WHEN 'Services aux particuliers' THEN 'ServicesAuxParticuliers'
           WHEN 'Téléphonie / Eau-Gaz-Electricité' THEN 'TelEauGazElec'
           WHEN 'Eau / Gaz / Electricité' THEN 'EauGazElectricite'
           WHEN 'Téléphonie / Fournisseur d''accès internet / médias' THEN 'TelephonieFaiMedias'
           WHEN 'Banque / Assurance / Mutuelle' THEN 'BanqueAssuranceMutuelle'
           WHEN 'Intoxication alimentaire' THEN 'IntoxicationAlimentaire'
           WHEN 'Produits / Objets' THEN 'ProduitsObjets'
           WHEN 'Internet (hors achats)' THEN 'Internet'
           WHEN 'Travaux / Rénovation' THEN 'TravauxRenovations'
           WHEN 'Voyage / Loisirs' THEN 'VoyageLoisirs'
           WHEN 'Immobilier' THEN 'Immobilier'
           WHEN 'Secteur de la santé' THEN 'Sante'
           WHEN 'Voiture / Véhicule' THEN 'VoitureVehicule'
           WHEN 'Animaux' THEN 'Animaux'
           WHEN 'Démarches administratives' THEN 'DemarchesAdministratives'
           WHEN 'Voiture / Véhicule / Vélo' THEN 'VoitureVehiculeVelo'
           WHEN 'Démarchage abusif' THEN 'DemarchageAbusif'
           ELSE category END
        FROM unnest(categories) category
        )
    );