package tasks.website

import cats.implicits.toTraverseOps
import config.TaskConfiguration
import org.apache.pekko.actor.ActorSystem
import repositories.siretextraction.SiretExtractionRepositoryInterface
import repositories.tasklock.TaskRepositoryInterface
import services.SiretExtractorService
import tasks.ScheduledTask
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
    siretExtractorService: SiretExtractorService
)(implicit executionContext: ExecutionContext)
    extends ScheduledTask(14, "siret_extraction_task", taskRepository, actorSystem, taskConfiguration) {

  override val taskSettings: TaskSettings = FrequentTaskSettings(interval = 1.hour)

  override def runTask(): Future[Unit] = for {
    websites <- siretExtractionRepository.listUnextractedWebsiteHosts(10)
    results <- websites.traverse(website =>
      siretExtractorService
        .extractSiret(website.host)
        .map { response =>
          response.body match {
            case Left(error) =>
              ExtractionResultApi(
                website.host,
                "error while calling Siret extractor service",
                Some(error.getMessage),
                None
              )
            case Right(body) => body
          }
        }
    )
    _ <- results.traverse(siretExtractionRepository.insertOrReplace)
  } yield ()
}
