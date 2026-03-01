package actors

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import spoiwo.model._
import spoiwo.model.enums.CellFill
import spoiwo.model.enums.CellHorizontalAlignment
import spoiwo.model.enums.CellStyleInheritance
import spoiwo.model.enums.CellVerticalAlignment
import spoiwo.natures.xlsx.Model2XlsxConversions._
import config.SignalConsoConfiguration
import models._
import play.api.Logger
import repositories.asyncfiles.AsyncFileRepositoryInterface
import repositories.website.WebsiteRepositoryInterface
import services.ExtractService
import utils.DateUtils

import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Random
import scala.util.Success

object WebsiteExtractActor {
  sealed trait WebsiteExtractCommand

  case class RawFilters(query: Option[String], start: Option[String], end: Option[String])
  case class ExtractRequest(requestedBy: User, rawFilters: RawFilters) extends WebsiteExtractCommand
  case class ExtractRequestSuccess(fileId: UUID, requestedBy: User)    extends WebsiteExtractCommand
  case object ExtractRequestFailure                                    extends WebsiteExtractCommand

  val logger: Logger = Logger(this.getClass)

  def create(
      websiteRepository: WebsiteRepositoryInterface,
      asyncFileRepository: AsyncFileRepositoryInterface,
      signalConsoConfiguration: SignalConsoConfiguration,
      extractService: ExtractService
  ): Behavior[WebsiteExtractCommand] =
    Behaviors.setup { context =>
      import context.executionContext

      Behaviors.receiveMessage[WebsiteExtractCommand] {
        case ExtractRequest(requestedBy, rawFilters) =>
          val result = for {
            asyncFile <- asyncFileRepository.create(AsyncFile.build(requestedBy, kind = AsyncFileKind.ReportedWebsites))
            tmpPath   <- genTmpFile(websiteRepository, signalConsoConfiguration, rawFilters)
            _         <- extractService.buildAndUploadFile(tmpPath, asyncFile.id, requestedBy, "website-extracts")
          } yield ExtractRequestSuccess(asyncFile.id, requestedBy)

          context.pipeToSelf(result) {
            case Success(success) => success
            case Failure(_)       => ExtractRequestFailure
          }
          Behaviors.same

        case ExtractRequestSuccess(fileId: UUID, requestedBy: User) =>
          logger.debug(s"Built websites for User ${requestedBy.id} — async file $fileId")
          Behaviors.same

        case ExtractRequestFailure =>
          logger.info(s"Extract failed")
          Behaviors.same
      }
    }

  // Common layout variables
  private val headerStyle = CellStyle(
    fillPattern = CellFill.Solid,
    fillForegroundColor = Color.Gainsborough,
    font = Font(bold = true),
    horizontalAlignment = CellHorizontalAlignment.Center
  )
  private val centerAlignmentStyle = CellStyle(
    horizontalAlignment = CellHorizontalAlignment.Center,
    verticalAlignment = CellVerticalAlignment.Center,
    wrapText = true
  )
  private val centerAlignmentColumn = Column(autoSized = true, style = centerAlignmentStyle)
  private val leftAlignmentStyle = CellStyle(
    horizontalAlignment = CellHorizontalAlignment.Left,
    verticalAlignment = CellVerticalAlignment.Center,
    wrapText = true
  )
  private val leftAlignmentColumn = Column(autoSized = true, style = leftAlignmentStyle)

  private def genTmpFile(
      websiteRepository: WebsiteRepositoryInterface,
      signalConsoConfiguration: SignalConsoConfiguration,
      filters: RawFilters
  )(implicit ec: ExecutionContext): Future[Path] = {

    val startDate = DateUtils.parseDate(filters.start)
    val endDate   = DateUtils.parseDate(filters.end)
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    websiteRepository.getUnkonwnReportCountByHost(filters.query, startDate, endDate).map { reports =>
      val hostsWithCount: Map[String, Int] = Map.from(reports)

      val targetFilename = s"sites-non-identifies-${Random.alphanumeric.take(12).mkString}.xlsx"
      val extractSheet = Sheet(name = "Sites non identifiés")
        .withRows(
          Row(style = headerStyle).withCellValues("Nombre de signalement", "Nom du site") ::
            hostsWithCount.toList.sortBy(_._2)(Ordering.Int.reverse).map { case (host, count) =>
              Row().withCells(
                StringCell(s"$count", None, None, CellStyleInheritance.CellThenRowThenColumnThenSheet),
                StringCell(host, None, None, CellStyleInheritance.CellThenRowThenColumnThenSheet)
              )
            }
        )
        .withColumns(centerAlignmentColumn, centerAlignmentColumn)

      val filtersSheet = Sheet(name = "Filtres")
        .withRows(
          List(
            Some(
              Row().withCellValues(
                "Date de l'export",
                LocalDateTime
                  .now()
                  .format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm:ss"))
              )
            ),
            filters.query.map(q => Row().withCellValues("Nom du site", q)),
            (startDate, DateUtils.parseDate(filters.end)) match {
              case (Some(startDate), Some(endDate)) =>
                Some(
                  Row().withCellValues("Période", s"Du ${startDate.format(formatter)} au ${endDate.format(formatter)}")
                )
              case (Some(startDate), _) =>
                Some(Row().withCellValues("Période", s"Depuis le ${startDate.format(formatter)}"))
              case (_, Some(endDate)) => Some(Row().withCellValues("Période", s"Jusqu'au ${endDate.format(formatter)}"))
              case (_)                => None
            }
          ).filter(_.isDefined).map(_.get)
        )
        .withColumns(
          Column(autoSized = true, style = headerStyle),
          leftAlignmentColumn
        )

      val localPath = Paths.get(signalConsoConfiguration.tmpDirectory, targetFilename)
      Workbook(extractSheet, filtersSheet).saveAsXlsx(localPath.toString)
      logger.debug(s"Generated extract locally: ${localPath}")
      localPath
    }
  }

}
