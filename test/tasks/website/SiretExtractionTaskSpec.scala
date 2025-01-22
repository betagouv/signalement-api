package tasks.website

import models.company.CompanyCreation
import models.website.IdentificationStatus.Identified
import models.website.IdentificationStatus.NotIdentified
import models.website.Website
import models.website.WebsiteAndCompany
import orchestrators.WebsitesOrchestrator
import org.apache.pekko.stream.Materializer
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable
import services.SiretExtractorService
import sttp.client3.Response
import sttp.model.StatusCode
import tasks.company.CompanySearchResult
import utils.AppSpec
import utils.Fixtures
import utils.TaskRepositoryMock
import utils.TestApp

import scala.concurrent.Future

class SiretExtractionTaskSpec(implicit ee: ExecutionEnv)
    extends mutable.Specification
    with AppSpec
    with Mockito
    with FutureMatchers {

  val (app, components)          = TestApp.buildApp()
  implicit val mat: Materializer = app.materializer

  val taskRepositoryMock = new TaskRepositoryMock()

  "SiretExtractionTask" should {
    "correctly save and associate websites" in {
      val siretExtractorServiceMock = mock[SiretExtractorService]
      val websitesOrchestratorMock  = mock[WebsitesOrchestrator]

      val service = new SiretExtractionTask(
        app.actorSystem,
        components.applicationConfiguration.task,
        taskRepositoryMock,
        components.siretExtractionRepository,
        siretExtractorServiceMock,
        websitesOrchestratorMock,
        components.companyRepository
      )

      val company       = Fixtures.genCompany.sample.get
      val unusedCompany = Fixtures.genCompany.sample.get
      val companySearchResult =
        CompanySearchResult.fromCompany(company, Website(host = "", companyCountry = None, companyId = None))
      val website = Fixtures
        .genWebsite()
        .sample
        .get
        .copy(
          host = "test2.com",
          companyCountry = None,
          companyId = Some(company.id),
          identificationStatus = NotIdentified
        )

      siretExtractorServiceMock.extractSiret("test.com") returns Future.successful(
        Response(
          Right(
            ExtractionResultApi(
              website = "test.com",
              status = "success",
              error = None,
              extractions = Some(
                List(
                  SiretExtractionApi(
                    Some(SiretApi(company.siret.value, true)),
                    None,
                    List.empty,
                    Some(companySearchResult)
                  )
                )
              )
            )
          ),
          StatusCode.Ok
        )
      )

      siretExtractorServiceMock.extractSiret("test2.com") returns Future.successful(
        Response(
          Right(
            ExtractionResultApi(
              website = "test2.com",
              status = "success",
              error = None,
              extractions = Some(
                List(
                  SiretExtractionApi(
                    Some(SiretApi(company.siret.value, true)),
                    None,
                    List.empty,
                    Some(companySearchResult)
                  )
                )
              )
            )
          ),
          StatusCode.Ok
        )
      )

      websitesOrchestratorMock.updateCompany(
        website.id,
        CompanyCreation(
          siret = companySearchResult.siret,
          name = companySearchResult.name.getOrElse(""),
          address = companySearchResult.address,
          activityCode = companySearchResult.activityCode,
          isHeadOffice = Some(companySearchResult.isHeadOffice),
          isOpen = Some(companySearchResult.isOpen),
          isPublic = Some(companySearchResult.isPublic),
          brand = companySearchResult.brand,
          commercialName = companySearchResult.commercialName,
          establishmentCommercialName = companySearchResult.establishmentCommercialName
        ),
        None
      ) returns Future.successful(WebsiteAndCompany.toApi(website, Some(company)))

      for {
        _ <- components.companyRepository.create(company)
        _ <- components.companyRepository.create(unusedCompany)
        _ <- components.websiteRepository.create(Website(host = "test.com", companyCountry = None, companyId = None))
        _ <- components.websiteRepository.create(website)
        _ <- components.websiteRepository.create(
          Website(
            host = "test3.com",
            companyCountry = None,
            companyId = Some(unusedCompany.id),
            identificationStatus = Identified
          )
        )
        b1 <- components.siretExtractionRepository.getByHost("test.com")
        b2 <- components.siretExtractionRepository.getByHost("test2.com")
        b3 <- components.siretExtractionRepository.getByHost("test3.com")
        _  <- service.runTask()
        a1 <- components.siretExtractionRepository.getByHost("test.com")
        a2 <- components.siretExtractionRepository.getByHost("test2.com")
        a3 <- components.siretExtractionRepository.getByHost("test3.com")
      } yield (b1 should beNone).and(a1.isDefined shouldEqual true) and
        (b2 should beNone).and(a2.isDefined shouldEqual true) and
        (b3 should beNone).and(a3 should beNone)
    }
  }

}
