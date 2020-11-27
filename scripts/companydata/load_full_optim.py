import argparse
from collections import OrderedDict
import csv
import psycopg2
import psycopg2.extras
from datetime import datetime

# See https://www.data.gouv.fr/fr/datasets/base-sirene-des-entreprises-et-de-leurs-etablissements-siren-siret/

SIREN = 'siren'
SIRET = 'siret'

PAGE_SIZE = 10000

# DB fields
FIELDS = ['siret', 'siren', 'datederniertraitementetablissement', 'complementadresseetablissement', 'numerovoieetablissement', 'indicerepetitionetablissement', 'typevoieetablissement', 'libellevoieetablissement', 'codepostaletablissement', 'libellecommuneetablissement', 'libellecommuneetrangeretablissement', 'distributionspecialeetablissement', 'codecommuneetablissement', 'codecedexetablissement', 'libellecedexetablissement', 'denominationusuelleetablissement', 'enseigne1etablissement', 'activiteprincipaleetablissement']

def iter_csv(path):
    with open(path) as csvfile:
        reader = csv.DictReader(csvfile)
        yield from reader

def iter_queries(path):
    def isset(v):
        return v and v != 'false'
    count = 0
    for d in iter_csv(path):
        d =  {k.lower(): v for k, v in d.items()}
        count = count + 1
        if count < 10000:
            if args.type == SIRET:
                updates = OrderedDict((k, v) for k, v in d.items())
            elif args.type == SIREN:
                d['denominationusuelleetablissement'] = d['denominationunitelegale'] or d['denominationusuelle1unitelegale'] or d['denominationusuelle2unitelegale'] or d['denominationusuelle3unitelegale'] or (d['prenomusuelunitelegale'] + ' ' + d['nomusageunitelegale'])
                # d['activitePrincipaleEtablissement'] = d['activitePrincipaleUniteLegale']
                updates = OrderedDict((k, v) for k, v in d.items() if k in FIELDS and isset(v))
            yield updates
        else:
            break

def eval_query():
    if args.type == SIRET:
        return f"""
            INSERT INTO etablissements ({",".join(FIELDS)})
            VALUES ({",".join(f"%({k})s" for k in updates)})
            ON CONFLICT(siret) DO UPDATE SET {",".join(f"{k}=%({k})s" for k in FIELDS)}
        """
        return query
    elif args.type == SIREN:
        return f"""
            UPDATE etablissements SET {",".join(f"{k}=%({k})s" for k in updates)}
            WHERE siren = %(siren)s AND denominationusuelleetablissement IS NULL
        """

def run(pg_uri, source_csv):
    conn = psycopg2.connect(pg_uri)
    conn.set_session(autocommit=True)
    cur = conn.cursor()

    print(datetime.now())

    data = [{
                **line,
            } for line in iter_queries(source_csv) if 'denominationusuelleetablissement' in line.keys() ]

    print(data)


    psycopg2.extras.execute_batch(cur, eval_query(), data, page_size = PAGE_SIZE)
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

if __name__ == "__main__":
    args = parser.parse_args()
    run(args.pg_uri, args.source)


