package orchestrators

import cats.implicits.catsSyntaxOption
import config.TokenConfiguration
import controllers.error.AppError._
import io.scalaland.chimney.dsl._
import models._
import models.company.AccessLevel
import models.company.Company
import models.event.Event
import models.event.Event.stringToDetailsJsValue
import models.token.TokenKind.CompanyJoin
import models.token._
import play.api.Logger
import play.api.libs.json.Json
import repositories.accesstoken.AccessTokenRepositoryInterface
import repositories.company.CompanyRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface
import repositories.event.EventRepositoryInterface
import repositories.user.UserRepositoryInterface
import services.emails.EmailDefinitionsPro.ProCompaniesAccessesInvitations
import services.emails.EmailDefinitionsPro.ProCompanyAccessInvitation
import services.emails.EmailDefinitionsPro.ProNewCompaniesAccesses
import services.emails.EmailDefinitionsPro.ProNewCompanyAccess
import services.emails.MailServiceInterface
import utils.Constants.ActionEvent
import utils.Constants.EventType
import utils._

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

trait ProAccessTokenOrchestratorInterface {
  def listProPendingToken(company: Company, user: User): Future[List[ProAccessToken]]
  def proFirstActivationCount(ticks: Option[Int]): Future[Seq[CountByDate]]
  def activateProUser(draftUser: DraftUser, token: String, siret: SIRET): Future[User]
  def fetchCompanyUserActivationToken(siret: SIRET, token: String): Future[CompanyUserActivationToken]
  def addUserOrInvite(
      company: Company,
      email: EmailAddress,
      level: AccessLevel,
      invitedBy: Option[User]
  ): Future[Unit]
  def addInvitedUserAndNotify(user: User, company: Company, level: AccessLevel, invitedBy: Option[User]): Future[Unit]
  def addInvitedUserAndNotify(user: User, companies: List[Company], level: AccessLevel): Future[Unit]
  def sendInvitation(company: Company, email: EmailAddress, level: AccessLevel, invitedBy: Option[User]): Future[Unit]
  def sendInvitations(
      companies: List[Company],
      email: EmailAddress,
      level: AccessLevel
  ): Future[Unit]
}

class ProAccessTokenOrchestrator(
    userOrchestrator: UserOrchestratorInterface,
    companyRepository: CompanyRepositoryInterface,
    companyAccessRepository: CompanyAccessRepositoryInterface,
    accessTokenRepository: AccessTokenRepositoryInterface,
    userRepository: UserRepositoryInterface,
    eventRepository: EventRepositoryInterface,
    mailService: MailServiceInterface,
    frontRoute: FrontRoute,
    tokenConfiguration: TokenConfiguration
)(implicit val executionContext: ExecutionContext)
    extends ProAccessTokenOrchestratorInterface {

  val logger                                          = Logger(this.getClass)
  implicit val timeout: org.apache.pekko.util.Timeout = 5.seconds

  def listProPendingToken(company: Company, user: User): Future[List[ProAccessToken]] =
    for {
      tokens <- accessTokenRepository.fetchPendingTokens(company)
    } yield tokens
      .map { token =>
        ProAccessToken(token.id, token.companyLevel, token.emailedTo, token.expirationDate, token.token, user.userRole)
      }

  def proFirstActivationCount(ticks: Option[Int]): Future[Seq[CountByDate]] =
    companyAccessRepository
      .proFirstActivationCount(ticks.getOrElse(12))
      .map(StatsOrchestrator.formatStatData(_, ticks.getOrElse(12)))

  def activateProUser(draftUser: DraftUser, token: String, siret: SIRET): Future[User] = for {
    _     <- PasswordComplexityHelper.validatePasswordComplexity(draftUser.password)
    token <- fetchCompanyToken(token, siret)
    user  <- userOrchestrator.createUser(draftUser, token, UserRole.Professionnel)
    _     <- bindPendingTokens(user)
    _ <- eventRepository.create(
      Event(
        UUID.randomUUID(),
        None,
        token.companyId,
        Some(user.id),
        OffsetDateTime.now(),
        EventType.PRO,
        ActionEvent.ACCOUNT_ACTIVATION,
        stringToDetailsJsValue(s"Email du compte : ${token.emailedTo.getOrElse("")}")
      )
    )
  } yield user

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
      .flatMap(maybeToken => maybeToken.liftTo[Future](ProAccountActivationTokenNotFoundOrInvalid(token, siret)))
  } yield accessToken

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
    userRepository.findByEmail(email.value).flatMap {
      case Some(user) =>
        logger.debug("User with email already exist, creating access")
        addInvitedUserAndNotify(user, company, level, invitedBy)
      case None =>
        logger.debug("No user found for given email, sending invitation")
        sendInvitation(company, email, level, invitedBy)
    }

  def addInvitedUserAndNotify(user: User, company: Company, level: AccessLevel, invitedBy: Option[User]): Future[Unit] =
    for {
      _ <- accessTokenRepository.giveCompanyAccess(company, user, level)
      _ <- invitedBy match {
        case Some(u) =>
          eventRepository.create(
            Event(
              UUID.randomUUID(),
              None,
              Some(company.id),
              Some(u.id),
              OffsetDateTime.now(),
              Constants.EventType.fromUserRole(u.userRole),
              Constants.ActionEvent.USER_ACCESS_CREATED,
              Json.obj("userId" -> user.id, "email" -> user.email, "level" -> level)
            )
          )
        case None => Future.unit
      }
      _ <- mailService.send(ProNewCompanyAccess.Email(user.email, company, invitedBy))
      _ = logger.debug(s"User ${user.id} may now access company ${company.id}")
    } yield ()

  def addInvitedUserAndNotify(user: User, companies: List[Company], level: AccessLevel): Future[Unit] =
    for {
      _ <- Future.sequence(companies.map(company => accessTokenRepository.giveCompanyAccess(company, user, level)))
      _ <- companies match {
        case Nil => Future.successful(())
        case c :: _ =>
          mailService.send(ProNewCompaniesAccesses.Email(user.email, companies, SIREN.fromSIRET(c.siret)))
      }
      _ = logger.debug(s"User ${user.id} may now access companies ${companies.map(_.siret)}")
    } yield ()

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
        .getOrElse(Future.successful(None))
      token <- existingToken
        .map(Future.successful)
        .getOrElse {
          logger.debug("Creating user invitation token")
          accessTokenRepository.create(
            AccessToken.build(
              kind = CompanyJoin,
              token = UUID.randomUUID.toString,
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
        ProCompanyAccessInvitation.Email(
          recipient = email,
          company = company,
          invitationUrl = frontRoute.dashboard.Pro.register(company.siret, tokenCode),
          invitedBy = invitedBy
        )
      )
      _ = logger.debug(s"Token sent to ${email} for company ${company.id}")
    } yield ()

  def sendInvitations(
      companies: List[Company],
      email: EmailAddress,
      level: AccessLevel
  ): Future[Unit] =
    for {
      list <- Future.sequence(
        companies.map(company =>
          genInvitationToken(company, level, tokenConfiguration.companyJoinDuration, email).map(token =>
            token -> company
          )
        )
      )
      _ <- list match {
        case Nil => Future.successful(())
        case (tokenCode, c) :: _ =>
          mailService.send(
            ProCompaniesAccessesInvitations.Email(
              recipient = email,
              companies = list.map(_._2),
              siren = SIREN.fromSIRET(c.siret),
              invitationUrl = frontRoute.dashboard.Pro.register(c.siret, tokenCode)
            )
          )
      }
      _ = logger.debug(s"Token sent to ${email} for companies ${companies}")
    } yield ()

}
