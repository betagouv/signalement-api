package repositories

import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable
import utils.Fixtures
import utils.TestApp

class BarcodeProductRepositorySpec(implicit ee: ExecutionEnv) extends mutable.Specification with FutureMatchers {

  val (app, components) = TestApp.buildApp()

  "BarcodeProductRepository" should {
    "Fetch product by gtin" in {
      val repository = components.barcodeProductRepository
      val product    = Fixtures.genBarcodeProduct.sample.get

      for {
        _   <- repository.create(product)
        res <- repository.getByGTIN(product.gtin)
      } yield res.isDefined shouldEqual true
    }
  }

}
