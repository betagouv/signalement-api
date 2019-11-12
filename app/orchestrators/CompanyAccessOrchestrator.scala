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

class CompanyAccessOrchestrator @Inject()(companyRepository: CompanyRepository,
                                   companyAccessRepository: CompanyAccessRepository,
                                   userRepository: UserRepository,
                                   mailerService: MailerService,
                                   configuration: Configuration,
                                   environment: Environment)
                                   (implicit val executionContext: ExecutionContext) {

  val logger = Logger(this.getClass)
  val mailFrom = configuration.get[String]("play.mail.from")
  val tokenDuration = configuration.getOptional[String]("play.tokens.duration").map(java.time.Period.parse(_))
  val websiteUrl = configuration.get[String]("play.website.url")

  def handleActivationRequest(draftUser: DraftUser, tokenInfo: TokenInfo): Future[Boolean] = {
    for {
      company     <- companyRepository.findBySiret(tokenInfo.companySiret)
      token       <- company.map(companyAccessRepository.findToken(_, tokenInfo.token)).getOrElse(Future(None))
      applied     <- token.map(t =>
                      userRepository.create(User(
                        // TODO: Remove login field once we drop support for old-accounts SIRET login
                        UUID.randomUUID(), draftUser.email, draftUser.password, None,
                        Some(draftUser.email), Some(draftUser.firstName), Some(draftUser.lastName), UserRoles.Pro
                      )).flatMap(companyAccessRepository.applyToken(t, _)))
                      .getOrElse(Future(false))
    } yield applied
  }

  def addUserOrInvite(company: Company, email: String, level: AccessLevel, invitedBy: User): Future[Unit] =
    userRepository.findByLogin(email).map{
      case Some(user) => addInvitedUserAndNotify(user, company, level, invitedBy)
      case None       => sendInvitation(company, email, level, invitedBy)
    }

  def addInvitedUserAndNotify(user: User, company: Company, level: AccessLevel, invitedBy: User) =
    for {
      _ <- companyAccessRepository.setUserLevel(company, user, level)
    } yield {
      mailerService.sendEmail(
        from = mailFrom,
        recipients = user.email.get)(
        subject = s"Vous avez maintenant accès à l'entreprise ${company.name} sur SignalConso",
        bodyHtml = views.html.mails.professional.newCompanyAccessNotification(company, invitedBy).toString,
        attachments = Seq(
          AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
        )
      )
      Unit
    }

  def sendInvitation(company: Company, email: String, level: AccessLevel, invitedBy: User) = {
    for {
      accessToken <- companyAccessRepository.createToken(company, level, UUID.randomUUID.toString, tokenDuration, emailedTo = Some(email))
    } yield {
      val invitationUrl = s"${websiteUrl}/entreprise/rejoindre/${company.siret}?token=${accessToken.token}"
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
}
