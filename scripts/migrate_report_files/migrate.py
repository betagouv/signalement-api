import os
import psycopg2
import random
import string

genRand = lambda length: ''.join(random.choices(string.ascii_uppercase + string.digits, k=length))

conn = psycopg2.connect(os.getenv("SIGNALCONSO_DB_URI"))
cur = conn.cursor()

cur.execute("""SELECT id, filename FROM report_files WHERE storage_filename = id::TEXT;""")
rows = cur.fetchall()
for i, row in enumerate(rows):
    storage_filename = f"{genRand(12)}_{row[1]}"
    print(f"id={row[0]} => {storage_filename}")
    # UPDATE
    # s3cmd cp
    print(f"Done ({i})")
