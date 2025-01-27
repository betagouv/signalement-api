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

  override val taskSettings: TaskSettings = FrequentTaskSettings(interval = 15.minutes)

  private def findMatchingAndValidExtraction(
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
    _ = logger.debug(s"Found ${websites.length} websites to handle (siret extraction)")
    results <- websites.traverse(website =>
      siretExtractorService
        .extractSiret(website.host)
        .map { response =>
          response.body match {
            case Left(error) =>
              logger.debug(s"Siret extraction failed for ${website.id} : $error")
              website -> ExtractionResultApi(
                website.host,
                "error while calling Siret extractor service",
                Some(error.getMessage),
                None
              )
            case Right(body) =>
              logger.debug(s"Siret extraction succeeded for ${website.id}")
              website -> body
          }
        }
    )
    // Sauvegarde des résultats en DB
    _ = logger.debug(s"Saving ${results.length} extraction results un DB")
    _ <- results.traverse { case (_, extraction) => siretExtractionRepository.insertOrReplace(extraction) }

    // Association automatique si :
    // - Un siret trouvé est valide et présent dans SIRENE
    // - Le conso a fourni la même entreprise
    // - L'entreprise est ouverte
    companyIdsAndExtractions = results.collect {
      case (Website(id, _, _, _, _, Some(companyId), _, _, _), ExtractionResultApi(_, _, _, Some(extractions))) =>
        (id, companyId, extractions)
    }
    _ = logger.debug(
      s"${companyIdsAndExtractions.length} websites remaining after filtering succeeded extractions and companyId already provided by consumer"
    )

    companyAndExtractions <- companyIdsAndExtractions.traverse { case (websiteId, companyId, extractions) =>
      companyRepository
        .get(companyId)
        .flatMap(_.liftTo[Future](CompanyNotFound(companyId)))
        .map(company => (websiteId, company, extractions))
    }
    _ = logger.debug(s"${companyAndExtractions.length} websites remaining after fetching companies")
    websiteIdAndFoundCompany = companyAndExtractions.flatMap { case (websiteId, company, extractions) =>
      findMatchingAndValidExtraction(company, extractions).map(companySearchResult => websiteId -> companySearchResult)
    }
    _ = logger.debug(s"${websiteIdAndFoundCompany.length} website(s) will be associated to companies")
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
      logger.debug(s"Website $websiteId will be associated to company ${companyCreation.siret}")
      websitesOrchestrator.updateCompany(websiteId, companyCreation, None)
    }
  } yield ()
}
