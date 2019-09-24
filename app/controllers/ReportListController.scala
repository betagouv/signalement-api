package controllers

import java.io.File
import java.time.{LocalDate, LocalDateTime, OffsetDateTime}
import java.time.format.DateTimeFormatter
import java.util.UUID

import akka.stream.alpakka.s3.scaladsl.MultipartUploadResult
import com.mohiva.play.silhouette.api.Silhouette
import com.norbitltd.spoiwo.model._
import com.norbitltd.spoiwo.model.enums.{CellFill, CellHorizontalAlignment, CellVerticalAlignment}
import com.norbitltd.spoiwo.natures.xlsx.Model2XlsxConversions._
import javax.inject.Inject
import models._
import play.api.libs.json.{JsError, Json}
import play.api.libs.mailer.AttachmentFile
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.{Configuration, Environment, Logger}
import play.core.parsers.Multipart
import play.core.parsers.Multipart.FileInfo
import repositories._
import services.{MailerService, S3Service}
import utils.Constants.ActionEvent._
import utils.Constants.StatusConso._
import utils.Constants.StatusPro._
import utils.Constants.{Departments, EventType, StatusPro}
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.{AuthEnv, WithPermission}
import utils.{Constants, DateUtils}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Random, Success, Try}

class ReportListController @Inject()(reportRepository: ReportRepository,
                                 eventRepository: EventRepository,
                                 userRepository: UserRepository,
                                 mailerService: MailerService,
                                 s3Service: S3Service,
                                 val silhouette: Silhouette[AuthEnv],
                                 val silhouetteAPIKey: Silhouette[APIKeyEnv],
                                 configuration: Configuration,
                                 environment: Environment)
                                (implicit val executionContext: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def getReports(
    offset: Option[Long], 
    limit: Option[Int], 
    departments: Option[String],
    email: Option[String],
    siret: Option[String],
    companyName: Option[String],
    start: Option[String],
    end: Option[String],
    category: Option[String],
    statusPro: Option[String],
    statusConso: Option[String],
    details: Option[String]

  ) = SecuredAction.async { implicit request =>

    implicit val paginatedReportWriter = request.identity.userRole match {
      case UserRoles.Pro => PaginatedResult.paginatedReportProWriter
      case _ => PaginatedResult.paginatedReportWriter
    }

    // valeurs par défaut
    val LIMIT_DEFAULT = 25
    val LIMIT_MAX = 250

    // normalisation des entrées
    val offsetNormalized: Long = offset.map(Math.max(_, 0)).getOrElse(0)
    val limitNormalized = limit.map(Math.max(_, 0)).map(Math.min(_, LIMIT_MAX)).getOrElse(LIMIT_DEFAULT)

    val startDate = DateUtils.parseDate(start)
    val endDate = DateUtils.parseEndDate(end)

    val filter = ReportFilter(
      departments.map(d => d.split(",").toSeq).getOrElse(Seq()),
      email,
      request.identity.userRole match {
        case UserRoles.Pro => Some(request.identity.login)
        case _ => siret
      },
      companyName,
      startDate,
      endDate,
      category,
      getSpecificsStatusProWithUserRole(statusPro, request.identity.userRole),
      statusConso,
      details
    )

    logger.debug(s"ReportFilter $filter")
    reportRepository.getReports(offsetNormalized, limitNormalized, filter).flatMap( paginatedReports => {
      val reports = paginatedReports.copy(
        entities = paginatedReports.entities.map {
          report => report.copy(statusPro = StatusPro.fromValue(getGenericStatusProWithUserRole(report.statusPro, request.identity.userRole)))
        }
      )
      Future.successful(Ok(Json.toJson(reports)))
    })
  }

  def extractReports(departments: Option[String],
                     siret: Option[String],
                     start: Option[String],
                     end: Option[String],
                     category: Option[String],
                     statusPro: Option[String],
                     statusConso: Option[String],
                     details: Option[String]) = SecuredAction(WithPermission(UserPermission.listReports)).async { implicit request =>

    val startDate = DateUtils.parseDate(start)
    val endDate = DateUtils.parseEndDate(end)
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    logger.debug(s"role ${request.identity.userRole}")

    val statusProsSeq = getSpecificsStatusProWithUserRole(statusPro, request.identity.userRole)

    val headerStyle = CellStyle(fillPattern = CellFill.Solid, fillForegroundColor = Color.Gainsborough, font = Font(bold = true), horizontalAlignment = CellHorizontalAlignment.Center)
    val centerAlignmentStyle = CellStyle(horizontalAlignment = CellHorizontalAlignment.Center, verticalAlignment = CellVerticalAlignment.Center, wrapText = true)
    val leftAlignmentStyle = CellStyle(horizontalAlignment = CellHorizontalAlignment.Left, verticalAlignment = CellVerticalAlignment.Center, wrapText = true)
    val leftAlignmentColumn = Column(autoSized = true, style = leftAlignmentStyle)
    val centerAlignmentColumn = Column(autoSized = true, style = centerAlignmentStyle)

    case class ReportColumn(
      name: String, column: Column,
      extract: (Report, List[Event], Option[User]) => String, available: Boolean = true
    )

    val reportColumns = List(
      ReportColumn(
        "Date de création", centerAlignmentColumn,
        (report, _, _) => report.creationDate.map(_.format(DateTimeFormatter.ofPattern(("dd/MM/yyyy")))).getOrElse("")
      ),
      ReportColumn(
        "Département", centerAlignmentColumn,
        (report, _, _) => report.companyPostalCode.filter(_.length >= 2).map(_.substring(0, 2)).getOrElse("")
      ),
      ReportColumn(
        "Code postal", centerAlignmentColumn,
        (report, _, _) => report.companyPostalCode.getOrElse("")
      ),
      ReportColumn(
        "Siret", centerAlignmentColumn,
        (report, _, _) => report.companySiret.getOrElse("")
      ),
      ReportColumn(
        "Nom de l'établissement", leftAlignmentColumn,
        (report, _, _) => report.companyName
      ),
      ReportColumn(
        "Adresse de l'établissement", leftAlignmentColumn,
        (report, _, _) => report.companyAddress
      ),
      ReportColumn(
        "Email de l'établissement", centerAlignmentColumn,
        (report, _, companyUser) => companyUser.filter(_ => report.isEligible).flatMap(_.email).getOrElse(""),
        available=request.identity.userRole == UserRoles.Admin
      ),
      ReportColumn(
        "Catégorie", leftAlignmentColumn,
        (report, _, _) => report.category
      ),
      ReportColumn(
        "Sous-catégories", leftAlignmentColumn,
        (report, _, _) => report.subcategories.filter(s => s != null).mkString("\n").replace("&#160;", " ")
      ),
      ReportColumn(
        "Détails", Column(width = new Width(100, WidthUnit.Character), style = leftAlignmentStyle),
        (report, _, _) => report.subcategories.filter(s => s != null).mkString("\n").replace("&#160;", " ")
      ),
      ReportColumn(
        "Pièces jointes", leftAlignmentColumn,
        (report, _, _) =>
          report.files
          .map(file => routes.ReportController.downloadReportFile(file.id.toString, file.filename).absoluteURL())
          .mkString("\n")
      ),
      ReportColumn(
        "Statut pro", leftAlignmentColumn,
        (report, _, _) => getGenericStatusProWithUserRole(report.statusPro, request.identity.userRole)
      ),
      ReportColumn(
        "Détail promesse d'action", leftAlignmentColumn,
        (report, events, _) =>
          report.statusPro
          .filter(_ == StatusPro.PROMESSE_ACTION)
          .flatMap(_ => events.find(event => event.action == Constants.ActionEvent.REPONSE_PRO_SIGNALEMENT).flatMap(_.detail))
          .getOrElse("")
      ),
      ReportColumn(
        "Statut conso", Column(autoSized = true, style = leftAlignmentStyle, hidden = (request.identity.userRole == UserRoles.DGCCRF)),
        (report, _, _) => report.statusConso.map(_.value).getOrElse("")
      ),
      ReportColumn(
        "Identifiant", centerAlignmentColumn,
        (report, _, _) => report.id.map(_.toString).getOrElse("")
      ),
      ReportColumn(
        "Prénom", leftAlignmentColumn,
        (report, _, _) => report.firstName
      ),
      ReportColumn(
        "Nom", leftAlignmentColumn,
        (report, _, _) => report.lastName
      ),
      ReportColumn(
        "Email", leftAlignmentColumn,
        (report, _, _) => report.email
      ),
      ReportColumn(
        "Code d'activation", centerAlignmentColumn,
        (report, _, companyUser) => companyUser.filter(_ => report.isEligible).flatMap(_.activationKey).getOrElse(""),
        available=request.identity.userRole == UserRoles.Admin
      ),
      ReportColumn(
        "Accord pour contact", centerAlignmentColumn,
        (report, _, _) => if (report.contactAgreement) "Oui" else "Non"
      ),
      ReportColumn(
        "Actions DGCCRF", leftAlignmentColumn,
        (report, events, _) =>
          events.filter(event => event.eventType == Constants.EventType.DGCCRF)
          .map(event => s"Le ${event.creationDate.get.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} : ${event.action.value} - ${event.detail.getOrElse("")}")
          .mkString("\n"),
        available=request.identity.userRole == UserRoles.DGCCRF
      )
    ).filter(_.available)

    for {
      paginatedReports <- reportRepository.getReports(
        0,
        10000,
        ReportFilter(departments.map(d => d.split(",").toSeq).getOrElse(Seq()), None, siret, None, startDate, endDate, category, statusProsSeq, statusConso, details)
      )
      reportEventsMap <- eventRepository.prefetchReportsEvents(paginatedReports.entities)
      companyUsersMap <- userRepository.prefetchLogins(paginatedReports.entities.flatMap(_.companySiret))
    } yield {
      val tmpFileName = s"${configuration.get[String]("play.tmpDirectory")}/signalements-${Random.alphanumeric.take(12).mkString}.xlsx";
      val reportsSheet = Sheet(name = "Signalements")
        .withRows(
          Row(style = headerStyle).withCellValues(reportColumns.map(_.name)) ::
          paginatedReports.entities.map(report =>
            Row().withCellValues(reportColumns.map(
              _.extract(
                report,
                reportEventsMap.getOrElse(report.id.get, Nil),
                report.companySiret.flatMap(companyUsersMap.get(_))
              )
            ))
          )
        )
        .withColumns(reportColumns.map(_.column))

      val filtersSheet = Sheet(name = "Filtres")
        .withRows(
          List(
            Some(Row().withCellValues("Date de l'export", LocalDateTime.now().format(DateTimeFormatter.ofPattern(("dd/MM/yyyy à HH:mm:ss"))))),
            departments.map(departments => Row().withCellValues("Départment(s)", departments)),
            (startDate, DateUtils.parseDate(end)) match {
              case (Some(startDate), Some(endDate)) => Some(Row().withCellValues("Période", s"Du ${startDate.format(formatter)} au ${endDate.format(formatter)}"))
              case (Some(startDate), _) => Some(Row().withCellValues("Période", s"Depuis le ${startDate.format(formatter)}"))
              case (_, Some(endDate)) => Some(Row().withCellValues("Période", s"Jusqu'au ${endDate.format(formatter)}"))
              case(_) => None
            },
            siret.map(siret => Row().withCellValues("Siret", siret)),
            statusPro.map(statusPro => Row().withCellValues("Statut pro", statusPro)),
            statusConso.map(statusConso => Row().withCellValues("Statut conso", statusConso)),
            category.map(category => Row().withCellValues("Catégorie", category)),
            details.map(details => Row().withCellValues("Mots clés", details)),
          ).filter(_.isDefined).map(_.get)
        )
        .withColumns(
          Column(autoSized = true, style = headerStyle),
          leftAlignmentColumn
        )

      Workbook(reportsSheet, filtersSheet).saveAsXlsx(tmpFileName)

      Ok.sendFile(
        new File(tmpFileName),
        fileName = _ => "signalements.xlsx",
        inline = false,
        onClose = () => new File(tmpFileName).delete
      )
    }
  }

}
