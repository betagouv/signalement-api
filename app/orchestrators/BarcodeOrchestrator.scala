package orchestrators

import actors.GS1AuthTokenActor
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Scheduler
import org.apache.pekko.util.Timeout
import models.barcode.BarcodeProduct
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

  def getByGTIN(gtin: String): Future[Option[BarcodeProduct]] =
    Future.successful(None) // TEMPORARY TO STOP QUERYING OFF AND GS1
//    for {
//      maybeExistingProductInDB <- barcodeRepository.getByGTIN(gtin)
//      product <- maybeExistingProductInDB match {
//        case Some(_) =>
//          logger.debug(s"Fetched product with gtin $gtin from DB")
//          Future.successful(maybeExistingProductInDB)
//        case None =>
//          logger.debug(s"Product with gtin $gtin not in DB, fetching from GS1 API")
//          for {
//            maybeProductFromAPI             <- getFromGS1API(gtin)
//            maybeProductFromOpenFoodFacts   <- openFoodFactsService.getProductByBarcode(gtin)
//            maybeProductFromOpenBeautyFacts <- openBeautyFactsService.getProductByBarcode(gtin)
//            createdProduct <- maybeProductFromAPI match {
//              case Some(product) =>
//                barcodeRepository
//                  .create(
//                    BarcodeProduct(
//                      gtin = gtin,
//                      gs1Product = product,
//                      openFoodFactsProduct = maybeProductFromOpenFoodFacts,
//                      openBeautyFactsProduct = maybeProductFromOpenBeautyFacts
//                    )
//                  )
//                  .map(Some(_))
//              case None => Future.successful(None)
//            }
//          } yield createdProduct
//      }
//    } yield product

  def get(id: UUID): Future[Option[BarcodeProduct]] =
    barcodeRepository.get(id)
}
