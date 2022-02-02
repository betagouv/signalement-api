package actors

import akka.actor._
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import com.google.inject.AbstractModule
import com.norbitltd.spoiwo.model._
import com.norbitltd.spoiwo.model.enums.CellFill
import com.norbitltd.spoiwo.model.enums.CellHorizontalAlignment
import com.norbitltd.spoiwo.model.enums.CellStyleInheritance
import com.norbitltd.spoiwo.model.enums.CellVerticalAlignment
import com.norbitltd.spoiwo.natures.xlsx.Model2XlsxConversions._
import config.SignalConsoConfiguration
import models._
import play.api.Logger
import play.api.libs.concurrent.AkkaGuiceSupport
import repositories._
import services.S3Service
import utils.DateUtils

import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.util.Random

object WebsitesExtractActor {
  def props = Props[WebsitesExtractActor]()

  case class RawFilters(query: Option[String], start: Option[String], end: Option[String])
  case class ExtractRequest(requestedBy: User, rawFilters: RawFilters)
}

@Singleton
class WebsitesExtractActor @Inject() (
    reportRepository: ReportRepository,
    asyncFileRepository: AsyncFileRepository,
    s3Service: S3Service,
    signalConsoConfiguration: SignalConsoConfiguration
)(implicit val mat: Materializer)
    extends Actor {
  import WebsitesExtractActor._
  implicit val ec: ExecutionContext = context.dispatcher

  val logger: Logger = Logger(this.getClass)
  override def preStart() =
    logger.debug("Starting")
  override def preRestart(reason: Throwable, message: Option[Any]): Unit =
    logger.debug(s"Restarting due to [${reason.getMessage}] when processing [${message.getOrElse("")}]")
  override def receive = {
    case ExtractRequest(requestedBy, rawFilters) =>
      for {
        // FIXME: We might want to move the random name generation
        // in a common place if we want to reuse it for other async files
        asyncFile <- asyncFileRepository.create(requestedBy, kind = AsyncFileKind.ReportedWebsites)
        tmpPath <- {
          sender() ! ()
          genTmpFile(rawFilters)
        }
        remotePath <- saveRemotely(tmpPath, tmpPath.getFileName.toString)
        _ <- asyncFileRepository.update(asyncFile.id, tmpPath.getFileName.toString, remotePath)
      } yield logger.debug(s"Built websites for User ${requestedBy.id} — async file ${asyncFile.id}")
    case _ => logger.debug("Could not handle request")
  }

  // Common layout variables
  val headerStyle = CellStyle(
    fillPattern = CellFill.Solid,
    fillForegroundColor = Color.Gainsborough,
    font = Font(bold = true),
    horizontalAlignment = CellHorizontalAlignment.Center
  )
  val centerAlignmentStyle = CellStyle(
    horizontalAlignment = CellHorizontalAlignment.Center,
    verticalAlignment = CellVerticalAlignment.Center,
    wrapText = true
  )
  val centerAlignmentColumn = Column(autoSized = true, style = centerAlignmentStyle)
  val leftAlignmentStyle = CellStyle(
    horizontalAlignment = CellHorizontalAlignment.Left,
    verticalAlignment = CellVerticalAlignment.Center,
    wrapText = true
  )
  val leftAlignmentColumn = Column(autoSized = true, style = leftAlignmentStyle)

  def genTmpFile(filters: RawFilters) = {

    val startDate = DateUtils.parseDate(filters.start)
    val endDate = DateUtils.parseDate(filters.end)
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    reportRepository.getWebsiteReportsWithoutCompany(startDate, endDate).map { reports =>
      val hostsWithCount = reports
        .groupBy(_.websiteURL.websiteURL.flatMap(_.getHost))
        .collect {
          case (Some(host), reports) if filters.query.map(host.contains(_)).getOrElse(true) => (host, reports.length)
        }

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
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm:ss"))
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

  def saveRemotely(localPath: Path, remoteName: String) = {
    val remotePath = s"website-extracts/${remoteName}"
    s3Service.upload(remotePath).runWith(FileIO.fromPath(localPath)).map(_ => remotePath)
  }
}

class WebsitesExtractModule extends AbstractModule with AkkaGuiceSupport {
  override def configure =
    bindActor[WebsitesExtractActor]("websites-extract-actor")
}
