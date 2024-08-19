package orchestrators

import actors.GS1AuthTokenActor
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import models.barcode.gs1.OAuthAccessToken
import org.mockito.Mockito.when
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito.mock
import org.specs2.mutable.Specification
import repositories.barcode.BarcodeProductRepositoryInterface
import services.GS1Service
import services.GS1ServiceInterface
import services.OpenBeautyFactsServiceInterface
import services.OpenFoodFactsServiceInterface
import org.apache.pekko.actor.typed.Scheduler
import org.apache.pekko.util.Timeout
import models.barcode.BarcodeProduct
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.mockito.ArgumentMatchers.argThat
import org.specs2.specification.BeforeAfterAll
import play.api.libs.json.Json
import utils.Fixtures

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future

class BarcodeOrchestratorSpec extends Specification with FutureMatchers with BeforeAfterAll {

  def argMatching[T](pf: PartialFunction[Any, Unit]) = argThat[T](pf.isDefinedAt(_))

  val testKit = ActorTestKit()

  implicit val ec: ExecutionContextExecutor = testKit.internalSystem.executionContext
  implicit val timeout: Timeout             = testKit.timeout
  implicit val scheduler: Scheduler         = testKit.scheduler

  override def beforeAll(): Unit = ()
  override def afterAll(): Unit  = testKit.shutdownTestKit()

  "BarcodeOrchestrator" should {
    "getByGTIN" should {
      "return the product when in DB" in {
        val accessToken = OAuthAccessToken(Fixtures.arbString.arbitrary.sample.get)
        val gtin        = Fixtures.arbString.arbitrary.sample.get
        val data        = Fixtures.arbString.arbitrary.sample.get

        val mockedBehavior = Behaviors.receiveMessagePartial[GS1AuthTokenActor.Command] {
          case GS1AuthTokenActor.GetToken(replyTo) =>
            replyTo ! GS1AuthTokenActor.GotToken(accessToken)
            Behaviors.same
        }
        val probe           = testKit.createTestProbe[GS1AuthTokenActor.Command]()
        val mockedPublisher = testKit.spawn(Behaviors.monitor(probe.ref, mockedBehavior))

        val gs1Service               = mock[GS1ServiceInterface]
        val openFoodFactsService     = mock[OpenFoodFactsServiceInterface]
        val openBeautyFactsService   = mock[OpenBeautyFactsServiceInterface]
        val barcodeProductRepository = mock[BarcodeProductRepositoryInterface]

        val expectedBarcodeProduct = BarcodeProduct(
          gtin = gtin,
          gs1Product = Some(Json.obj("field" -> data)),
          openFoodFactsProduct = None,
          openBeautyFactsProduct = None
        )

        when(barcodeProductRepository.getByGTIN(gtin)).thenReturn(Future.successful(Some(expectedBarcodeProduct)))

        val barcodeOrchestrator = new BarcodeOrchestrator(
          mockedPublisher,
          gs1Service,
          openFoodFactsService,
          openBeautyFactsService,
          barcodeProductRepository
        )

        barcodeOrchestrator.getByGTIN(gtin).map { res =>
          (res.map(_.gtin) shouldEqual Some(gtin)) &&
          (res.flatMap(_.gs1Product) shouldEqual Some(Json.obj("field" -> data)))
        }
      }

      "return the product when not in DB" in {
        val accessToken = OAuthAccessToken(Fixtures.arbString.arbitrary.sample.get)
        val gtin        = Fixtures.arbString.arbitrary.sample.get
        val data        = Fixtures.arbString.arbitrary.sample.get

        val mockedBehavior = Behaviors.receiveMessagePartial[GS1AuthTokenActor.Command] {
          case GS1AuthTokenActor.GetToken(replyTo) =>
            replyTo ! GS1AuthTokenActor.GotToken(accessToken)
            Behaviors.same
        }
        val probe           = testKit.createTestProbe[GS1AuthTokenActor.Command]()
        val mockedPublisher = testKit.spawn(Behaviors.monitor(probe.ref, mockedBehavior))

        val gs1Service               = mock[GS1ServiceInterface]
        val openFoodFactsService     = mock[OpenFoodFactsServiceInterface]
        val openBeautyFactsService   = mock[OpenBeautyFactsServiceInterface]
        val barcodeProductRepository = mock[BarcodeProductRepositoryInterface]

        val expectedBarcodeProduct = BarcodeProduct(
          gtin = gtin,
          gs1Product = Some(Json.obj("field" -> data)),
          openFoodFactsProduct = None,
          openBeautyFactsProduct = None
        )

        when(barcodeProductRepository.getByGTIN(gtin)).thenReturn(Future.successful(None))
        when(gs1Service.getProductByGTIN(accessToken, gtin)).thenReturn(Future.successful(Right(Some(Json.obj()))))
        when(openFoodFactsService.getProductByBarcode(gtin)).thenReturn(Future.successful(None))
        when(openBeautyFactsService.getProductByBarcode(gtin)).thenReturn(Future.successful(None))
        when(barcodeProductRepository.create(argMatching[BarcodeProduct] {
          case BarcodeProduct(_, `gtin`, _, _, _, _, _) =>
        }))
          .thenReturn(Future.successful(expectedBarcodeProduct))

        val barcodeOrchestrator = new BarcodeOrchestrator(
          mockedPublisher,
          gs1Service,
          openFoodFactsService,
          openBeautyFactsService,
          barcodeProductRepository
        )

        barcodeOrchestrator.getByGTIN(gtin).map { res =>
          (res.map(_.gtin) shouldEqual Some(gtin)) &&
          (res.flatMap(_.gs1Product) shouldEqual Some(Json.obj("field" -> data)))
        }
      }

      "return the product when not in DB and when the token is expired" in {
        val accessToken = OAuthAccessToken(Fixtures.arbString.arbitrary.sample.get)
        val gtin        = Fixtures.arbString.arbitrary.sample.get
        val data        = Fixtures.arbString.arbitrary.sample.get

        val mockedBehavior = Behaviors.receiveMessagePartial[GS1AuthTokenActor.Command] {
          case GS1AuthTokenActor.GetToken(replyTo) =>
            replyTo ! GS1AuthTokenActor.GotToken(accessToken)
            Behaviors.same
          case GS1AuthTokenActor.RenewToken(replyTo) =>
            replyTo ! GS1AuthTokenActor.GotToken(accessToken)
            Behaviors.same
        }
        val probe           = testKit.createTestProbe[GS1AuthTokenActor.Command]()
        val mockedPublisher = testKit.spawn(Behaviors.monitor(probe.ref, mockedBehavior))

        val gs1Service               = mock[GS1ServiceInterface]
        val openFoodFactsService     = mock[OpenFoodFactsServiceInterface]
        val openBeautyFactsService   = mock[OpenBeautyFactsServiceInterface]
        val barcodeProductRepository = mock[BarcodeProductRepositoryInterface]

        val expectedBarcodeProduct = BarcodeProduct(
          gtin = gtin,
          gs1Product = Some(Json.obj("field" -> data)),
          openFoodFactsProduct = None,
          openBeautyFactsProduct = None
        )

        when(barcodeProductRepository.getByGTIN(gtin)).thenReturn(Future.successful(None))
        when(gs1Service.getProductByGTIN(accessToken, gtin))
          .thenReturn(Future.successful(Left(GS1Service.TokenExpired)))
        when(gs1Service.getProductByGTIN(accessToken, gtin)).thenReturn(Future.successful(Right(Some(Json.obj()))))
        when(openFoodFactsService.getProductByBarcode(gtin)).thenReturn(Future.successful(None))
        when(openBeautyFactsService.getProductByBarcode(gtin)).thenReturn(Future.successful(None))
        when(barcodeProductRepository.create(argMatching[BarcodeProduct] {
          case BarcodeProduct(_, `gtin`, _, _, _, _, _) =>
        }))
          .thenReturn(Future.successful(expectedBarcodeProduct))

        val barcodeOrchestrator = new BarcodeOrchestrator(
          mockedPublisher,
          gs1Service,
          openFoodFactsService,
          openBeautyFactsService,
          barcodeProductRepository
        )

        barcodeOrchestrator.getByGTIN(gtin).map { res =>
          (res.map(_.gtin) shouldEqual Some(gtin)) &&
          (res.flatMap(_.gs1Product) shouldEqual Some(Json.obj("field" -> data)))
        }
      }
    }
  }
}
