package tasks.website

import cats.implicits.catsSyntaxOption
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

  override val taskSettings: TaskSettings = FrequentTaskSettings(interval = taskConfiguration.siretExtraction.interval)

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

  // Fonction de traitement unitaire d'un site web.
  // Cela permet un traitement séquentiel des sites web
  // (pour mieux répartir la charge et ne pas surcharger le siret extractor)
  private def handleWebsite(website: Website): Future[Unit] = for {
    extraction <- siretExtractorService
      .extractSiret(website.host)
      .map { response =>
        response.body match {
          case Left(error) =>
            logger.debug(s"Siret extraction failed for ${website.id} : $error")
            ExtractionResultApi(
              website.host,
              "error while calling Siret extractor service",
              Some(error.getMessage),
              None
            )
          case Right(body) =>
            logger.debug(s"Siret extraction succeeded for ${website.id}")
            body
        }
      }
    // Sauvegarde des résultats en DB
    _ = logger.debug(s"Saving extraction for ${website.host} results un DB")
    _ <- siretExtractionRepository.insertOrReplace(extraction)
    // Association automatique si :
    // - Un siret trouvé est valide et présent dans SIRENE
    // - Le conso a fourni la même entreprise
    // - L'entreprise est ouverte
    _ = (website, extraction) match {
      case (
            Website(websiteId, _, _, _, _, Some(companyId), _, _, _),
            ExtractionResultApi(_, _, _, Some(extractions))
          ) =>
        logger.debug(
          s"Website ${website.host} can be associated: succeeded extractions found and company already provided by consumer"
        )
        for {
          company <- companyRepository
            .get(companyId)
            .flatMap(_.liftTo[Future](CompanyNotFound(companyId)))
          _ = logger.debug("Fetched company")
          websiteIdAndFoundCompany = findMatchingAndValidExtraction(company, extractions).map(companySearchResult =>
            websiteId -> companySearchResult
          )
          _ <- websiteIdAndFoundCompany match {
            case Some((websiteId, companySearchResult)) =>
              logger.debug(s"Website ${website.host} will be associated to company ${companySearchResult.siret}")
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
            case None =>
              logger.debug(s"No matching extraction found for ${website.host}")
              Future.unit
          }
        } yield ()
      case _ => Future.unit
    }
  } yield ()

  override def runTask(): Future[Unit] = for {
    websites <- siretExtractionRepository.listUnextractedWebsiteHosts(taskConfiguration.siretExtraction.websiteCount)
    _ = logger.debug(s"Found ${websites.length} websites to handle (siret extraction)")
    // Using flatMap to ensure sequential handling (traverse or map / sequence will be parallel due to Future nature)
    _ <- websites.foldLeft(Future.unit)((acc, website) => acc.flatMap(_ => handleWebsite(website)))
  } yield ()
}
