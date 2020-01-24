package controllers

import java.io.File
import java.time.{LocalDateTime, OffsetDateTime}
import java.time.format.DateTimeFormatter
import java.util.UUID

import com.mohiva.play.silhouette.api.Silhouette
import com.norbitltd.spoiwo.model._
import com.norbitltd.spoiwo.model.enums.{CellFill, CellHorizontalAlignment, CellVerticalAlignment}
import com.norbitltd.spoiwo.natures.xlsx.Model2XlsxConversions._
import javax.inject.Inject
import models._
import models.Event._
import orchestrators.ReportOrchestrator
import play.api.libs.json.{JsError, JsObject, Json}
import play.api.{Configuration, Environment, Logger}
import repositories._
import services.{MailerService, S3Service}
import utils.Constants.{ActionEvent, EventType, ReportStatus}
import utils.Constants.ReportStatus._
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.{AuthEnv, WithPermission}
import utils.{Constants, DateUtils, SIRET}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Random, Success, Try}

class ReportListController @Inject()(reportOrchestrator: ReportOrchestrator,
                                     reportRepository: ReportRepository,
                                     companyRepository: CompanyRepository,
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

  def fetchCompany(user: User, siret: Option[String]): Future[Company] = {
    for {
      accesses <- companyRepository.fetchCompaniesWithLevel(user)
    } yield {
      siret.map(s => accesses.filter(_._1.siret == SIRET(s))).getOrElse(accesses).map(_._1).head
    }
  }

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
                  status: Option[String],
                  details: Option[String]

  ) = SecuredAction.async { implicit request =>

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
      siret,
      companyName,
      startDate,
      endDate,
      category,
      getStatusListForValueWithUserRole(status, request.identity.userRole),
      details,
      request.identity.userRole match {
        case UserRoles.Pro => Some(false)
        case _ => None
      }
    )

    logger.debug(s"ReportFilter $filter")
    for {
      company <- Some(request.identity)
                  .filter(_.userRole == UserRoles.Pro)
                  .map(u => fetchCompany(u, siret).map(Some(_)))
                  .getOrElse(Future(None))
      paginatedReports <- reportRepository.getReports(
                            offsetNormalized,
                            limitNormalized,
                            company.map(c => filter.copy(siret=Some(c.siret.value)))
                                   .getOrElse(filter))
      reportFilesMap <- reportRepository.prefetchReportsFiles(paginatedReports.entities.map(_.id))
    } yield {
      Ok(Json.toJson(paginatedReports.copy(entities = paginatedReports.entities.map(r => ReportWithFiles(r, reportFilesMap.getOrElse(r.id, Nil))))))
    }
  }

  def extractReports(departments: Option[String],
                     siret: Option[String],
                     start: Option[String],
                     end: Option[String],
                     category: Option[String],
                     status: Option[String],
                     details: Option[String]) = SecuredAction(WithPermission(UserPermission.listReports)).async { implicit request =>

    val startDate = DateUtils.parseDate(start)
    val endDate = DateUtils.parseEndDate(end)
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    logger.debug(s"role ${request.identity.userRole}")

    val statusList = getStatusListForValueWithUserRole(status, request.identity.userRole)

    val headerStyle = CellStyle(fillPattern = CellFill.Solid, fillForegroundColor = Color.Gainsborough, font = Font(bold = true), horizontalAlignment = CellHorizontalAlignment.Center)
    val centerAlignmentStyle = CellStyle(horizontalAlignment = CellHorizontalAlignment.Center, verticalAlignment = CellVerticalAlignment.Center, wrapText = true)
    val leftAlignmentStyle = CellStyle(horizontalAlignment = CellHorizontalAlignment.Left, verticalAlignment = CellVerticalAlignment.Center, wrapText = true)
    val leftAlignmentColumn = Column(autoSized = true, style = leftAlignmentStyle)
    val centerAlignmentColumn = Column(autoSized = true, style = centerAlignmentStyle)

    case class ReportColumn(
      name: String, column: Column,
      extract: (Report, List[ReportFile], List[Event], List[User]) => String, available: Boolean = true
    )

    val reportColumns = List(
      ReportColumn(
        "Date de création", centerAlignmentColumn,
        (report, _, _, _) => report.creationDate.format(DateTimeFormatter.ofPattern(("dd/MM/yyyy")))
      ),
      ReportColumn(
        "Département", centerAlignmentColumn,
        (report, _, _, _) => report.companyPostalCode.filter(_.length >= 2).map(_.substring(0, 2)).getOrElse(""),
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains request.identity.userRole
      ),
      ReportColumn(
        "Code postal", centerAlignmentColumn,
        (report, _, _, _) => report.companyPostalCode.getOrElse(""),
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains request.identity.userRole
      ),
      ReportColumn(
        "Siret", centerAlignmentColumn,
        (report, _, _, _) => report.companySiret.map(_.value).getOrElse(""),
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains request.identity.userRole
      ),
      ReportColumn(
        "Nom de l'établissement", leftAlignmentColumn,
        (report, _, _, _) => report.companyName,
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains request.identity.userRole
      ),
      ReportColumn(
        "Adresse de l'établissement", leftAlignmentColumn,
        (report, _, _, _) => report.companyAddress,
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains request.identity.userRole
      ),
      ReportColumn(
        "Email de l'établissement", centerAlignmentColumn,
        (report, _, _, companyAdmins) => companyAdmins.filter(_ => report.isEligible).map(_.email).mkString(","),
        available=request.identity.userRole == UserRoles.Admin
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
            .map(file => routes.ReportController.downloadReportFile(file.id.toString, file.filename).absoluteURL())
            .mkString("\n"),
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains request.identity.userRole
      ),
      ReportColumn(
        "Statut", leftAlignmentColumn,
        (report, _, _, _) => report.status.getValueWithUserRole(request.identity.userRole).getOrElse(""),
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains request.identity.userRole
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
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains request.identity.userRole
      ),
      ReportColumn(
        "Prénom", leftAlignmentColumn,
        (report, _, _, _) => report.firstName,
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains request.identity.userRole
      ),
      ReportColumn(
        "Nom", leftAlignmentColumn,
        (report, _, _, _) => report.lastName,
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains request.identity.userRole
      ),
      ReportColumn(
        "Email", leftAlignmentColumn,
        (report, _, _, _) => report.email.value,
        available = List(UserRoles.DGCCRF, UserRoles.Admin) contains request.identity.userRole
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
        available=request.identity.userRole == UserRoles.DGCCRF
      )
    ).filter(_.available)

    for {
      restrictToCompany <- if (request.identity.userRole == UserRoles.Pro)
                              fetchCompany(request.identity, siret).map(Some(_))
                           else
                              Future(None)
      paginatedReports <- reportRepository.getReports(
        0,
        10000,
        ReportFilter(
          departments.map(d => d.split(",").toSeq).getOrElse(Seq()),
          None,
          restrictToCompany.map(c => Some(c.siret.value)).getOrElse(siret),
          None,
          startDate,
          endDate,
          category,
          statusList,
          details,
          request.identity.userRole match {
            case UserRoles.Pro => Some(false)
            case _ => None
          }
        )
      )
      reportFilesMap <- reportRepository.prefetchReportsFiles(paginatedReports.entities.map(_.id))
      reportEventsMap <- eventRepository.prefetchReportsEvents(paginatedReports.entities)
      companyAdminsMap   <- companyRepository.fetchAdminsByCompany(paginatedReports.entities.flatMap(_.companyId))
    } yield {
      val tmpFileName = s"${configuration.get[String]("play.tmpDirectory")}/signalements-${Random.alphanumeric.take(12).mkString}.xlsx";
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
            departments.map(departments => Row().withCellValues("Départment(s)", departments)),
            (startDate, DateUtils.parseDate(end)) match {
              case (Some(startDate), Some(endDate)) => Some(Row().withCellValues("Période", s"Du ${startDate.format(formatter)} au ${endDate.format(formatter)}"))
              case (Some(startDate), _) => Some(Row().withCellValues("Période", s"Depuis le ${startDate.format(formatter)}"))
              case (_, Some(endDate)) => Some(Row().withCellValues("Période", s"Jusqu'au ${endDate.format(formatter)}"))
              case(_) => None
            },
            siret.map(siret => Row().withCellValues("Siret", siret)),
            status.map(status => Row().withCellValues("Statut", status)),
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

  def confirmContactByPostOnReportList() = SecuredAction(WithPermission(UserPermission.createEvent)).async(parse.json) { implicit request =>

    import ReportListObjects.ReportList

    request.body.validate[ReportList](Json.reads[ReportList]).fold(
      errors => {
        Future.successful(BadRequest(JsError.toJson(errors)))
      },
      reportList => {

        logger.debug(s"confirmContactByPostOnReportList ${reportList.reportIds}")

        Future.sequence(reportList.reportIds.map(reportId =>
          reportOrchestrator
            .newEvent(
              reportId,
              Event(Some(UUID.randomUUID()), Some(reportId), Some(request.identity.id), Some(OffsetDateTime.now), EventType.PRO, ActionEvent.CONTACT_COURRIER, Json.obj()),
              request.identity
            )
        )).map(events => Ok(Json.toJson(events)))
      }
    )
  }

}

object ReportListObjects {
  case class ReportList(reportIds: List[UUID])
}
