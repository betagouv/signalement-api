import argparse
from collections import OrderedDict
import csv
import psycopg2

# "Etablissements" file
SIRET_FIELDS = ['siren', 'nic', 'siret', 'statutDiffusionEtablissement', 'dateCreationEtablissement', 'trancheEffectifsEtablissement', 'anneeEffectifsEtablissement', 'activitePrincipaleRegistreMetiersEtablissement', 'dateDernierTraitementEtablissement', 'etablissementSiege', 'nombrePeriodesEtablissement', 'complementAdresseEtablissement', 'numeroVoieEtablissement', 'indiceRepetitionEtablissement', 'typeVoieEtablissement', 'libelleVoieEtablissement', 'codePostalEtablissement', 'libelleCommuneEtablissement', 'libelleCommuneEtrangerEtablissement', 'distributionSpecialeEtablissement', 'codeCommuneEtablissement', 'codeCedexEtablissement', 'libelleCedexEtablissement', 'codePaysEtrangerEtablissement', 'libellePaysEtrangerEtablissement', 'complementAdresse2Etablissement', 'numeroVoie2Etablissement', 'indiceRepetition2Etablissement', 'typeVoie2Etablissement', 'libelleVoie2Etablissement', 'codePostal2Etablissement', 'libelleCommune2Etablissement', 'libelleCommuneEtranger2Etablissement', 'distributionSpeciale2Etablissement', 'codeCommune2Etablissement', 'codeCedex2Etablissement', 'libelleCedex2Etablissement', 'codePaysEtranger2Etablissement', 'libellePaysEtranger2Etablissement', 'dateDebut', 'etatAdministratifEtablissement', 'enseigne1Etablissement', 'enseigne2Etablissement', 'enseigne3Etablissement', 'denominationUsuelleEtablissement', 'activitePrincipaleEtablissement', 'nomenclatureActivitePrincipaleEtablissement', 'caractereEmployeurEtablissement']

def iter_csv(path):
    with open(path) as csvfile:
        reader = csv.DictReader(csvfile)
        yield from reader

def update_sirets_diff(path):
    for d in iter_csv(path):
        if not d['denominationUsuelleEtablissement']:
            d['denominationUsuelleEtablissement'] = d['denominationUniteLegale']
        if not d['activitePrincipaleEtablissement']:
            d['activitePrincipaleEtablissement'] = d['activitePrincipaleUniteLegale']
        updates = OrderedDict((k, v) for k, v in d.items() if k in SIRET_FIELDS)
        yield f"""
            INSERT INTO etablissements ({",".join(updates)})
            VALUES ({",".join(f"%({k})s" for k in updates)})
            ON CONFLICT(siret) DO UPDATE SET {",".join(f"{k}=%({k})s" for k in updates)}
        """, updates

def run(pg_uri, source_csv):
    conn = psycopg2.connect(pg_uri)
    conn.set_session(autocommit=True)
    cur = conn.cursor()
    for query, data in update_sirets_diff(source_csv):
        cur.execute(query, data)
        print(cur.rowcount)
    conn.close()

parser = argparse.ArgumentParser(description='Intégrer le fichier mises à jour des établissements.')
parser.add_argument('--pg-uri', required=True,
                   help='URI complète de la base de données postgres')
parser.add_argument('--source', required=True,
                    help='Fichier CSV source (différentiel)')

if __name__ == "__main__":
    args = parser.parse_args()
    run(args.pg_uri, args.source)
