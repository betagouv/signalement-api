package orchestrators

import controllers.error.AppError.CompanyActivationTokenNotFound
import controllers.error.AppError.CompanySiretNotFound
import controllers.error.AppError.DGCCRFActivationTokenNotFound
import controllers.error.AppError.ServerError
import io.scalaland.chimney.dsl.TransformerOps
import models.Event.stringToDetailsJsValue
import models._
import models.access.ActivationOutcome.ActivationOutcome
import models.access.ActivationOutcome
import models.access.UserWithAccessLevel
import models.access.UserWithAccessLevel.toApi
import models.token.CompanyUserActivationToken
import models.token.DGCCRFUserActivationToken
import models.token.TokenKind.CompanyJoin
import models.token.TokenKind.DGCCRFAccount
import models.token.TokenKind.ValidateEmail
import play.api.Configuration
import play.api.Logger
import repositories.AccessTokenRepository
import repositories._
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
    companyRepository: CompanyRepository,
    companyDataRepository: CompanyDataRepository,
    accessTokenRepository: AccessTokenRepository,
    userRepository: UserRepository,
    eventRepository: EventRepository,
    mailService: MailService,
    configuration: Configuration,
    frontRoute: FrontRoute
)(implicit val executionContext: ExecutionContext) {

  val logger = Logger(this.getClass)
  val tokenDuration = configuration.getOptional[String]("play.tokens.duration").map(java.time.Period.parse(_))
  implicit val timeout: akka.util.Timeout = 5.seconds

  def listAccesses(company: Company, user: User) =
    getHeadOffice(company.siret).flatMap {

      case headOffice if headOffice.siret == company.siret =>
        logger.debug(s"$company is a head office, returning access for head office")
        for {
          userLevel <- companyRepository.getUserLevel(company.id, user)
          access <- getHeadOfficeAccess(user.id, userLevel, company, editable = true)
        } yield access

      case headOffice =>
        logger.debug(s"$company is not a head office, returning access for head office and subsidiaries")
        for {
          userLevel <- companyRepository.getUserLevel(company.id, user)
          subsidiaryUserAccess <- getSubsidiaryAccess(user.id, userLevel, List(company), editable = true)
          maybeHeadOfficeCompany <- companyRepository.findBySiret(headOffice.siret)
          headOfficeCompany <- maybeHeadOfficeCompany match {
            case Some(value) => Future.successful(value)
            case None        => Future.failed(CompanySiretNotFound(headOffice.siret))
          }
          headOfficeAccess <- getHeadOfficeAccess(user.id, userLevel, headOfficeCompany, editable = false)
          _ = logger.debug(s"Removing duplicate access")
          filteredHeadOfficeAccess = headOfficeAccess.filterNot(a => subsidiaryUserAccess.exists(_.userId == a.userId))
        } yield filteredHeadOfficeAccess ++ subsidiaryUserAccess
    }

  private def getHeadOffice(siret: SIRET): Future[CompanyData] =
    companyDataRepository.getHeadOffice(siret).flatMap {
      case Nil =>
        logger.error(s"No head office for siret $siret")
        Future.failed(ServerError(s"Unexpected error when fetching head office for company with siret ${siret}"))
      case company :: Nil =>
        Future.successful(company)
      case companies =>
        logger.error(s"Multiple head offices for siret $siret company data ids ${companies.map(_.id)} ")
        Future.failed(ServerError(s"Unexpected error when fetching head office for company with siret ${siret}"))
    }

  private def getHeadOfficeAccess(
      userId: UUID,
      userLevel: AccessLevel,
      company: Company,
      editable: Boolean
  ): Future[List[UserWithAccessLevel]] =
    getUserAccess(userId, userLevel, List(company), editable, isHeadOffice = true)

  private def getSubsidiaryAccess(
      userId: UUID,
      userLevel: AccessLevel,
      companies: List[Company],
      editable: Boolean
  ): Future[List[UserWithAccessLevel]] =
    getUserAccess(userId, userLevel, companies, editable, isHeadOffice = false)

  private def getUserAccess(
      userId: UUID,
      userLevel: AccessLevel,
      companies: List[Company],
      editable: Boolean,
      isHeadOffice: Boolean
  ): Future[List[UserWithAccessLevel]] =
    for {
      companyAccess <- companyRepository
        .fetchUsersWithLevel(companies.map(_.id))
      userAccess =
        if (userLevel != AccessLevel.ADMIN) {
          logger.debug(s"User is not an admin : setting editable to false")
          companyAccess.map { case (user, level) => toApi(user, level, editable = false, isHeadOffice) }
        } else {
          companyAccess.map {
            case (user, level) if user.id == userId =>
              logger.debug(s"User cannot edit his own access : setting editable to false")
              toApi(user, level, editable = false, isHeadOffice)
            case (user, level) =>
              toApi(user, level, editable, isHeadOffice)
          }

        }
    } yield userAccess

  abstract class TokenWorkflow(draftUser: DraftUser, token: String) {
    def log(msg: String) = logger.debug(s"${this.getClass.getSimpleName} - ${msg}")

    def fetchToken: Future[Option[AccessToken]]
    def run: Future[Boolean]
    def createUser(accessToken: AccessToken, role: UserRole): Future[User] = {
      // If invited on emailedTo, user should register using that email
      val email = accessToken.emailedTo.getOrElse(draftUser.email)
      userRepository
        .create(
          User(
            UUID.randomUUID,
            draftUser.password,
            email,
            draftUser.firstName,
            draftUser.lastName,
            role,
            Some(OffsetDateTime.now)
          )
        )
        .map { u =>
          log(s"User with id ${u.id} created through token ${accessToken.id}")
          u
        }
    }
  }

  class GenericTokenWorkflow(draftUser: DraftUser, token: String) extends TokenWorkflow(draftUser, token) {
    def fetchToken = accessTokenRepository.findToken(token)
    def run = for {
      accessToken <- fetchToken
      user <- accessToken
        .filter(_.kind == DGCCRFAccount)
        .map(t => createUser(t, UserRoles.DGCCRF).map(Some(_)))
        .getOrElse(Future(None))
      _ <- user
        .flatMap(_ => accessToken)
        .map(accessTokenRepository.invalidateToken)
        .getOrElse(Future(0))
    } yield user.isDefined
  }

  class CompanyTokenWorkflow(draftUser: DraftUser, token: String, siret: SIRET)
      extends TokenWorkflow(draftUser, token) {
    def fetchCompany: Future[Option[Company]] = companyRepository.findBySiret(siret)
    def fetchToken: Future[Option[AccessToken]] = for {
      company <- fetchCompany
      accessToken <- company.map(accessTokenRepository.findToken(_, token)).getOrElse(Future(None))
    } yield accessToken
    def bindPendingTokens(user: User) =
      accessTokenRepository
        .fetchPendingTokens(user.email)
        .flatMap(tokens =>
          Future.sequence(tokens.filter(_.companyId.isDefined).map(accessTokenRepository.applyCompanyToken(_, user)))
        )
    def run = for {
      accessToken <- fetchToken
      user <- accessToken.map(t => createUser(t, UserRoles.Pro).map(Some(_))).getOrElse(Future(None))
      applied <- (for { u <- user; t <- accessToken } yield accessTokenRepository.applyCompanyToken(t, u))
        .getOrElse(Future(false))
      _ <- user.map(bindPendingTokens(_)).getOrElse(Future(Nil))
      _ <- accessToken
        .map(t =>
          eventRepository.createEvent(
            Event(
              Some(UUID.randomUUID()),
              None,
              t.companyId,
              user.map(_.id),
              Some(OffsetDateTime.now),
              EventType.PRO,
              ActionEvent.ACCOUNT_ACTIVATION,
              stringToDetailsJsValue(s"Email du compte : ${t.emailedTo.getOrElse("")}")
            )
          )
        )
        .getOrElse(Future(None))
    } yield applied
  }

  def handleActivationRequest(draftUser: DraftUser, token: String, siret: Option[SIRET]): Future[ActivationOutcome] = {
    val workflow = siret
      .map(s => new CompanyTokenWorkflow(draftUser, token, s))
      .getOrElse(new GenericTokenWorkflow(draftUser, token))
    workflow.run
      .map(if (_) ActivationOutcome.Success else ActivationOutcome.NotFound)
      .recover {
        case (e: org.postgresql.util.PSQLException) if e.getMessage.contains("email_unique") =>
          ActivationOutcome.EmailConflict
      }
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
        .map(accessTokenRepository.findToken(_, token))
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
      case Some(user) => addInvitedUserAndNotify(user, company, level, invitedBy)
      case None       => sendInvitation(company, email, level, invitedBy)
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
    } yield {
      mailService.Pro.sendNewCompanyAccessNotification(user, company, invitedBy)
      logger.debug(s"User ${user.id} may now access company ${company.id}")
      ()
    }

  private def randomToken = UUID.randomUUID.toString

  private def genInvitationToken(
      company: Company,
      level: AccessLevel,
      validity: Option[java.time.temporal.TemporalAmount],
      emailedTo: EmailAddress
  ): Future[String] =
    for {
      existingToken <- accessTokenRepository.fetchToken(company, emailedTo)
      _ <- existingToken.map(accessTokenRepository.updateToken(_, level, validity)).getOrElse(Future(None))
      token <- existingToken
        .map(Future(_))
        .getOrElse(
          accessTokenRepository.createToken(
            CompanyJoin,
            randomToken,
            tokenDuration,
            Some(company.id),
            Some(level),
            emailedTo = Some(emailedTo)
          )
        )
    } yield token.token

  def sendInvitation(company: Company, email: EmailAddress, level: AccessLevel, invitedBy: Option[User]): Future[Unit] =
    genInvitationToken(company, level, tokenDuration, email).map { tokenCode =>
      mailService.Pro.sendCompanyAccessInvitation(
        company = company,
        email = email,
        invitationUrl = frontRoute.dashboard.registerPro(company.siret, tokenCode),
        invitedBy = invitedBy
      )
      logger.debug(s"Token sent to ${email} for company ${company.id}")
    }

  def sendDGCCRFInvitation(email: EmailAddress): Future[Unit] =
    for {
      token <-
        accessTokenRepository.createToken(
          kind = DGCCRFAccount,
          token = randomToken,
          validity = tokenDuration,
          companyId = None,
          level = None,
          emailedTo = Some(email)
        )
    } yield {
      mailService.Dgccrf.sendAccessLink(
        email = email,
        invitationUrl = frontRoute.dashboard.registerDgccrf(token.token)
      )
      logger.debug(s"Sent DGCCRF account invitation to ${email}")
    }

  def sendEmailValidation(user: User): Future[Unit] =
    for {
      token <- accessTokenRepository.createToken(
        ValidateEmail,
        randomToken,
        Some(Duration.ofHours(1)),
        None,
        None,
        Some(user.email)
      )
    } yield {
      mailService.Common.sendValidateEmail(
        user = user,
        validationUrl = frontRoute.dashboard.validateEmail(token.token)
      )
      logger.debug(s"Sent email validation to ${user.email}")
    }

  def validateEmail(token: AccessToken): Future[Option[User]] =
    for {
      u <- userRepository.findByLogin(token.emailedTo.map(_.toString).get)
      _ <- u.map(accessTokenRepository.useEmailValidationToken(token, _)).getOrElse(Future(false))
    } yield {
      logger.debug(s"Validated email ${token.emailedTo.get}")
      u
    }
}
