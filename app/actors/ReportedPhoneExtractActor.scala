package actors

import java.nio.file.{Path, Paths}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.actor._
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import com.google.inject.AbstractModule
import com.norbitltd.spoiwo.model._
import com.norbitltd.spoiwo.model.enums.{CellFill, CellHorizontalAlignment, CellStyleInheritance, CellVerticalAlignment}
import com.norbitltd.spoiwo.natures.xlsx.Model2XlsxConversions._
import javax.inject.{Inject, Singleton}
import models._
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.{Configuration, Logger}
import repositories._
import services.S3Service
import utils.DateUtils

import scala.concurrent.ExecutionContext
import scala.util.Random

object ReportedPhonesExtractActor {
  def props = Props[ReportedPhonesExtractActor]

  case class RawFilters(query: Option[String],
                        start: Option[String],
                        end: Option[String])
  case class ExtractRequest(requestedBy: User, rawFilters: RawFilters)
}

@Singleton
class ReportedPhonesExtractActor @Inject()(configuration: Configuration,
                                    reportRepository: ReportRepository,
                                    asyncFileRepository: AsyncFileRepository,
                                    s3Service: S3Service)
                                   (implicit val mat: Materializer)
  extends Actor {
  import ReportedPhonesExtractActor._
  implicit val ec: ExecutionContext = context.dispatcher

  val baseUrl = configuration.get[String]("play.application.url")
  val BucketName = configuration.get[String]("play.buckets.report")
  val tmpDirectory = configuration.get[String]("play.tmpDirectory")

  val logger: Logger = Logger(this.getClass)
  override def preStart() = {
    logger.debug("Starting")
  }
  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    logger.debug(s"Restarting due to [${reason.getMessage}] when processing [${message.getOrElse("")}]")
  }
  override def receive = {
    case ExtractRequest(requestedBy, rawFilters) =>
      for {
        // FIXME: We might want to move the random name generation
        // in a common place if we want to reuse it for other async files
        asyncFile     <- asyncFileRepository.create(requestedBy)
        tmpPath       <- {
          sender() ! Unit
          genTmpFile(rawFilters)
        }
        remotePath    <- saveRemotely(tmpPath, tmpPath.getFileName.toString)
        _             <- asyncFileRepository.update(asyncFile.id, tmpPath.getFileName.toString, remotePath)
      } yield {
        logger.debug(s"Built reportedPhones for User ${requestedBy.id} — async file ${asyncFile.id}")
      }
    case _ => logger.debug("Could not handle request")
  }

  // Common layout variables
  val headerStyle = CellStyle(fillPattern = CellFill.Solid, fillForegroundColor = Color.Gainsborough, font = Font(bold = true), horizontalAlignment = CellHorizontalAlignment.Center)
  val centerAlignmentStyle = CellStyle(horizontalAlignment = CellHorizontalAlignment.Center, verticalAlignment = CellVerticalAlignment.Center, wrapText = true)
  val centerAlignmentColumn = Column(autoSized = true, style = centerAlignmentStyle)
  val leftAlignmentStyle = CellStyle(horizontalAlignment = CellHorizontalAlignment.Left, verticalAlignment = CellVerticalAlignment.Center, wrapText = true)
  val leftAlignmentColumn = Column(autoSized = true, style = leftAlignmentStyle)

  def genTmpFile(filters: RawFilters) = {

    val startDate = DateUtils.parseDate(filters.start)
    val endDate = DateUtils.parseDate(filters.end)
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    reportRepository.getPhoneReports(startDate, endDate).map{ reports =>
      val hostsWithCount = reports
        .groupBy(_.phone)
        .collect { case (Some(host), reports) if filters.query.map(host.contains(_)).getOrElse(true) => (host, reports.length) }

      val targetFilename = s"telephones-non-identifies-${Random.alphanumeric.take(12).mkString}.xlsx"
      val extractSheet = Sheet(name = "Téléphones non identifiés")
        .withRows(
          Row(style = headerStyle).withCellValues("Nombre de signalement", "Numéro de téléphone") ::
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
            Some(Row().withCellValues("Date de l'export", LocalDateTime.now().format(DateTimeFormatter.ofPattern(("dd/MM/yyyy à HH:mm:ss"))))),
            filters.query.map(q => Row().withCellValues("Numéro de téléphone", q)),
            (startDate, DateUtils.parseDate(filters.end)) match {
              case (Some(startDate), Some(endDate)) => Some(Row().withCellValues("Période", s"Du ${startDate.format(formatter)} au ${endDate.format(formatter)}"))
              case (Some(startDate), _) => Some(Row().withCellValues("Période", s"Depuis le ${startDate.format(formatter)}"))
              case (_, Some(endDate)) => Some(Row().withCellValues("Période", s"Jusqu'au ${endDate.format(formatter)}"))
              case(_) => None
            },
          ).filter(_.isDefined).map(_.get)
        )
        .withColumns(
          Column(autoSized = true, style = headerStyle),
          leftAlignmentColumn
        )

      val localPath = Paths.get(tmpDirectory, targetFilename)
      Workbook(extractSheet, filtersSheet).saveAsXlsx(localPath.toString)
      logger.debug(s"Generated extract locally: ${localPath}")
      localPath
    }
  }

  def saveRemotely(localPath: Path, remoteName: String) = {
    val remotePath = s"reported-phones-extracts/${remoteName}"
    s3Service.upload(BucketName, remotePath).runWith(FileIO.fromPath(localPath)).map(_ => remotePath)
  }
}

class ReportedPhonesExtractModule extends AbstractModule with AkkaGuiceSupport {
  override def configure = {
    bindActor[ReportedPhonesExtractActor]("reported-phones-extract-actor")
  }
}
