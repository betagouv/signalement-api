package orchestrators

import akka.actor.typed.Scheduler
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

  

  private def getFromGS1API(gtin: String): Future[Option[JsValue]] =
    for {
      firstTry <- gs1Service.getProductByGTIN(OAuthAccessToken("accesstokenbidon"), gtin)

      result <- firstTry match {
        case Right(value) => Future.successful(value)
        case Left(_) =>
          for {
            renewedAccessToken <- Future.successful(OAuthAccessToken("wallalacestbidon"))
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
        case None =>
          logger.debug(s"Product with gtin $gtin not in DB, fetching from GS1 API")
          for {
            maybeProductFromAPI             <- getFromGS1API(gtin)
            maybeProductFromOpenFoodFacts   <- openFoodFactsService.getProductByBarcode(gtin)
            maybeProductFromOpenBeautyFacts <- openBeautyFactsService.getProductByBarcode(gtin)
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
