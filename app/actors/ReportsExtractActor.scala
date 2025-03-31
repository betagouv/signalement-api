package actors

import config.SignalConsoConfiguration
import io.scalaland.chimney.dsl.TransformationOps
import models._
import models.company.AccessLevel
import models.event.Event
import models.event.EventUser
import models.event.EventWithUser
import models.report._
import models.report.review.EngagementReview
import models.report.review.ResponseConsumerReview
import orchestrators.ReportOrchestrator
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.FileIO
import play.api.Logger
import repositories.asyncfiles.AsyncFileRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface
import repositories.event.EventFilter
import repositories.event.EventRepositoryInterface
import services.ExcelColumnsService
import services.S3ServiceInterface
import spoiwo.model._
import spoiwo.model.enums.CellStyleInheritance
import spoiwo.natures.xlsx.Model2XlsxConversions._
import utils.DateUtils.frenchFormatDate
import utils.DateUtils.frenchFormatDateAndTime
import utils.ExcelUtils._

import java.nio.file.Path
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Random
import scala.util.Success

object ReportsExtractActor {
  sealed trait ReportsExtractCommand
  case class ExtractRequest(fileId: UUID, requestedBy: User, filters: ReportFilter, zone: ZoneId)
      extends ReportsExtractCommand
  case class ExtractRequestSuccess(fileId: UUID, requestedBy: User) extends ReportsExtractCommand
  case class ExtractRequestFailure(error: Throwable)                extends ReportsExtractCommand

  val logger: Logger = Logger(this.getClass)

  def create(
      companyAccessRepository: CompanyAccessRepositoryInterface,
      reportOrchestrator: ReportOrchestrator,
      eventRepository: EventRepositoryInterface,
      asyncFileRepository: AsyncFileRepositoryInterface,
      s3Service: S3ServiceInterface,
      signalConsoConfiguration: SignalConsoConfiguration
  )(implicit mat: Materializer): Behavior[ReportsExtractCommand] =
    Behaviors.setup { context =>
      import context.executionContext

      Behaviors.receiveMessage[ReportsExtractCommand] {
        case ExtractRequest(fileId: UUID, requestedBy: User, filters: ReportFilter, zone: ZoneId) =>
          val result = for {
            // FIXME: We might want to move the random name generation
            // in a common place if we want to reuse it for other async files
            tmpPath <- genTmpFile(
              reportOrchestrator,
              signalConsoConfiguration,
              eventRepository,
              companyAccessRepository,
              requestedBy,
              filters,
              zone
            )
            remotePath <- saveRemotely(s3Service, tmpPath, tmpPath.getFileName.toString)
            _          <- asyncFileRepository.update(fileId, tmpPath.getFileName.toString, remotePath)
          } yield ExtractRequestSuccess(fileId, requestedBy)

          context.pipeToSelf(result) {
            case Success(success) => success
            case Failure(error)   => ExtractRequestFailure(error)
          }
          Behaviors.same

        case ExtractRequestSuccess(fileId: UUID, requestedBy: User) =>
          logger.debug(s"Built report for User ${requestedBy.id} — async file ${fileId}")
          Behaviors.same

        case ExtractRequestFailure(error) =>
          logger.info(s"Extract failed", error)
          Behaviors.same
      }
    }

  case class ReportColumn(
      name: String,
      extract: (
          Report,
          List[ReportFile],
          List[EventWithUser],
          Option[ResponseConsumerReview],
          Option[EngagementReview],
          List[User]
      ) => String,
      available: Boolean = true,
      column: Column = leftAlignmentColumn
  ) {
    def extractStringValue(
        report: Report,
        reportFiles: List[ReportFile],
        events: List[EventWithUser],
        consumerReview: Option[ResponseConsumerReview],
        engagementReview: Option[EngagementReview],
        users: List[User]
    ): String = extract(report, reportFiles, events, consumerReview, engagementReview, users).take(MaxCharInSingleCell)

  }

  private def genTmpFile(
      reportOrchestrator: ReportOrchestrator,
      signalConsoConfiguration: SignalConsoConfiguration,
      eventRepository: EventRepositoryInterface,
      companyAccessRepository: CompanyAccessRepositoryInterface,
      requestedBy: User,
      filters: ReportFilter,
      zone: ZoneId
  )(implicit ec: ExecutionContext): Future[Path] = {
    val reportColumns = ExcelColumnsService.buildColumns(signalConsoConfiguration, requestedBy, zone)
    for {
      paginatedReports <- reportOrchestrator
        .getReportsForUser(
          requestedBy,
          filter = filters,
          offset = Some(0),
          limit = Some(signalConsoConfiguration.reportsExportLimitMax),
          sortBy = None,
          orderBy = None,
          signalConsoConfiguration.reportsExportLimitMax
        )
      reportIds = paginatedReports.entities.map(_.report.id)
      reportEventsMap <- eventRepository
        .getEventsWithUsers(reportIds, EventFilter.Empty)
        .map {
          _.collect { case (event @ Event(_, Some(reportId), _, _, _, _, _, _), user) =>
            (reportId, EventWithUser(event, user.map(_.into[EventUser].withFieldRenamed(_.userRole, _.role).transform)))
          }.groupMap(_._1)(_._2)
        }
      companyAdminsMap <- companyAccessRepository.fetchUsersByCompanyIds(
        paginatedReports.entities.flatMap(_.report.companyId),
        Seq(AccessLevel.ADMIN)
      )
    } yield {
      val targetFilename = s"signalements-${Random.alphanumeric.take(12).mkString}.xlsx"
      val reportsSheet = Sheet(name = "Signalements")
        .withRows(
          Row(style = headerStyle).withCellValues(reportColumns.map(_.name)) ::
            paginatedReports.entities.map {
              case ReportFromSearchWithFiles(report, _, _, consumerReview, engagementReview, files) =>
                Row().withCells(
                  reportColumns
                    .map(
                      _.extractStringValue(
                        report,
                        files,
                        reportEventsMap.getOrElse(report.id, Nil),
                        consumerReview,
                        engagementReview,
                        report.companyId.flatMap(companyAdminsMap.get).getOrElse(Nil)
                      )
                    )
                    .map(StringCell(_, None, None, CellStyleInheritance.CellThenRowThenColumnThenSheet))
                )
            }
        )
        .withColumns(reportColumns.map(_.column))

      val filtersSheet = Sheet(name = "Filtres")
        .withRows(
          List(
            Some(
              Row().withCellValues(
                "Date de l'export",
                frenchFormatDateAndTime(OffsetDateTime.now(), zone)
              )
            ),
            Some(filters.departments)
              .filter(_.nonEmpty)
              .map(departments => Row().withCellValues("Départment(s)", departments.mkString(","))),
            (filters.start, filters.end) match {
              case (Some(startDate), Some(endDate)) =>
                Some(
                  Row().withCellValues(
                    "Période",
                    s"Du ${frenchFormatDate(startDate, zone)} au ${frenchFormatDate(endDate, zone)}"
                  )
                )
              case (Some(startDate), _) =>
                Some(Row().withCellValues("Période", s"Depuis le ${frenchFormatDate(startDate, zone)}"))
              case (_, Some(endDate)) =>
                Some(Row().withCellValues("Période", s"Jusqu'au ${frenchFormatDate(endDate, zone)}"))
              case _ => None
            },
            Some(Row().withCellValues("Siret", filters.siretSirenList.mkString(","))),
            filters.websiteURL.map(websiteURL => Row().withCellValues("Site internet", websiteURL)),
            filters.phone.map(phone => Row().withCellValues("Numéro de téléphone", phone)),
            Some(filters.status)
              .filter(_.nonEmpty)
              .map(status =>
                Row()
                  .withCellValues("Statut", status.map(ReportStatus.translate(_, requestedBy.userRole)).mkString(","))
              ),
            filters.category.map(category => Row().withCellValues("Catégorie", category)),
            filters.details.map(details => Row().withCellValues("Mots clés", details))
          ).flatten
        )
        .withColumns(
          Column(autoSized = true, style = headerStyle),
          leftAlignmentColumn
        )

      val localPath = Paths.get(signalConsoConfiguration.tmpDirectory, targetFilename)
      Workbook(reportsSheet, filtersSheet).saveAsXlsx(localPath.toString)
      logger.debug(s"Generated extract locally: ${localPath}")
      localPath
    }
  }

  private def saveRemotely(s3Service: S3ServiceInterface, localPath: Path, remoteName: String)(implicit
      ec: ExecutionContext,
      mat: Materializer
  ): Future[String] = {
    val remotePath = s"extracts/$remoteName"
    s3Service.upload(remotePath).runWith(FileIO.fromPath(localPath)).map(_ => remotePath)
  }
}
