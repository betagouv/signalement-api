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

    frLabels = toSet(minimizedAnomalies.fr, french = true)
    enLabels = toSet(minimizedAnomalies.en, french = false)

    _ <- frLabels.toList.traverse { label =>
      subcategoryLabelRepository.get(label.category, label.subcategories).flatMap {
        case Some(existing) =>
          val updatedLabel =
            existing.copy(categoryLabelFr = label.categoryLabelFr, subcategoryLabelsFr = label.subcategoryLabelsFr)
          subcategoryLabelRepository.createOrUpdate(updatedLabel)
        case None =>
          subcategoryLabelRepository.createOrUpdate(label)
      }
    }

    _ <- enLabels.toList.traverse { label =>
      subcategoryLabelRepository.get(label.category, label.subcategories).flatMap {
        case Some(existing) =>
          val updatedLabel =
            existing.copy(categoryLabelEn = label.categoryLabelEn, subcategoryLabelsEn = label.subcategoryLabelsEn)
          subcategoryLabelRepository.createOrUpdate(updatedLabel)
        case None =>
          subcategoryLabelRepository.createOrUpdate(label)
      }
    }
  } yield ()

  @tailrec
  private def toSet(
      nodes: List[ArborescenceNode],
      french: Boolean,
      res: Set[SubcategoryLabel] = Set.empty
  ): Set[SubcategoryLabel] =
    nodes match {
      case Nil => res
      case h :: t =>
        val cats = h.path.map(_._1)
        val labels = cats.indices.map { i =>
          val categoryInfos = cats.take(i + 1)

          if (french)
            SubcategoryLabel(
              category = categoryInfos.head.key,
              subcategories = categoryInfos.tail.map(_.key).toList,
              categoryLabelFr = Some(categoryInfos.head.label),
              subcategoryLabelsFr = Some(categoryInfos.tail.map(_.label).toList),
              categoryLabelEn = None,
              subcategoryLabelsEn = None
            )
          else
            SubcategoryLabel(
              category = categoryInfos.head.key,
              subcategories = categoryInfos.tail.map(_.key).toList,
              categoryLabelFr = None,
              subcategoryLabelsFr = None,
              categoryLabelEn = Some(categoryInfos.head.label),
              subcategoryLabelsEn = Some(categoryInfos.tail.map(_.label).toList)
            )
        }
        toSet(t, french, res ++ labels)
    }
}
