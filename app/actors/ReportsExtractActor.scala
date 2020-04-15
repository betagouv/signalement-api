package actors

import akka.actor._
import akka.stream.scaladsl.FileIO
import akka.stream.Materializer
import play.api.{Configuration, Logger}
import play.api.libs.json.JsObject
import java.nio.file.{Path, Paths}
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime

import javax.inject.{Inject, Singleton}
import com.norbitltd.spoiwo.model._
import com.norbitltd.spoiwo.model.enums.{CellFill, CellHorizontalAlignment, CellVerticalAlignment}
import com.norbitltd.spoiwo.natures.xlsx.Model2XlsxConversions._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random
import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport
import controllers.routes
import models._
import repositories._
import services.S3Service
import utils.Constants
import utils.Constants.{Departments, ReportStatus}
import utils.DateUtils
import java.util.UUID

object ReportsExtractActor {
  def props = Props[ReportsExtractActor]

  case class RawFilters(departments: List[String],
                        siret: Option[String],
                        start: Option[String],
                        end: Option[String],
                        category: Option[String],
                        status: Option[String],
                        details: Option[String],
                        hasCompany: Option[Boolean])
  case class ExtractRequest(requestedBy: User, restrictToCompany: Option[Company], filters: RawFilters)
}

@Singleton
class ReportsExtractActor @Inject()(configuration: Configuration,
                                    companyRepository: CompanyRepository,
                                    reportRepository: ReportRepository,
                                    eventRepository: EventRepository,
                                    asyncFileRepository: AsyncFileRepository,
                                    s3Service: S3Service)
                                    (implicit val mat: Materializer)
                                  extends Actor {
  import ReportsExtractActor._
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
    case ExtractRequest(requestedBy: User, restrictToCompany: Option[Company], filters: RawFilters) =>
      for {
        // FIXME: We might want to move the random name generation
        // in a common place if we want to reuse it for other async files
        asyncFile     <- asyncFileRepository.create(requestedBy)
        tmpPath       <- {
          sender() ! Unit
          genTmpFile(requestedBy, restrictToCompany, filters)
        }
        remotePath    <- saveRemotely(tmpPath, tmpPath.getFileName.toString)
        _             <- asyncFileRepository.update(asyncFile.id, tmpPath.getFileName.toString, remotePath)
      } yield {
        logger.debug(s"Built report for User ${requestedBy.id} — async file ${asyncFile.id}")
      }
    case _ => logger.debug("Could not handle request")
  }

  // Common layout variables
  val headerStyle = CellStyle(fillPattern = CellFill.Solid, fillForegroundColor = Color.Gainsborough, font = Font(bold = true), horizontalAlignment = CellHorizontalAlignment.Center)
  val centerAlignmentStyle = CellStyle(horizontalAlignment = CellHorizontalAlignment.Center, verticalAlignment = CellVerticalAlignment.Center, wrapText = true)
  val leftAlignmentStyle = CellStyle(horizontalAlignment = CellHorizontalAlignment.Left, verticalAlignment = CellVerticalAlignment.Center, wrapText = true)
  val leftAlignmentColumn = Column(autoSized = true, style = leftAlignmentStyle)
  val centerAlignmentColumn = Column(autoSized = true, style = centerAlignmentStyle)

  // Columns definition
  case class ReportColumn(
    name: String, column: Column,
    extract: (Report, List[ReportFile], List[Event], List[User]) => String, available: Boolean = true
  )
  def buildColumns(requestedBy: User) = {
    List(
      ReportColumn(
        "Date de création", centerAlignmentColumn,
        (report, _, _, _) => report.creationDate.format(DateTimeFormatter.ofPattern(("dd/MM/yyyy")))
      ),
      ReportColumn(
        "Département", centerAlignmentColumn,
        (report, _, _, _) => report.companyPostalCode.map(Departments.fromPostalCode(_)).flatten.getOrElse(""),
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Code postal", centerAlignmentColumn,
        (report, _, _, _) => report.companyPostalCode.getOrElse(""),
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Siret", centerAlignmentColumn,
        (report, _, _, _) => report.companySiret.map(_.value).getOrElse(""),
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Nom de l'entreprise", leftAlignmentColumn,
        (report, _, _, _) => report.companyName.getOrElse(""),
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Adresse de l'entreprise", leftAlignmentColumn,
        (report, _, _, _) => report.companyAddress.map(_.value).getOrElse(""),
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Email de l'entreprise", centerAlignmentColumn,
        (report, _, _, companyAdmins) => companyAdmins.map(_.email).mkString(","),
        available=requestedBy.userRole == UserRoles.Admin
      ),
      ReportColumn(
        "Site web de l'entreprise", centerAlignmentColumn,
        (report, _, _, _) => report.websiteURL.map(_.value).getOrElse(""),
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Catégorie", leftAlignmentColumn,
        (report, _, _, _) => report.category
      ),
      ReportColumn(
        "Sous-catégories", leftAlignmentColumn,
        (report, _, _, _) => report.subcategories.filter(s => s != null).mkString("\n").replace("&#160;", " ")
      ),
      ReportColumn(
        "Détails", Column(width = new Width(100, WidthUnit.Character), style = leftAlignmentStyle),
        (report, _, _, _) => report.details.map(d => s"${d.label} ${d.value}").mkString("\n").replace("&#160;", " ")
      ),
      ReportColumn(
        "Pièces jointes", leftAlignmentColumn,
        (report, files, _, _) =>
          files
            .filter(file => file.origin == ReportFileOrigin.CONSUMER)
            .map(file => s"${baseUrl}${routes.ReportController.downloadReportFile(file.id.toString, file.filename).url}")
            .mkString("\n"),
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Statut", leftAlignmentColumn,
        (report, _, _, _) => report.status.getValueWithUserRole(requestedBy.userRole).getOrElse(""),
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Réponse au consommateur", leftAlignmentColumn,
        (report, _, events, _) =>
          Some(report.status)
          .filter(List(ReportStatus.PROMESSE_ACTION, ReportStatus.SIGNALEMENT_MAL_ATTRIBUE, ReportStatus.SIGNALEMENT_INFONDE) contains _ )
          .flatMap(_ => events.find(event => event.action == Constants.ActionEvent.REPONSE_PRO_SIGNALEMENT).map(e =>
            e.details.validate[ReportResponse].get.consumerDetails
          ))
          .getOrElse("")
      ),
      ReportColumn(
        "Réponse à la DGCCRF", leftAlignmentColumn,
        (report, _, events, _) =>
          Some(report.status)
          .filter(List(ReportStatus.PROMESSE_ACTION, ReportStatus.SIGNALEMENT_MAL_ATTRIBUE, ReportStatus.SIGNALEMENT_INFONDE) contains _ )
          .flatMap(_ => events.find(event => event.action == Constants.ActionEvent.REPONSE_PRO_SIGNALEMENT).flatMap(e =>
            e.details.validate[ReportResponse].get.dgccrfDetails
          ))
          .getOrElse("")
      ),
      ReportColumn(
        "Identifiant", centerAlignmentColumn,
        (report, _, _, _) => report.id.toString,
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Prénom", leftAlignmentColumn,
        (report, _, _, _) => report.firstName,
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Nom", leftAlignmentColumn,
        (report, _, _, _) => report.lastName,
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Email", leftAlignmentColumn,
        (report, _, _, _) => report.email.value,
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Accord pour contact", centerAlignmentColumn,
        (report, _, _, _) => if (report.contactAgreement) "Oui" else "Non"
      ),
      ReportColumn(
        "Actions DGCCRF", leftAlignmentColumn,
        (report, _, events, _) =>
          events.filter(event => event.eventType == Constants.EventType.DGCCRF)
          .map(event => s"Le ${event.creationDate.get.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} : ${event.action.value} - ${event.details.as[JsObject].value.get("description").getOrElse("")}")
          .mkString("\n"),
        available = requestedBy.userRole == UserRoles.DGCCRF
      ),
      ReportColumn(
        "Contrôle effectué", centerAlignmentColumn,
        (report, _, events, _) => if (events.exists(event => event.action == Constants.ActionEvent.CONTROL)) "Oui" else "Non",
        available = requestedBy.userRole == UserRoles.DGCCRF
      )
    ).filter(_.available)
  }

  def genTmpFile(requestedBy: User, restrictToCompany: Option[Company], filters: RawFilters) = {
    val startDate = DateUtils.parseDate(filters.start)
    val endDate = DateUtils.parseEndDate(filters.end)
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    val reportColumns = buildColumns(requestedBy)
    val statusList = ReportStatus.getStatusListForValueWithUserRole(filters.status, requestedBy.userRole)
    for {
      paginatedReports <- reportRepository.getReports(
        0,
        10000,
        ReportFilter(
          filters.departments,
          None,
          restrictToCompany.map(c => Some(c.siret.value)).getOrElse(filters.siret),
          None,
          startDate,
          endDate,
          filters.category,
          statusList,
          filters.details,
          requestedBy.userRole match {
            case UserRoles.Pro => Some(false)
            case _ => None
          },
          filters.hasCompany
        )
      )
      reportFilesMap <- reportRepository.prefetchReportsFiles(paginatedReports.entities.map(_.id))
      reportEventsMap <- eventRepository.prefetchReportsEvents(paginatedReports.entities)
      companyAdminsMap   <- companyRepository.fetchAdminsByCompany(paginatedReports.entities.flatMap(_.companyId))
    } yield {
      val targetFilename = s"signalements-${Random.alphanumeric.take(12).mkString}.xlsx"
      val reportsSheet = Sheet(name = "Signalements")
        .withRows(
          Row(style = headerStyle).withCellValues(reportColumns.map(_.name)) ::
          paginatedReports.entities.map(report =>
            Row().withCellValues(reportColumns.map(
              _.extract(
                report,
                reportFilesMap.getOrElse(report.id, Nil),
                reportEventsMap.getOrElse(report.id, Nil),
                report.companyId.flatMap(companyAdminsMap.get(_)).getOrElse(Nil)
              )
            ))
          )
        )
        .withColumns(reportColumns.map(_.column))

      val filtersSheet = Sheet(name = "Filtres")
        .withRows(
          List(
            Some(Row().withCellValues("Date de l'export", LocalDateTime.now().format(DateTimeFormatter.ofPattern(("dd/MM/yyyy à HH:mm:ss"))))),
            Some(filters.departments).filter(!_.isEmpty).map(departments => Row().withCellValues("Départment(s)", departments.mkString(","))),
            (startDate, DateUtils.parseDate(filters.end)) match {
              case (Some(startDate), Some(endDate)) => Some(Row().withCellValues("Période", s"Du ${startDate.format(formatter)} au ${endDate.format(formatter)}"))
              case (Some(startDate), _) => Some(Row().withCellValues("Période", s"Depuis le ${startDate.format(formatter)}"))
              case (_, Some(endDate)) => Some(Row().withCellValues("Période", s"Jusqu'au ${endDate.format(formatter)}"))
              case(_) => None
            },
            filters.siret.map(siret => Row().withCellValues("Siret", siret)),
            filters.status.map(status => Row().withCellValues("Statut", status)),
            filters.category.map(category => Row().withCellValues("Catégorie", category)),
            filters.details.map(details => Row().withCellValues("Mots clés", details)),
          ).filter(_.isDefined).map(_.get)
        )
        .withColumns(
          Column(autoSized = true, style = headerStyle),
          leftAlignmentColumn
        )

      val localPath = Paths.get(tmpDirectory, targetFilename)
      Workbook(reportsSheet, filtersSheet).saveAsXlsx(localPath.toString)
      logger.debug(s"Generated extract locally: ${localPath}")
      localPath
    }
  }

  def saveRemotely(localPath: Path, remoteName: String) = {
    val remotePath = s"extracts/${remoteName}"
    s3Service.upload(BucketName, remotePath).runWith(FileIO.fromPath(localPath)).map(_ => remotePath)
  }
}

class ReportsExtractModule extends AbstractModule with AkkaGuiceSupport {
  override def configure = {
    bindActor[ReportsExtractActor]("reports-extract-actor")
  }
}
