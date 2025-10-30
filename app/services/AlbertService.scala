package services

import config.AlbertConfiguration
import models.albert.AlbertProblem
import models.albert.AlbertProblemsResult
import models.report.Report
import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import services.AlbertService.AlbertError
import services.AlbertService.AlbertLarge
import services.AlbertService.AlbertModel
import services.AlbertService.AlbertSmall
import sttp.capabilities
import sttp.client3.HttpClientFutureBackend
import sttp.client3.SttpBackend
import sttp.client3.UriContext
import sttp.client3.basicRequest
import sttp.client3.playJson._
import sttp.model.Header
import sttp.model.StatusCode.TooManyRequests
import utils.AlbertPrompts
import utils.Logs.RichLogger

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Success
import scala.util.Failure
import scala.util.Try

class AlbertService(albertConfiguration: AlbertConfiguration)(implicit ec: ExecutionContext) {

  val logger: Logger = Logger(this.getClass)

  private val backend: SttpBackend[Future, capabilities.WebSockets] = HttpClientFutureBackend()

  private def chatCompletion(chatPrompt: String, model: AlbertModel = AlbertLarge): Future[String] = {
    val url = uri"https://albert.api.etalab.gouv.fr/v1/chat/completions"
    val modelStr = model match {
      case AlbertLarge => "albert-large"
      case AlbertSmall => "albert-small"
    }
    val body = Json.obj(
      "messages" -> Json.arr(
        Json.obj(
          "content" -> chatPrompt,
          "role"    -> "user"
        )
      ),
      "model"             -> modelStr,
      "frequency_penalty" -> 0,
      "max_tokens"        -> 1000,
      "presence_penalty"  -> 0,
      "temperature"       -> 0,
      "top_p"             -> 1
    )
    logger.infoWithTitle(
      "albert_call",
      s"Call Albert ${modelStr}, prompt length ${chatPrompt.length} : ${chatPrompt.slice(0, 50)}..."
    )
    for {
      response <- basicRequest
        .headers(Header.authorization("Bearer", albertConfiguration.apiKey))
        .post(url)
        .body(body)
        .response(asJson[JsValue])
        .send(backend)
    } yield
      if (response.isSuccess) {
        response.body match {
          case Right(jsValue) =>
            (jsValue \\ "content").headOption
              .map(_.as[String])
              .getOrElse(
                throw AlbertError(s"Albert call failed, incorrect structure of response body $jsValue")
              )
          case Left(e) =>
            logger.warnWithTitle("albert_call", s"Albert call failed ${e.getMessage}")
            throw AlbertError("Albert call failed")
        }
      } else if (response.code == TooManyRequests) {
        logger.warnWithTitle("albert_call", s"Albert call failed too many requests")
        throw AlbertError("Albert call failed too many requests")
      } else {
        logger.warnWithTitle("albert_call", s"Albert call failed with code ${response.code}")
        throw AlbertError(s"Albert call failed with code ${response.code}")
      }
  }

  def classifyReport(report: Report): Future[Option[String]] = {
    val getPrompt = AlbertPrompts.reportClassification _
    (report.getDescription, report.getReponseConsoDescription) match {
      case (Some(description), Some(question)) =>
        val text =
          s"""$description
             |
             |Ma question :
             |$question""".stripMargin
        chatCompletion(getPrompt(text))
          .map(Some(_))
          .recover(_ => None)
      case (Some(description), None) =>
        chatCompletion(getPrompt(description))
          .map(Some(_))
          .recover(_ => None)
      case (None, Some(question)) =>
        chatCompletion(getPrompt(question))
          .map(Some(_))
          .recover(_ => None)
      case (None, None) => Future.successful(None)
    }
  }

  def qualifyReportBasedOnCodeConso(report: Report): Future[Option[String]] =
    report.details.find(_.label == "Description :") match {
      case Some(description) =>
        val url = uri"https://albert.api.etalab.gouv.fr/v1/search"
        val body = Json.obj(
          "collections" -> Json.arr("831476c9-f326-44d6-a2d2-72adbf7e60a6", "1cfcccb5-4d11-46d7-84bf-807d51826175"),
          "prompt"      -> AlbertPrompts.codeConsoSearch(description.value),
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
                  chatCompletion(AlbertPrompts.codeConso(description.value, chunks))
                    .map(Some(_))
                    .recover(_ => None)
                case Left(_) =>
                  Future.successful(None)
              }
            } else {
              Future.successful(None)
            }
          }
      case None => Future.successful(None)
    }

  def labelCompanyActivity(companyId: UUID, selectedCompanyReportsDescriptions: Seq[String]): Future[Option[String]] =
    for {
      label <- chatCompletion(AlbertPrompts.labelCompanyActivity(selectedCompanyReportsDescriptions))
    } yield label match {
      case "Inclassable" => None
      case s if s.length > 100 =>
        logger.info(s"Invalid Albert result, output way too long for company $companyId : ${s.slice(0, 100)}...")
        None
      case _ => Some(label)
    }

  def findProblems(
      selectedCompanyReportsDescriptions: Seq[String]
  ): Future[Option[AlbertProblemsResult]] = {
    val prompt = AlbertPrompts.findProblems(selectedCompanyReportsDescriptions, maxPromptLength = 10000)
    for {
      answer <- chatCompletion(prompt, AlbertLarge)
    } yield Try(Json.parse(answer)) match {
      case Failure(ex) =>
        logger.warnWithTitle("albert_problems_invalid_json", "Albert returned something that wasn't a valid JSON", ex)
        None
      case Success(json) =>
        json.validate[Seq[AlbertProblem]] match {
          case JsError(_) =>
            logger.warnWithTitle(
              "albert_problems_invalid_json",
              s"Albert returned a JSON that wasn't the correct structure : ${Json.stringify(json)}"
            )
            None
          case JsSuccess(problems, _) =>
            Some(
              AlbertProblemsResult(
                nbReportsUsed = selectedCompanyReportsDescriptions.length,
                problemsFound = problems
              )
            )
        }
    }
  }

}

object AlbertService {
  case class AlbertError(message: String, cause: Throwable = None.orNull) extends Exception(message, cause)

  sealed trait AlbertModel
  case object AlbertLarge extends AlbertModel // Currently implemented using Mistral 24B
  case object AlbertSmall extends AlbertModel // Currently implemented using Llama 8B

}
