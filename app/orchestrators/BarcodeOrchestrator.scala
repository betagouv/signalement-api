package orchestrators

import actors.GS1AuthTokenActor
import akka.actor.typed.ActorRef
import akka.actor.typed.Scheduler
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.util.Timeout
import models.barcode.BarcodeProduct
import models.barcode.gs1.OAuthAccessToken
import play.api.Logger
import play.api.libs.json.JsValue
import repositories.barcode.BarcodeProductRepositoryInterface
import services.GS1ServiceInterface
import services.OpenBeautyFactsServiceInterface
import services.OpenFoodFactsServiceInterface

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class BarcodeOrchestrator(
    gs1AuthTokenActor: ActorRef[GS1AuthTokenActor.Command],
    gs1Service: GS1ServiceInterface,
    openFoodFactsService: OpenFoodFactsServiceInterface,
    openBeautyFactsService: OpenBeautyFactsServiceInterface,
    barcodeRepository: BarcodeProductRepositoryInterface
)(implicit
    val executionContext: ExecutionContext,
    timeout: Timeout,
    scheduler: Scheduler
) {

  private val logger = Logger(this.getClass)

  private def getGS1Token(
      request: ActorRef[GS1AuthTokenActor.Reply] => GS1AuthTokenActor.Command
  ): Future[OAuthAccessToken] =
    gs1AuthTokenActor.ask[GS1AuthTokenActor.Reply](request).flatMap {
      case GS1AuthTokenActor.GotToken(token)   => Future.successful(token)
      case GS1AuthTokenActor.TokenError(error) => Future.failed(error)
    }

  private def getFromGS1API(gtin: String): Future[Option[JsValue]] =
    for {
      accessToken <- getGS1Token(GS1AuthTokenActor.GetToken.apply)
      firstTry    <- gs1Service.getProductByGTIN(accessToken, gtin)

      result <- firstTry match {
        case Right(value) => Future.successful(value)
        case Left(_) =>
          for {
            renewedAccessToken <- getGS1Token(GS1AuthTokenActor.RenewToken.apply)
            secondTry          <- gs1Service.getProductByGTIN(renewedAccessToken, gtin)
            maybeProductFromAPI <- secondTry match {
              case Right(value) => Future.successful(value)
              case Left(_)      => Future.successful(None)
            }
          } yield maybeProductFromAPI
      }

    } yield result

  private def getFromOpenFoodFactsAPI(gtin: String, gs1Product: Option[JsValue]): Future[Option[JsValue]] =
    gs1Product match {
      case Some(gs1Product) =>
        if ((gs1Product \ "itemOffered" \ "productDescription").isDefined) {
          Future.successful(None)
        } else {
          openFoodFactsService.getProductByBarcode(gtin)
        }
      case None =>
        Future.successful(None)
    }

  private def getFromOpenBeautyFactsAPI(gtin: String, gs1Product: Option[JsValue]): Future[Option[JsValue]] =
    gs1Product match {
      case Some(gs1Product) =>
        if ((gs1Product \ "itemOffered" \ "productDescription").isDefined) {
          Future.successful(None)
        } else {
          openBeautyFactsService.getProductByBarcode(gtin)
        }
      case None =>
        Future.successful(None)
    }

  def getByGTIN(gtin: String): Future[Option[BarcodeProduct]] =
    for {
      maybeExistingProductInDB <- barcodeRepository.getByGTIN(gtin)
      product <- maybeExistingProductInDB match {
        case Some(_) =>
          logger.debug(s"Fetched product with gtin $gtin from DB")
          Future.successful(maybeExistingProductInDB)
        case None =>
          logger.debug(s"Product with gtin $gtin not in DB, fetching from GS1 API")
          for {
            maybeProductFromAPI             <- getFromGS1API(gtin)
            maybeProductFromOpenFoodFacts   <- getFromOpenFoodFactsAPI(gtin, maybeProductFromAPI)
            maybeProductFromOpenBeautyFacts <- getFromOpenBeautyFactsAPI(gtin, maybeProductFromAPI)
            createdProduct <- maybeProductFromAPI match {
              case Some(product) =>
                barcodeRepository
                  .create(
                    BarcodeProduct(
                      gtin = gtin,
                      gs1Product = product,
                      openFoodFactsProduct = maybeProductFromOpenFoodFacts,
                      openBeautyFactsProduct = maybeProductFromOpenBeautyFacts
                    )
                  )
                  .map(Some(_))
              case None => Future.successful(None)
            }
          } yield createdProduct
      }
    } yield product

  def get(id: UUID): Future[Option[BarcodeProduct]] =
    barcodeRepository.get(id)
}
