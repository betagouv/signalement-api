package orchestrators

import java.net.URI

import javax.inject.{Inject, Named}
import akka.actor.ActorRef
import akka.pattern.ask
import actors.EmailActor
import models._
import play.api.{Configuration, Logger}
import repositories._
import services.MailerService
import java.util.UUID

import utils.{EmailAddress, SIRET}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class AccessesOrchestrator @Inject()(companyRepository: CompanyRepository,
                                   accessTokenRepository: AccessTokenRepository,
                                   userRepository: UserRepository,
                                   mailerService: MailerService,
                                   @Named("email-actor") emailActor: ActorRef,
                                   configuration: Configuration)
                                   (implicit val executionContext: ExecutionContext) {

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
      .create(User(
        UUID.randomUUID, draftUser.password, email,
        draftUser.firstName, draftUser.lastName, role))
      .map(u => {
        log(s"User with id ${u.id} created through token ${accessToken.id}")
        u
      })
    }
  }

  class GenericTokenWorkflow(draftUser: DraftUser, token: String) extends TokenWorkflow(draftUser, token) {
    def fetchToken = { accessTokenRepository.findToken(token) }
    def run = for {
      accessToken <- fetchToken
      user        <- accessToken.filter(_.kind == TokenKind.DGCCRF_ACCOUNT)
                                .map(t => createUser(t, UserRoles.DGCCRF).map(Some(_)))
                                .getOrElse(Future(None))
      _           <- user.flatMap(_ => accessToken)
                         .map(accessTokenRepository.invalidateToken)
                         .getOrElse(Future(0))
    } yield user.isDefined
  }

  class CompanyTokenWorkflow(draftUser: DraftUser, token: String, siret: SIRET) extends TokenWorkflow(draftUser, token) {
    def fetchCompany: Future[Option[Company]] = companyRepository.findBySiret(siret)
    def fetchToken: Future[Option[AccessToken]] = for {
        company     <- fetchCompany
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
      user        <- accessToken.map(t => createUser(t, UserRoles.Pro).map(Some(_))).getOrElse(Future(None))
      applied     <- (for { u <- user; t <- accessToken }
                        yield accessTokenRepository.applyCompanyToken(t, u)).getOrElse(Future(false))
      _           <- user.map(bindPendingTokens(_)).getOrElse(Future(Nil))
    } yield applied
  }

  object ActivationOutcome extends Enumeration {
    type ActivationOutcome = Value
    val Success, NotFound, EmailConflict = Value
  }
  import ActivationOutcome._
  def handleActivationRequest(draftUser: DraftUser, token: String, siret: Option[SIRET]): Future[ActivationOutcome] = {
    val workflow = siret.map(s => new CompanyTokenWorkflow(draftUser, token, s))
                        .getOrElse(new GenericTokenWorkflow(draftUser, token))
    workflow.run
            .map(if (_) ActivationOutcome.Success else ActivationOutcome.NotFound)
            .recover {
              case (e: org.postgresql.util.PSQLException) if e.getMessage.contains("email_unique")
                => ActivationOutcome.EmailConflict
            }
  }

  def addUserOrInvite(company: Company, email: EmailAddress, level: AccessLevel, invitedBy: User): Future[Unit] =
    userRepository.findByLogin(email.value).flatMap{
      case Some(user) => addInvitedUserAndNotify(user, company, level, invitedBy)
      case None       => sendInvitation(company, email, level, invitedBy)
    }

  def addInvitedUserAndNotify(user: User, company: Company, level: AccessLevel, invitedBy: User) =
    for {
      _ <- companyRepository.setUserLevel(company, user, level)
    } yield {
      emailActor ? EmailActor.EmailRequest(
        from = mailFrom,
        recipients = Seq(user.email),
        subject = s"Vous avez maintenant accès à l'entreprise ${company.name} sur SignalConso",
        bodyHtml = views.html.mails.professional.newCompanyAccessNotification(company, invitedBy).toString
      )
      logger.debug(s"User ${user.id} may now access company ${company.id}")
      ()
    }

  private def randomToken = UUID.randomUUID.toString

  private def genInvitationToken(
    company: Company, level: AccessLevel, validity: Option[java.time.temporal.TemporalAmount],
    emailedTo: EmailAddress
  ): Future[String] =
    for {
      existingToken <- accessTokenRepository.fetchToken(company, emailedTo)
      _             <- existingToken.map(accessTokenRepository.updateToken(_, level, validity)).getOrElse(Future(None))
      token         <- existingToken.map(Future(_)).getOrElse(
                        accessTokenRepository.createToken(
                            TokenKind.COMPANY_JOIN, randomToken, tokenDuration,
                            Some(company), Some(level), emailedTo = Some(emailedTo)
                        ))
     } yield token.token

  def sendInvitation(company: Company, email: EmailAddress, level: AccessLevel, invitedBy: User): Future[Unit] =
    genInvitationToken(company, level, tokenDuration, email).map(tokenCode => {
      val invitationUrl = websiteUrl.resolve(s"/entreprise/rejoindre/${company.siret}?token=${tokenCode}")
      emailActor ? EmailActor.EmailRequest(
        from = mailFrom,
        recipients = Seq(email),
        subject = s"Rejoignez l'entreprise ${company.name} sur SignalConso",
        bodyHtml = views.html.mails.professional.companyAccessInvitation(invitationUrl, company, invitedBy).toString
      )
      logger.debug(s"Token sent to ${email} for company ${company.id}")
      Unit
    })

  def sendDGCCRFInvitation(email: EmailAddress): Future[Unit] = {
    for {
      token <- accessTokenRepository.createToken(TokenKind.DGCCRF_ACCOUNT, randomToken, tokenDuration, None, None, Some(email))
    } yield {
      val invitationUrl = websiteUrl.resolve(s"/dgccrf/rejoindre/?token=${token.token}")
      emailActor ? EmailActor.EmailRequest(
        from = mailFrom,
        recipients = Seq(email),
        subject = "Votre accès DGCCRF sur SignalConso",
        bodyHtml = views.html.mails.dgccrf.accessLink(invitationUrl).toString
      )
      logger.debug(s"Sent DGCCRF account invitation to ${email}")
      Unit
    }
  }
}
