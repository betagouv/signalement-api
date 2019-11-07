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
                                   mailerService: MailerService,
                                   configuration: Configuration,
                                   environment: Environment)
                                   (implicit val executionContext: ExecutionContext) {

  val logger = Logger(this.getClass)
  val mailFrom = configuration.get[String]("play.mail.from")
  val tokenDuration = configuration.getOptional[String]("play.tokens.duration").map(java.time.Period.parse(_))
  val websiteUrl = configuration.get[String]("play.website.url")

  def sendInvitation(company: Company, email: String, level: AccessLevel, invitedBy: User): Future[Unit] = {
    for {
      accessToken <- companyAccessRepository.createToken(company, level, UUID.randomUUID.toString, tokenDuration)
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
