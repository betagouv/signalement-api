package repositories

import org.scalacheck.Gen
import org.specs2._
import org.specs2.matcher.FutureMatchers
import org.specs2.concurrent.ExecutionEnv
import utils.Fixtures
import utils.TestApp

class CompanyRepositorySpec(implicit ee: ExecutionEnv) extends mutable.Specification with FutureMatchers {

  val (app, components) = TestApp.buildApp()

  "CompanyRepository" should {
    "update by SIRET correctly" in {
      val repository = components.companyRepository
      val company = Fixtures.genCompany.sample.get
      val expectedAddress = company.address.copy(
        number = Gen.option(Gen.alphaNumStr).sample.get,
        street = Gen.option(Gen.alphaNumStr).sample.get,
        addressSupplement = Gen.option(Gen.alphaNumStr).sample.get
      )
      val expectedCompany = company.copy(
        isOpen = Gen.oneOf(true, false).sample.get,
        isHeadOffice = Gen.oneOf(true, false).sample.get,
        isPublic = Gen.oneOf(true, false).sample.get,
        address = expectedAddress,
        name = Gen.alphaNumStr.sample.get
      )
      for {
        _ <- repository.create(company)
        before <- repository.get(company.id)
        _ <- repository.updateBySiret(
          expectedCompany.siret,
          expectedCompany.isOpen,
          expectedCompany.isHeadOffice,
          expectedCompany.isPublic,
          expectedCompany.address.number,
          expectedCompany.address.street,
          expectedCompany.address.addressSupplement,
          expectedCompany.name
        )
        after <- repository.get(company.id)
      } yield (before shouldEqual Some(company)) and (after shouldEqual Some(expectedCompany))
    }
  }
}
