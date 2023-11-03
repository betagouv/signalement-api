package orchestrators

import actors.GS1AuthTokenActor
import akka.actor.typed.ActorRef
import akka.actor.typed.Scheduler
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.util.Timeout
import models.gs1.GS1Product
import models.gs1.OAuthAccessToken
import play.api.Logger
import play.api.libs.json.JsValue
import repositories.gs1.GS1ProductRepositoryInterface
import services.GS1ServiceInterface

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class GS1Orchestrator(
    gs1AuthTokenActor: ActorRef[GS1AuthTokenActor.Command],
    gs1Service: GS1ServiceInterface,
    gs1Repository: GS1ProductRepositoryInterface
)(implicit
    val executionContext: ExecutionContext,
    timeout: Timeout,
    scheduler: Scheduler
) {

  private val logger = Logger(this.getClass)

  private def getToken(
      request: ActorRef[GS1AuthTokenActor.Reply] => GS1AuthTokenActor.Command
  ): Future[OAuthAccessToken] =
    gs1AuthTokenActor.ask[GS1AuthTokenActor.Reply](request).flatMap {
      case GS1AuthTokenActor.GotToken(token)   => Future.successful(token)
      case GS1AuthTokenActor.TokenError(error) => Future.failed(error)
    }

  private def getFromAPI(gtin: String): Future[Option[JsValue]] =
    for {
      accessToken <- getToken(GS1AuthTokenActor.GetToken.apply)
      firstTry    <- gs1Service.getProductByGTIN(accessToken, gtin)

      result <- firstTry match {
        case Right(value) => Future.successful(value)
        case Left(_) =>
          for {
            renewedAccessToken <- getToken(GS1AuthTokenActor.RenewToken.apply)
            secondTry          <- gs1Service.getProductByGTIN(renewedAccessToken, gtin)
            maybeProductFromAPI <- secondTry match {
              case Right(value) => Future.successful(value)
              case Left(_)      => Future.successful(None)
            }
          } yield maybeProductFromAPI
      }

    } yield result

  def getByGTIN(gtin: String): Future[Option[GS1Product]] =
    for {
      maybeExistingProductInDB <- gs1Repository.getByGTIN(gtin)
      product <- maybeExistingProductInDB match {
        case Some(_) =>
          logger.debug(s"Fetched product with gtin $gtin from DB")
          Future.successful(maybeExistingProductInDB)
        case None =>
          logger.debug(s"Product with gtin $gtin not in DB, fetching from GS1 API")
          for {
            maybeProductFromAPI <- getFromAPI(gtin)
            createdProduct <- maybeProductFromAPI match {
              case Some(product) => gs1Repository.create(GS1Product.fromAPI(gtin, product)).map(Some(_))
              case None          => Future.successful(None)
            }
          } yield createdProduct
      }
    } yield product

  def get(id: UUID): Future[Option[GS1Product]] =
    gs1Repository.get(id)
}
