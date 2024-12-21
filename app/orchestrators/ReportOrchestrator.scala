package orchestrators

import org.apache.pekko.Done
import cats.data.NonEmptyList
import cats.data.OptionT
import cats.implicits.catsSyntaxMonadError
import cats.implicits.catsSyntaxOption
import cats.implicits.toTraverseOps
import config.EmailConfiguration
import config.SignalConsoConfiguration
import config.TokenConfiguration
import controllers.error.AppError
import controllers.error.AppError._
import models.UserRole.Professionnel
import models._
import models.company.AccessLevel
import models.company.Address
import models.company.Company
import models.event.Event
import models.event.Event._
import models.engagement.Engagement
import models.engagement.Engagement.EngagementReminderPeriod
import models.engagement.EngagementId
import models.report.ReportStatus.{SuppressionRGPD, hasResponse}
import models.report.ReportWordOccurrence.StopWords
import models.report._
import models.report.reportmetadata.ReportExtra
import models.report.reportmetadata.ReportWithMetadataAndBookmark
import models.token.TokenKind.CompanyInit
import models.website.Website
import orchestrators.ReportOrchestrator.ReportCompanyChangeThresholdInDays
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import repositories.accesstoken.AccessTokenRepositoryInterface
import repositories.blacklistedemails.BlacklistedEmailsRepositoryInterface
import repositories.company.CompanyRepositoryInterface
import repositories.event.EventFilter
import repositories.event.EventRepositoryInterface
import repositories.engagement.EngagementRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.reportmetadata.ReportMetadataRepositoryInterface
import repositories.socialnetwork.SocialNetworkRepositoryInterface
import repositories.subcategorylabel.SubcategoryLabel
import repositories.user.UserRepositoryInterface
import repositories.website.WebsiteRepositoryInterface
import services.emails.EmailDefinitionsConsumer.ConsumerProResponseNotification
import services.emails.EmailDefinitionsConsumer.ConsumerReportAcknowledgment
import services.emails.EmailDefinitionsConsumer.ConsumerReportReadByProNotification
import services.emails.EmailDefinitionsPro.ProNewReportNotification
import services.emails.EmailDefinitionsPro.ProResponseAcknowledgment
import services.emails.MailServiceInterface
import tasks.company.CompanySyncServiceInterface
import utils.Constants.ActionEvent._
import utils.Constants.ActionEvent
import utils.Constants.EventType
import utils.Logs.RichLogger
import utils._

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.Period
import java.time.ZoneOffset
import java.time.temporal.TemporalAmount
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

class ReportOrchestrator(
    mailService: MailServiceInterface,
    reportConsumerReviewOrchestrator: ReportConsumerReviewOrchestrator,
    reportRepository: ReportRepositoryInterface,
    reportMetadataRepository: ReportMetadataRepositoryInterface,
    reportFileOrchestrator: ReportFileOrchestrator,
    companyRepository: CompanyRepositoryInterface,
    socialNetworkRepository: SocialNetworkRepositoryInterface,
    accessTokenRepository: AccessTokenRepositoryInterface,
    eventRepository: EventRepositoryInterface,
    userRepository: UserRepositoryInterface,
    websiteRepository: WebsiteRepositoryInterface,
    companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    emailNotificationOrchestrator: EmailNotificationOrchestrator,
    blacklistedEmailsRepository: BlacklistedEmailsRepositoryInterface,
    emailValidationOrchestrator: EmailValidationOrchestrator,
    emailConfiguration: EmailConfiguration,
    tokenConfiguration: TokenConfiguration,
    signalConsoConfiguration: SignalConsoConfiguration,
    companySyncService: CompanySyncServiceInterface,
    engagementRepository: EngagementRepositoryInterface,
    engagementOrchestrator: EngagementOrchestrator,
    messagesApi: MessagesApi
)(implicit val executionContext: ExecutionContext) {
  val logger = Logger(this.getClass)

  // On envoi tous les signalements concernant une gare à la SNCF pour le moment (on changera lors de la privatisation)
  // L'entité responsable des gares à la SNCF est SNCF Gares & connections https://annuaire-entreprises.data.gouv.fr/entreprise/sncf-gares-connexions-507523801
  private val SncfGaresEtConnexionsSIRET: SIRET = SIRET("50752380102157")
  // On envoi tous les signalements concernant un train de la SNCF au SIRET SNCF Voyageurs
  private val SncfVoyageursSIRET: SIRET = SIRET("51903758408747")
  private val TrenitaliaSIRET: SIRET    = SIRET("52028700400078")

  implicit val timeout: org.apache.pekko.util.Timeout = 5.seconds

  private def genActivationToken(companyId: UUID, validity: Option[TemporalAmount]): Future[String] =
    for {
      existingToken <- accessTokenRepository.fetchValidActivationToken(companyId)
      _ <- existingToken
        .map(accessTokenRepository.updateToken(_, AccessLevel.ADMIN, validity))
        .getOrElse(Future.successful(None))
      token <- existingToken
        .map(Future.successful)
        .getOrElse(
          accessTokenRepository.create(
            AccessToken.build(
              kind = CompanyInit,
              token = f"${Random.nextInt(1000000)}%06d",
              validity = validity,
              companyId = Some(companyId),
              level = Some(AccessLevel.ADMIN)
            )
          )
        )
    } yield token.token

  private def notifyProfessionalOfNewReportAndUpdateStatus(report: Report, company: Company): Future[Report] =
    for {
      maybeCompanyUsers <- companiesVisibilityOrchestrator
        .fetchUsersWithHeadOffices(company.siret)
        .map(NonEmptyList.fromList)
      updatedReport <- maybeCompanyUsers match {

        case Some(companyUsers) =>
          logger.debug("Found user, sending notification")
          val companyUserEmails: NonEmptyList[EmailAddress] = companyUsers.map(_.email)
          for {

            _ <- mailService.send(ProNewReportNotification.Email(companyUserEmails, report))
            reportWithUpdatedStatus <- reportRepository.update(
              report.id,
              report.copy(status = ReportStatus.TraitementEnCours)
            )
            _ <- createEmailProNewReportEvent(report, company, companyUsers)
          } yield reportWithUpdatedStatus
        case None =>
          logger.debug("No user found, generating activation token")
          genActivationToken(company.id, tokenConfiguration.companyInitDuration).map(_ => report)
      }
    } yield updatedReport

  private def createEmailProNewReportEvent(report: Report, company: Company, companyUsers: NonEmptyList[User]) =
    eventRepository
      .create(
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
      companyCountry: Option[Country],
      websiteURLOpt: Option[URL]
  ): Future[Option[Website]] = {
    val maybeWebsite: Option[Website] = for {
      websiteUrl <- websiteURLOpt
      host       <- websiteUrl.getHost
    } yield Website(host = host, companyCountry = companyCountry.map(_.code.value), companyId = companyOpt.map(_.id))

    maybeWebsite.map { website =>
      logger.debug("Creating website entry")
      websiteRepository.validateAndCreate(website)
    }.sequence
  }

  def validateAndCreateReport(draftReport: ReportDraft): Future[Report] =
    for {
      _             <- validateCompany(draftReport)
      _             <- validateSpamSimilarReport(draftReport)
      _             <- validateReportIdentification(draftReport)
      _             <- validateConsumerEmail(draftReport)
      _             <- validateNumberOfAttachments(draftReport)
      createdReport <- createReport(draftReport)
    } yield createdReport

  private def validateReportIdentification(draftReport: ReportDraft) =
    if (ReportDraft.isValid(draftReport)) {
      Future.unit
    } else {
      Future.failed(ReportCreationInvalidBody)
    }

  private def validateNumberOfAttachments(draftReport: ReportDraft) =
    if (draftReport.fileIds.length <= signalConsoConfiguration.reportMaxNumberOfAttachments) {
      Future.unit
    } else {
      Future.failed(
        TooManyAttachments(signalConsoConfiguration.reportMaxNumberOfAttachments, draftReport.fileIds.length)
      )
    }

  private def validateConsumerEmail(draftReport: ReportDraft) = for {
    _ <- emailValidationOrchestrator.validateProvider(draftReport.email)
    _ <- emailValidationOrchestrator
      .isEmailValid(draftReport.email)
      .ensure {
        logger.warn(s"Email ${draftReport.email} is not valid, abort report creation")
        InvalidEmail(draftReport.email.value)
      }(isValid => isValid || emailConfiguration.skipReportEmailValidation)
    _ <- validateReportSpammerBlockList(draftReport.email)
  } yield ()

  private[orchestrators] def validateSpamSimilarReport(draftReport: ReportDraft): Future[Unit] = {
    logger.debug(s"Checking if similar report have been submitted")

    val startOfDay  = LocalDate.now().atStartOfDay().atOffset(ZoneOffset.UTC)
    val startOfWeek = LocalDate.now().minusDays(7).atStartOfDay().atOffset(ZoneOffset.UTC)

    val MAX_SIMILAR_CONSUMER_COMPANY_REPORT_WHITHIN_A_WEEK = 4
    val MAX_SIMILAR_CONSUMER_COMPANY_REPORT_WHITHIN_A_DAY  = 2

    reportRepository
      .findSimilarReportList(draftReport, after = startOfWeek, emailConfiguration.extendedComparison)
      .map { reportList =>
        val exactSameReportList =
          reportList
            .filter(r => r.creationDate.isAfter(startOfDay) || r.creationDate.isEqual(startOfDay))
            .filter(_.details.containsSlice(draftReport.details))
            .filter(_.firstName == draftReport.firstName)
            .filter(_.lastName == draftReport.lastName)

        val reportsWithSameUserAndCompanyTodayList =
          reportList.filter(r => r.creationDate.isAfter(startOfDay) || r.creationDate.isEqual(startOfDay))

        val reportsWithSameUserAndCompanyThisWeek =
          reportList.filter(r => r.creationDate.isAfter(startOfWeek) || r.creationDate.isEqual(startOfWeek))

        if (exactSameReportList.nonEmpty) {
          throw DuplicateReportCreation(exactSameReportList)
        } else if (
          reportsWithSameUserAndCompanyTodayList.size > MAX_SIMILAR_CONSUMER_COMPANY_REPORT_WHITHIN_A_DAY - 1
        ) {
          throw DuplicateReportCreation(reportsWithSameUserAndCompanyTodayList)
        } else if (
          reportsWithSameUserAndCompanyThisWeek.size > MAX_SIMILAR_CONSUMER_COMPANY_REPORT_WHITHIN_A_WEEK - 1
        ) {
          throw DuplicateReportCreation(reportsWithSameUserAndCompanyThisWeek)
        } else ()
      }

  }

  private def validateReportSpammerBlockList(emailAddress: EmailAddress) =
    for {
      blacklistFromDb <- blacklistedEmailsRepository.list()
    } yield {
      val fullBlacklist = blacklistFromDb.map(_.email)
      // Small optimisation to only computed the splitted email once
      // Instead of multiple times in the exists loop
      val splittedEmailAddress = emailAddress.split

      if (
        emailConfiguration.extendedComparison &&
        fullBlacklist.exists(blacklistedEmail => splittedEmailAddress.isEquivalentTo(blacklistedEmail))
      ) {
        throw SpammerEmailBlocked(emailAddress)
      } else if (fullBlacklist.contains(emailAddress.value)) {
        throw SpammerEmailBlocked(emailAddress)
      } else ()
    }

  private[orchestrators] def validateCompany(reportDraft: ReportDraft): Future[Done.type] = {

    def validateSiretExistsOnEntrepriseApi(siret: SIRET) = companySyncService
      .companyBySiret(siret)
      .recoverWith { case error =>
        // We should accept the reports anyway if there is something wrong during the process
        logger
          .warnWithTitle("report_company_check_error", "Unable to check company siret on company service", error)
        Future.successful(Some(()))
      }
      .flatMap {
        case Some(_) => Future.unit
        case None    => Future.failed(CompanySiretNotFound(siret))
      }

    for {
      _ <- reportDraft.companyActivityCode match {
        // 84 correspond à l'administration publique, sauf quelques cas particuliers :
        // - 84.30B : Complémentaires retraites donc pas publique
        case Some(activityCode) if activityCode.startsWith("84.") && activityCode != "84.30B" =>
          Future.failed(AppError.CannotReportPublicAdministration)
        case _ => Future.unit
      }
      _ <- reportDraft.companySiret match {
        case Some(siret) =>
          // Try to check if siret exist in signal conso database
          companyRepository.findBySiret(siret).flatMap {
            case Some(_) => Future.unit
            case None    =>
              // If not found we check using SignalConsoEntrepriseApi
              validateSiretExistsOnEntrepriseApi(siret)
          }
        case None => Future.unit
      }
    } yield Done
  }

  def createReport(draftReport: ReportDraft): Future[Report] =
    for {
      maybeCompany <- extractOptionalCompany(draftReport)
      maybeCountry = extractOptionalCountry(draftReport)
      _ <- createReportedWebsite(maybeCompany, maybeCountry, draftReport.websiteURL)
      maybeCompanyWithUsers <- maybeCompany.traverse(company =>
        companiesVisibilityOrchestrator.fetchUsersWithHeadOffices(company.siret)
      )
      reportCreationDate = OffsetDateTime.now()
      expirationDate = chooseExpirationDate(
        baseDate = reportCreationDate,
        companyHasUsers = maybeCompanyWithUsers.exists(_.nonEmpty)
      )
      reportToCreate = draftReport.generateReport(
        maybeCompany.map(_.id),
        maybeCompany,
        reportCreationDate,
        expirationDate
      )
      report <- reportRepository.create(reportToCreate)
      _ = logger.debug(s"Created report with id ${report.id}")
      _             <- createReportMetadata(draftReport, report)
      files         <- reportFileOrchestrator.attachFilesToReport(draftReport.fileIds, report.id)
      updatedReport <- notifyProfessionalIfNeeded(maybeCompany, report)
      _             <- emailNotificationOrchestrator.notifyDgccrfIfNeeded(updatedReport)
      _             <- notifyConsumer(updatedReport, maybeCompany, files)
      _ = logger.debug(s"Report ${updatedReport.id} created")
    } yield updatedReport

  def createReportMetadata(draftReport: ReportDraft, createdReport: Report): Future[Any] =
    draftReport.metadata
      .map { metadataDraft =>
        val metadata = metadataDraft.toReportMetadata(reportId = createdReport.id)
        reportMetadataRepository.create(metadata)
      }
      .getOrElse(Future.unit)

  def createFakeReportForBlacklistedUser(draftReport: ReportDraft): Report = {
    val maybeCompanyId     = draftReport.companySiret.map(_ => UUID.randomUUID())
    val reportCreationDate = OffsetDateTime.now()
    draftReport.generateReport(maybeCompanyId, None, reportCreationDate, reportCreationDate)
  }

  def chooseExpirationDate(
      baseDate: OffsetDateTime,
      companyHasUsers: Boolean
  ): OffsetDateTime = {
    val delayIfCompanyHasNoUsers = Period.ofDays(60)
    val delayIfCompanyHasUsers   = Period.ofDays(25)
    val delay                    = if (companyHasUsers) delayIfCompanyHasUsers else delayIfCompanyHasNoUsers
    baseDate.plus(delay)
  }

  private def notifyConsumer(report: Report, maybeCompany: Option[Company], reportAttachements: List[ReportFile]) = {
    val event = Event(
      UUID.randomUUID(),
      Some(report.id),
      maybeCompany.map(_.id),
      None,
      OffsetDateTime.now(),
      Constants.EventType.CONSO,
      Constants.ActionEvent.EMAIL_CONSUMER_ACKNOWLEDGMENT
    )
    for {
      _ <- mailService.send(
        ConsumerReportAcknowledgment.Email(report, maybeCompany, event, reportAttachements, messagesApi)
      )
      _ <- eventRepository.create(event)
    } yield ()
  }

  private def notifyProfessionalIfNeeded(maybeCompany: Option[Company], report: Report) =
    (report.status, maybeCompany) match {
      case (ReportStatus.TraitementEnCours, Some(company)) =>
        val reportNotificationThresholdPeriod = OffsetDateTime.now().minusHours(1)
        val maxReportNotificationThreshold    = 10
        for {
          events <- eventRepository
            .fetchEventFromActionEvents(company.id, EMAIL_PRO_NEW_REPORT)
          companyNotificationWithinPeriod = events.count(_.creationDate.isAfter(reportNotificationThresholdPeriod))
          report <-
            if (companyNotificationWithinPeriod > maxReportNotificationThreshold) {
              logger.debug(
                "Do not send notification email, company users have been notified about 1 hour ago for another report we don't want to spam the company users."
              )
              Future.successful(report)
            } else {
              notifyProfessionalOfNewReportAndUpdateStatus(report, company)
            }
        } yield report

      case _ => Future.successful(report)
    }

  private def extractOptionalCountry(draftReport: ReportDraft) =
    draftReport.companyAddress.flatMap(_.country.map { country =>
      logger.debug(s"Found country ${country} from draft report")
      country
    })

  private def extractOptionalCompany(draftReport: ReportDraft): Future[Option[Company]] =
    OptionT(extractOptionalCompanyFromDraft(draftReport))
      .orElse(OptionT(extractCompanyOfSocialNetwork(draftReport)))
      .orElse(OptionT(extractCompanyOfTrain(draftReport)))
      .orElse(OptionT(extractCompanyOfStation(draftReport)))
      .value

  private def extractOptionalCompanyFromDraft(draftReport: ReportDraft): Future[Option[Company]] =
    draftReport.companySiret match {
      case Some(siret) =>
        val company = Company(
          siret = siret,
          name = draftReport.companyName.get,
          address = draftReport.companyAddress.get,
          activityCode = draftReport.companyActivityCode,
          isHeadOffice = draftReport.companyIsHeadOffice.getOrElse(false),
          isOpen = draftReport.companyIsOpen.getOrElse(true),
          isPublic = {
            if (draftReport.companyIsPublic.isEmpty) {
              logger.error(s"draftReport.companyIsPublic should not be empty, company details (siret:  $siret)")
            }
            draftReport.companyIsPublic.getOrElse(false)
          },
          brand = draftReport.companyBrand,
          commercialName = draftReport.companyCommercialName,
          establishmentCommercialName = draftReport.companyEstablishmentCommercialName
        )
        companyRepository.getOrCreate(siret, company).map { company =>
          logger.debug("Company extracted from report")
          Some(company)
        }
      case None =>
        logger.debug("No company attached to report")
        Future.successful(None)
    }

  private def searchCompanyOfSocialNetwork(socialNetworkSlug: SocialNetworkSlug): Future[Option[Company]] =
    (for {
      socialNetwork   <- OptionT(socialNetworkRepository.get(socialNetworkSlug))
      companyToCreate <- OptionT(companySyncService.companyBySiret(socialNetwork.siret))
      c = Company(
        siret = companyToCreate.siret,
        name = companyToCreate.name.getOrElse(""),
        address = companyToCreate.address,
        activityCode = companyToCreate.activityCode,
        isHeadOffice = companyToCreate.isHeadOffice,
        isOpen = companyToCreate.isOpen,
        isPublic = companyToCreate.isPublic,
        brand = companyToCreate.brand,
        commercialName = companyToCreate.commercialName,
        establishmentCommercialName = companyToCreate.establishmentCommercialName
      )
      company <- OptionT.liftF(companyRepository.getOrCreate(companyToCreate.siret, c))
    } yield company).value

  private def extractCompanyOfSocialNetwork(reportDraft: ReportDraft): Future[Option[Company]] =
    reportDraft.influencer.flatMap(_.socialNetwork) match {
      case Some(socialNetwork) =>
        for {
          maybeCompany <- socialNetworkRepository.findCompanyBySocialNetworkSlug(socialNetwork)
          resultingCompany <-
            if (maybeCompany.isDefined) Future.successful(maybeCompany) else searchCompanyOfSocialNetwork(socialNetwork)
        } yield resultingCompany
      case None =>
        Future.successful(None)
    }

  private def extractCompanyOfStation(reportDraft: ReportDraft): Future[Option[Company]] =
    reportDraft.station match {
      case Some(_) =>
        (for {
          companyToCreate <- OptionT(companySyncService.companyBySiret(SncfGaresEtConnexionsSIRET))
          c = Company(
            siret = companyToCreate.siret,
            name = companyToCreate.name.getOrElse(""),
            address = companyToCreate.address,
            activityCode = companyToCreate.activityCode,
            isHeadOffice = companyToCreate.isHeadOffice,
            isOpen = companyToCreate.isOpen,
            isPublic = companyToCreate.isPublic,
            brand = companyToCreate.brand,
            commercialName = companyToCreate.commercialName,
            establishmentCommercialName = companyToCreate.establishmentCommercialName
          )
          company <- OptionT.liftF(companyRepository.getOrCreate(companyToCreate.siret, c))
        } yield company).value
      case None =>
        Future.successful(None)
    }

  private def extractCompanyOfTrain(reportDraft: ReportDraft): Future[Option[Company]] =
    reportDraft.train match {
      case Some(Train(train, _, _)) =>
        val trainSiret = train match {
          case "TRENITALIA" => TrenitaliaSIRET
          case _            => SncfVoyageursSIRET
        }
        (for {
          companyToCreate <- OptionT(companySyncService.companyBySiret(trainSiret))
          c = Company(
            siret = companyToCreate.siret,
            name = companyToCreate.name.getOrElse(""),
            address = companyToCreate.address,
            activityCode = companyToCreate.activityCode,
            isHeadOffice = companyToCreate.isHeadOffice,
            isOpen = companyToCreate.isOpen,
            isPublic = companyToCreate.isPublic,
            brand = companyToCreate.brand,
            commercialName = companyToCreate.commercialName,
            establishmentCommercialName = companyToCreate.establishmentCommercialName
          )
          company <- OptionT.liftF(companyRepository.getOrCreate(companyToCreate.siret, c))
        } yield company).value
      case None =>
        Future.successful(None)
    }

  def updateReportCountry(reportId: UUID, countryCode: String, userId: UUID): Future[Option[Report]] =
    for {
      existingReport <- reportRepository.get(reportId)
      country           = Country.fromCode(countryCode)
      updateCompanyDate = OffsetDateTime.now()

      reportWithNewData <- existingReport match {
        case Some(report) =>
          reportRepository
            .update(
              report.id,
              report.copy(
                companyId = None,
                companyName = None,
                companyAddress = Address(country = Some(country)),
                companySiret = None,
                status = Report.initialStatus(
                  employeeConsumer = report.employeeConsumer,
                  visibleToPro = report.visibleToPro,
                  companySiret = None,
                  companyCountry = Some(country)
                )
              )
            )
            .map(Some(_))
        case _ => Future.successful(None)
      }

      _ <- existingReport match {
        case Some(report) =>
          eventRepository
            .create(
              Event(
                UUID.randomUUID(),
                Some(report.id),
                None,
                Some(userId),
                updateCompanyDate,
                Constants.EventType.ADMIN,
                Constants.ActionEvent.REPORT_COUNTRY_CHANGE,
                stringToDetailsJsValue(
                  s"Entreprise ou pays précédent : Siret ${report.companySiret
                      .getOrElse("non renseigné")} - ${Some(report.companyAddress.toString).filter(_ != "").getOrElse("Adresse non renseignée")}. Nouveau pays : ${country.name}"
                )
              )
            )
            .map(Some(_))
        case _ => Future.successful(None)
      }
      _ <- existingReport.flatMap(_.companyId).map(id => removeAccessTokenWhenNoMoreReports(id)).getOrElse(Future.unit)
    } yield reportWithNewData

  private def isReportTooOld(report: Report) =
    report.creationDate.isBefore(OffsetDateTime.now().minusDays(ReportCompanyChangeThresholdInDays))

  def updateReportCompanyIfRecent(
      reportId: UUID,
      reportCompany: ReportCompany,
      requestingUserId: UUID
  ): Future[Report] =
    for {
      existingReport <- reportRepository.get(reportId).flatMap {
        case Some(report) => Future.successful(report)
        case None         => Future.failed(ReportNotFound(reportId))
      }
      _ <- if (isReportTooOld(existingReport)) Future.failed(ReportTooOldToChangeCompany) else Future.unit
      _ <- if (hasResponse(existingReport)) Future.failed(ReportIsInFinalStatus) else Future.unit
      updatedReport <- updateReportCompany(
        existingReport,
        reportCompany,
        requestingUserId
      )
    } yield updatedReport

  def updateReportCompanyForWebsite(
      existingReport: Report,
      reportCompany: ReportCompany,
      adminUserId: UUID
  ) =
    if (isReportTooOld(existingReport)) {
      logger.debug(s"Report ${existingReport.id} is too old to be updated")
      Future.unit
    } else
      updateReportCompany(existingReport, reportCompany, adminUserId).map(_ => ())

  private def updateReportCompany(
      existingReport: Report,
      reportCompany: ReportCompany,
      adminUserId: UUID
  ): Future[Report] = {
    val updateDateTime = OffsetDateTime.now()

    val newReportStatus = Report.initialStatus(
      employeeConsumer = existingReport.employeeConsumer,
      visibleToPro = existingReport.visibleToPro,
      companySiret = Some(reportCompany.siret),
      companyCountry = reportCompany.address.country
    )
    val isSameCompany = existingReport.companySiret.contains(reportCompany.siret)
    for {
      _       <- if (isSameCompany) Future.failed(CannotAlreadyAssociatedToReport(reportCompany.siret)) else Future.unit
      company <- companyRepository.getOrCreate(reportCompany.siret, reportCompany.toCompany)
      newExpirationDate <-
        if (newReportStatus.isNotFinal) {
          companiesVisibilityOrchestrator
            .fetchUsersWithHeadOffices(company.siret)
            .map { companyAndHeadOfficeUsers =>
              chooseExpirationDate(baseDate = updateDateTime, companyHasUsers = companyAndHeadOfficeUsers.nonEmpty)
            }
        } else Future.successful(existingReport.expirationDate)

      reportWithNewCompany <- reportRepository.update(
        existingReport.id,
        existingReport.copy(
          companyId = Some(company.id),
          companyName = Some(company.name),
          companyAddress = company.address,
          companySiret = Some(company.siret),
          status = newReportStatus,
          expirationDate = newExpirationDate
        )
      )
      // Notify the pro if needed
      updatedReport <-
        if (
          reportWithNewCompany.status == ReportStatus.TraitementEnCours &&
          reportWithNewCompany.companySiret.isDefined &&
          reportWithNewCompany.companySiret != existingReport.companySiret
        ) {
          notifyProfessionalOfNewReportAndUpdateStatus(reportWithNewCompany, company)
        } else Future.successful(reportWithNewCompany)

      _ <-
        eventRepository
          .create(
            Event(
              UUID.randomUUID(),
              Some(updatedReport.id),
              Some(company.id),
              Some(adminUserId),
              updateDateTime,
              Constants.EventType.ADMIN,
              Constants.ActionEvent.REPORT_COMPANY_CHANGE,
              stringToDetailsJsValue(
                s"Entreprise précédente : Siret ${existingReport.companySiret
                    .getOrElse("non renseigné")} - ${Some(existingReport.companyAddress.toString).filter(_ != "").getOrElse("Adresse non renseignée")}"
              )
            )
          )

      _ <- updatedReport.companyId.map(id => removeAccessTokenWhenNoMoreReports(id)).getOrElse(Future.successful(()))
    } yield updatedReport
  }

  def updateReportConsumer(
      reportId: UUID,
      reportConsumer: ReportConsumerUpdate,
      userUUID: UUID
  ): Future[Option[Report]] =
    for {
      existingReport <- reportRepository.get(reportId)
      updatedReport <- existingReport match {
        case Some(report) =>
          reportRepository
            .update(
              report.id,
              report.copy(
                firstName = reportConsumer.firstName,
                lastName = reportConsumer.lastName,
                email = reportConsumer.email,
                consumerReferenceNumber = reportConsumer.consumerReferenceNumber
              )
            )
            .map(Some(_))
        case _ => Future.successful(None)
      }
      _ <- existingReport match {
        case Some(report) =>
          eventRepository
            .create(
              Event(
                UUID.randomUUID(),
                Some(report.id),
                report.companyId,
                Some(userUUID),
                OffsetDateTime.now(),
                Constants.EventType.ADMIN,
                Constants.ActionEvent.REPORT_CONSUMER_CHANGE,
                stringToDetailsJsValue(
                  s"Consommateur précédent : ${report.firstName} ${report.lastName} - ${report.email}" +
                    report.consumerReferenceNumber.map(nb => s" - ref $nb").getOrElse("") +
                    s"- Accord pour contact : ${if (report.contactAgreement) "oui" else "non"}"
                )
              )
            )
            .map(Some(_))
        case _ => Future.successful(None)
      }
    } yield updatedReport

  def handleReportView(
      reportExtra: ReportExtra,
      user: User
  ): Future[ReportExtra] =
    if (
      user.userRole == UserRole.Professionnel && user.impersonator.isEmpty && reportExtra.report.status != SuppressionRGPD
    ) {
      val report = reportExtra.report
      eventRepository
        .getEvents(report.id, EventFilter(None))
        .flatMap(events =>
          if (!events.exists(_.action == Constants.ActionEvent.REPORT_READING_BY_PRO)) {
            for {
              viewedReport <- manageFirstViewOfReportByPro(report, user.id)
              viewedReportWithMetadata = reportExtra.copy(report = viewedReport)
            } yield viewedReportWithMetadata
          } else {
            Future.successful(reportExtra)
          }
        )
    } else {
      Future.successful(reportExtra)
    }

  def removeAccessTokenWhenNoMoreReports(companyId: UUID) =
    for {
      company <- companyRepository.get(companyId)
      reports <- company
        .map(c =>
          reportRepository
            .getReports(None, ReportFilter(companyIds = Seq(c.id)), None, None, None, None)
            .map(_.entities)
        )
        .getOrElse(Future.successful(Nil))
      cnt <- if (reports.isEmpty) accessTokenRepository.removePendingTokens(company.get) else Future.successful(0)
    } yield {
      logger.debug(s"Removed ${cnt} tokens for company ${companyId}")
      ()
    }

  private def manageFirstViewOfReportByPro(report: Report, userUUID: UUID) =
    for {
      _ <- eventRepository.create(
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
      isReportAlreadyClosed = report.status.isFinal
      updatedReport <-
        if (isReportAlreadyClosed) {
          Future.successful(report)
        } else {
          updateReportAndEventuallyNotifyConsumer(report)
        }
    } yield updatedReport

  private def updateReportAndEventuallyNotifyConsumer(report: Report): Future[Report] =
    for {
      newReport <- reportRepository.update(report.id, report.copy(status = ReportStatus.Transmis))
      hasReportBeenReopened = report.reopenDate.isDefined
      // We don't want the consumer to be notified when a pro is requesting a report reopening.
      // The consumer will only be notified when the pro will reply.
      _ <- if (!hasReportBeenReopened) notifyConsumer(report) else Future.successful(())
    } yield newReport

  private def notifyConsumer(report: Report) = for {
    maybeCompany <- report.companySiret.map(companyRepository.findBySiret(_)).flatSequence
    _            <- mailService.send(ConsumerReportReadByProNotification.Email(report, maybeCompany, messagesApi))
    _ <- eventRepository.create(
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
  } yield ()

  private def sendMailsAfterProAcknowledgment(
      report: Report,
      reportResponse: IncomingReportResponse,
      user: User,
      maybeCompany: Option[Company]
  ) = {
    val existingReportResponse = reportResponse.toExisting
    for {
      _ <- mailService.send(ProResponseAcknowledgment.Email(report, existingReportResponse, user))
      _ <- mailService.send(
        ConsumerProResponseNotification.Email(report, existingReportResponse, maybeCompany, messagesApi)
      )
    } yield ()
  }

  def handleReportResponse(report: Report, reportResponse: IncomingReportResponse, user: User): Future[Report] = {
    logger.debug(s"handleReportResponse ${reportResponse.responseType}")
    val now = OffsetDateTime.now()
    for {
      _ <- reportFileOrchestrator.attachFilesToReport(reportResponse.fileIds, report.id)
      updatedReport <- reportRepository.update(
        report.id,
        report.copy(
          status = ReportStatus.fromResponseType(reportResponse.responseType)
        )
      )
      maybeCompany <- report.companySiret.map(companyRepository.findBySiret).flatSequence
      responseEvent <- eventRepository.create(
        Event(
          UUID.randomUUID(),
          Some(report.id),
          report.companyId,
          Some(user.id),
          now,
          EventType.PRO,
          ActionEvent.REPORT_PRO_RESPONSE,
          Json.toJson(reportResponse)
        )
      )
      _ <- reportResponse.responseType match {
        case ReportResponseType.ACCEPTED =>
          engagementRepository.create(
            Engagement(
              id = EngagementId(UUID.randomUUID()),
              reportId = report.id,
              promiseEventId = responseEvent.id,
              resolutionEventId = None,
              expirationDate = now.plusDays(EngagementReminderPeriod.toLong)
            )
          )
        case _ => Future.unit
      }
      _ <- sendMailsAfterProAcknowledgment(updatedReport, reportResponse, user, maybeCompany)
      _ <- eventRepository.create(
        Event(
          UUID.randomUUID(),
          Some(report.id),
          updatedReport.companyId,
          None,
          now,
          Constants.EventType.CONSO,
          Constants.ActionEvent.EMAIL_CONSUMER_REPORT_RESPONSE
        )
      )
      _ <- eventRepository.create(
        Event(
          UUID.randomUUID(),
          Some(report.id),
          updatedReport.companyId,
          Some(user.id),
          now,
          Constants.EventType.PRO,
          Constants.ActionEvent.EMAIL_PRO_RESPONSE_ACKNOWLEDGMENT
        )
      )
    } yield updatedReport
  }

  def handleReportAction(report: Report, reportAction: ReportAction, user: User): Future[Event] =
    for {
      _ <- reportFileOrchestrator.attachFilesToReport(reportAction.fileIds, report.id)
      newEvent <- eventRepository.create(
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
    } yield {
      logger.debug(
        s"Create event ${newEvent.id} on report ${report.id} for reportActionType ${reportAction.actionType}"
      )
      newEvent
    }

  def getReportsForUser(
      connectedUser: User,
      filter: ReportFilter,
      offset: Option[Long],
      limit: Option[Int],
      sortBy: Option[ReportSort],
      orderBy: Option[SortOrder],
      maxResults: Int
  ): Future[PaginatedResult[ReportWithFiles]] =
    for {
      sanitizedSirenSirets <- companiesVisibilityOrchestrator.filterUnauthorizedSiretSirenList(
        filter.siretSirenList,
        connectedUser
      )
      _ = logger.trace(
        s"Original sirenSirets : ${filter.siretSirenList} , SanitizedSirenSirets : $sanitizedSirenSirets"
      )
      paginatedReportFiles <-
        if (sanitizedSirenSirets.isEmpty && connectedUser.userRole == UserRole.Professionnel) {
          Future.successful(
            PaginatedResult(totalCount = 0, hasNextPage = false, entities = List.empty[ReportWithFiles])
          )
        } else {
          getReportsWithFile[ReportWithFiles](
            Some(connectedUser),
            filter.copy(siretSirenList = sanitizedSirenSirets),
            offset,
            limit,
            sortBy,
            orderBy,
            maxResults,
            (r: ReportWithMetadataAndBookmark, m: Map[UUID, List[ReportFile]]) =>
              ReportWithFiles(
                SubcategoryLabel.translateSubcategories(r.report, r.subcategoryLabel),
                r.metadata,
                r.bookmark.isDefined,
                m.getOrElse(r.report.id, Nil)
              )
          )
        }
    } yield paginatedReportFiles

  def getReportsWithResponsesForUser(
      connectedUser: User,
      filter: ReportFilter,
      offset: Option[Long],
      limit: Option[Int],
      sortBy: Option[ReportSort],
      orderBy: Option[SortOrder]
  ): Future[PaginatedResult[ReportWithFilesAndResponses]] = {

    val filterByReportProResponse = EventFilter(None, Some(ActionEvent.REPORT_PRO_RESPONSE))
    for {
      reportsWithFiles <- getReportsForUser(
        connectedUser,
        filter,
        offset,
        limit,
        sortBy,
        orderBy,
        signalConsoConfiguration.reportsListLimitMax
      )

      reports   = reportsWithFiles.entities.map(_.report)
      reportsId = reports.map(_.id)

      reportEventsMap <- eventRepository
        .getEventsWithUsers(reportsId, filterByReportProResponse)
        .map(events =>
          events.collect { case (event @ Event(_, Some(reportId), _, _, _, _, _, _), user) =>
            (reportId, EventWithUser(event, user))
          }.toMap
        )

      assignedUsersIds = reportsWithFiles.entities.flatMap(_.metadata.flatMap(_.assignedUserId))
      assignedUsers      <- userRepository.findByIds(assignedUsersIds)
      consumerReviewsMap <- reportConsumerReviewOrchestrator.find(reportsId)
      engagementReviews  <- engagementOrchestrator.findEngagementReviews(reportsId)
    } yield reportsWithFiles.copy(
      entities = reportsWithFiles.entities.map { reportWithFiles =>
        val maybeAssignedUserId = reportWithFiles.metadata.flatMap(_.assignedUserId)
        val reportId            = reportWithFiles.report.id
        ReportWithFilesAndResponses(
          reportWithFiles.report,
          reportWithFiles.metadata,
          reportWithFiles.isBookmarked,
          assignedUser = assignedUsers.find(u => maybeAssignedUserId.contains(u.id)).map(MinimalUser.fromUser),
          reportWithFiles.files,
          consumerReviewsMap.getOrElse(reportId, None),
          engagementReviews.getOrElse(reportId, None),
          reportEventsMap.get(reportId)
        )
      }
    )
  }

  def getReportsWithFile[T](
      user: Option[User],
      filter: ReportFilter,
      offset: Option[Long],
      limit: Option[Int],
      sortBy: Option[ReportSort],
      orderBy: Option[SortOrder],
      maxResults: Int,
      toApi: (ReportWithMetadataAndBookmark, Map[UUID, List[ReportFile]]) => T
  ): Future[PaginatedResult[T]] =
    for {
      _ <- limit match {
        case Some(limitValue) if limitValue > maxResults =>
          logger.error(s"Max page size reached $limitValue > $maxResults")
          Future.failed(ExternalReportsMaxPageSizeExceeded(maxResults))
        case a => Future.successful(a)
      }
      validLimit      = limit.orElse(Some(maxResults))
      validOffset     = offset.orElse(Some(0L))
      startGetReports = System.nanoTime()
      _               = logger.trace("----------------  BEGIN  getReports  ------------------")
      paginatedReports <-
        reportRepository.getReports(
          user,
          filter,
          validOffset,
          validLimit,
          sortBy,
          orderBy
        )
      endGetReports = System.nanoTime()
      _ = logger.trace(
        s"----------------  END  getReports ${TimeUnit.MILLISECONDS.convert(endGetReports - startGetReports, TimeUnit.NANOSECONDS)}  ------------------"
      )
      startGetReportFiles = System.nanoTime()
      _                   = logger.trace("----------------  BEGIN  prefetchReportsFiles  ------------------")
      reportsIds          = paginatedReports.entities.map(_.report.id)
      reportFilesMap <- reportFileOrchestrator.prefetchReportsFiles(reportsIds)
      endGetReportFiles = System.nanoTime()
      _ = logger.trace(s"----------------  END  prefetchReportsFiles ${TimeUnit.MILLISECONDS
          .convert(endGetReportFiles - startGetReportFiles, TimeUnit.NANOSECONDS)}  ------------------")
    } yield paginatedReports.mapEntities(r => toApi(r, reportFilesMap))

  def getVisibleReportForUser(reportId: UUID, user: User): Future[Option[ReportExtra]] =
    for {
      reportWithMetadata <- reportRepository.getFor(Some(user), reportId)
      report = reportWithMetadata.map(_.report)
      company <- report.flatMap(_.companyId).map(r => companyRepository.get(r)).flatSequence
      address = Address.merge(company.map(_.address), report.map(_.companyAddress))
      reportExtra = reportWithMetadata.map(r =>
        ReportExtra
          .from(r, company)
          .setAddress(address)
      )
      visibleReportExtra <-
        user.userRole match {
          case UserRole.DGCCRF | UserRole.DGAL | UserRole.SuperAdmin | UserRole.Admin | UserRole.ReadOnlyAdmin =>
            Future.successful(reportExtra)
          case Professionnel =>
            companiesVisibilityOrchestrator
              .fetchVisibleCompanies(user)
              .map(_.map(v => Some(v.company.siret)))
              .map { visibleSirets =>
                reportExtra
                  .filter(r => visibleSirets.contains(r.report.companySiret))
                  .map(_.copy(companyAlbertActivityLabel = None))
              }
        }
    } yield visibleReportExtra

  def getCloudWord(companyId: UUID): Future[List[ReportWordOccurrence]] =
    for {
      maybeCompany      <- companyRepository.get(companyId)
      company           <- maybeCompany.liftTo[Future](AppError.CompanyNotFound(companyId))
      wordOccurenceList <- reportRepository.cloudWord(companyId)
    } yield wordOccurenceList
      .filterNot { wordOccurrence =>
        wordOccurrence.value.exists(_.isDigit) ||
        wordOccurrence.count < 10 ||
        StopWords.contains(wordOccurrence.value) ||
        wordOccurrence.value.contains(company.name.toLowerCase)
      }
      .sortWith(_.count > _.count)
      .slice(0, 10)

}

object ReportOrchestrator {
  val ReportCompanyChangeThresholdInDays: Long = 90L
}
