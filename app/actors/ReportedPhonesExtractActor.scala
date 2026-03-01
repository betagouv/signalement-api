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
import models.User
import play.api.Logger
import repositories.report.ReportRepositoryInterface
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

object ReportedPhonesExtractActor {
  case class RawFilters(query: Option[String], start: Option[String], end: Option[String])

  sealed trait ReportedPhonesExtractCommand
  final case class ExtractRequest(fileId: UUID, requestedBy: User, rawFilters: RawFilters)
      extends ReportedPhonesExtractCommand
  final case class ExtractRequestSuccess(fileId: UUID, requestedBy: User) extends ReportedPhonesExtractCommand
  final case class ExtractRequestFailure(fileId: UUID, requestedBy: User) extends ReportedPhonesExtractCommand

  val logger: Logger = Logger(this.getClass)

  def create(
      config: SignalConsoConfiguration,
      reportRepository: ReportRepositoryInterface,
      extractService: ExtractService
  ): Behavior[ReportedPhonesExtractCommand] =
    Behaviors.setup { context =>
      import context.executionContext

      Behaviors.receiveMessage[ReportedPhonesExtractCommand] {
        case ExtractRequest(fileId, requestedBy, rawFilters) =>
          val result = genTmpFile(config, reportRepository, rawFilters).flatMap(
            extractService.buildAndUploadFile(_, fileId, requestedBy, "reported-phones-extracts")
          )

          context.pipeToSelf(result) {
            case Success(_) => ExtractRequestSuccess(fileId, requestedBy)
            case Failure(_) => ExtractRequestFailure(fileId, requestedBy)
          }
          Behaviors.same

        case ExtractRequestSuccess(fileId, requestedBy) =>
          logger.debug(s"Built reportedPhones for User ${requestedBy.id} — async file ${fileId}")
          Behaviors.same

        case ExtractRequestFailure(fileId, requestedBy) =>
          logger.info(s"ExtractRequest failure for User ${requestedBy.id} — async file ${fileId}")
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
      config: SignalConsoConfiguration,
      reportRepository: ReportRepositoryInterface,
      filters: RawFilters
  )(implicit
      ec: ExecutionContext
  ): Future[Path] = {

    val startDate = DateUtils.parseDate(filters.start)
    val endDate   = DateUtils.parseDate(filters.end)
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    reportRepository.getPhoneReports(startDate, endDate).map { reports =>
      val hostsWithCount = reports
        .groupBy(report => (report.phone, report.companySiret))
        .collect {
          case ((Some(phone), siretOpt), reports) if filters.query.forall(phone.contains(_)) =>
            ((phone, siretOpt), reports.length)
        }

      val targetFilename = s"telephones-signales-${Random.alphanumeric.take(12).mkString}.xlsx"
      val extractSheet = Sheet(name = "Téléphones signalés")
        .withRows(
          Row(style = headerStyle).withCellValues("Numéro de téléphone", "SIRET", "Nombre de signalement") ::
            hostsWithCount.toList.sortBy(_._2)(Ordering.Int.reverse).map { case ((host, siretOpt), count) =>
              Row().withCells(
                StringCell(host, None, None, CellStyleInheritance.CellThenRowThenColumnThenSheet),
                StringCell(
                  siretOpt.map(_.value).getOrElse(""),
                  None,
                  None,
                  CellStyleInheritance.CellThenRowThenColumnThenSheet
                ),
                StringCell(s"$count", None, None, CellStyleInheritance.CellThenRowThenColumnThenSheet)
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
            filters.query.map(q => Row().withCellValues("Numéro de téléphone", q)),
            (startDate, DateUtils.parseDate(filters.end)) match {
              case (Some(startDate), Some(endDate)) =>
                Some(
                  Row().withCellValues("Période", s"Du ${startDate.format(formatter)} au ${endDate.format(formatter)}")
                )
              case (Some(startDate), _) =>
                Some(Row().withCellValues("Période", s"Depuis le ${startDate.format(formatter)}"))
              case (_, Some(endDate)) => Some(Row().withCellValues("Période", s"Jusqu'au ${endDate.format(formatter)}"))
              case _                  => None
            }
          ).flatten
        )
        .withColumns(
          Column(autoSized = true, style = headerStyle),
          leftAlignmentColumn
        )

      val localPath = Paths.get(config.tmpDirectory, targetFilename)
      Workbook(extractSheet, filtersSheet).saveAsXlsx(localPath.toString)
      logger.debug(s"Generated extract locally: ${localPath}")
      localPath
    }
  }
}
