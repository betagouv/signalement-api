import argparse
from collections import OrderedDict
import csv
import psycopg2

# See https://www.data.gouv.fr/fr/datasets/base-sirene-des-entreprises-et-de-leurs-etablissements-siren-siret/

SIREN = 'siren'
SIRET = 'siret'

# DB fields
FIELDS = ['siret', 'siren', 'datederniertraitementetablissement', 'complementadresseetablissement', 'numerovoieetablissement', 'indicerepetitionetablissement', 'typevoieetablissement', 'libellevoieetablissement', 'codepostaletablissement', 'libellecommuneetablissement', 'libellecommuneetrangeretablissement', 'distributionspecialeetablissement', 'codecommuneetablissement', 'codecedexetablissement', 'libellecedexetablissement', 'denominationusuelleetablissement', 'enseigne1etablissement', 'activiteprincipaleetablissement']

def iter_csv(path):
    with open(path) as csvfile:
        reader = csv.DictReader(csvfile)
        yield from reader

def iter_queries(path):
    def isset(v):
        return v and v != 'false'
    for d in iter_csv(path):
        if args.type == SIRET:
            updates = OrderedDict((k, v) for k, v in d.items() if k.lower() in FIELDS and v)
            query = f"""
                INSERT INTO etablissements ({",".join(updates)})
                VALUES ({",".join(f"%({k})s" for k in updates)})
                ON CONFLICT(siret) DO UPDATE SET {",".join(f"{k}=%({k})s" for k in updates)}
            """
        elif args.type == SIREN:
            d['denominationUsuelleEtablissement'] = d['denominationUniteLegale'] or d['denominationUsuelle1UniteLegale'] or d['denominationUsuelle2UniteLegale'] or d['denominationUsuelle3UniteLegale']
            # d['activitePrincipaleEtablissement'] = d['activitePrincipaleUniteLegale']
            updates = OrderedDict((k, v) for k, v in d.items() if k.lower() in FIELDS and isset(v))
            query = f"""
                UPDATE etablissements SET {",".join(f"{k}=%({k})s" for k in updates)} WHERE siren = %(siren)s AND denominationusuelleetablissement IS NULL
            """
        yield query, updates

def run(pg_uri, source_csv):
    conn = psycopg2.connect(pg_uri)
    conn.set_session(autocommit=True)
    cur = conn.cursor()
    query, updates = iter_queries(source_csv)
    cur.executemany(query, updates)
    print(cur.rowcount)
    conn.close()

parser = argparse.ArgumentParser(description='Intégrer le fichier des établissements (mise à jour ou base complète).')
parser.add_argument('--pg-uri', required=True,
                   help='URI complète de la base de données postgres')
parser.add_argument('--source', required=True,
                    help='Fichier CSV source')
parser.add_argument('--type', required=True, choices=(SIREN, SIRET),
                    help='SIREN: stock unités légale / SIRET: stock établissements')

if __name__ == "__main__":
    args = parser.parse_args()
    run(args.pg_uri, args.source)
