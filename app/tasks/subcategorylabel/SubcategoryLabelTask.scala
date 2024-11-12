package tasks.subcategorylabel

import cats.implicits.catsSyntaxOption
import cats.implicits.toTraverseOps
import config.TaskConfiguration
import controllers.error.AppError.WebsiteApiError
import models.report.ArborescenceNode
import org.apache.pekko.actor.ActorSystem
import repositories.subcategorylabel.SubcategoryLabel
import repositories.subcategorylabel.SubcategoryLabelRepositoryInterface
import repositories.tasklock.TaskRepositoryInterface
import services.WebsiteApiServiceInterface
import tasks.ScheduledTask
import tasks.model.TaskSettings.DailyTaskSettings

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class SubcategoryLabelTask(
    actorSystem: ActorSystem,
    taskConfiguration: TaskConfiguration,
    taskRepository: TaskRepositoryInterface,
    subcategoryLabelRepository: SubcategoryLabelRepositoryInterface,
    websiteApiService: WebsiteApiServiceInterface
)(implicit executionContext: ExecutionContext)
    extends ScheduledTask(12, "subcategory_label_task", taskRepository, actorSystem, taskConfiguration) {

  override val taskSettings = DailyTaskSettings(taskConfiguration.subcategoryLabels.startTime)

  override def runTask(): Future[Unit] = for {
    maybeMinimizedAnomalies <- websiteApiService.fetchMinimizedAnomalies()
    minimizedAnomalies      <- maybeMinimizedAnomalies.liftTo[Future](WebsiteApiError)
    labels = toSet(minimizedAnomalies.fr)
    _ <- labels.toList.traverse { label =>
      subcategoryLabelRepository.createOrUpdate(label)
    }
  } yield ()

  @tailrec
  private def toSet(nodes: List[ArborescenceNode], res: Set[SubcategoryLabel] = Set.empty): Set[SubcategoryLabel] =
    nodes match {
      case Nil => res
      case h :: t =>
        val cats = h.path.map(_._1)
        val a = cats.indices.map { i =>
          val tmp = cats.take(i + 1)

          SubcategoryLabel(
            category = tmp.head.key,
            subcategories = tmp.tail.map(_.key).toList,
            categoryLabel = tmp.head.label,
            subcategoryLabels = tmp.tail.map(_.label).toList
          )
        }
        toSet(t, res ++ a)
    }
}
