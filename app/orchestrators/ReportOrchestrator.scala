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
import models._
import models.company.AccessLevel
import models.company.Address
import models.company.Company
import models.event.Event
import models.event.Event._
import models.engagement.Engagement
import models.engagement.Engagement.EngagementReminderPeriod
import models.engagement.EngagementId
import models.report.ReportStatus.SuppressionRGPD
import models.report.ReportStatus.hasResponse
import models.report.ReportWordOccurrence.StopWords
import models.report._
import models.report.reportmetadata.ReportExtra
import models.report.reportmetadata.ReportMetadataDraft
import models.token.TokenKind.CompanyInit
import models.website.Website
import orchestrators.ReportOrchestrator.ReportCompanyChangeThresholdInDays
import orchestrators.ReportOrchestrator.validateNotGouvWebsite
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.libs.json.JsObject
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
import repositories.subcategorylabel.SubcategoryLabelRepositoryInterface
import repositories.website.WebsiteRepositoryInterface
import services.emails.EmailDefinitionsConsumer.ConsumerProResponseNotification
import services.emails.EmailDefinitionsConsumer.ConsumerReportAcknowledgment
import services.emails.EmailDefinitionsConsumer.ConsumerReportReadByProNotification
import services.emails.EmailDefinitionsPro.ProNewReportNotification
import services.emails.EmailDefinitionsPro.ProResponseAcknowledgment
import services.emails.MailServiceInterface
import tasks.company.CompanySearchResult
import tasks.company.CompanySyncServiceInterface
import utils.Constants.ActionEvent._
import utils.Constants.ActionEvent
import utils.Constants.EventType
import utils.Logs.RichLogger
import utils._
import cats.syntax.either._
import repositories.user.UserRepositoryInterface

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.Period
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAmount
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

class ReportOrchestrator(
    mailService: MailServiceInterface,
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
    subcategoryLabelRepository: SubcategoryLabelRepositoryInterface,
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

  def validateAndCreateReport(draft: ReportDraft, consumerIp: ConsumerIp): Future[Report] =
    for {
      _ <- validateCompany(draft)
      _ <- validateNotGouvWebsite(draft)
      _ <- validateSpamSimilarReport(draft)
      _ <- validateReportIdentification(draft)
      _ <- validateConsumerEmail(draft)
      _ <- validateNumberOfAttachments(draft)
      cleanDraft = sanitizeDraft(draft)
      createdReport <- createReport(cleanDraft, consumerIp)
    } yield createdReport

  private def sanitizeDraft(draft: ReportDraft): ReportDraft =
    draft.copy(
      phone = draft.phone.map(PhoneNumberUtils.sanitizeIncomingPhoneNumber),
      consumerPhone = draft.consumerPhone.map(PhoneNumberUtils.sanitizeIncomingPhoneNumber).filterNot(_.isEmpty),
      consumerReferenceNumber = draft.consumerReferenceNumber.map(_.trim).filterNot(_.isEmpty)
    )

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
      // Small optimisation to only computed the splitted email once
      // Instead of multiple times in the exists loop
      splittedEmailAddress <- emailAddress.split.liftTo[Future]
      fullBlacklist = blacklistFromDb.map(_.email)
    } yield
      if (
        emailConfiguration.extendedComparison &&
        fullBlacklist.exists(blacklistedEmail => splittedEmailAddress.isEquivalentTo(blacklistedEmail))
      ) {
        throw SpammerEmailBlocked(emailAddress)
      } else if (fullBlacklist.contains(emailAddress.value)) {
        throw SpammerEmailBlocked(emailAddress)
      } else ()

  private[orchestrators] def validateCompany(reportDraft: ReportDraft): Future[Done.type] =
    validateCompany(reportDraft.companyActivityCode, reportDraft.companySiret)

  private def validateCompany(maybeActivityCode: Option[String], maybeSiret: Option[SIRET]): Future[Done.type] = {
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
      _ <- maybeActivityCode match {
        // 84 correspond à l'administration publique, sauf quelques cas particuliers :
        // - 84.30B : Complémentaires retraites donc pas publique
        case Some(activityCode) if activityCode.startsWith("84.") && activityCode != "84.30B" =>
          Future.failed(AppError.CannotReportPublicAdministration)
        case _ => Future.unit
      }
      _ <- maybeSiret match {
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

  def createReport(draftReport: ReportDraft, consumerIp: ConsumerIp): Future[Report] =
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
      report                <- reportRepository.create(reportToCreate)
      maybeSubcategoryLabel <- subcategoryLabelRepository.get(report.category, report.subcategories)
      _ = logger.debug(s"Created report with id ${report.id}")
      _             <- createReportMetadata(draftReport, report, consumerIp)
      files         <- reportFileOrchestrator.attachFilesToReport(draftReport.fileIds, report.id)
      updatedReport <- notifyProfessionalIfNeeded(maybeCompany, report)
      _             <- emailNotificationOrchestrator.notifyDgccrfIfNeeded(updatedReport, maybeSubcategoryLabel)
      _             <- notifyConsumer(updatedReport, maybeSubcategoryLabel, maybeCompany, files)
      _ = logger.debug(s"Report ${updatedReport.id} created")
    } yield updatedReport

  private def createReportMetadata(
      draftReport: ReportDraft,
      createdReport: Report,
      consumerIp: ConsumerIp
  ): Future[Any] =
    draftReport.metadata
      .map { metadataDraft =>
        val metadata = metadataDraft.toReportMetadata(reportId = createdReport.id, consumerIp)
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

  private def notifyConsumer(
      report: Report,
      maybeSubcategoryLabel: Option[SubcategoryLabel],
      maybeCompany: Option[Company],
      reportAttachements: List[ReportFile]
  ) = {
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
        ConsumerReportAcknowledgment.Email(
          report,
          maybeSubcategoryLabel,
          maybeCompany,
          event,
          reportAttachements,
          messagesApi
        )
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
        Some(requestingUserId)
      )
    } yield updatedReport

  def updateReportCompanyForWebsite(
      existingReport: Report,
      reportCompany: ReportCompany,
      adminUserId: Option[UUID]
  ) =
    if (isReportTooOld(existingReport)) {
      logger.debug(s"Report ${existingReport.id} is too old to be updated")
      Future.unit
    } else
      updateReportCompany(existingReport, reportCompany, adminUserId).map(_ => ())

  private def updateReportCompany(
      existingReport: Report,
      reportCompany: ReportCompany,
      adminUserId: Option[UUID]
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
              adminUserId,
              updateDateTime,
              if (adminUserId.isDefined) Constants.EventType.ADMIN else Constants.EventType.SYSTEM,
              Constants.ActionEvent.REPORT_COMPANY_CHANGE,
              stringToDetailsJsValue(
                s"Entreprise précédente : Siret ${existingReport.companySiret
                    .getOrElse("non renseigné")} - ${Some(existingReport.companyAddress.toString).filter(_ != "").getOrElse("Adresse non renseignée")}"
              )
            )
          )

      _ <- updatedReport.companyId.map(id => removeAccessTokenWhenNoMoreReports(id)).getOrElse(Future.unit)
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
      _ <- if (!hasReportBeenReopened) notifyConsumer(report) else Future.unit
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
            (r: ReportFromSearch, m: Map[UUID, List[ReportFile]]) =>
              ReportWithFiles(
                SubcategoryLabel.translateSubcategories(r.report, r.subcategoryLabel),
                r.metadata,
                r.bookmark,
                r.consumerReview,
                r.engagementReview,
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
    } yield reportsWithFiles.copy(
      entities = reportsWithFiles.entities.map { reportWithFiles =>
        val maybeAssignedUserId = reportWithFiles.metadata.flatMap(_.assignedUserId)
        val reportId            = reportWithFiles.report.id
        ReportWithFilesAndResponses(
          reportWithFiles.report,
          reportWithFiles.metadata,
          reportWithFiles.bookmark,
          reportWithFiles.consumerReview,
          reportWithFiles.engagementReview,
          reportWithFiles.files,
          assignedUser = assignedUsers.find(u => maybeAssignedUserId.contains(u.id)).map(MinimalUser.fromUser),
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
      toApi: (ReportFromSearch, Map[UUID, List[ReportFile]]) => T
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

  private def ensureReportReattributable(report: Report) =
    report.websiteURL.websiteURL.isEmpty &&
      report.websiteURL.host.isEmpty &&
      report.influencer.isEmpty &&
      report.barcodeProductId.isEmpty &&
      report.train.isEmpty &&
      report.station.isEmpty &&
      report.companyId.isDefined

  private def isReattributable(report: Report) = for {
    proEvents <- eventRepository.getEvents(
      report.id,
      EventFilter(eventType = Some(EventType.PRO), action = Some(ActionEvent.REPORT_PRO_RESPONSE))
    )
    filteredProEvents = proEvents
      .filter(event => event.details.as[IncomingReportResponse].responseType == ReportResponseType.NOT_CONCERNED)
      .filter(_.creationDate.isAfter(OffsetDateTime.now().minusDays(15)))
    consoEvents <- eventRepository.getEvents(
      report.id,
      EventFilter(eventType = Some(EventType.CONSO), action = Some(ActionEvent.REATTRIBUTE))
    )
  } yield
    if (ensureReportReattributable(report) && consoEvents.isEmpty) filteredProEvents.headOption.map(_.creationDate)
    else None

  // Signalement "réattribuable" si :
  // - Le signalement existe et il ne concerne pas un site web, un train etc. (voir méthode ensureReportReattributable)
  // - Le pro a répondu 'MalAttribue' (voir isReattributable)
  // - Le conso ne l'a pas déjà réattribué
  // - La réponse du pro n'est pas trop vieille (moins de 15 jours)
  def isReattributable(reportId: UUID): Future[JsObject] = for {
    maybeReport          <- reportRepository.get(reportId)
    report               <- maybeReport.liftTo[Future](ReportNotFound(reportId))
    maybeProResponseDate <- isReattributable(report)
    proResponseDate <- maybeProResponseDate match {
      case Some(date) => Future.successful(date)
      case None       => Future.failed(ReportNotReattributable(reportId))
    }
  } yield Json.obj(
    "creationDate" -> report.creationDate,
    "tags"         -> report.tags,
    "companyName"  -> report.companyName,
    "daysToAnswer" -> (15 - ChronoUnit.DAYS.between(proResponseDate, OffsetDateTime.now()))
  )

  private def toCompany(companySearchResult: CompanySearchResult) =
    Company(
      siret = companySearchResult.siret,
      name = companySearchResult.name.getOrElse(""),
      address = companySearchResult.address,
      activityCode = companySearchResult.activityCode,
      isHeadOffice = companySearchResult.isHeadOffice,
      isOpen = companySearchResult.isOpen,
      isPublic = companySearchResult.isPublic,
      brand = companySearchResult.brand,
      commercialName = companySearchResult.commercialName,
      establishmentCommercialName = companySearchResult.establishmentCommercialName
    )

  // On vérifie si le signalement est réattribuable
  // On vérifie en plus que la réattribution n'est pas à la meme entreprise
  def reattribute(
      reportId: UUID,
      companyCreation: CompanySearchResult,
      metadata: ReportMetadataDraft,
      consumerIp: ConsumerIp
  ): Future[Report] = for {
    maybeReport          <- reportRepository.get(reportId)
    report               <- maybeReport.liftTo[Future](ReportNotFound(reportId))
    maybeProResponseDate <- isReattributable(report)
    _ <- if (maybeProResponseDate.nonEmpty) Future.unit else Future.failed(ReportNotReattributable(reportId))
    _ <- validateCompany(companyCreation.activityCode, Some(companyCreation.siret))
    _ <-
      if (report.companySiret.contains(companyCreation.siret)) Future.failed(CantReattributeToTheSameCompany)
      else Future.unit
    company        <- companyRepository.getOrCreate(companyCreation.siret, toCompany(companyCreation))
    reportFilesMap <- reportFileOrchestrator.prefetchReportsFiles(List(reportId))
    files = reportFilesMap.getOrElse(reportId, List.empty).filter(_.origin == ReportFileOrigin.Consumer)
    newFiles <- files.traverse(f => reportFileOrchestrator.duplicate(f.id, f.filename, f.reportId))

    reportDraft = ReportDraft(
      gender = report.gender,
      category = report.category,
      subcategories = report.subcategories,
      details = report.details,
      influencer = report.influencer,
      companyName = Some(company.name),
      companyCommercialName = company.commercialName,
      companyEstablishmentCommercialName = company.establishmentCommercialName,
      companyBrand = company.brand,
      companyAddress = Some(company.address),
      companySiret = Some(company.siret),
      companyActivityCode = company.activityCode,
      websiteURL = report.websiteURL.websiteURL,
      phone = report.phone,
      firstName = report.firstName,
      lastName = report.lastName,
      email = report.email,
      consumerPhone = report.consumerPhone,
      consumerReferenceNumber = report.consumerReferenceNumber,
      contactAgreement = report.contactAgreement,
      employeeConsumer = report.employeeConsumer,
      forwardToReponseConso = Some(report.forwardToReponseConso),
      fileIds = newFiles.map(_.id),
      vendor = report.vendor,
      tags = report.tags,
      reponseconsoCode = Some(report.reponseconsoCode),
      ccrfCode = Some(report.ccrfCode),
      lang = report.lang,
      barcodeProductId = report.barcodeProductId,
      metadata = Some(metadata),
      train = report.train,
      station = report.station,
      rappelConsoId = report.rappelConsoId,
      companyIsHeadOffice = None,
      companyIsOpen = None,
      companyIsPublic = None
    )
    createdReport <- createReport(reportDraft, consumerIp)
    _ <- eventRepository.create(
      Event(
        UUID.randomUUID(),
        Some(report.id),
        report.companyId,
        None,
        OffsetDateTime.now(),
        Constants.EventType.CONSO,
        Constants.ActionEvent.REATTRIBUTE,
        Json.obj(
          "newReportId"  -> createdReport.id,
          "newCompanyId" -> createdReport.companyId
        )
      )
    )
  } yield createdReport
}

object ReportOrchestrator {
  val ReportCompanyChangeThresholdInDays: Long = 90L

  def validateNotGouvWebsite(reportDraft: ReportDraft): Future[Unit] = {

    def isAGouvWebsite(input: URL): Boolean = {
      val regex   = "^(?!.*\\.gouv\\.fr(?:[\\/\\?#]|$)).*$"
      val pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
      !pattern.matcher(input.value).matches()
    }

    reportDraft.websiteURL match {
      case Some(websiteURL) if isAGouvWebsite(websiteURL) =>
        Future.failed(AppError.CannotReportPublicAdministration)
      case _ => Future.unit
    }
  }

}
