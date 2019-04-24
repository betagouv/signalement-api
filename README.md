# SignalConso API

API de l'outil SignalConso (ex signalement).

L’outil SignalConso permet à chaque consommateur de signaler directement les anomalies constatées dans sa vie de tous les jours (chez son épicier, dans un bar..), de manière très rapide et très simple auprès du professionnel.

Plus d'information ici : https://beta.gouv.fr/startup/signalement.html

L'API nécessite une base PostgreSQL pour la persistence des données (versions supportées : 9.5, 9.6, 10.x).

Le build se fait à l'aide de [SBT](https://www.scala-sbt.org/) version >= 1.2.6

## Développement

Créer un fichier de configuration local, par exemple local.conf, et configurer la connexion à la base de données via la propriété `slick.dbs.default.db.properties.url` (ne fonctionne que si le compte requiert un mot de passe)

```scala
include "application.conf"

slick.dbs.default.db.properties.url=postgres://user:pass@host/dbname
play.mailer.mock = yes
```

Note :
*La propriété `play.mailer.mock` désactive uniquement l'envoi des mails. 
Si elle est active, la constitution du mail est bien effective et le contenu du mail est uniquement loggué dans la console.
Si elle n'est pas active, il faut configurer un serveur de mails à travers les variables définies dans le fichier /conf/slick.conf*

Lancer l'application en local :

```bash
sbt "run -Dconfig.file=[chemin vers le fichier de configuration local]"

Ex:
sbt "run -Dconfig.file=./conf/local.conf"

# alternative
sbt "run -Dconfig.file=./conf/local.conf -DAPPLICATION_HOST=." # permet de rendre sa machine accessible par un autre appareil, tel qu'un smartphone, etc..
```

Remarques:

- les guillemets après la commande `sbt` sont nécessaires pour que sbt sache où commence la sous-commande
- on peut aussi lancer en 2 temps. D'abord, on lance sbt, on laisse la commande répondre, puis `run -Dconfig.file=chemin-vers-fichier.conf`. Dans ce cas, les guillemets ne sont pas nécessaires.

L'API est accessible à l'adresse `http://localhost:9000/api` avec rechargement à chaud des modifications.

## Installation Postgres

Après intallation de Postgres, il faut créer la database nécessaire pour l'application. 
Le nom de cette base de données doit correspondre à la configuration utilisée. 

Si l'on utilise un fichier spécifique `local.conf` contenant :

```
slick.dbs.default.db.properties.url = "postgres://randomUser@localhost:5432/api"
```

Il faudra alors créer une base api :

```sh
CREATE DATABASE api;
```

Au lancement du programme, les tables seront automatiquement créées si elles n'existent pas.

## Tests

Pour exécuter les tests :

```bash
sbt test
```

## Démo

La version de démo de l'API est accessible à l'adresse http://demo-signalement-api.beta.gouv.fr/api.

## Production

L'API de production de l'application  est accessible à l'adresse https://signalconso-api.beta.gouv.fr/api.

## Variables d'environnement

|Nom|Description|Valeur par défaut|
|:---|:---|:---|
|<a name="APPLICATION_HOST">APPLICATION_HOST</a>|Hôte du serveur hébergeant l'application||
|<a name="APPLICATION_HOST">APPLICATION_SECRET</a>|Clé secrète de l'application||
|<a name="APPLICATION_HOST">EVOLUTIONS_AUTO_APPLY</a>|Exécution automatique des scripts `upgrade` de la base de données|false|
|<a name="APPLICATION_HOST">EVOLUTIONS_AUTO_APPLY_DOWNS</a>|Exécution automatique des scripts `downgrade` de la base de données|false|
|<a name="APPLICATION_HOST">MAX_CONNECTIONS</a>|Nombre maximum de connexions ouvertes vers la base de données||
|<a name="APPLICATION_HOST">MAIL_FROM</a>|Expéditeur des mails|dev-noreply@signalconso.beta.gouv.fr|
|<a name="APPLICATION_HOST">MAIL_CONTACT_RECIPIENT</a>|Boite mail destinataire des mails génériques|contact@signalconso.beta.gouv.fr|
|<a name="APPLICATION_HOST">MAILER_HOST</a>|Hôte du serveur de mails||
|<a name="APPLICATION_HOST">MAILER_PORT</a>|Port du serveur de mails||
|<a name="APPLICATION_HOST">MAILER_USER</a>|Nom d'utilisateur du serveur de mails||
|<a name="APPLICATION_HOST">MAILER_PASSWORD</a>|Mot de passe du serveur de mails||
|<a name="APPLICATION_HOST">SENTRY_DSN</a>|Identifiant pour intégration avec [Sentry](https://sentry.io)||

## Exemples d'appel du WS getReports

L'objet retour rendu est de la forme : 

```js
{
"totalCount": 2,
"hasNextPage": false,
"entities": [ ... ]    
}
```

- totalCount rend le nombre de résultats trouvés au total pour la requête GET envoyé à l'API, en dehors du système de pagination
- hasNextPage indique s'il existe une page suivante de résultat. L'appelant doit calculer le nouvel offset pour avoir la page suivante
- entities contient les données de signalement de la page courrante


**Récupération de tous les signalements (250 par défaut sont rendus)**

http://localhost:9000/api/reports

**Récupération des 10 signalements à partir du 30ème**

http://localhost:9000/api/reports?offset=30&limit=10

- offset est ignoré s'il est négatif ou s'il dépasse le nombre de signalement
- limit est ignoré s'il est négatif. Sa valeur maximum est 250

**Récupération des 10 signalements à partir du 30ème pour le code postal 49000**

http://localhost:9000/api/reports?offset=30&limit=10&codePostal=49000

NB: Récupère toutes les entreprises commençant par le code postal. 
http://localhost:9000/api/reports?offset=30&limit=10&codePostal=49 récupèrera tous les signalements du département 49.

**Récupération par email**

http://localhost:9000/api/reports?offset=30&limit=10&email=john@gmail.com

**Récupération par siret**

http://localhost:9000/api/reports?offset=30&limit=10&siret=40305211101436

**Récupération par nom d'entreprise**

http://localhost:9000/api/reports?offset=30&limit=10&entreprise=Géant

NB: Récupère toutes les entreprises commençant par Géant (Géant Casino est retrouvé).

## WS getEvents

Récupère la liste des évènements d'un signalement.

http://localhost:9000/api/events/:uuidReport?eventType=:eventType

- uuidReport: identifiant du signalement
- eventType: (optionnel) Type de l'évènement parmi : PRO, CONSO, DGCCRF


Ex: 

http://localhost:9000/api/events/7d20dbab-9983-4ded-8c38-20ceba449b06?eventType=PRO

