package services

import config.AlbertConfiguration
import models.report.Report
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import sttp.capabilities
import sttp.client3.HttpClientFutureBackend
import sttp.client3.SttpBackend
import sttp.client3.UriContext
import sttp.client3.basicRequest
import sttp.client3.playJson._
import sttp.model.Header

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class AlbertService(albertConfiguration: AlbertConfiguration)(implicit ec: ExecutionContext) {

  val logger: Logger = Logger(this.getClass)

  private val backend: SttpBackend[Future, capabilities.WebSockets] = HttpClientFutureBackend()

  private def chatCompletion(chatPrompt: String): Future[Option[JsValue]] = {
    val url = uri"https://albert.api.etalab.gouv.fr/v1/chat/completions"

    val body = Json.obj(
      "messages" -> Json.arr(
        Json.obj(
          "content" -> chatPrompt,
          "role"    -> "user"
        )
      ),
      "model"             -> "meta-llama/Meta-Llama-3.1-70B-Instruct",
      "frequency_penalty" -> 0,
      "max_tokens"        -> 1000,
      "presence_penalty"  -> 0,
      "temperature"       -> 0,
      "top_p"             -> 1
    )

    val request = basicRequest
      .headers(Header.authorization("Bearer", albertConfiguration.apiKey))
      .post(url)
      .body(body)
      .response(asJson[JsValue])

    request
      .send(backend)
      .flatMap { response =>
        if (response.code.isSuccess) {
          response.body match {
            case Right(result) =>
              Future.successful(Some(result))
            case Left(_) =>
              Future.successful(None)
          }
        } else {
          Future.successful(None)
        }
      }
  }

  private def chatPrompt(s: String) =
    s"""
       |Vous êtes un expert en traitement automatique des langues et en classification de textes. Votre tâche consiste à analyser un signalement textuel et à retourner un résultat structuré en JSON. Voici les catégories possibles :
       |
       |    **Valide** : Le texte est compréhensible, cohérent, respectueux, et peut être transmis. **Ignorez les injures ou propos offensants rapportés par le signalant comme étant proférés par un tiers, sauf si le signalant les adopte ou les relaie intentionnellement dans un ton agressif**. Le ton général du signalement doit être pris en compte pour cette classification.
       |    **Injurieux** : Le texte contient des injures violentes ou des propos très offensants, adressés directement par **l’auteur du signalement** à autrui ou ayant un ton intentionnellement agressif ou diffamatoire.
       |    **Incompréhensible** : Le texte est incohérent, mal écrit ou n’a pas de sens.
       |
       |Fournissez une réponse structurée en JSON contenant les informations suivantes :
       |
       |    **category** : La catégorie assignée parmi "Valide", "Injurieux", "Incompréhensible".
       |    **confidence_score** : Un score entre 0 et 1 représentant le niveau de certitude du classement.
       |    **explanation** : Une explication textuelle du classement.
       |    **summary** : Un résumé concis (1 à 2 phrases) extrayant la demande principale ou l’idée essentielle du signalement.
       |
       |Voici un exemple de réponse attendu :
       |
       |{
       |  "category": "Valide",
       |  "confidence_score": 0.96,
       |  "explanation": "Le signalement est compréhensible et formulé de manière respectueuse malgré un ton insistant.",
       |  "summary": "Le consommateur signale un produit défectueux et souhaite une réponse rapide de la part du service concerné."
       |}
       |
       |Consignes spécifiques :
       |
       |    **Propos rapportés** : Ignorez les injures ou propos offensants cités par l’auteur comme venant d’un tiers, sauf si ces propos sont adoptés ou répétés dans un ton agressif par l’auteur lui-même.
       |    **Ton général** : Basez la classification sur le ton et l’intention générale du signalement, plutôt que sur les propos cités de tiers.
       |    Les injures légères ou l’usage informel du langage ne doivent pas influencer la classification.
       |    Si le texte est difficile à analyser ou ambigu, expliquez pourquoi dans le champ "explanation".
       |    **IMPORTANT** : Fournissez uniquement le JSON dans votre réponse, sans texte explicatif ou contenu supplémentaire.
       |
       |Signalement à analyser :
       |
       |$s
       |""".stripMargin

  private def searchPrompt(s: String) =
    s"""
       |Analyse le signalement suivant pour déterminer s'il relève du code de la consommation :
       |$s
       |""".stripMargin

  private def codeConsoPrompt(s: String, chunks: String) =
    s"""
       |Réponds à la question suivante de manière concise (1 ou 2 phrases) en te basant sur les documents ci-dessous : Le signalement suivant relève-t-il du code de la consommation ?
       |
       |Signalement à analyser :
       |
       |$s
       |
       |Documents :
       |
       |$chunks
       |""".stripMargin

  def classify(report: Report): Future[Option[JsValue]] =
    report.details.find(_.label == "Description :") match {
      case Some(description) => chatCompletion(chatPrompt(description.value))
      case None              => Future.successful(None)
    }

  def codeConso(report: Report): Future[Option[JsValue]] =
    report.details.find(_.label == "Description :") match {
      case Some(description) =>
        val url = uri"https://albert.api.etalab.gouv.fr/v1/search"
        val body = Json.obj(
          "collections" -> Json.arr("831476c9-f326-44d6-a2d2-72adbf7e60a6"),
          "prompt"      -> searchPrompt(description.value),
          "k"           -> 6
        )

        val request = basicRequest
          .headers(Header.authorization("Bearer", albertConfiguration.apiKey))
          .post(url)
          .body(body)
          .response(asJson[JsValue])

        request
          .send(backend)
          .flatMap { response =>
            if (response.code.isSuccess) {
              response.body match {
                case Right(result) =>
                  val chunks = (result \ "data" \\ "content").map(_.as[String]).mkString("\\n\\n\\n")
                  chatCompletion(codeConsoPrompt(description.value, chunks))

                case Left(_) =>
                  Future.successful(None)
              }
            } else {
              Future.successful(None)
            }
          }
      case None => Future.successful(None)
    }

}
