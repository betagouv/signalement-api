# SignalConso API

API de l'outil SignalConso (ex signalement).

L’outil SignalConso permet à chaque consommateur de signaler directement les anomalies constatées dans sa vie de tous
les jours (chez son épicier, dans un bar..), de manière très rapide et très simple auprès du professionnel.

Plus d'information ici : https://beta.gouv.fr/startup/signalement.html

L'API nécessite une base PostgreSQL pour la persistence des données (versions supportées : 9.5+).

Le build se fait à l'aide de [SBT](https://www.scala-sbt.org/) (voir [build.sbt])

### Variables d'environnement

| Nom                                  | Description                                                                                                                                                     | Valeur par défaut                |
|:-------------------------------------| :-------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------- |
| APPLICATION_HOST                     | Hôte du serveur hébergeant l'application                                                                                                                        |                                  |
| APPLICATION_SECRET                   | Clé secrète de l'application                                                                                                                                    |                                  |
| EVOLUTIONS_ENABLED                   | Active la fonctionnalité d'"évolution" pour l'exécution des scripts de base de données                                                                          | false                            |
| EVOLUTIONS_AUTO_APPLY                | Exécution automatique des scripts `upgrade` de la base de données                                                                                               | false                            |
| EVOLUTIONS_AUTO_APPLY_DOWNS          | Exécution automatique des scripts `downgrade` de la base de données                                                                                             | false                            |
| MAIL_FROM                            | Expéditeur des mails                                                                                                                                            | dev-noreply@signal.conso.gouv.fr |
| MAIL_CONTACT_ADDRESS                 | Boite mail destinataire des mails génériques                                                                                                                    | support@signal.conso.gouv.fr     |
| MAILER_HOST                          | Hôte du serveur de mails                                                                                                                                        |                                  |
| MAILER_PORT                          | Port du serveur de mails                                                                                                                                        |                                  |
| MAILER_USER                          | Nom d'utilisateur du serveur de mails                                                                                                                           |                                  |
| MAILER_MOCK                          | Will only log all the email properties instead of sending an email                                                                                              | no                               |
| MAILER_SSL                           | use SSL                                                                                                                                                         | no                               |
| MAILER_TLS                           | use TLS                                                                                                                                                         | no                               |
| MAILER_TLS_REQUIRED                  | force TLS use                                                                                                                                                   | no                               |
| MAILER_PASSWORD                      | Mot de passe du serveur de mails                                                                                                                                |                                  |
| SENTRY_DSN                           | Identifiant pour intégration avec [Sentry](https://sentry.io)                                                                                                   |                                  |
| ETABLISSEMENT_API_URL                | Url de synchronisation des entreprises                                                                                                                          |                                  |
| ETABLISSEMENT_API_KEY                | token machine pour communiquer avec l'api entreprise pour la mise à jour des entrepise signal conso                                                             |                                  |
| WEBSITE_URL                          | Url complète du site web consommateur                                                                                                                           |                                  |
| APPLICATION_PROTOCOL                 | Protocole de l'url de l'api signal conso                                                                                                                        |                                  |
| DASHBOARD_URL                        | Url complète du site web pro/dgccrf/admin                                                                                                                       |                                  |
| TMP_DIR                              | Dossier temporaire qui sert de tampon pour la génération des fichiers / import de fichiers                                                                      |                                  |
| AV_SCAN_ENABLED                      | Active l'antivirus pour le scan des pièces jointe                                                                                                               | true                             |
| REPORT_EMAILS_BLACKLIST              | Liste d'email pour lesquels les signalements seront ignorés                                                                                                     |                                  |
| SIGNAL_CONSO_SCHEDULED_JOB_ACTIVE    | Active/Désactive les jobs signal conso (utile pour ne pas les lancer en local)                                                                                  | true                             |
| REPORT_NOTIF_TASK_START_TIME         | Heure de lancement du job des mail d'abonnements                                                                                                                | "05:00:00"                       |
| REPORT_TASK_WEEKLY_NOTIF_DAY_OF_WEEK | Jour de lancement du job des mail d'abonnements                                                                                                                 | MONDAY                           |
| REMINDER_TASK_START_TIME             | Heure de lancement du job de relance pro                                                                                                                        | "04:00:00"                       |
| REMINDER_TASK_INTERVAL               | Intervalle de lancement du job de relance pro (ie "24 hours" , toutes les 24 heures)                                                                            | 24 hours                         |
| ARCHIVE_TASK_START_TIME              | Heure de lancement du job suppression des comptes inactifs                                                                                                      | "06:00:00"                       |
| POSTGRESQL_ADDON_URI                 | Full database url                                                                                                                                               |                                  |
| MAX_CONNECTIONS                      | Max connection (hikari property)                                                                                                                                |                                  |
| NUM_THREADS                          | Thread count used to process db connections (hikari property)                                                                                                   |                                  |
| SKIP_REPORT_EMAIL_VALIDATION         | Ignorer la validation d'email consommateur lors d'un dépôt de signalement, à utiliser en cas de problème avec le provider email                                 | false                            |
| EMAIL_PROVIDERS_BLOCKLIST            | Ne valide pas les emails avec les providers listés dans cette variable                                                                                          |                                  |
| OUTBOUND_EMAIL_FILTER_REGEX          | Filter l'envoi d'email sortant (utilisé sur demo / local )                                                                                                      | ".\*"                            |
| S3_ACCESS_KEY_ID                     | ID du compte S3 utilisé                                                                                                                                         |                                  |
| S3_SECRET_ACCESS_KEY                 | SECRET du compte S3 utilisé                                                                                                                                     |                                  |
| S3_ENDPOINT_URL                      | host du bucket                                                                                                                                                  |                                  |
| BUCKETS_REPORT                       | nom du bucket                                                                                                                                                   |                                  |
| AUTHENTICATOR_SECRET                 | Secret utlisé pour forger un token JWT, une modification invalidera les tokens jwt courants                                                                     |                                  |
| CRYPTER_KEY                          | clé utlisée pour forger un token JWT, une modification invalidera les tokens jwt courants                                                                       |                                  |
| USE_TEXT_LOGS                        | Si true, les logs seront au format texte (plus lisible pour travailler en local) plutôt que JSON (qui est le format en prod, pour que New Relic les parse bien) |                                  |




# Développement

### Java

Le projet nécessite la version 17 de java

### PostgreSQL

L'application requiert une connexion à un serveur PostgreSQL (sur macOS, vous pouvez
utiliser [https://postgresapp.com/]).
Créez une base de données pour l'application : `createdb signalconso` (par défaut localement, la base sera accessible au
user `$USER`, sans mot de passe).

Il est possible de lancer un PostgreSQL à partir d'une commande docker-compose (le fichier en question est disponible
sous scripts/local/)

à la racine du projet faire :

```
docker-compose -f scripts/local/docker-compose.yml up

```

Au lancement du programme, les tables seront automatiquement créées si elles n'existent pas (
voir [https://www.playframework.com/documentation/2.7.x/Evolutions] **et s'assurer que les properties play.evolutions
sont a true**).

Il est possible d'injecter des données de test dans la base signal conso, pour cela il faut jouer les scripts suivants :

- /test/scripts/insert_users.sql
- /test/scripts/insert_companies.sql
- /test/scripts/insert_company_accesses.sql
- /test/scripts/insert_reports.sql

### Configuration locale

Lancer une base de donnes PosgreSQL provisionée avec les tables et données (voir plus haut)

L'application a besoin de variables d'environnements. Vous devez les configurer. Il y a plusieurs manières de faire,
nous recommandons la suivante :

```bash
# à ajouter dans votre .zprofile, .zshenv .bash_profile, ou équivalent
# pour toutes les valeurs avec XXX, vous devez renseigner des vraies valeurs.
# Vous pouvez par exemple reprendre les valeurs de l'environnement de démo dans Clever Cloud

function scsbt {
  # Set all environnements variables for the api then launch sbt
  # It forwards arguments, so you can do "scsbt", "scscbt compile", etc.
  echo "Launching sbt with extra environnement variables"
  MAILER_HOST="XXX" \
  MAILER_PORT="XXX" \
  MAILER_SSL="yes" \
  MAILER_TLS="no" \
  MAILER_TLS_REQUIRED="no" \
  MAILER_USER="XXX" \
  MAILER_PASSWORD="XXX" \
  MAILER_MOCK="yes" \
  OUTBOUND_EMAIL_FILTER_REGEX="beta?.gouv|@.*gouv.fr" \
  SIGNAL_CONSO_SCHEDULED_JOB_ACTIVE="false" \
  MAIL_FROM="XXX" \
  MAIL_CONTACT_ADDRESS="XXX" \
  EVOLUTIONS_ENABLED=true \
  EVOLUTIONS_AUTO_APPLY=false \
  TMP_DIR="/tmp/" \
  S3_ACCESS_KEY_ID="XXX" \
  S3_SECRET_ACCESS_KEY="XXX" \
  POSTGRESQL_ADDON_URI="XXX" \
  ETABLISSEMENT_API_URL="http://localhost:9002/api/companies/search" \
  ETABLISSEMENT_API_KEY="XXX" \
  USE_TEXT_LOGS="true" \
  sbt "$@"
}

```

Ceci définit une commande `scsbt`, à utiliser à la place de `sbt`

#### ❓ Pourquoi définir cette fonction, pourquoi ne pas juste exporter les variables en permanence ?

Pour éviter que ces variables ne soient lisibles dans l'environnement par n'importe quel process lancés sur votre
machine. Bien sûr c'est approximatif, on ne peut pas empêcher un process de parser le fichier de conf directement, mais
c'est déjà un petit niveau de protection.

#### ❓ Puis-je mettre ces variables dans un fichier local dans le projet, que j'ajouterai au .gitignore ?

C'est trop dangereux. Nos repos sont publics, la moindre erreur humaine au niveau du .gitignore pourrait diffuser toutes
les variables.

### Lancer l'appli

Lancer

```bash
scsbt run
```

L'API est accessible à l'adresse `http://localhost:9000/api` avec rechargement à chaud des modifications.

## Tests

Pour exécuter les tests :

```bash
scsbt test
```

Pour éxecuter uniquement un test (donné par son nom de classe):

```bash
scsbt "testOnly *SomeTestSpec"
```


# Démo

La version de démo de l'API est accessible à l'adresse http://demo-signalement-api.beta.gouv.fr/api.


# Production

L'API de production de l'application est accessible à l'adresse https://signal-api.conso.gouv.fr/api.

### Gestion des logs

Procédure pour pousser les logs sur new relic :

```
clever login
clever link --org orga_ID app_ID
#  You should see application alias displayed
clever applications
#To drain log to new relic
clever drain create --alias "ALIAS_PROD" NewRelicHTTP "https://log-api.eu.newrelic.com/log/v1" --api-key NEW_RELIC_API_KEY
clever applications
```

### Restauration de de base de données


Des backups sont disponibles pour récupérer la base données en cas de problème.
2. Récupérer le fichier de backup
3. Créer une nouvelle base de données vierge
4. Lancer la commande suivante :

```
#Pour restaurer sur une nouvelle base de données
pg_restore -h $HOST -p $PORT -U $USER -d $DATABASE_NAME --format=c ./path/backup.dump --no-owner --role=$USER --verbose --no-acl --schema=$SIGNAL_CONSO_SCHEMA
```

4. Rattacher la base de données à l'application.


