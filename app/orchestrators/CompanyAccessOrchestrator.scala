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

class CompanyAccessOrchestrator @Inject()(companyRepository: CompanyRepository,
                                   companyAccessRepository: CompanyAccessRepository,
                                   userRepository: UserRepository,
                                   mailerService: MailerService,
                                   configuration: Configuration,
                                   environment: Environment)
                                   (implicit val executionContext: ExecutionContext) {

  val logger = Logger(this.getClass)
  val mailFrom = EmailAddress(configuration.get[String]("play.mail.from"))
  val tokenDuration = configuration.getOptional[String]("play.tokens.duration").map(java.time.Period.parse(_))
  val websiteUrl = configuration.get[String]("play.website.url")

  def handleActivationRequest(draftUser: DraftUser, tokenInfo: TokenInfo): Future[Boolean] = {
    for {
      company     <- companyRepository.findBySiret(tokenInfo.companySiret)
      token       <- company.map(companyAccessRepository.findToken(_, tokenInfo.token)).getOrElse(Future(None))
      applied     <- token.map(t => {
                      val email = tokenInfo.emailedTo.getOrElse(draftUser.email)
                      userRepository.create(User(
                        UUID.randomUUID(), draftUser.password, email,
                        draftUser.firstName, draftUser.lastName, UserRoles.Pro
                      )).flatMap(companyAccessRepository.applyToken(t, _))})
                      .getOrElse(Future(false))
    } yield applied
  }

  def addUserOrInvite(company: Company, email: EmailAddress, level: AccessLevel, invitedBy: User): Future[Unit] =
    userRepository.findByLogin(email.value).map{
      case Some(user) => {
        // TODO: Allow multiple companies per user once we support it in UI
        // addInvitedUserAndNotify(user, company, level, invitedBy)
        logger.error(s"Invitation for email ${email} not sent: user already exist")
        Future(None)
      }
      case None       => sendInvitation(company, email, level, invitedBy)
    }

  def addInvitedUserAndNotify(user: User, company: Company, level: AccessLevel, invitedBy: User) =
    for {
      _ <- companyAccessRepository.setUserLevel(company, user, level)
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
      Unit
    }

  private def genInvitationToken(
    company: Company, level: AccessLevel, validity: Option[java.time.temporal.TemporalAmount],
    emailedTo: EmailAddress
  ): Future[String] =
    for {
      existingToken <- companyAccessRepository.fetchValidToken(company, emailedTo)
      _             <- existingToken.map(companyAccessRepository.updateToken(_, level, validity)).getOrElse(Future(None))
      token         <- existingToken.map(Future(_)).getOrElse(
                        companyAccessRepository.createToken(
                            company, level, UUID.randomUUID.toString,
                            tokenDuration, emailedTo = Some(emailedTo)
                        ))
     } yield token.token

  def sendInvitation(company: Company, email: EmailAddress, level: AccessLevel, invitedBy: User) =
    for {
      tokenCode <- genInvitationToken(company, level, tokenDuration, email)
    } yield {
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
    }
}
