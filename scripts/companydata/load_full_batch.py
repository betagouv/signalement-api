import argparse
from collections import OrderedDict
import csv
import psycopg2
import psycopg2.extras
from datetime import datetime

# See https://www.data.gouv.fr/fr/datasets/base-sirene-des-entreprises-et-de-leurs-etablissements-siren-siret/

SIREN = 'siren'
SIRET = 'siret'

PAGE_SIZE = 20000

# DB etablissements fields
FIELDS = [
    'siret',
    'siren',
    'datederniertraitementetablissement',
    'complementadresseetablissement',
    'numerovoieetablissement',
    'indicerepetitionetablissement',
    'typevoieetablissement',
    'libellevoieetablissement',
    'codepostaletablissement',
    'libellecommuneetablissement',
    'libellecommuneetrangeretablissement',
    'distributionspecialeetablissement',
    'codecommuneetablissement',
    'codecedexetablissement',
    'libellecedexetablissement',
    'denominationusuelleetablissement',
    'enseigne1etablissement',
    'activiteprincipaleetablissement',
    'etatadministratifetablissement'
]

FIELDS_EXCEPT_NAME = list(filter(lambda x: x != 'denominationusuelleetablissement', FIELDS))

def iter_csv(path):
    with open(path) as csvfile:
        reader = csv.DictReader(csvfile)
        yield from reader

def iter_queries(path):
    def isset(v):
        return v and v != 'false'
    for d in iter_csv(path):
        d =  {k.lower(): v for k, v in d.items()}
        if args.type == SIRET: # Etablissement
            updates = d #OrderedDict((k, v) for k, v in d.items())
        elif args.type == SIREN: # UniteLegale
            d['denominationusuelleetablissement'] = d['denominationunitelegale'] or d['denominationusuelle1unitelegale'] or d['denominationusuelle2unitelegale'] or d['denominationusuelle3unitelegale'] or (d['prenomusuelunitelegale'] + ' ' + (d['nomusageunitelegale'] or d['nomunitelegale']))
            d['etatadministratifetablissement'] = 'F' if (d['etatadministratifunitelegale'] == 'C') else d['etatadministratifunitelegale']
            if isset(d['denominationusuelleetablissement']):
                updates = d
        yield updates

def eval_query():
    if args.type == SIRET: # Etablissement
        return f"""
            INSERT INTO etablissements ({",".join(FIELDS)})
            VALUES ({",".join(f"%({k})s" for k in FIELDS)})
            ON CONFLICT(siret) DO UPDATE SET {",".join(f"{k}=%({k})s" for k in FIELDS_EXCEPT_NAME)},
            denominationusuelleetablissement=COALESCE(NULLIF(%(denominationusuelleetablissement)s, ''), etablissements.denominationusuelleetablissement)
        """
        return query
    elif args.type == SIREN: # UniteLegale
        return f"""
            UPDATE etablissements SET {",".join(f"{k}=%({k})s" for k in ['denominationusuelleetablissement'])}
            WHERE siren = %(siren)s AND (denominationusuelleetablissement = '') IS NOT FALSE;
        """

def run(pg_uri, source_csv):
    conn = psycopg2.connect(pg_uri)
    conn.set_session(autocommit=True)
    cur = conn.cursor()

    print(datetime.now())

    psycopg2.extras.execute_batch(cur, eval_query(), iter_queries(source_csv), page_size = args.page_size or PAGE_SIZE)

    print(cur.rowcount)

    print(datetime.now())
    conn.close()


parser = argparse.ArgumentParser(description='Intégrer le fichier des établissements (mise à jour ou base complète).')
parser.add_argument('--pg-uri', required=True,
                   help='URI complète de la base de données postgres')
parser.add_argument('--source', required=True,
                    help='Fichier CSV source')
parser.add_argument('--type', required=True, choices=(SIREN, SIRET),
                    help='SIREN: stock unités légale / SIRET: stock établissements')
parser.add_argument('--page_size', required=False, type=int,
                    help='Valeur par défaut 20000')

if __name__ == "__main__":
    args = parser.parse_args()
    run(args.pg_uri, args.source)



