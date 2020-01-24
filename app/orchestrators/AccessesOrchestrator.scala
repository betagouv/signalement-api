package orchestrators

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random
import play.api.{Configuration, Environment, Logger}
import play.api.libs.mailer.AttachmentFile
import play.api.libs.json._

import models._
import repositories._
import services.MailerService
import java.util.UUID
import utils.EmailAddress

class AccessesOrchestrator @Inject()(companyRepository: CompanyRepository,
                                   accessTokenRepository: AccessTokenRepository,
                                   userRepository: UserRepository,
                                   mailerService: MailerService,
                                   configuration: Configuration,
                                   environment: Environment)
                                   (implicit val executionContext: ExecutionContext) {

  val logger = Logger(this.getClass)
  val mailFrom = EmailAddress(configuration.get[String]("play.mail.from"))
  val tokenDuration = configuration.getOptional[String]("play.tokens.duration").map(java.time.Period.parse(_))
  val websiteUrl = configuration.get[String]("play.website.url")

  object ActivationOutcome extends Enumeration {
    type ActivationOutcome = Value
    val Success, NotFound, EmailConflict = Value
  }
  import ActivationOutcome._
  def handleActivationRequest(draftUser: DraftUser, tokenInfo: TokenInfo): Future[ActivationOutcome] = {
    for {
      // FIXME: handle different token kinds (without company)
      company     <- tokenInfo.companySiret.map(siret => companyRepository.findBySiret(siret)).getOrElse(Future(None))
      token       <- company.map(accessTokenRepository.findToken(_, tokenInfo.token)).getOrElse(Future(None))
      user        <- token.map(_ => {
                      val email = tokenInfo.emailedTo.getOrElse(draftUser.email)
                      userRepository.create(User(
                        UUID.randomUUID(), draftUser.password, email,
                        draftUser.firstName, draftUser.lastName, UserRoles.Pro)).map(Some(_))
                      }).getOrElse(Future(None))
      applied     <- user.map(accessTokenRepository.applyToken(token.get, _))
                         .getOrElse(Future(false))
      // If the token pointed to an email (hence validated), bind other tokens with same email
      _           <- token.flatMap(_.emailedTo).flatMap(_ => user).map(u =>
                        accessTokenRepository
                        .fetchPendingTokens(u.email)
                        .flatMap(tokens =>
                          Future.sequence(tokens.map(accessTokenRepository.applyToken(_, u)))
                        )
                      ).getOrElse(Future(Nil))
    } yield if (applied) ActivationOutcome.Success else ActivationOutcome.NotFound
  } recover {
    case (e: org.postgresql.util.PSQLException) if e.getMessage.contains("email_unique")
      => ActivationOutcome.EmailConflict
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
      mailerService.sendEmail(
        from = mailFrom,
        recipients = user.email)(
        subject = s"Vous avez maintenant accès à l'entreprise ${company.name} sur SignalConso",
        bodyHtml = views.html.mails.professional.newCompanyAccessNotification(company, invitedBy).toString,
        attachments = Seq(
          AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
        )
      )
      ()
    }

  private def genInvitationToken(
    company: Company, level: AccessLevel, validity: Option[java.time.temporal.TemporalAmount],
    emailedTo: EmailAddress
  ): Future[String] =
    for {
      existingToken <- accessTokenRepository.fetchValidToken(company, emailedTo)
      _             <- existingToken.map(accessTokenRepository.updateToken(_, level, validity)).getOrElse(Future(None))
      token         <- existingToken.map(Future(_)).getOrElse(
                        accessTokenRepository.createToken(
                            TokenKind.COMPANY_JOIN, UUID.randomUUID.toString, tokenDuration,
                            Some(company), Some(level), emailedTo = Some(emailedTo)
                        ))
     } yield token.token

  def sendInvitation(company: Company, email: EmailAddress, level: AccessLevel, invitedBy: User): Future[Unit] =
    genInvitationToken(company, level, tokenDuration, email).map(tokenCode => {
      val invitationUrl = s"${websiteUrl}/entreprise/rejoindre/${company.siret}?token=${tokenCode}"
      mailerService.sendEmail(
        from = mailFrom,
        recipients = email)(
        subject = s"Rejoignez l'entreprise ${company.name} sur SignalConso",
        bodyHtml = views.html.mails.professional.companyAccessInvitation(invitationUrl, company, invitedBy).toString,
        attachments = Seq(
          AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
        )
      )
      Unit
    })
}
