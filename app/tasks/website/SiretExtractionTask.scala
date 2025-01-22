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

  def findMatchingAndValidExtraction(
      company: Company,
      extractions: List[SiretExtractionApi]
  ): Option[CompanySearchResult] = {
    val a = extractions
      .filter(extraction => extraction.siret.forall(_.valid) && extraction.siren.forall(_.valid))
      .find { extraction =>
        extraction.sirene match {
          case Some(sirene) => sirene.siret == company.siret && company.isOpen
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
    // Sauvegarde des résultats en DB
    _ <- results.traverse { case (_, extraction) => siretExtractionRepository.insertOrReplace(extraction) }

    // Association automatique si :
    // - Un siret trouvé est valide et présent dans SIRENE
    // - Le conso a fourni la même entreprise
    // - L'entreprise est ouverte
    companyIdsAndExtractions = results.collect {
      case (Website(id, _, _, _, _, Some(companyId), _, _, _), ExtractionResultApi(_, _, _, Some(extractions))) =>
        (id, companyId, extractions)
    }
    companyAndExtractions <- companyIdsAndExtractions.traverse { case (websiteId, companyId, extractions) =>
      companyRepository
        .get(companyId)
        .flatMap(_.liftTo[Future](CompanyNotFound(companyId)))
        .map(company => (websiteId, company, extractions))
    }
    websiteIdAndFoundCompany = companyAndExtractions.flatMap { case (websiteId, company, extractions) =>
      findMatchingAndValidExtraction(company, extractions).map(companySearchResult => websiteId -> companySearchResult)
    }
    _ <- websiteIdAndFoundCompany.traverse { case (websiteId, companySearchResult) =>
      val companyCreation = CompanyCreation(
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
      websitesOrchestrator.updateCompany(websiteId, companyCreation, None)
    }
  } yield ()
}
