package tasks.website

import cats.implicits.catsSyntaxOption
import cats.implicits.toTraverseOps
import config.TaskConfiguration
import controllers.error.AppError.CompanyNotFound
import models.company.Company
import models.company.CompanyCreation
import models.website.Website
import orchestrators.WebsitesOrchestrator
import org.apache.pekko.actor.ActorSystem
import repositories.company.CompanyRepositoryInterface
import repositories.siretextraction.SiretExtractionRepositoryInterface
import repositories.tasklock.TaskRepositoryInterface
import services.SiretExtractorService
import tasks.ScheduledTask
import tasks.company.CompanySearchResult
import tasks.model.TaskSettings
import tasks.model.TaskSettings.FrequentTaskSettings

import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class SiretExtractionTask(
    actorSystem: ActorSystem,
    taskConfiguration: TaskConfiguration,
    taskRepository: TaskRepositoryInterface,
    siretExtractionRepository: SiretExtractionRepositoryInterface,
    siretExtractorService: SiretExtractorService,
    websitesOrchestrator: WebsitesOrchestrator,
    companyRepository: CompanyRepositoryInterface
)(implicit executionContext: ExecutionContext)
    extends ScheduledTask(14, "siret_extraction_task", taskRepository, actorSystem, taskConfiguration) {

  override val taskSettings: TaskSettings = FrequentTaskSettings(interval = 1.hour)

  def matchingExtraction(company: Company, extractions: List[SiretExtractionApi]): Option[CompanySearchResult] = {
    val a = extractions.find { extraction =>
      extraction.sirene match {
        case Some(sirene) => sirene.siret == company.siret
        case None         => false
      }
    }
    a.flatMap(_.sirene)
  }

  override def runTask(): Future[Unit] = for {
    websites <- siretExtractionRepository.listUnextractedWebsiteHosts(10)
    results <- websites.traverse(website =>
      siretExtractorService
        .extractSiret(website.host)
        .map { response =>
          response.body match {
            case Left(error) =>
              website -> ExtractionResultApi(
                website.host,
                "error while calling Siret extractor service",
                Some(error.getMessage),
                None
              )
            case Right(body) => website -> body
          }
        }
    )
    _ <- results.traverse { case (_, extraction) => siretExtractionRepository.insertOrReplace(extraction) }

    test = results.collect {
      case (Website(id, _, _, _, _, Some(companyId), _, _, _), ExtractionResultApi(_, _, _, Some(l))) =>
        (id, companyId, l)
    }
    test2 <- test.traverse { case (websiteId, companyId, l) =>
      companyRepository
        .get(companyId)
        .flatMap(_.liftTo[Future](CompanyNotFound(companyId)))
        .map(company => (websiteId, company, l))
    }
    test3 = test2.flatMap { case (websiteId, company, extractions) =>
      matchingExtraction(company, extractions).map(a => websiteId -> a)
    }
    _ <- test3.traverse { case (websiteId, companySearchResult) =>
      val a = CompanyCreation(
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
      )
      websitesOrchestrator.updateCompany(websiteId, a, null) // TODO
    }
  } yield ()
}
