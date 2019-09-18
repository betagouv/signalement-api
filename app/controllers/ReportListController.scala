package controllers

import java.io.File
import java.time.{LocalDateTime, OffsetDateTime}
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

    for {
      paginatedReports <- reportRepository.getReports(
        0,
        10000,
        ReportFilter(departments.map(d => d.split(",").toSeq).getOrElse(Seq()), None, siret, None, startDate, endDate, category, statusProsSeq, statusConso, details)
      )
      reportEventsMap <- eventRepository.prefetchReportsEvents(paginatedReports.entities)
      reportsData <- Future.sequence(paginatedReports.entities.map(extractDataFromReport(_, request.identity.userRole, reportEventsMap)))
    } yield {

      val headerStyle = CellStyle(fillPattern = CellFill.Solid, fillForegroundColor = Color.Gainsborough, font = Font(bold = true), horizontalAlignment = CellHorizontalAlignment.Center)
      val centerAlignmentStyle = CellStyle(horizontalAlignment = CellHorizontalAlignment.Center, verticalAlignment = CellVerticalAlignment.Center, wrapText = true)
      val leftAlignmentStyle = CellStyle(horizontalAlignment = CellHorizontalAlignment.Left, verticalAlignment = CellVerticalAlignment.Center, wrapText = true)
      
      val fields = List(
        ("Date de création", Column(autoSized = true, style = centerAlignmentStyle)),
        ("Département", Column(autoSized = true, style = centerAlignmentStyle)),
        ("Code postal", Column(autoSized = true, style = centerAlignmentStyle)),
        ("Siret", Column(autoSized = true, style = centerAlignmentStyle)),
        ("Nom de l'établissement", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Adresse de l'établissement", Column(autoSized = true, style = leftAlignmentStyle))
      ) ::: {
        request.identity.userRole match {
          case UserRoles.Admin => List(
            ("Email de l'établissement", Column(autoSized = true, style = centerAlignmentStyle))
          )
          case _ => List()
        }
      } ::: List(
        ("Catégorie", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Sous-catégories", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Détails", Column(width = new Width(100, WidthUnit.Character), style = leftAlignmentStyle)),
        ("Pièces jointes", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Statut pro", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Détail promesse d'action", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Statut conso", Column(autoSized = true, style = leftAlignmentStyle, hidden = (request.identity.userRole == UserRoles.DGCCRF))),
        ("Identifiant", Column(autoSized = true, style = centerAlignmentStyle)),
        ("Prénom", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Nom", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Email", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Accord pour contact", Column(autoSized = true, style = centerAlignmentStyle)),
      ) ::: {
        request.identity.userRole match {
          case UserRoles.DGCCRF => List(
            ("Actions DGCCRF", Column(autoSized = true, style = leftAlignmentStyle))
          )
          case _ => List()
        }
      }

      val tmpFileName = s"${configuration.get[String]("play.tmpDirectory")}/signalements-${Random.alphanumeric.take(12).mkString}.xlsx";
      val reportsSheet = Sheet(name = "Signalements")
        .withRows(
          Row(style = headerStyle).withCellValues(fields.map(_._1)) ::
          reportsData.map(reportData => {
            Row().withCellValues(reportData)
          })
        )
        .withColumns(
          fields.map(_._2)
        )

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
          Column(autoSized = true, style = leftAlignmentStyle)
        )

      Workbook(reportsSheet, filtersSheet).saveAsXlsx(tmpFileName)

      Ok.sendFile(new File(tmpFileName), onClose = () => new File(tmpFileName).delete)
    }

  }

  private def extractDataFromReport(report: Report, userRole: UserRole, reportEventsMap: Map[UUID, List[Event]])(implicit request: play.api.mvc.Request[Any]) = {
    val events = reportEventsMap.getOrElse(report.id.get, Nil)
    for {
      companyMail <- (report.companySiret, report.departmentAuthorized) match {
        case (Some(siret), true) => userRepository.findByLogin(siret).map(user => user.map(_.email).getOrElse(None))
        case _ => Future(None)
      }
    }
    yield {
      logger.debug(s"length events ${events.filter(event => event.eventType == Constants.EventType.DGCCRF).length}")
      List(
        report.creationDate.map(_.format(DateTimeFormatter.ofPattern(("dd/MM/yyyy")))).getOrElse(""),
        report.companyPostalCode match {
          case Some(codePostal) if codePostal.length >= 2 => codePostal.substring(0, 2)
          case _ => ""
        },
        report.companyPostalCode.getOrElse(""),
        report.companySiret.getOrElse(""),
        report.companyName,
        report.companyAddress
      ) ::: {
        userRole match {
          case UserRoles.Admin => List(
            companyMail.getOrElse("")
          )
          case _ => List()
        }
      } ::: List(
        report.category,
        report.subcategories.filter(s => s != null).reduceOption((s1, s2) => s"$s1\n$s2").getOrElse("").replace("&#160;", " "),
        report.details.map(detailInputValue => s"${detailInputValue.label.replace("&#160;", " ")} ${detailInputValue.value}").reduceOption((s1, s2) => s"$s1\n$s2").getOrElse(""),
        report.files
          .map(file => routes.ReportController.downloadReportFile(file.id.toString, file.filename).absoluteURL())
          .reduceOption((s1, s2) => s"$s1\n$s2").getOrElse(""),
        getGenericStatusProWithUserRole(report.statusPro, userRole),
        report.statusPro
          .filter(_ == StatusPro.PROMESSE_ACTION)
          .flatMap(_ => events.find(event => event.action == Constants.ActionEvent.REPONSE_PRO_SIGNALEMENT).flatMap(_.detail)).getOrElse(""),
        report.statusConso.map(_.value).getOrElse(""),
        report.id.map(_.toString).getOrElse(""),
        report.firstName,
        report.lastName,
        report.email,
        report.contactAgreement match {
          case true => "Oui"
          case _ => "Non"
        }
      ) ::: {
        userRole match {
          case UserRoles.DGCCRF => List(
            events.filter(event => event.eventType == Constants.EventType.DGCCRF)
              .map(event => s"Le ${event.creationDate.get.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} : ${event.action.value} - ${event.detail.getOrElse("")}")
              .reduceLeftOption((eventDetail1, eventDetail2) => s"$eventDetail1\n$eventDetail2").getOrElse("")
          )
          case _ => List()
        }
      }
    }
  }
}
