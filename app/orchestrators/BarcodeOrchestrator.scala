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

import java.time.OffsetDateTime
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

  private def outdated(product: BarcodeProduct): Boolean = {
    val oneMonthAgo = OffsetDateTime.now().minusMonths(1)
    product.gs1Product.isEmpty && product.updateDate.isBefore(oneMonthAgo)
  }

  def getByGTIN(gtin: String): Future[Option[BarcodeProduct]] =
    for {
      maybeExistingProductInDB <- barcodeRepository.getByGTIN(gtin)
      product <- maybeExistingProductInDB match {
        case Some(oldProduct) if outdated(oldProduct) =>
          for {
            newProduct     <- fetchProductFromAPIs(gtin)
            updatedProduct <- updateProduct(oldProduct = oldProduct, newProduct = newProduct)
          } yield updatedProduct
        case Some(_) =>
          logger.debug(s"Fetched product with gtin $gtin from DB")
          Future.successful(maybeExistingProductInDB)
        case None =>
          for {
            newProduct     <- fetchProductFromAPIs(gtin)
            createdProduct <- createProduct(newProduct)
          } yield createdProduct
      }
    } yield product

  private def fetchProductFromAPIs(gtin: String): Future[BarcodeProduct] =
    for {
      maybeGS1Product                 <- getFromGS1API(gtin)
      maybeProductFromOpenFoodFacts   <- openFoodFactsService.getProductByBarcode(gtin)
      maybeProductFromOpenBeautyFacts <- openBeautyFactsService.getProductByBarcode(gtin)
    } yield BarcodeProduct(
      gtin = gtin,
      gs1Product = maybeGS1Product,
      openFoodFactsProduct = maybeProductFromOpenFoodFacts,
      openBeautyFactsProduct = maybeProductFromOpenBeautyFacts
    )

  private def createProduct(newProduct: BarcodeProduct): Future[Option[BarcodeProduct]] =
    barcodeRepository
      .create(newProduct)
      .map(Some(_))
      .recover { case error =>
        logger.warnWithTitle(
          "GS1_barcode_product_already_exists",
          s"GTIN ${newProduct.gtin} already exist cannot create it again",
          error
        )
        None
      }

  private def updateProduct(oldProduct: BarcodeProduct, newProduct: BarcodeProduct): Future[Option[BarcodeProduct]] =
    barcodeRepository
      .update(
        oldProduct.id,
        oldProduct.copy(
          gs1Product = newProduct.gs1Product,
          openFoodFactsProduct = newProduct.openFoodFactsProduct,
          openBeautyFactsProduct = newProduct.openBeautyFactsProduct,
          updateDate = OffsetDateTime.now()
        )
      )
      .map(Some(_))

  def get(id: UUID): Future[Option[BarcodeProduct]] =
    barcodeRepository.get(id)
}
