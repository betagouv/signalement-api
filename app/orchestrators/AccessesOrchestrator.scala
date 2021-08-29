package orchestrators

import models.Event.stringToDetailsJsValue
import models._
import play.api.Configuration
import play.api.Logger
import repositories._
import services.MailService
import utils.Constants.ActionEvent
import utils.Constants.EventType
import utils.EmailAddress
import utils.SIRET

import java.net.URI
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class AccessesOrchestrator @Inject() (
    companyRepository: CompanyRepository,
    val companyDataRepository: CompanyDataRepository,
    accessTokenRepository: AccessTokenRepository,
    userRepository: UserRepository,
    eventRepository: EventRepository,
    mailService: MailService,
    configuration: Configuration
)(implicit val executionContext: ExecutionContext) {

  val logger = Logger(this.getClass)
  val mailFrom = EmailAddress(configuration.get[String]("play.mail.from"))
  val tokenDuration = configuration.getOptional[String]("play.tokens.duration").map(java.time.Period.parse(_))
  val websiteUrl = configuration.get[URI]("play.website.url")
  implicit val timeout: akka.util.Timeout = 5.seconds

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
                .filter(_.kind == TokenKind.DGCCRF_ACCOUNT)
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

  object ActivationOutcome extends Enumeration {
    type ActivationOutcome = Value
    val Success, NotFound, EmailConflict = Value
  }
  import ActivationOutcome._
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
                     TokenKind.COMPANY_JOIN,
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
        invitationUrl = websiteUrl.resolve(s"/entreprise/rejoindre/${company.siret}?token=${tokenCode}"),
        invitedBy = invitedBy
      )
      logger.debug(s"Token sent to ${email} for company ${company.id}")
    }

  def sendDGCCRFInvitation(email: EmailAddress): Future[Unit] =
    for {
      token <- accessTokenRepository.createToken(
                 TokenKind.DGCCRF_ACCOUNT,
                 randomToken,
                 tokenDuration,
                 None,
                 None,
                 Some(email)
               )
    } yield {
      mailService.Dgccrf.sendAccessLink(
        email = email,
        invitationUrl = websiteUrl.resolve(s"/dgccrf/rejoindre/?token=${token.token}")
      )
      logger.debug(s"Sent DGCCRF account invitation to ${email}")
    }

  def sendEmailValidation(user: User): Future[Unit] =
    for {
      token <- accessTokenRepository.createToken(
                 TokenKind.VALIDATE_EMAIL,
                 randomToken,
                 Some(Duration.ofHours(1)),
                 None,
                 None,
                 Some(user.email)
               )
    } yield {
      mailService.Common.sendValidateEmail(
        user = user,
        validationUrl = websiteUrl.resolve(s"/connexion/validation-email?token=${token.token}")
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
