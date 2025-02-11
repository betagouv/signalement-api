package controllers

import authentication.Authenticator
import cats.data.NonEmptyList
import cats.implicits.catsSyntaxOption
import cats.implicits.toTraverseOps
import config.EmailConfiguration
import config.TaskConfiguration
import controllers.error.AppError
import models._
import models.event.Event
import models.report._
import models.report.sampledata.SampleDataService
import orchestrators.EmailNotificationOrchestrator
import play.api.Logger
import play.api.i18n.Lang
import play.api.i18n.Messages
import play.api.i18n.MessagesApi
import play.api.i18n.MessagesImpl
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.twirl.api.Html
import repositories.albert.AlbertClassification
import repositories.albert.AlbertClassificationRepositoryInterface
import repositories.company.CompanyRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface
import repositories.event.EventRepositoryInterface
import repositories.ipblacklist.BlackListedIp
import repositories.ipblacklist.IpBlackListRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.subcategorylabel.SubcategoryLabel
import repositories.subcategorylabel.SubcategoryLabelRepositoryInterface
import services.AlbertService
import services.PDFService
import services.emails.EmailDefinitions.allEmailDefinitions
import services.emails.EmailDefinitionsConsumer._
import services.emails.EmailDefinitionsPro._
import services.emails.EmailsExamplesUtils._
import services.emails.BaseEmail
import services.emails.EmailDefinition
import services.emails.MailService
import utils.Constants.ActionEvent.REPORT_PRO_RESPONSE
import utils.Constants.ActionEvent
import utils.Constants.EventType
import utils._

import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class AdminController(
    reportRepository: ReportRepositoryInterface,
    companyAccessRepository: CompanyAccessRepositoryInterface,
    eventRepository: EventRepositoryInterface,
    mailService: MailService,
    pdfService: PDFService,
    emailConfiguration: EmailConfiguration,
    taskConfiguration: TaskConfiguration,
    companyRepository: CompanyRepositoryInterface,
    emailNotificationOrchestrator: EmailNotificationOrchestrator,
    ipBlackListRepository: IpBlackListRepositoryInterface,
    albertClassificationRepository: AlbertClassificationRepositoryInterface,
    albertService: AlbertService,
    sampleDataService: SampleDataService,
    subcategoryLabelRepository: SubcategoryLabelRepositoryInterface,
    implicit val frontRoute: FrontRoute,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  val logger: Logger                                  = Logger(this.getClass)
  implicit val contactAddress: EmailAddress           = emailConfiguration.contactAddress
  implicit val timeout: org.apache.pekko.util.Timeout = 5.seconds

  implicit val messagesProvider: Messages = MessagesImpl(Lang(Locale.FRENCH), controllerComponents.messagesApi)
  private val availablePdfs = Seq[(String, Html)](
    "accountActivation" -> views.html.pdfs.accountActivation(
      genCompany,
      Some(genReport.creationDate.toLocalDate),
      Some(genReport.expirationDate.toLocalDate),
      "123456"
    )(frontRoute = frontRoute, contactAddress = contactAddress),
    "accountActivationReminder" -> views.html.pdfs.accountActivationReminder(
      genCompany,
      Some(genReport.creationDate.toLocalDate),
      Some(genReport.expirationDate.toLocalDate),
      "123456"
    )(frontRoute = frontRoute, contactAddress = contactAddress),
    "accountActivationLastReminder" -> views.html.pdfs.accountActivationLastReminder(
      genCompany,
      reportNumber = 33,
      Some(genReport.creationDate.toLocalDate),
      Some(genReport.expirationDate.toLocalDate),
      "123456"
    )(frontRoute = frontRoute, contactAddress = contactAddress),
    "accountFollowUp" -> views.html.pdfs.accountFollowUp(
      genCompany,
      "123456"
    )(frontRoute = frontRoute, contactAddress = contactAddress),
    "report" -> views.html.pdfs.report(
      genReport,
      Some(genCompany),
      Nil,
      Some(genExistingReportResponse),
      Some(genResponseConsumerReview),
      Some(genEngagementReview),
      Nil,
      Nil
    )(frontRoute = frontRoute, None, messagesProvider),
    "proResponse" -> views.html.pdfs.proResponse(
      Some(
        Event(
          UUID.randomUUID(),
          Some(genReport.id),
          genReport.companyId,
          Some(genUser.id),
          OffsetDateTime.now(),
          EventType.PRO,
          ActionEvent.REPORT_PRO_RESPONSE,
          Json.toJson(genReportResponse)
        )
      )
    )(messagesProvider)
  )

  private val allEmailExamples: Seq[(String, (EmailAddress, MessagesApi) => BaseEmail)] =
    allEmailDefinitions.flatMap(readExamplesWithFullKey)

  def getEmailCodes = Act.secured.superAdmins.async { _ =>
    val keys = allEmailExamples.map(_._1)
    Future.successful(Ok(Json.toJson(keys)))
  }
  def sendTestEmail(templateRef: String, to: String) = Act.secured.superAdmins.async { _ =>
    val maybeEmail = allEmailExamples
      .find(_._1 == templateRef)
      .map(_._2(EmailAddress(to), controllerComponents.messagesApi))
    maybeEmail match {
      case Some(email) =>
        mailService.send(email).map(_ => Ok)
      case None =>
        Future.successful(NotFound)
    }
  }

  private def readExamplesWithFullKey(
      emailDefinition: EmailDefinition
  ): Seq[(String, (EmailAddress, MessagesApi) => BaseEmail)] =
    emailDefinition.examples.map { case (key, fn) =>
      s"${emailDefinition.category.toString.toLowerCase}.$key" -> fn
    }

  def getPdfCodes = Act.secured.superAdmins { _ =>
    Ok(Json.toJson(availablePdfs.map(_._1)))
  }
  def sendTestPdf(templateRef: String) = Act.secured.superAdmins { _ =>
    availablePdfs.toMap
      .get(templateRef)
      .map { html =>
        val pdfSource = pdfService.createPdfSource(Seq(html))
        Ok.chunked(
          content = pdfSource,
          inline = false,
          fileName = Some(s"${templateRef}_${OffsetDateTime.now().toString}.pdf")
        )
      }
      .getOrElse(NotFound)
  }

  private def _sendProAckToConsumer(reports: List[Report]) =
    for {
      eventsMap <- eventRepository.fetchEventsOfReports(reports)
      companies <- companyRepository.fetchCompanies(reports.flatMap(_.companyId))
      filteredEvents = reports.flatMap { report =>
        eventsMap
          .get(report.id)
          .flatMap(_.find(_.action == REPORT_PRO_RESPONSE))
          .map(evt => (report, evt))
      }

      _ <- filteredEvents.map { case (report, responseEvent) =>
        val maybeCompany = report.companyId.flatMap(companyId => companies.find(_.id == companyId))
        mailService.send(
          ConsumerProResponseNotification.Email(
            report,
            responseEvent.details.as[ExistingReportResponse],
            maybeCompany,
            controllerComponents.messagesApi
          )
        )
      }.sequence
    } yield ()

  private def _sendNewReportToPro(reports: List[Report]) = {
    val reportAndCompanyIdList = reports.flatMap(report => report.companyId.map(c => (report, c)))
    for {
      reportAndEmailList <- reportAndCompanyIdList.map { case (report, companyId) =>
        companyAccessRepository
          .fetchAdmins(companyId)
          .map(_.map(_.email).distinct)
          .map(emails => (report, NonEmptyList.fromList(emails)))
      }.sequence
      _ <- reportAndEmailList.map {
        case (report, Some(adminsEmails)) =>
          mailService.send(ProNewReportNotification.Email(adminsEmails, report))
        case (report, None) =>
          logger.debug(s"Not sending email for report ${report.id}, no admin found")
          Future.unit
      }.sequence
    } yield ()
  }

  private def _sendReportAckToConsumer(
      reportsMap: Map[Report, List[ReportFile]],
      subcategoryLabels: List[SubcategoryLabel]
  ) = {
    val reports = reportsMap.keys.toList
    for {
      events    <- eventRepository.fetchEventsOfReports(reports)
      companies <- companyRepository.fetchCompanies(reports.flatMap(_.companyId))

      emailsToSend = reportsMap.flatMap { case (report, reportAttachements) =>
        val maybeCompany = report.companyId.flatMap(companyId => companies.find(_.id == companyId))
        val event =
          events.get(report.id).flatMap(_.find(_.action == Constants.ActionEvent.EMAIL_CONSUMER_ACKNOWLEDGMENT))
        val subcategoryLabel =
          subcategoryLabels.find(sl => sl.category == report.category && sl.subcategories == report.subcategories)

        event match {
          case Some(evt) =>
            Some(
              ConsumerReportAcknowledgment.Email(
                report,
                subcategoryLabel,
                maybeCompany,
                evt,
                reportAttachements,
                controllerComponents.messagesApi
              )
            )
          case None =>
            logger.debug(s"Not sending email for report ${report.id}, no event found")
            None
        }
      }
      _ <- emailsToSend.toList.map(mailService.send).sequence
    } yield ()
  }

  private def _sendReportToDgccrfIfNeeded(
      reportsMap: Map[Report, List[ReportFile]],
      subcategoryLabels: List[SubcategoryLabel]
  ) = {
    val reports = reportsMap.keys.toList

    reports.traverse { report =>
      val subcategoryLabel =
        subcategoryLabels.find(sl => sl.category == report.category && sl.subcategories == report.subcategories)
      emailNotificationOrchestrator.notifyDgccrfIfNeeded(report, subcategoryLabel)
    }
  }

  def resend(start: OffsetDateTime, end: OffsetDateTime, emailType: ResendEmailType) =
    Act.secured.superAdmins.async { implicit request =>
      for {
        reports <- reportRepository.getReportsWithFiles(
          Some(request.identity),
          ReportFilter(start = Some(start), end = Some(end))
        )
        reportsList = reports.keys.toList
        subcategoryLabels <- reportsList
          .traverse(report => subcategoryLabelRepository.get(report.category, report.subcategories))
          .map(_.flatten)
        _ <- emailType match {
          case ResendEmailType.NewReportAckToConsumer => _sendReportAckToConsumer(reports.toMap, subcategoryLabels)
          case ResendEmailType.NewReportAckToPro      => _sendNewReportToPro(reportsList)
          case ResendEmailType.NotifyDGCCRF           => _sendReportToDgccrfIfNeeded(reports.toMap, subcategoryLabels)
          case ResendEmailType.ReportProResponse      => _sendProAckToConsumer(reportsList)
        }
      } yield NoContent
    }

  def blackListedIPs() = Act.secured.superAdmins.async { _ =>
    ipBlackListRepository.list().map(blackListedIps => Ok(Json.toJson(blackListedIps)))
  }

  def deleteBlacklistedIp(ip: String) = Act.secured.superAdmins.async { _ =>
    ipBlackListRepository.delete(ip).map(_ => NoContent)
  }

  def createBlacklistedIp() =
    Act.secured.superAdmins.async(parse.json) { implicit request =>
      for {
        blacklistedIpRequest <- request.parseBody[BlackListedIp]()
        blackListedIp        <- ipBlackListRepository.create(blacklistedIpRequest)
      } yield Created(Json.toJson(blackListedIp))
    }

  def generateAlbertReportAnalysis(reportId: UUID) =
    Act.secured.adminsAndReadonlyAndAgents.allowImpersonation.async { _ =>
      for {
        maybeReport          <- reportRepository.get(reportId)
        report               <- maybeReport.liftTo[Future](AppError.ReportNotFound(reportId))
        albertClassification <- albertService.classifyReport(report)
        albertCodeConsoRes   <- albertService.qualifyReportBasedOnCodeConso(report)
        maybeClassification = albertClassification.map(classificationJsonStr =>
          AlbertClassification
            .fromAlbertApi(
              reportId,
              Json.parse(classificationJsonStr),
              albertCodeConsoRes.map(Json.parse)
            )
        )
        _ <- maybeClassification match {
          case Some(albert) => albertClassificationRepository.createOrUpdate(albert)
          case None         => Future.unit
        }
      } yield NoContent
    }

  def getAlbertReportAnalysis(reportId: UUID) =
    Act.secured.adminsAndReadonlyAndAgents.allowImpersonation.async { _ =>
      albertClassificationRepository
        .getByReportId(reportId)
        .map(maybeClassification => Ok(Json.toJson(maybeClassification)))
    }

  def regenSampleData() = Act.secured.superAdmins.async { _ =>
    if (taskConfiguration.sampleData.active) {
      for {
        _ <-
          sampleDataService.genSampleData()
      } yield Ok
    } else Future.successful(BadRequest)

  }
}
