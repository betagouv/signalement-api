# SignalConso API

API de l'outil SignalConso (ex signalement).

L’outil SignalConso permet à chaque consommateur de signaler directement les anomalies constatées dans sa vie de tous
les jours (chez son épicier, dans un bar..), de manière très rapide et très simple auprès du professionnel.

Plus d'information ici : https://beta.gouv.fr/startup/signalement.html

L'API nécessite une base PostgreSQL pour la persistence des données (versions supportées : 9.5+).

Le build se fait à l'aide de [SBT](https://www.scala-sbt.org/) (voir [build.sbt])

### Variables d'environnement

| Nom                                       | Description                                                                                                                                                     | Valeur par défaut                |
|:------------------------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------|:---------------------------------|
| APPLICATION_HOST                          | Hôte du serveur hébergeant l'application                                                                                                                        |                                  |
| APPLICATION_SECRET                        | Clé secrète de l'application                                                                                                                                    |                                  |
| MAIL_FROM                                 | Expéditeur des mails                                                                                                                                            | dev-noreply@signal.conso.gouv.fr |
| MAIL_CONTACT_ADDRESS                      | Boite mail destinataire des mails génériques                                                                                                                    | support@signal.conso.gouv.fr     |
| MAILER_HOST                               | Hôte du serveur de mails                                                                                                                                        |                                  |
| MAILER_PORT                               | Port du serveur de mails                                                                                                                                        |                                  |
| MAILER_USER                               | Nom d'utilisateur du serveur de mails                                                                                                                           |                                  |
| MAILER_MOCK                               | Will only log all the email properties instead of sending an email                                                                                              | no                               |
| MAILER_SSL                                | use SSL                                                                                                                                                         | no                               |
| MAILER_TLS                                | use TLS                                                                                                                                                         | no                               |
| MAILER_TLS_REQUIRED                       | force TLS use                                                                                                                                                   | no                               |
| MAILER_PASSWORD                           | Mot de passe du serveur de mails                                                                                                                                |                                  |
| SENTRY_DSN                                | Identifiant pour intégration avec [Sentry](https://sentry.io)                                                                                                   |                                  |
| ETABLISSEMENT_API_URL                     | Url de synchronisation des entreprises                                                                                                                          |                                  |
| ETABLISSEMENT_API_KEY                     | token machine pour communiquer avec l'api entreprise pour la mise à jour des entrepise signal conso                                                             |                                  |
| WEBSITE_URL                               | Url complète du site web consommateur                                                                                                                           |                                  |
| APPLICATION_PROTOCOL                      | Protocole de l'url de l'api signal conso                                                                                                                        |                                  |
| DASHBOARD_URL                             | Url complète du site web pro/dgccrf/admin                                                                                                                       |                                  |
| TMP_DIR                                   | Dossier temporaire qui sert de tampon pour la génération des fichiers / import de fichiers                                                                      |                                  |
| AV_SCAN_ENABLED                           | Active l'antivirus pour le scan des pièces jointe                                                                                                               | true                             |
| AV_API_ACTIVE                             | Active l'antivirus via API et désactive l'antivirus local                                                                                                       | true                             |
| AV_BYPASS_SCAN                            | Autorise le telechargement des fichiers sans scan et désactive l'utilisation de l'antivirus (pour dev demo)                                                     | true                             |
| AV_API_KEY                                | Token client utilisé pour appeler l'api antivirus (voir readme projet antivirus)                                                                                | mon token securise               |
| AV_API_URL                                | URL de l'api antivirus                                                                                                                                          | https://api-av.com/              |
| SIGNAL_CONSO_SCHEDULED_JOB_ACTIVE         | Active/Désactive les jobs signal conso (utile pour ne pas les lancer en local)                                                                                  | true                             |
| REPORT_NOTIF_TASK_START_TIME              | Heure de lancement du job des mail d'abonnements                                                                                                                | "05:00:00"                       |
| REPORT_TASK_WEEKLY_NOTIF_DAY_OF_WEEK      | Jour de lancement du job des mail d'abonnements                                                                                                                 | MONDAY                           |
| REMINDER_TASK_START_TIME                  | Heure de lancement du job de relance pro                                                                                                                        | "04:00:00"                       |
| REMINDER_TASK_INTERVAL                    | Intervalle de lancement du job de relance pro (ie "24 hours" , toutes les 24 heures)                                                                            | 24 hours                         |
| ARCHIVE_TASK_START_TIME                   | Heure de lancement du job suppression des comptes inactifs                                                                                                      | "06:00:00"                       |
| POSTGRESQL_ADDON_URI                      | Full database url                                                                                                                                               |                                  |
| POSTGRESQL_ADDON_HOST                     | Database host                                                                                                                                                   |                                  |
| POSTGRESQL_ADDON_PORT                     | Database port                                                                                                                                                   |                                  |
| POSTGRESQL_ADDON_DB                       | Database name                                                                                                                                                   |                                  |
| POSTGRESQL_ADDON_USER                     | Database user                                                                                                                                                   |                                  |
| POSTGRESQL_ADDON_PASSWORD                 | Database password                                                                                                                                               |                                  |
| MAX_CONNECTIONS                           | Max connection (hikari property)                                                                                                                                |                                  |
| NUM_THREADS                               | Thread count used to process db connections (hikari property)                                                                                                   |                                  |
| SKIP_REPORT_EMAIL_VALIDATION              | Ignorer la validation d'email consommateur lors d'un dépôt de signalement, à utiliser en cas de problème avec le provider email                                 | false                            |
| EMAIL_PROVIDERS_BLOCKLIST                 | Ne valide pas les emails avec les providers listés dans cette variable                                                                                          |                                  |
| OUTBOUND_EMAIL_FILTER_REGEX               | Filter l'envoi d'email sortant (utilisé sur demo / local )                                                                                                      | ".\*"                            |
| S3_ACCESS_KEY_ID                          | ID du compte S3 utilisé                                                                                                                                         |                                  |
| S3_SECRET_ACCESS_KEY                      | SECRET du compte S3 utilisé                                                                                                                                     |                                  |
| S3_ENDPOINT_URL                           | host du bucket                                                                                                                                                  |                                  |
| BUCKETS_REPORT                            | nom du bucket                                                                                                                                                   |                                  |
| SIGNER_KEY                                | Secret utlisé pour forger un cookie, une modification invalidera les cookies courants                                                                           |                                  |
| CRYPTER_KEY                               | clé utlisée pour forger un cookie, une modification invalidera les cookies courants                                                                             |                                  |
| USE_TEXT_LOGS                             | Si true, les logs seront au format texte (plus lisible pour travailler en local) plutôt que JSON (qui est le format en prod, pour que New Relic les parse bien) |                                  |
| COOKIE_DOMAIN                             | Voir la section dédiée plus bas                                                                                                                                 |                                  |
| COOKIE_SAME_SITE                          | Voir la section dédiée plus bas                                                                                                                                 | strict                           |
| COOKIE_SECURE                             | Voir la section dédiée plus bas                                                                                                                                 | true                             |

# Développement

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

#### Script de migration

Le projet utilise l'outil flyway (https://flywaydb.org/) pour la gestion des scripts de migration.

Les scripts de migration sont lancés au run de l'application, ils sont disponibles dans le repertoire conf/db/migration/default.

**Warning** Important 

Un script doit impérativement être écrit de manière à ce que l'application fonctionne toujours en cas de rollback de l'application.

Ceci afin de ne pas avoir gérer de procédure de rollback complexe :
Avoir l'ancienne la structure de données et la nouvelle qui fonctionnent en parralèle puis un certain temps après supprimer l'ancienne structure.

Cette méthode est recommandée par flyway et est décrite sur le lien suivant : https://documentation.red-gate.com/fd/rollback-guidance-138347143.html





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
  TMP_DIR="/tmp/" \
  S3_ACCESS_KEY_ID="XXX" \
  S3_SECRET_ACCESS_KEY="XXX" \
  POSTGRESQL_ADDON_URI="XXX" \
  ETABLISSEMENT_API_URL="http://localhost:9002/api/companies/search" \
  ETABLISSEMENT_API_KEY="XXX" \
  USE_TEXT_LOGS="true" \
  POSTGRESQL_ADDON_HOST="XXX" \
  POSTGRESQL_ADDON_PORT="XXX" \
  POSTGRESQL_ADDON_DB="XXX" \
  POSTGRESQL_ADDON_USER="XXX" \
  POSTGRESQL_ADDON_PASSWORD= \
  LOCAL_SYNC="false" \
  sbt "$@"
}

```

Ou si vous utilisez fish shell :

```fish
# à ajouter dans votre fish.config
# pour toutes les valeurs avec XXX, vous devez renseigner des vraies valeurs.
# Vous pouvez par exemple reprendre les valeurs de l'environnement de démo dans Clever Cloud

function scsbt
  # Set all environnements variables for the api then launch sbt
  # It forwards arguments, so you can do "scsbt", "scscbt compile", etc.
  echo "Launching sbt with extra environnement variables"
  set -x MAILER_HOST "XXX"
  set -x MAILER_PORT "XXX"
  set -x MAILER_SSL "yes"
  set -x MAILER_TLS "no"
  set -x MAILER_TLS_REQUIRED "no"
  set -x MAILER_USER "XXX"
  set -x MAILER_PASSWORD "XXX"
  set -x MAILER_MOCK "yes"
  set -x OUTBOUND_EMAIL_FILTER_REGEX "beta?.gouv|@.*gouv.fr"
  set -x SIGNAL_CONSO_SCHEDULED_JOB_ACTIVE "false"
  set -x TMP_DIR "/tmp/"
  set -x S3_ACCESS_KEY_ID "XXX"
  set -x S3_SECRET_ACCESS_KEY "XXX"
  set -x POSTGRESQL_ADDON_URI "XXX"
  set -x ETABLISSEMENT_API_URL "http://localhost:9002/api/companies/search"
  set -x ETABLISSEMENT_API_KEY "XXX"
  set -x USE_TEXT_LOGS "true"
  set -x POSTGRESQL_ADDON_HOST "XXX"
  set -x POSTGRESQL_ADDON_PORT "XXX"
  set -x POSTGRESQL_ADDON_DB "XXX"
  set -x POSTGRESQL_ADDON_USER "XXX"
  set -x POSTGRESQL_ADDON_PASSWORD
  set -x LOCAL_SYNC false
  sbt $argv
end
```

Pour lancer les tests uniquement, renseigner simplement les valeurs suivantes :

 | Variable                  | Valeur           |
|:--------------------------|:-----------------|
 | POSTGRESQL_ADDON_HOST     | localhost        |
 | POSTGRESQL_ADDON_PORT     | 5432             |
 | POSTGRESQL_ADDON_DB       | test_signalconso |
 | POSTGRESQL_ADDON_USER     | $USER            |
 | POSTGRESQL_ADDON_PASSWORD |                  |


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

1. Récupérer le fichier de backup
2. Créer une nouvelle base de données vierge
3. Lancer la commande suivante :

```
#Pour restaurer sur une nouvelle base de données
pg_restore -h $HOST -p $PORT -U $USER -d $DATABASE_NAME --format=c ./path/backup.dump --no-owner --role=$USER --verbose --no-acl --schema=$SIGNAL_CONSO_SCHEMA
```

4. Rattacher la base de données à l'application.


### Gestion de l'authentification

Au départ, le dashboard utilisait une authentification par token JWT classique (stockée avec le user dans le local storage). 
Suite à l'audit de sécurité, il nous a été demandé de migrer vers une authentification par cookie pour se prémunir des attaques XSS.

Pour sécuriser un maximum le dashboard, les critères suivants ont été implémentés :
- Le cookie est en `HttpOnly` (non accessible en javascript)
- Le cookie est en `Secure` (ne peut être transmis qu'en https pour éviter les attaques min in the middle)
- Plus rien n'est stocké en local storage pour éviter les désynchronisations : le user est récupéré lors du 1er chargement de la page. Si on reçoit un 401, on redirige vers la page de connexion.
- En production, le paramètre `SameSite` sera configuré en `Strict`, ou éventuellement en `Lax` pour se prémunir des attaques CSRF
- Puisque le paramètre SameSite est utilisé, il n'est pas nécessaire de controller l'origine de la requête. Mais cela peut être fait via la configuration play suivante : `allowedOrigins = [${?ALLOWED_ORIGIN}, ${?ALLOWED_LOCALHOST_ORIGIN}]`


#### Variables d'env

`COOKIE_DOMAIN` : Permets de définir le domaine (coté serveur) sur lequel s'applique le cookie. Laisser vide sauf besoin spécifique. 
Le domaine par défaut correspond à l'url complète du serveur.

`COOKIE_SAME_SITE` : Permet de définir depuis quelle est origine peut être envoyé le cookie. Doit être configuré à `None` sur démo. 
C'est essentiel pour pouvoir se connecter depuis localhost (développement local). De plus, `cleverapps.io` est un suffixe public connu et il est donc impossible de définir un cookie sur ce domaine (voir resources ci-dessous).
`gouv.fr` est également un suffixe public connu, mais pas le sous domaine `conso.gouv.fr`, on peut donc définir des cookies dessus.
Mettre `Lax` pour le développement local car ne peut pas être `None` si `Secure` est `false`.

`COOKIE_SECURE` : Mettre à `false` pour le développement local. 

#### Safari

Safari bloque les cookies de type "3rd party". Sur démo, meme si le domaine "cleverapps.io" est commun, celui-ci étant un suffixe public connu, Safari refuse de définir un cookie même si SameSite est à None et que le domaine est vide.
Il est possible de désactiver cela en allant dans : Safari > Réglages > Confidentialité > Empêcher le suivi intersite.

#### Resources

- Suffixes publics : https://stackoverflow.com/questions/42419074/chrome-not-sharing-the-cookie-between-the-subdomains/52475993#52475993
- Safari : https://developer.apple.com/forums/thread/680039, https://stackoverflow.com/questions/64487333/cross-domain-cookies-not-working-on-safari, https://stackoverflow.com/questions/61386688/safari-not-include-cookie-to-second-cors-request
- https://stackoverflow.com/questions/54942698/cookie-is-not-sent-with-cors-web-request
- https://security.stackexchange.com/questions/203890/how-to-implement-csrf-protection-with-a-cross-origin-request-cors
- https://developer.mozilla.org/fr/docs/Web/HTTP/Headers/Set-Cookie