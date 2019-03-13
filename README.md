# SignalementApi

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
```

Remarques:

- les guillemets après la commande `sbt` sont nécessaires pour que sbt sache où commence la sous-commande
- on peut aussi lancer en 2 temps. D'abord, on lance sbt, on laisse la commande répondre, puis `run -Dconfig.file=chemin-vers-fichier.conf`. Dans ce cas, les guillemets ne sont pas nécessaires.

L'API est accessible à l'adresse `http://localhost:9000/api` avec rechargement à chaud des modifications.

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
