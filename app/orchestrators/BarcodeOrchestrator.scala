package orchestrators

import actors.GS1AuthTokenActor
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Scheduler
import org.apache.pekko.actor.typed.scaladsl.AskPattern.Askable
import org.apache.pekko.util.Timeout
import models.barcode.BarcodeProduct
import models.barcode.gs1.OAuthAccessToken
import play.api.Logger
import play.api.libs.json.JsValue
import repositories.barcode.BarcodeProductRepositoryInterface
import services.GS1ServiceInterface
import services.OpenBeautyFactsServiceInterface
import services.OpenFoodFactsServiceInterface
import utils.Logs.RichLogger

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

  def getByGTIN(gtin: String): Future[Option[BarcodeProduct]] =
    for {
      maybeExistingProductInDB <- barcodeRepository.getByGTIN(gtin)
      product <- maybeExistingProductInDB match {
        case Some(_) =>
          logger.debug(s"Fetched product with gtin $gtin from DB")
          Future.successful(maybeExistingProductInDB)
        case None => fetchProductFromGS1(gtin)
      }
    } yield product

  private def fetchProductFromGS1(gtin: String): Future[Option[BarcodeProduct]] =
    getFromGS1API(gtin).flatMap {
      case Some(apiProduct) =>
        val newProduct = BarcodeProduct(
          gtin = gtin,
          gs1Product = apiProduct,
          openFoodFactsProduct = None,
          openBeautyFactsProduct = None
        )
        barcodeRepository
          .create(newProduct)
          .flatMap { createdProduct =>
            enrichWithOpenFactData(createdProduct).map(Some(_))
          }
          .recover { case error =>
            logger.warnWithTitle(
              "GS1_barcode_product_already_exists",
              s"GTIN $gtin already exist cannot create it again",
              error
            )
            None
          }

      case None =>
        // GS1 API did not return any product
        Future.successful(None)
    }

  /** Enrich BarcodeProduct after the creation to prevent calling open data api when multiple async call are made from
    * all instances
    */
  private def enrichWithOpenFactData(barcodeProduct: BarcodeProduct): Future[BarcodeProduct] =
    for {
      maybeProductFromOpenFoodFacts   <- openFoodFactsService.getProductByBarcode(barcodeProduct.gtin)
      maybeProductFromOpenBeautyFacts <- openBeautyFactsService.getProductByBarcode(barcodeProduct.gtin)
      updatedProduct <- {
        val updatedProduct = barcodeProduct.copy(
          openFoodFactsProduct = maybeProductFromOpenFoodFacts,
          openBeautyFactsProduct = maybeProductFromOpenBeautyFacts
        )
        // Update the product in the database if any additional data was found
        if (maybeProductFromOpenFoodFacts.isDefined || maybeProductFromOpenBeautyFacts.isDefined) {
          barcodeRepository.update(updatedProduct.id, updatedProduct).map(_ => updatedProduct)
        } else {
          Future.successful(updatedProduct)
        }
      }
    } yield updatedProduct

  def get(id: UUID): Future[Option[BarcodeProduct]] =
    barcodeRepository.get(id)
}
