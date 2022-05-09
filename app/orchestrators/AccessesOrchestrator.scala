package orchestrators

import cats.implicits.catsSyntaxMonadError
import cats.implicits.catsSyntaxOption
import cats.implicits.toTraverseOps
import config.EmailConfiguration
import config.TokenConfiguration
import controllers.error.AppError._
import io.scalaland.chimney.dsl.TransformerOps
import models.event.Event.stringToDetailsJsValue
import models.UserRole.Admin
import models.UserRole.DGCCRF
import models.UserRole.Professionnel
import models.ActivationRequest
import models._
import models.access.UserWithAccessLevel
import models.access.UserWithAccessLevel.toApi
import models.event.Event
import models.token.CompanyUserActivationToken
import models.token.DGCCRFUserActivationToken
import models.token.TokenKind
import models.token.TokenKind.CompanyJoin
import models.token.TokenKind.DGCCRFAccount
import models.token.TokenKind.ValidateEmail
import play.api.Logger
import repositories.accesstoken.AccessTokenRepository
import repositories.company.CompanyRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface
import repositories.companydata.CompanyDataRepositoryInterface
import repositories.event.EventRepositoryInterface
import repositories.user.UserRepositoryInterface
import services.Email.DgccrfAccessLink
import services.Email.ProCompanyAccessInvitation
import services.Email.ProNewCompanyAccess
import services.Email
import services.MailService
import utils.Constants.ActionEvent
import utils.Constants.EventType
import utils.EmailAddress
import utils.FrontRoute
import utils.SIRET

import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class AccessesOrchestrator @Inject() (
    companyRepository: CompanyRepositoryInterface,
    companyAccessRepository: CompanyAccessRepositoryInterface,
    companyDataRepository: CompanyDataRepositoryInterface,
    accessTokenRepository: AccessTokenRepository,
    userRepository: UserRepositoryInterface,
    eventRepository: EventRepositoryInterface,
    mailService: MailService,
    frontRoute: FrontRoute,
    emailConfiguration: EmailConfiguration,
    tokenConfiguration: TokenConfiguration
)(implicit val executionContext: ExecutionContext) {

  val logger = Logger(this.getClass)
  implicit val ccrfEmailSuffix = emailConfiguration.ccrfEmailSuffix
  implicit val timeout: akka.util.Timeout = 5.seconds

  def listAccesses(company: Company, user: User): Future[List[UserWithAccessLevel]] =
    getHeadOffice(company).flatMap {

      case Some(headOffice) if headOffice.siret == company.siret =>
        logger.debug(s"$company is a head office, returning access for head office")
        for {
          userLevel <- companyAccessRepository.getUserLevel(company.id, user)
          access <- getHeadOfficeAccess(user, userLevel, company, editable = true)
        } yield access

      case maybeHeadOffice =>
        logger.debug(s"$company is not a head office, returning access for head office and subsidiaries")
        for {
          userAccessLevel <- companyAccessRepository.getUserLevel(company.id, user)
          subsidiaryUserAccess <- getSubsidiaryAccess(user, userAccessLevel, List(company), editable = true)
          maybeHeadOfficeCompany <- maybeHeadOffice match {
            case Some(headOffice) => companyRepository.findBySiret(headOffice.siret)
            case None             =>
              // No head office found in company database ( Company DB is not synced )
              Future.successful(None)
          }
          headOfficeAccess <- maybeHeadOfficeCompany.map { headOfficeCompany =>
            getHeadOfficeAccess(user, userAccessLevel, headOfficeCompany, editable = false)
          }.sequence
          _ = logger.debug(s"Removing duplicate access")
          filteredHeadOfficeAccess = headOfficeAccess.map(
            _.filterNot(a => subsidiaryUserAccess.exists(_.userId == a.userId))
          )
        } yield filteredHeadOfficeAccess.getOrElse(List.empty) ++ subsidiaryUserAccess
    }

  private def getHeadOffice(company: Company): Future[Option[CompanyData]] =
    companyDataRepository.getHeadOffice(company.siret).flatMap {
      case Nil =>
        logger.warn(s"No head office for siret ${company.siret}")
        Future.successful(None)
      case c :: Nil =>
        Future.successful(Some(c))
      case companies =>
        logger.error(s"Multiple head offices for siret ${company.siret} company data ids ${companies.map(_.id)} ")
        Future.failed(
          ServerError(s"Unexpected error when fetching head office for company with siret ${company.siret}")
        )
    }

  private def getHeadOfficeAccess(
      user: User,
      userLevel: AccessLevel,
      company: Company,
      editable: Boolean
  ): Future[List[UserWithAccessLevel]] =
    getUserAccess(user, userLevel, List(company), editable, isHeadOffice = true)

  private def getSubsidiaryAccess(
      user: User,
      userLevel: AccessLevel,
      companies: List[Company],
      editable: Boolean
  ): Future[List[UserWithAccessLevel]] =
    getUserAccess(user, userLevel, companies, editable, isHeadOffice = false)

  private def getUserAccess(
      user: User,
      userLevel: AccessLevel,
      companies: List[Company],
      editable: Boolean,
      isHeadOffice: Boolean
  ): Future[List[UserWithAccessLevel]] =
    for {
      companyAccess <- companyAccessRepository
        .fetchUsersWithLevel(companies.map(_.id))
    } yield (userLevel, user.userRole) match {
      case (_, Admin) =>
        logger.debug(s"Signal conso admin user : setting editable to true")
        companyAccess.map { case (user, level) => toApi(user, level, editable = true, isHeadOffice) }
      case (_, DGCCRF) =>
        logger.debug(s"Signal conso dgccrf user : setting editable to false")
        companyAccess.map { case (user, level) => toApi(user, level, editable = false, isHeadOffice) }
      case (AccessLevel.ADMIN, Professionnel) =>
        companyAccess.map {
          case (companyUser, level) if companyUser.id == user.id =>
            toApi(companyUser, level, editable = false, isHeadOffice)
          case (companyUser, level) =>
            toApi(companyUser, level, editable, isHeadOffice)
        }
      case (_, Professionnel) =>
        logger.debug(s"User PRO does not have admin access to company : setting editable to false")
        companyAccess.map { case (user, level) => toApi(user, level, editable = false, isHeadOffice) }
      case _ =>
        logger.error(s"User is not supposed to access this feature")
        List.empty[UserWithAccessLevel]
    }

  def proFirstActivationCount(ticks: Option[Int]) =
    companyAccessRepository
      .proFirstActivationCount(ticks.getOrElse(12))
      .map(StatsOrchestrator.formatStatData(_, (ticks.getOrElse(12))))

  private def activateDGCCRFUser(draftUser: DraftUser, token: String) = for {
    maybeAccessToken <- accessTokenRepository.findToken(token)
    accessToken <- maybeAccessToken
      .find(_.kind == TokenKind.DGCCRFAccount)
      .liftTo[Future](AccountActivationTokenNotFoundOrInvalid(token))
    _ = logger.debug(s"Token $token found, creating user")
    _ <- createUser(draftUser, accessToken, UserRole.DGCCRF)
    _ = logger.debug(s"User created successfully, invalidating token")
    _ <- accessTokenRepository.invalidateToken(accessToken)
    _ = logger.debug(s"Token has been revoked")
  } yield ()

  private def activateCompany(draftUser: DraftUser, token: String, siret: SIRET) = for {
    token <- fetchCompanyToken(token, siret)
    user <- createUser(draftUser, token, UserRole.Professionnel)
    _ <- bindPendingTokens(user)
    _ <- eventRepository.create(
      Event(
        UUID.randomUUID(),
        None,
        token.companyId,
        Some(user.id),
        OffsetDateTime.now,
        EventType.PRO,
        ActionEvent.ACCOUNT_ACTIVATION,
        stringToDetailsJsValue(s"Email du compte : ${token.emailedTo.getOrElse("")}")
      )
    )
  } yield ()

  private def bindPendingTokens(user: User) =
    accessTokenRepository
      .fetchPendingTokens(user.email)
      .flatMap(tokens =>
        Future.sequence(
          tokens.filter(_.companyId.isDefined).map(accessTokenRepository.createCompanyAccessAndRevokeToken(_, user))
        )
      )

  private def fetchCompanyToken(token: String, siret: SIRET): Future[AccessToken] = for {
    company <- companyRepository
      .findBySiret(siret)
      .flatMap(maybeCompany => maybeCompany.liftTo[Future](CompanySiretNotFound(siret)))
    accessToken <- accessTokenRepository
      .findValidToken(company, token)
      .flatMap(maybeToken => maybeToken.liftTo[Future](AccountActivationTokenNotFoundOrInvalid(token)))
  } yield accessToken

  private def createUser(draftUser: DraftUser, accessToken: AccessToken, role: UserRole): Future[User] = {
    val email: EmailAddress = accessToken.emailedTo.getOrElse(draftUser.email)
    val user = User(
      id = UUID.randomUUID,
      password = draftUser.password,
      email = email,
      firstName = draftUser.firstName,
      lastName = draftUser.lastName,
      userRole = role,
      lastEmailValidation = Some(OffsetDateTime.now)
    )
    for {
      _ <- userRepository.findByLogin(draftUser.email.value).ensure(EmailAlreadyExist)(user => user.isEmpty)
      _ <- userRepository.create(user)
    } yield user
  }

  def handleActivationRequest(activateAccount: ActivationRequest): Future[Unit] =
    activateAccount.companySiret match {
      case Some(siret) =>
        logger.info(s"Activating pro user for company $siret with token $activateAccount.token")
        activateCompany(activateAccount.draftUser, activateAccount.token, siret)
      case None =>
        logger.info(s"Activating DGCCRF user with token ${activateAccount.token}")
        activateDGCCRFUser(activateAccount.draftUser, activateAccount.token)
    }

  def fetchDGCCRFUserActivationToken(token: String): Future[DGCCRFUserActivationToken] = for {
    maybeAccessToken <- accessTokenRepository
      .findToken(token)
    accessToken <- maybeAccessToken
      .map(Future.successful(_))
      .getOrElse(Future.failed[AccessToken](DGCCRFActivationTokenNotFound(token)))
    _ = logger.debug("Validating email")
    emailTo <-
      accessToken.emailedTo
        .map(Future.successful(_))
        .getOrElse(Future.failed[EmailAddress](ServerError(s"Email should be defined for access token $token")))
    _ = logger.debug(s"Access token found ${accessToken}")
  } yield accessToken
    .into[DGCCRFUserActivationToken]
    .withFieldConst(_.emailedTo, emailTo)
    .transform

  def fetchCompanyUserActivationToken(siret: SIRET, token: String): Future[CompanyUserActivationToken] =
    for {
      company <- companyRepository.findBySiret(siret)
      maybeAccessToken <- company
        .map(accessTokenRepository.findValidToken(_, token))
        .getOrElse(Future.failed[Option[AccessToken]](CompanySiretNotFound(siret)))
      accessToken <- maybeAccessToken
        .map(Future.successful)
        .getOrElse(Future.failed[AccessToken](CompanyActivationTokenNotFound(token, siret)))
      emailTo <-
        accessToken.emailedTo
          .map(Future.successful)
          .getOrElse(Future.failed[EmailAddress](ServerError(s"Email should be defined for access token $token")))

    } yield accessToken
      .into[CompanyUserActivationToken]
      .withFieldConst(_.emailedTo, emailTo)
      .withFieldConst(_.companySiret, siret)
      .transform

  def addUserOrInvite(
      company: Company,
      email: EmailAddress,
      level: AccessLevel,
      invitedBy: Option[User]
  ): Future[Unit] =
    userRepository.findByLogin(email.value).flatMap {
      case Some(user) =>
        logger.debug("User with email already exist, creating access")
        addInvitedUserAndNotify(user, company, level, invitedBy)
      case None =>
        logger.debug("No user found for given email, sending invitation")
        sendInvitation(company, email, level, invitedBy)
    }

  def addUserOrInvite(
      sirets: List[SIRET],
      email: EmailAddress,
      level: AccessLevel,
      invitedBy: Option[User]
  ): Future[Unit] =
    for {
      companiesData <- Future.sequence(sirets.map(companyDataRepository.searchBySiret(_)))
      companies <-
        Future.sequence(companiesData.flatten.map { case (companyData, activity) =>
          companyRepository.getOrCreate(companyData.siret, companyData.toSearchResult(activity.map(_.label)).toCompany)
        })
      _ <- Future.sequence(companies.map(company => addUserOrInvite(company, email, level, invitedBy)))
    } yield ()

  def addInvitedUserAndNotify(user: User, company: Company, level: AccessLevel, invitedBy: Option[User]) =
    for {
      _ <- accessTokenRepository.giveCompanyAccess(company, user, level)
      _ <- mailService.send(ProNewCompanyAccess(user.email, company, invitedBy))
      _ = logger.debug(s"User ${user.id} may now access company ${company.id}")
    } yield ()

  private def randomToken = UUID.randomUUID.toString

  private def genInvitationToken(
      company: Company,
      level: AccessLevel,
      validity: Option[java.time.temporal.TemporalAmount],
      emailedTo: EmailAddress
  ): Future[String] =
    for {
      existingToken <- accessTokenRepository.fetchToken(company, emailedTo)
      _ <- existingToken
        .map { existingToken =>
          logger.debug("Found existing token for that user and company, updating existing token")
          accessTokenRepository.updateToken(existingToken, level, validity)
        }
        .getOrElse(Future(None))
      token <- existingToken
        .map(Future(_))
        .getOrElse {
          logger.debug("Creating user invitation token")
          accessTokenRepository.create(
            AccessToken.build(
              kind = CompanyJoin,
              token = randomToken,
              validity = tokenConfiguration.companyJoinDuration,
              companyId = Some(company.id),
              level = Some(level),
              emailedTo = Some(emailedTo)
            )
          )
        }
    } yield token.token

  def sendInvitation(company: Company, email: EmailAddress, level: AccessLevel, invitedBy: Option[User]): Future[Unit] =
    for {
      tokenCode <- genInvitationToken(company, level, tokenConfiguration.companyJoinDuration, email)
      _ <- mailService.send(
        ProCompanyAccessInvitation(
          recipient = email,
          company = company,
          invitationUrl = frontRoute.dashboard.Pro.register(company.siret, tokenCode),
          invitedBy = invitedBy
        )
      )
      _ = logger.debug(s"Token sent to ${email} for company ${company.id}")
    } yield ()

  def sendDGCCRFInvitation(email: EmailAddress): Future[Unit] =
    for {
      _ <-
        if (email.value.endsWith(ccrfEmailSuffix)) {
          Future.successful(())
        } else {
          Future.failed(InvalidDGCCRFEmail(email, ccrfEmailSuffix))
        }
      _ <- userRepository.list(email).ensure(UserAccountEmailAlreadyExist)(_.isEmpty)
      token <-
        accessTokenRepository.create(
          AccessToken.build(
            kind = DGCCRFAccount,
            token = randomToken,
            validity = tokenConfiguration.dgccrfJoinDuration,
            companyId = None,
            level = None,
            emailedTo = Some(email)
          )
        )
      _ <- mailService.send(
        DgccrfAccessLink(recipient = email, invitationUrl = frontRoute.dashboard.Dgccrf.register(token.token))
      )
      _ = logger.debug(s"Sent DGCCRF account invitation to ${email}")
    } yield ()

  def sendEmailValidation(user: User): Future[Unit] =
    for {
      token <- accessTokenRepository.create(
        AccessToken.build(
          kind = ValidateEmail,
          token = randomToken,
          validity = Some(Duration.ofDays(1)),
          companyId = None,
          level = None,
          emailedTo = Some(user.email)
        )
      )
      _ <- mailService.send(
        Email.ValidateEmail(
          user,
          frontRoute.dashboard.validateEmail(token.token)
        )
      )
    } yield logger.debug(s"Sent email validation to ${user.email}")

  def validateEmail(token: AccessToken): Future[Option[User]] =
    for {
      u <- userRepository.findByLogin(token.emailedTo.map(_.toString).get)
      _ <- u.map(accessTokenRepository.useEmailValidationToken(token, _)).getOrElse(Future(false))
    } yield {
      logger.debug(s"Validated email ${token.emailedTo.get}")
      u
    }
}
