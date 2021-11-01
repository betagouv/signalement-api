package orchestrators

import cats.implicits.catsSyntaxMonadError
import cats.implicits.toTraverseOps
import controllers.error.AppError._
import io.scalaland.chimney.dsl.TransformerOps
import models.Event.stringToDetailsJsValue
import models.UserRoles.Admin
import models.UserRoles.DGCCRF
import models.UserRoles.Pro
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
  implicit val ccrfEmailSuffix = configuration.get[String]("play.mail.ccrfEmailSuffix")
  implicit val timeout: akka.util.Timeout = 5.seconds

  def listAccesses(company: Company, user: User) =
    getHeadOffice(company).flatMap {

      case Some(headOffice) if headOffice.siret == company.siret =>
        logger.debug(s"$company is a head office, returning access for head office")
        for {
          userLevel <- companyRepository.getUserLevel(company.id, user)
          access <- getHeadOfficeAccess(user, userLevel, company, editable = true)
        } yield access

      case maybeHeadOffice =>
        logger.debug(s"$company is not a head office, returning access for head office and subsidiaries")
        for {
          userAccessLevel <- companyRepository.getUserLevel(company.id, user)
          subsidiaryUserAccess <- getSubsidiaryAccess(user, userAccessLevel, List(company), editable = true)
          maybeHeadOfficeCompany <- maybeHeadOffice match {
            case Some(headOffice) => companyRepository.findBySiret(headOffice.siret)
            case None             =>
              //No head office found in company database ( Company DB is not synced )
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
      companyAccess <- companyRepository
        .fetchUsersWithLevel(companies.map(_.id))
    } yield (userLevel, user.userRole) match {
      case (_, Admin) =>
        logger.debug(s"Signal conso admin user : setting editable to true")
        companyAccess.map { case (user, level) => toApi(user, level, editable = true, isHeadOffice) }
      case (_, DGCCRF) =>
        logger.debug(s"Signal conso dgccrf user : setting editable to false")
        companyAccess.map { case (user, level) => toApi(user, level, editable = false, isHeadOffice) }
      case (AccessLevel.ADMIN, Pro) =>
        companyAccess.map {
          case (companyUser, level) if companyUser.id == user.id =>
            toApi(companyUser, level, editable = false, isHeadOffice)
          case (companyUser, level) =>
            toApi(companyUser, level, editable, isHeadOffice)
        }
      case (_, Pro) =>
        logger.debug(s"User PRO does not have admin access to company : setting editable to false")
        companyAccess.map { case (user, level) => toApi(user, level, editable = false, isHeadOffice) }
      case _ =>
        logger.warn(s"User is not supposed to access this feature")
        List.empty[UserWithAccessLevel]
    }

  abstract class TokenWorkflow(draftUser: DraftUser, @annotation.unused token: String) {

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
        invitationUrl = frontRoute.dashboard.Pro.register(company.siret, tokenCode),
        invitedBy = invitedBy
      )
      logger.debug(s"Token sent to ${email} for company ${company.id}")
    }

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
        invitationUrl = frontRoute.dashboard.Dgccrf.register(token.token)
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
