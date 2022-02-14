package orchestrators

import actors.UploadActor
import akka.actor.ActorRef
import cats.data.NonEmptyList
import cats.implicits.catsSyntaxMonadError
import cats.implicits.toTraverseOps
import config.EmailConfiguration
import config.SignalConsoConfiguration
import config.TokenConfiguration
import controllers.error.AppError.DuplicateReportCreation
import controllers.error.AppError.ExternalReportsMaxPageSizeExceeded
import controllers.error.AppError.InvalidEmail
import controllers.error.AppError.ReportCreationInvalidBody
import controllers.error.AppError.SpammerEmailBlocked
import models.Event._
import models._
import models.report.Report
import models.report.ReportAction
import models.report.ReportCompany
import models.report.ReportConsumerUpdate
import models.report.ReportDraft
import models.report.ReportFile
import models.report.ReportFileOrigin
import models.report.ReportFilter
import models.report.ReportResponse
import models.report.ReportResponseType
import models.report.ReportStatus
import models.report.ReportWithFiles
import models.report.ReviewOnReportResponse
import models.report.Tag.ReportTag
import models.token.TokenKind.CompanyInit
import models.website.Website
import play.api.libs.json.Json
import play.api.Logger
import repositories._
import services.Email.ConsumerProResponseNotification
import services.Email.ConsumerReportAcknowledgment
import services.Email.ConsumerReportReadByProNotification
import services.Email.DgccrfDangerousProductReportNotification
import services.Email.ProNewReportNotification
import services.Email.ProResponseAcknowledgment
import services.MailService
import services.S3Service
import utils.Constants.ActionEvent._
import utils.Constants.ActionEvent
import utils.Constants.EventType
import utils.Constants.ReportResponseReview
import utils.Constants
import utils.EmailAddress
import utils.URL

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.TemporalAmount
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Random

class ReportOrchestrator @Inject() (
    mailService: MailService,
    reportRepository: ReportRepository,
    companyRepository: CompanyRepository,
    accessTokenRepository: AccessTokenRepository,
    eventRepository: EventRepository,
    websiteRepository: WebsiteRepository,
    companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    subscriptionRepository: SubscriptionRepository,
    emailValidationOrchestrator: EmailValidationOrchestrator,
    @Named("upload-actor") uploadActor: ActorRef,
    s3Service: S3Service,
    emailConfiguration: EmailConfiguration,
    tokenConfiguration: TokenConfiguration,
    signalConsoConfiguration: SignalConsoConfiguration
)(implicit val executionContext: ExecutionContext) {

  val logger = Logger(this.getClass)

  implicit val timeout: akka.util.Timeout = 5.seconds

  private def genActivationToken(companyId: UUID, validity: Option[TemporalAmount]): Future[String] =
    for {
      existingToken <- accessTokenRepository.fetchValidActivationToken(companyId)
      _ <- existingToken
        .map(accessTokenRepository.updateToken(_, AccessLevel.ADMIN, validity))
        .getOrElse(Future(None))
      token <- existingToken
        .map(Future(_))
        .getOrElse(
          accessTokenRepository.createToken(
            kind = CompanyInit,
            token = f"${Random.nextInt(1000000)}%06d",
            validity = validity,
            companyId = Some(companyId),
            level = Some(AccessLevel.ADMIN)
          )
        )
    } yield token.token

  private def notifyProfessionalOfNewReport(report: Report, company: Company): Future[Report] =
    for {
      maybeCompanyUsers <- companiesVisibilityOrchestrator
        .fetchAdminsWithHeadOffice(company.siret)
        .map(NonEmptyList.fromList)

      updatedReport <- maybeCompanyUsers match {
        case Some(companyUsers) =>
          logger.debug("Found user, sending notification")
          val companyUserEmails: NonEmptyList[EmailAddress] = companyUsers.map(_.email)
          for {
            _ <- mailService.send(ProNewReportNotification(companyUserEmails, report))
            reportWithUpdatedStatus <- reportRepository.update(report.copy(status = ReportStatus.TraitementEnCours))
            _ <- createEmailProNewReportEvent(report, company, companyUsers)
          } yield reportWithUpdatedStatus
        case None =>
          logger.debug("No user found, generating activation token")
          genActivationToken(company.id, tokenConfiguration.companyInitDuration).map(_ => report)
      }
    } yield updatedReport

  private def createEmailProNewReportEvent(report: Report, company: Company, companyUsers: NonEmptyList[User]) =
    eventRepository
      .createEvent(
        Event(
          UUID.randomUUID(),
          Some(report.id),
          Some(company.id),
          Some(companyUsers.head.id),
          OffsetDateTime.now(),
          Constants.EventType.PRO,
          Constants.ActionEvent.EMAIL_PRO_NEW_REPORT,
          stringToDetailsJsValue(
            s"Notification du professionnel par mail de la réception d'un nouveau signalement ( ${companyUsers.map(_.email).toList.mkString(", ")} )"
          )
        )
      )

  private[this] def createReportedWebsite(
      companyOpt: Option[Company],
      companyCountry: Option[String],
      websiteURLOpt: Option[URL]
  ): Future[Option[Website]] = {
    val maybeWebsite: Option[Website] = for {
      websiteUrl <- websiteURLOpt
      host <- websiteUrl.getHost
    } yield Website(host = host, companyCountry = companyCountry, companyId = companyOpt.map(_.id))

    maybeWebsite.map { website =>
      logger.debug("Creating website entry")
      websiteRepository.create(website)
    }.sequence
  }

  def validateAndCreateReport(draftReport: ReportDraft): Future[Report] =
    for {
      _ <- if (ReportDraft.isValid(draftReport)) Future.unit else Future.failed(ReportCreationInvalidBody)
      _ <- emailValidationOrchestrator
        .isEmailValid(draftReport.email)
        .ensure {
          logger.warn(s"Email ${draftReport.email} is not valid, abort report creation")
          InvalidEmail(draftReport.email.value)
        }(isValid => isValid || emailConfiguration.skipReportEmailValidation)
      _ <- validateReportSpammerBlockList(draftReport.email)
      createdReport <- createReport(draftReport)
    } yield createdReport

  private def validateReportSpammerBlockList(emailAddress: EmailAddress) =
    if (signalConsoConfiguration.reportEmailsBlacklist.contains(emailAddress.value)) {
      Future.failed(SpammerEmailBlocked(emailAddress))
    } else {
      Future.unit
    }

  private def createReport(draftReport: ReportDraft): Future[Report] =
    for {
      maybeCompany <- extractOptionnalCompany(draftReport)
      maybeCountry = extractOptionnalCountry(draftReport)
      _ <- createReportedWebsite(maybeCompany, maybeCountry, draftReport.websiteURL)
      reportToCreate = draftReport.generateReport.copy(companyId = maybeCompany.map(_.id))
      _ <- reportRepository.findSimilarReportCount(reportToCreate).ensure(DuplicateReportCreation)(_ == 0)
      report <- reportRepository.create(reportToCreate)
      _ = logger.debug(s"Created report with id ${report.id}")
      _ <- reportRepository.attachFilesToReport(draftReport.fileIds, report.id)
      files <- reportRepository.retrieveReportFiles(report.id)
      updatedReport <- notifyProfessionalIfNeeded(maybeCompany, report)
      _ <- notifyDgccrfIfNeeded(updatedReport)
      _ <- notifyConsumer(updatedReport, maybeCompany, files)
      _ = logger.debug(s"Report ${updatedReport.id} created")
    } yield updatedReport

  private def notifyDgccrfIfNeeded(report: Report): Future[Unit] = for {
    ddEmails <-
      if (report.tags.contains(ReportTag.ProduitDangereux)) {
        report.companyAddress.postalCode
          .map(postalCode => subscriptionRepository.getDirectionDepartementaleEmail(postalCode.take(2)))
          .getOrElse(Future(Seq()))
      } else Future(Seq())
    _ <-
      if (ddEmails.nonEmpty) {
        mailService.send(DgccrfDangerousProductReportNotification(ddEmails, report))
      } else {
        Future.unit
      }
  } yield ()

  private def notifyConsumer(report: Report, maybeCompany: Option[Company], reportAttachements: List[ReportFile]) =
    for {
      event <- eventRepository.createEvent(
        Event(
          UUID.randomUUID(),
          Some(report.id),
          maybeCompany.map(_.id),
          None,
          OffsetDateTime.now(),
          Constants.EventType.CONSO,
          Constants.ActionEvent.EMAIL_CONSUMER_ACKNOWLEDGMENT
        )
      )
      _ <- mailService.send(ConsumerReportAcknowledgment(report, event, reportAttachements))
    } yield ()

  private def notifyProfessionalIfNeeded(maybeCompany: Option[Company], report: Report) =
    (report.status, maybeCompany) match {
      case (ReportStatus.TraitementEnCours, Some(company)) =>
        notifyProfessionalOfNewReport(report, company)
      case _ => Future.successful(report)
    }

  private def extractOptionnalCountry(draftReport: ReportDraft) =
    draftReport.companyAddress.flatMap(_.country.map { country =>
      logger.debug(s"Found country ${country} from draft report")
      country.name
    })

  private def extractOptionnalCompany(draftReport: ReportDraft): Future[Option[Company]] =
    draftReport.companySiret match {
      case Some(siret) =>
        val company = Company(
          siret = siret,
          name = draftReport.companyName.get,
          address = draftReport.companyAddress.get,
          activityCode = draftReport.companyActivityCode
        )
        companyRepository.getOrCreate(siret, company).map { company =>
          logger.debug("Company extracted from report")
          Some(company)
        }
      case None =>
        logger.debug("No company attached to report")
        Future(None)
    }

  def updateReportCompany(reportId: UUID, reportCompany: ReportCompany, userUUID: UUID): Future[Option[Report]] =
    for {
      existingReport <- reportRepository.getReport(reportId)
      company <- companyRepository.getOrCreate(
        reportCompany.siret,
        Company(
          siret = reportCompany.siret,
          name = reportCompany.name,
          address = reportCompany.address,
          activityCode = reportCompany.activityCode
        )
      )
      reportWithNewData <- existingReport match {
        case Some(report) =>
          reportRepository
            .update(
              report.copy(
                companyId = Some(company.id),
                companyName = Some(reportCompany.name),
                companyAddress = reportCompany.address,
                companySiret = Some(reportCompany.siret)
              )
            )
            .map(Some(_))
        case _ => Future(None)
      }
      reportWithNewStatus <- reportWithNewData
        .filter(_.companySiret != existingReport.flatMap(_.companySiret))
        .filter(_.creationDate.isAfter(OffsetDateTime.now(ZoneOffset.UTC).minusDays(7)))
        .map(report =>
          reportRepository
            .update(
              report.copy(
                status = report.initialStatus()
              )
            )
            .map(Some(_))
        )
        .getOrElse(Future(reportWithNewData))
      updatedReport <- reportWithNewStatus
        .filter(_.status == ReportStatus.TraitementEnCours)
        .filter(_.companySiret.isDefined)
        .filter(_.companySiret != existingReport.flatMap(_.companySiret))
        .map(r => notifyProfessionalOfNewReport(r, company).map(Some(_)))
        .getOrElse(Future(reportWithNewStatus))
      _ <- existingReport match {
        case Some(report) =>
          eventRepository
            .createEvent(
              Event(
                UUID.randomUUID(),
                Some(report.id),
                Some(company.id),
                Some(userUUID),
                OffsetDateTime.now(),
                Constants.EventType.ADMIN,
                Constants.ActionEvent.REPORT_COMPANY_CHANGE,
                stringToDetailsJsValue(
                  s"Entreprise précédente : Siret ${report.companySiret
                    .getOrElse("non renseigné")} - ${Some(report.companyAddress.toString).filter(_ != "").getOrElse("Adresse non renseignée")}"
                )
              )
            )
            .map(Some(_))
        case _ => Future(None)
      }
      _ <- existingReport.flatMap(_.companyId).map(id => removeAccessToken(id)).getOrElse(Future(()))
    } yield updatedReport

  def updateReportConsumer(
      reportId: UUID,
      reportConsumer: ReportConsumerUpdate,
      userUUID: UUID
  ): Future[Option[Report]] =
    for {
      existingReport <- reportRepository.getReport(reportId)
      updatedReport <- existingReport match {
        case Some(report) =>
          reportRepository
            .update(
              report.copy(
                firstName = reportConsumer.firstName,
                lastName = reportConsumer.lastName,
                email = reportConsumer.email,
                contactAgreement = reportConsumer.contactAgreement
              )
            )
            .map(Some(_))
        case _ => Future(None)
      }
      _ <- existingReport match {
        case Some(report) =>
          eventRepository
            .createEvent(
              Event(
                UUID.randomUUID(),
                Some(report.id),
                report.companyId,
                Some(userUUID),
                OffsetDateTime.now(),
                Constants.EventType.ADMIN,
                Constants.ActionEvent.REPORT_CONSUMER_CHANGE,
                stringToDetailsJsValue(
                  s"Consommateur précédent : ${report.firstName} ${report.lastName} - ${report.email} " +
                    s"- Accord pour contact : ${if (report.contactAgreement) "oui" else "non"}"
                )
              )
            )
            .map(Some(_))
        case _ => Future(None)
      }
    } yield updatedReport

  def handleReportView(report: Report, user: User): Future[Report] =
    if (user.userRole == UserRole.Professionnel) {
      eventRepository
        .getEvents(report.id, EventFilter(None))
        .flatMap(events =>
          if (!events.exists(_.action == Constants.ActionEvent.REPORT_READING_BY_PRO)) {
            manageFirstViewOfReportByPro(report, user.id)
          } else {
            Future(report)
          }
        )
    } else {
      Future(report)
    }

  def saveReportFile(filename: String, file: java.io.File, origin: ReportFileOrigin): Future[ReportFile] =
    for {
      reportFile <- reportRepository.createFile(
        ReportFile(
          UUID.randomUUID,
          None,
          OffsetDateTime.now(),
          filename,
          file.getName(),
          origin,
          None
        )
      )
    } yield {
      uploadActor ! UploadActor.Request(reportFile, file)
      reportFile
    }

  def removeReportFile(id: UUID) =
    for {
      reportFile <- reportRepository.getFile(id)
      _ <- reportFile.map(f => reportRepository.deleteFile(f.id)).getOrElse(Future(None))
      _ <- reportFile.map(f => s3Service.delete(f.storageFilename)).getOrElse(Future(None))
    } yield ()

  private def removeAccessToken(companyId: UUID) =
    for {
      company <- companyRepository.fetchCompany(companyId)
      reports <- company
        .map(c => reportRepository.getReports(ReportFilter(companyIds = Seq(c.id))).map(_.entities))
        .getOrElse(Future(Nil))
      cnt <- if (reports.isEmpty) accessTokenRepository.removePendingTokens(company.get) else Future(0)
    } yield {
      logger.debug(s"Removed ${cnt} tokens for company ${companyId}")
      ()
    }

  def deleteReport(id: UUID) =
    for {
      report <- reportRepository.getReport(id)
      _ <- eventRepository.deleteEvents(id)
      _ <- reportRepository
        .retrieveReportFiles(id)
        .map(files => files.map(file => removeReportFile(file.id)))
      _ <- reportRepository.delete(id)
      _ <- report.flatMap(_.companyId).map(id => removeAccessToken(id)).getOrElse(Future(()))
    } yield report.isDefined

  private def manageFirstViewOfReportByPro(report: Report, userUUID: UUID) =
    for {
      _ <- eventRepository.createEvent(
        Event(
          UUID.randomUUID(),
          Some(report.id),
          report.companyId,
          Some(userUUID),
          OffsetDateTime.now(),
          Constants.EventType.PRO,
          Constants.ActionEvent.REPORT_READING_BY_PRO
        )
      )
      updatedReport <-
        if (ReportStatus.isFinal(report.status)) {
          Future(report)
        } else {
          notifyConsumerOfReportTransmission(report)
        }
    } yield updatedReport

  private def notifyConsumerOfReportTransmission(report: Report): Future[Report] =
    for {
      _ <- mailService.send(ConsumerReportReadByProNotification(report))
      _ <- eventRepository.createEvent(
        Event(
          id = UUID.randomUUID(),
          reportId = Some(report.id),
          companyId = report.companyId,
          userId = None,
          creationDate = OffsetDateTime.now(),
          eventType = Constants.EventType.CONSO,
          action = Constants.ActionEvent.EMAIL_CONSUMER_REPORT_READING
        )
      )
      newReport <- reportRepository.update(report.copy(status = ReportStatus.Transmis))
    } yield newReport

  private def sendMailsAfterProAcknowledgment(report: Report, reportResponse: ReportResponse, user: User) = for {
    _ <- mailService.send(ProResponseAcknowledgment(report, reportResponse, user))
    _ <- mailService.send(ConsumerProResponseNotification(report, reportResponse))
  } yield ()

  def newEvent(reportId: UUID, draftEvent: Event, user: User): Future[Option[Event]] =
    for {
      report <- reportRepository.getReport(reportId)
      newEvent <- report match {
        case Some(r) =>
          eventRepository
            .createEvent(
              draftEvent.copy(
                id = UUID.randomUUID(),
                creationDate = OffsetDateTime.now(),
                reportId = Some(r.id),
                companyId = r.companyId,
                userId = Some(user.id)
              )
            )
            .map(Some(_))
        case _ => Future(None)
      }
      _ <- (report, newEvent) match {
        case (Some(r), Some(event)) =>
          reportRepository
            .update(
              r.copy(status = event.action match {
                case POST_ACCOUNT_ACTIVATION_DOC => ReportStatus.TraitementEnCours
                case _                           => r.status
              })
            )
            .map(Some(_))
        case _ => Future(None)
      }
    } yield {
      newEvent.foreach(event =>
        event.action match {
          case REPORT_READING_BY_PRO => notifyConsumerOfReportTransmission(report.get)
          case _                     => ()
        }
      )
      newEvent
    }

  def handleReportResponse(report: Report, reportResponse: ReportResponse, user: User): Future[Report] = {
    logger.debug(s"handleReportResponse ${reportResponse.responseType}")
    for {
      _ <- eventRepository.createEvent(
        Event(
          UUID.randomUUID(),
          Some(report.id),
          report.companyId,
          Some(user.id),
          OffsetDateTime.now(),
          EventType.PRO,
          ActionEvent.REPORT_PRO_RESPONSE,
          Json.toJson(reportResponse)
        )
      )
      _ <- reportRepository.attachFilesToReport(reportResponse.fileIds, report.id)
      updatedReport <- reportRepository.update(
        report.copy(
          status = reportResponse.responseType match {
            case ReportResponseType.ACCEPTED      => ReportStatus.PromesseAction
            case ReportResponseType.REJECTED      => ReportStatus.Infonde
            case ReportResponseType.NOT_CONCERNED => ReportStatus.MalAttribue
          }
        )
      )
      _ <- sendMailsAfterProAcknowledgment(updatedReport, reportResponse, user)
      _ <- eventRepository.createEvent(
        Event(
          UUID.randomUUID(),
          Some(report.id),
          updatedReport.companyId,
          None,
          OffsetDateTime.now(),
          Constants.EventType.CONSO,
          Constants.ActionEvent.EMAIL_CONSUMER_REPORT_RESPONSE
        )
      )
      _ <- eventRepository.createEvent(
        Event(
          UUID.randomUUID(),
          Some(report.id),
          updatedReport.companyId,
          Some(user.id),
          OffsetDateTime.now(),
          Constants.EventType.PRO,
          Constants.ActionEvent.EMAIL_PRO_RESPONSE_ACKNOWLEDGMENT
        )
      )
    } yield updatedReport
  }

  def handleReportAction(report: Report, reportAction: ReportAction, user: User): Future[Event] =
    for {
      newEvent <- eventRepository.createEvent(
        Event(
          UUID.randomUUID(),
          Some(report.id),
          report.companyId,
          Some(user.id),
          OffsetDateTime.now(),
          EventType.fromUserRole(user.userRole),
          reportAction.actionType,
          reportAction.details
            .map(details => Json.obj("description" -> details))
            .getOrElse(Json.toJson(reportAction))
        )
      )
      _ <- reportRepository.attachFilesToReport(reportAction.fileIds, report.id)
    } yield {
      logger.debug(
        s"Create event ${newEvent.id} on report ${report.id} for reportActionType ${reportAction.actionType}"
      )
      newEvent
    }

  def handleReviewOnReportResponse(reportId: UUID, reviewOnReportResponse: ReviewOnReportResponse): Future[Event] = {
    logger.debug(s"Report ${reportId} - the consumer give a review on response")
    eventRepository.createEvent(
      Event(
        id = UUID.randomUUID(),
        reportId = Some(reportId),
        companyId = None,
        userId = None,
        creationDate = OffsetDateTime.now(),
        eventType = EventType.CONSO,
        action = ActionEvent.REPORT_REVIEW_ON_RESPONSE,
        details = stringToDetailsJsValue(
          s"${if (reviewOnReportResponse.positive) ReportResponseReview.Positive.entryName
          else ReportResponseReview.Negative.entryName}" +
            s"${reviewOnReportResponse.details.map(d => s" - $d").getOrElse("")}"
        )
      )
    )
  }

  def getReportsForUser(
      connectedUser: User,
      filter: ReportFilter,
      offset: Option[Long],
      limit: Option[Int]
  ): Future[PaginatedResult[ReportWithFiles]] =
    for {
      sanitizedSirenSirets <- companiesVisibilityOrchestrator.filterUnauthorizedSiretSirenList(
        filter.siretSirenList,
        connectedUser
      )
      paginatedReportFiles <-
        if (sanitizedSirenSirets.isEmpty && connectedUser.userRole == UserRole.Professionnel) {
          Future(PaginatedResult(totalCount = 0, hasNextPage = false, entities = List.empty[ReportWithFiles]))
        } else {
          getReportsWithFile[ReportWithFiles](
            filter.copy(siretSirenList = sanitizedSirenSirets),
            offset,
            limit,
            (r: Report, m: Map[UUID, List[ReportFile]]) => ReportWithFiles(r, m.getOrElse(r.id, Nil))
          )
        }
    } yield paginatedReportFiles

  def getReportsWithFile[T](
      filter: ReportFilter,
      offset: Option[Long],
      limit: Option[Int],
      toApi: (Report, Map[UUID, List[ReportFile]]) => T
  ): Future[PaginatedResult[T]] = {
    val maxResults = 1000
    for {
      _ <- limit match {
        case Some(limitValue) if limitValue > maxResults =>
          Future.failed(ExternalReportsMaxPageSizeExceeded(maxResults))
        case a => Future.successful(a)
      }
      validLimit = limit.orElse(Some(maxResults))
      validOffset = offset.orElse(Some(0L))
      paginatedReports <-
        reportRepository.getReports(
          filter,
          validOffset,
          validLimit
        )
      reportFilesMap <- reportRepository.prefetchReportsFiles(paginatedReports.entities.map(_.id))
    } yield paginatedReports.copy(entities = paginatedReports.entities.map(r => toApi(r, reportFilesMap)))
  }

  def countByDepartments(start: Option[LocalDate], end: Option[LocalDate]): Future[Seq[(String, Int)]] =
    reportRepository.countByDepartments(start, end)
}
