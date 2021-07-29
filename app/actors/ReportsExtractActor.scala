package actors

import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
import controllers.routes
import javax.inject.Inject
import javax.inject.Singleton
import models._
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.Configuration
import play.api.Logger
import repositories._
import services.S3Service
import utils.Constants.Departments
import utils.Constants.ReportStatus
import utils.Constants
import utils.DateUtils

import scala.concurrent.ExecutionContext
import scala.util.Random

object ReportsExtractActor {
  def props = Props[ReportsExtractActor]

  case class ExtractRequest(requestedBy: User, filters: ReportFilterBody)
}

@Singleton
class ReportsExtractActor @Inject() (
    configuration: Configuration,
    companyRepository: CompanyRepository,
    reportRepository: ReportRepository,
    eventRepository: EventRepository,
    asyncFileRepository: AsyncFileRepository,
    s3Service: S3Service
)(implicit val mat: Materializer)
    extends Actor {
  import ReportsExtractActor._
  implicit val ec: ExecutionContext = context.dispatcher

  val baseUrl = configuration.get[String]("play.application.url")
  val BucketName = configuration.get[String]("play.buckets.report")
  val tmpDirectory = configuration.get[String]("play.tmpDirectory")

  val logger: Logger = Logger(this.getClass)
  override def preStart() =
    logger.debug("Starting")
  override def preRestart(reason: Throwable, message: Option[Any]): Unit =
    logger.debug(s"Restarting due to [${reason.getMessage}] when processing [${message.getOrElse("")}]")
  override def receive = {
    case ExtractRequest(requestedBy: User, filters: ReportFilterBody) =>
      for {
        // FIXME: We might want to move the random name generation
        // in a common place if we want to reuse it for other async files
        asyncFile <- asyncFileRepository.create(requestedBy, kind = AsyncFileKind.Reports)
        tmpPath <- {
          sender() ! Unit
          genTmpFile(requestedBy, filters)
        }
        remotePath <- saveRemotely(tmpPath, tmpPath.getFileName.toString)
        _ <- asyncFileRepository.update(asyncFile.id, tmpPath.getFileName.toString, remotePath)
      } yield logger.debug(s"Built report for User ${requestedBy.id} — async file ${asyncFile.id}")
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
  val leftAlignmentStyle = CellStyle(
    horizontalAlignment = CellHorizontalAlignment.Left,
    verticalAlignment = CellVerticalAlignment.Center,
    wrapText = true
  )
  val leftAlignmentColumn = Column(autoSized = true, style = leftAlignmentStyle)
  val centerAlignmentColumn = Column(autoSized = true, style = centerAlignmentStyle)

  // Columns definition
  case class ReportColumn(
      name: String,
      column: Column,
      extract: (Report, List[ReportFile], List[Event], List[User]) => String,
      available: Boolean = true
  )
  def buildColumns(requestedBy: User) = {
    List(
      ReportColumn(
        "Date de création",
        centerAlignmentColumn,
        (report, _, _, _) => report.creationDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
      ),
      ReportColumn(
        "Département",
        centerAlignmentColumn,
        (report, _, _, _) => report.companyAddress.postalCode.flatMap(Departments.fromPostalCode).getOrElse("")
      ),
      ReportColumn(
        "Code postal",
        centerAlignmentColumn,
        (report, _, _, _) => report.companyAddress.postalCode.getOrElse(""),
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Pays",
        centerAlignmentColumn,
        (report, _, _, _) => report.companyAddress.country.map(_.name).getOrElse(""),
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Siret",
        centerAlignmentColumn,
        (report, _, _, _) => report.companySiret.map(_.value).getOrElse("")
      ),
      ReportColumn(
        "Nom de l'entreprise",
        leftAlignmentColumn,
        (report, _, _, _) => report.companyName.getOrElse(""),
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Adresse de l'entreprise",
        leftAlignmentColumn,
        (report, _, _, _) => report.companyAddress.toString,
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Email de l'entreprise",
        centerAlignmentColumn,
        (report, _, _, companyAdmins) => companyAdmins.map(_.email).mkString(","),
        available = requestedBy.userRole == UserRoles.Admin
      ),
      ReportColumn(
        "Site web de l'entreprise",
        centerAlignmentColumn,
        (report, _, _, _) => report.websiteURL.websiteURL.map(_.value).getOrElse(""),
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Téléphone de l'entreprise",
        centerAlignmentColumn,
        (report, _, _, _) => report.phone.getOrElse(""),
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Vendeur (marketplace)",
        centerAlignmentColumn,
        (report, _, _, _) => report.vendor.getOrElse(""),
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Catégorie",
        leftAlignmentColumn,
        (report, _, _, _) => report.category
      ),
      ReportColumn(
        "Sous-catégories",
        leftAlignmentColumn,
        (report, _, _, _) => report.subcategories.filter(s => s != null).mkString("\n").replace("&#160;", " ")
      ),
      ReportColumn(
        "Détails",
        Column(width = new Width(100, WidthUnit.Character), style = leftAlignmentStyle),
        (report, _, _, _) => report.details.map(d => s"${d.label} ${d.value}").mkString("\n").replace("&#160;", " ")
      ),
      ReportColumn(
        "Pièces jointes",
        leftAlignmentColumn,
        (report, files, _, _) =>
          files
            .filter(file => file.origin == ReportFileOrigin.CONSUMER)
            .map(file =>
              s"${baseUrl}${routes.ReportController.downloadReportFile(file.id.toString, file.filename).url}"
            )
            .mkString("\n"),
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Statut",
        leftAlignmentColumn,
        (report, _, _, _) => report.status.getValueWithUserRole(requestedBy.userRole).getOrElse(""),
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Réponse au consommateur",
        leftAlignmentColumn,
        (report, _, events, _) =>
          Some(report.status)
            .filter(
              List(
                ReportStatus.PROMESSE_ACTION,
                ReportStatus.SIGNALEMENT_MAL_ATTRIBUE,
                ReportStatus.SIGNALEMENT_INFONDE
              ) contains _
            )
            .flatMap(_ =>
              events
                .find(event => event.action == Constants.ActionEvent.REPORT_PRO_RESPONSE)
                .map(e => e.details.validate[ReportResponse].get.consumerDetails)
            )
            .getOrElse("")
      ),
      ReportColumn(
        "Réponse à la DGCCRF",
        leftAlignmentColumn,
        (report, _, events, _) =>
          Some(report.status)
            .filter(
              List(
                ReportStatus.PROMESSE_ACTION,
                ReportStatus.SIGNALEMENT_MAL_ATTRIBUE,
                ReportStatus.SIGNALEMENT_INFONDE
              ) contains _
            )
            .flatMap(_ =>
              events
                .find(event => event.action == Constants.ActionEvent.REPORT_PRO_RESPONSE)
                .flatMap(e => e.details.validate[ReportResponse].get.dgccrfDetails)
            )
            .getOrElse("")
      ),
      ReportColumn(
        "Identifiant",
        centerAlignmentColumn,
        (report, _, _, _) => report.id.toString,
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Prénom",
        leftAlignmentColumn,
        (report, _, _, _) => report.firstName,
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Nom",
        leftAlignmentColumn,
        (report, _, _, _) => report.lastName,
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Email",
        leftAlignmentColumn,
        (report, _, _, _) => report.email.value,
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Accord pour contact",
        centerAlignmentColumn,
        (report, _, _, _) => if (report.contactAgreement) "Oui" else "Non"
      ),
      ReportColumn(
        "Actions DGCCRF",
        leftAlignmentColumn,
        (report, _, events, _) =>
          events
            .filter(event => event.eventType == Constants.EventType.DGCCRF)
            .map(event =>
              s"Le ${event.creationDate.get
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} : ${event.action.value} - ${event.getDescription}"
            )
            .mkString("\n"),
        available = requestedBy.userRole == UserRoles.DGCCRF
      ),
      ReportColumn(
        "Contrôle effectué",
        centerAlignmentColumn,
        (report, _, events, _) =>
          if (events.exists(event => event.action == Constants.ActionEvent.CONTROL)) "Oui" else "Non",
        available = requestedBy.userRole == UserRoles.DGCCRF
      )
    ).filter(_.available)
  }

  def genTmpFile(requestedBy: User, filters: ReportFilterBody) = {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    val reportColumns = buildColumns(requestedBy)
    val statusList = ReportStatus.getStatusListForValueWithUserRole(filters.status, requestedBy.userRole)
    val reportFilter = filters.toReportFilter(
      employeeConsumer = requestedBy.userRole match {
        case UserRoles.Pro => Some(false)
        case _             => None
      },
      statusList = statusList
    )
    for {
      paginatedReports <- reportRepository.getReports(offset = 0, limit = 100000, filter = reportFilter)
      reportFilesMap <- reportRepository.prefetchReportsFiles(paginatedReports.entities.map(_.id))
      reportEventsMap <- eventRepository.prefetchReportsEvents(paginatedReports.entities)
      companyAdminsMap <- companyRepository.fetchAdminsByCompany(paginatedReports.entities.flatMap(_.companyId))
    } yield {
      val targetFilename = s"signalements-${Random.alphanumeric.take(12).mkString}.xlsx"
      val reportsSheet = Sheet(name = "Signalements")
        .withRows(
          Row(style = headerStyle).withCellValues(reportColumns.map(_.name)) ::
            paginatedReports.entities.map(report =>
              Row().withCells(
                reportColumns
                  .map(
                    _.extract(
                      report,
                      reportFilesMap.getOrElse(report.id, Nil),
                      reportEventsMap.getOrElse(report.id, Nil),
                      report.companyId.flatMap(companyAdminsMap.get).getOrElse(Nil)
                    )
                  )
                  .map(StringCell(_, None, None, CellStyleInheritance.CellThenRowThenColumnThenSheet))
              )
            )
        )
        .withColumns(reportColumns.map(_.column))

      val filtersSheet = Sheet(name = "Filtres")
        .withRows(
          List(
            Some(
              Row().withCellValues(
                "Date de l'export",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm:ss"))
              )
            ),
            Some(filters.departments)
              .filter(_.isDefined)
              .map(departments => Row().withCellValues("Départment(s)", departments.mkString(","))),
            (reportFilter.start, DateUtils.parseDate(filters.end)) match {
              case (Some(startDate), Some(endDate)) =>
                Some(
                  Row().withCellValues("Période", s"Du ${startDate.format(formatter)} au ${endDate.format(formatter)}")
                )
              case (Some(startDate), _) =>
                Some(Row().withCellValues("Période", s"Depuis le ${startDate.format(formatter)}"))
              case (_, Some(endDate)) => Some(Row().withCellValues("Période", s"Jusqu'au ${endDate.format(formatter)}"))
              case (_)                => None
            },
            Some(Row().withCellValues("Siret", filters.siretSirenList.mkString(","))),
            filters.websiteURL.map(websiteURL => Row().withCellValues("Site internet", websiteURL)),
            filters.phone.map(phone => Row().withCellValues("Numéro de téléphone", phone)),
            filters.status.map(status => Row().withCellValues("Statut", status)),
            filters.category.map(category => Row().withCellValues("Catégorie", category)),
            filters.details.map(details => Row().withCellValues("Mots clés", details))
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
  override def configure =
    bindActor[ReportsExtractActor]("reports-extract-actor")
}
