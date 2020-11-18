# SignalConso API

API de l'outil SignalConso (ex signalement).

L’outil SignalConso permet à chaque consommateur de signaler directement les anomalies constatées dans sa vie de tous les jours (chez son épicier, dans un bar..), de manière très rapide et très simple auprès du professionnel.

Plus d'information ici : https://beta.gouv.fr/startup/signalement.html

L'API nécessite une base PostgreSQL pour la persistence des données (versions supportées : 9.5+).

Le build se fait à l'aide de [SBT](https://www.scala-sbt.org/) (voir [build.sbt])

## Développement

### PostgreSQL

L'application requiert une connexion à un serveur PostgreSQL (sur macOS, vous pouvez utiliser [https://postgresapp.com/]).
Créez une base de données pour l'application : `createdb signalconso` (par défaut localement, la base sera accessible au user `$USER`, sans mot de passe).

Au lancement du programme, les tables seront automatiquement créées si elles n'existent pas (voir [https://www.playframework.com/documentation/2.7.x/Evolutions]).

### Configuration locale

Créer un fichier de configuration local `conf/local.application.conf`, en vous inspirant du template suivant :
```
include "application.conf"

slick.dbs.default.db.properties.url = "postgres://user:pass@host/signalconso"
play.mailer.mock = yes
```

Note :
*La propriété `play.mailer.mock` désactive uniquement l'envoi des mails.
Si elle est active, la constitution du mail est bien effective et le contenu du mail est uniquement loggué dans la console.
Si elle n'est pas active, il faut configurer un serveur de mails à travers les variables définies dans le fichier /conf/slick.conf*

Lancer l'application en local :

```bash
sbt "run -Dconfig.resource=local.application.conf"

# alternative
sbt "run -Dconfig.resource=local.application.conf -DAPPLICATION_HOST=." # permet de rendre sa machine accessible par un autre appareil, tel qu'un smartphone, etc..
```

Remarques:

- les guillemets après la commande `sbt` sont nécessaires pour que sbt sache où commence la sous-commande
- on peut aussi lancer en 2 temps. D'abord, on lance sbt, on laisse la commande répondre, puis `run -Dconfig.resource=local.application.conf`. Dans ce cas, les guillemets ne sont pas nécessaires.

L'API est accessible à l'adresse `http://localhost:9000/api` avec rechargement à chaud des modifications.

## Tests

Pour exécuter les tests :

```bash
sbt test
```

Pour éxecuter uniquement un test (donné par son nom de classe):

```bash
sbt "testOnly *SomeTestSpec"
```

## Démo

La version de démo de l'API est accessible à l'adresse http://demo-signalement-api.beta.gouv.fr/api.

## Production

L'API de production de l'application  est accessible à l'adresse https://signal-api.conso.gouv.fr/api.

## Variables d'environnement

|Nom|Description|Valeur par défaut|
|:---|:---|:---|
|APPLICATION_HOST|Hôte du serveur hébergeant l'application||
|APPLICATION_SECRET|Clé secrète de l'application||
|EVOLUTIONS_AUTO_APPLY|Exécution automatique des scripts `upgrade` de la base de données|false|
|EVOLUTIONS_AUTO_APPLY_DOWNS|Exécution automatique des scripts `downgrade` de la base de données|false|
|MAX_CONNECTIONS|Nombre maximum de connexions ouvertes vers la base de données||
|MAIL_FROM|Expéditeur des mails|dev-noreply@signal.conso.gouv.fr|
|MAIL_CONTACT_RECIPIENT|Boite mail destinataire des mails génériques|contact@signal.conso.gouv.fr|
|MAILER_HOST|Hôte du serveur de mails||
|MAILER_PORT|Port du serveur de mails||
|MAILER_USER|Nom d'utilisateur du serveur de mails||
|MAILER_PASSWORD|Mot de passe du serveur de mails||
|SENTRY_DSN|Identifiant pour intégration avec [Sentry](https://sentry.io)||
|TMP_DIR|Répertoire temporaire pour création des fichiers xlsx||
---

## Liste des API

Le retour de tous les WS (web services) est au format JSON.
Sauf mention contraire, les appels se font en GET.

Pour la plupart des WS, une authentification est nécessaire.
Il faut donc que l'appel contienne le header HTTP `X-Auth-Token`, qui doit contenir un token délivré lors de l'appel au WS authenticate (cf. infra).

### 1. API d'authentification

http://localhost:9000/api/authenticate (POST)

Headers :

- Content-Type:application/json

Exemple body de la request (JSON):

```json
{
    "email":"prenom.nom@ovh.fr",
    "password":"mon-mot-de-passe"
}
```

### 2. API Signalement

*Récupération de tous les signalements*

http://localhost:9000/api/reports

Les signalements sont rendus par page. Le retour JSON est de la forme :

```json
{
    "totalCount": 2,
    "hasNextPage": false,
    "entities": [ ... ]
}
```

- totalCount rend le nombre de résultats trouvés au total pour la requête GET envoyé à l'API, en dehors du système de pagination
- hasNextPage indique s'il existe une page suivante de résultat. L'appelant doit calculer le nouvel offset pour avoir la page suivante
- entities contient les données de signalement de la page courrante
- 250 signalements par défaut sont renvoyés


*Exemple : Récupération des 10 signalements à partir du 30ème*

```
http://localhost:9000/api/reports?offset=0&limit=10
```

- offset est ignoré s'il est négatif ou s'il dépasse le nombre de signalement
- limit est ignoré s'il est négatif. Sa valeur maximum est 250

*Exemple : Récupération des 10 signalements à partir du 30ème pour le département 49*

```
http://localhost:9000/api/reports?offset=0&limit=10&departments=49
```

Le champ departments peut contenir une liste de département séparé par `,`.

*Exemple : récupèration de tous les signalements du département 49 et 94*

```
http://localhost:9000/api/reports?offset=0&limit=10&departments=49,94
```

*Exemple : Récupération par email*

```
http://localhost:9000/api/reports?offset=0&limit=10&email=john@gmail.com
```

*Exemple : Récupération par siret*

```
http://localhost:9000/api/reports?offset=0&limit=10&siret=40305211101436
```

*Exemple : Récupération de toutes les entreprises commençant par Géant*

```
http://localhost:9000/api/reports?offset=0&limit=10&companyName=Géant
```

*Exemple : Récupération par catégorie*

```
http://localhost:9000/api/reports?offset=0&limit=10&category=Nourriture / Boissons
```

*Exemple : Récupération par statusPro*

```
http://localhost:9000/api/reports?offset=0&limit=10&statusPro=À traiter
```

*Exemple : Récupération par détails (recherche plein texte sur les colonnes sous-categories et details)*

```
http://localhost:9000/api/reports?offset=0&limit=10&details=Huwavei
```

*Suppression d'un signalement*

http://localhost:9000/api/reports/:uuid (DELETE)

Statuts :
- 204 No Content : dans le cas où la suppression fonctionne
- 404 Not Found : dans le cas où le signalement n'existe pas
- 412 Precondition Failed : statut renvoyé si des fichiers sont liés à ce signalement. Si l'on souhaite malgré cela supprimer le signalement, il faudra préalablement supprimer ces fichiers

*Modification d'un signalement*

http://localhost:9000/api/reports (PUT)

Le body envoyé doit correspondre à un signalement (de la forme renvoyée par le WS getReport.

Seul les champs suivants sont modifiables :
- firstName
- lastName
- email
- contactAgreement
- companyName
- companyAddress
- companyPostalCode
- companySiret
- statusPro

Statuts :
- 204 No Content : dans le cas où la modification fonctionne
- 404 Not Found : dans le cas où le signalement n'existe pas


### 3. API Files

*Suppression d'un fichier*

http://localhost:9000/api/reports/files/:uuid/:filename (DELETE)

```
Ex: http://localhost:9000/api/reports/files/38702d6a-907f-4ade-8e93-c4b00e668e8a/logo.png
```

Les champs `uuid` et `filename` sont obligatoires.

Statuts :
- 204 No Content : dans le cas où la suppression fonctionne
- 404 Not Found : dans le cas où le fichier n'existe pas

### 4. API Events

*Récupère la liste des évènements d'un signalement*

http://localhost:9000/api/reports/:uuidReport/events

- uuidReport: identifiant du signalement
- eventType: (optionnel) Type de l'évènement parmi : PRO, CONSO, DGCCRF

*Création d'un évènement (i. e. une action à une date)*

http://localhost:9000/api/reports/:uuidReport/events (POST)

Exemple body de la request (JSON):

```json
 {
    "userId": "e6de6b48-1c53-4d3e-a7ff-dd9b643073cf",
    "creationDate": "2019-04-14T00:00:00",
    "eventType": "PRO",
    "action": "Envoi du signalement"
}
```

- action: ce champ doit contenir le libellé d'une action pro disponible via le ws actionPros (cf. infra)

### 5. API Constantes

*La liste des actions professionnels possibles*

http://localhost:9000/api/constants/actionPros

*La liste des actions consommateurs possibles*

http://localhost:9000/api/constants/actionConsos

*La liste des statuts professionnels possibles*

http://localhost:9000/api/constants/statusPros

*La liste des statuts consommateurs possibles*

http://localhost:9000/api/constants/statusConsos
