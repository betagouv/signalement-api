package controllers

import akka.actor.ActorRef
import akka.pattern.ask
import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID

import actors.EmailActor
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.{Inject, Named, Singleton}
import models._
import play.api.{Configuration, Logger}
import repositories._
import utils.silhouette.auth.{AuthEnv, WithPermission, WithRole}
import utils.EmailAddress

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


@Singleton
class AdminController @Inject()(reportRepository: ReportRepository,
                                val configuration: Configuration,
                                val silhouette: Silhouette[AuthEnv],
                                @Named("email-actor") emailActor: ActorRef,
                              )(implicit ec: ExecutionContext)
 extends BaseController {

  val logger: Logger = Logger(this.getClass)
  implicit val websiteUrl = configuration.get[URI]("play.website.url")
  val mailFrom = configuration.get[EmailAddress]("play.mail.from")
  implicit val timeout: akka.util.Timeout = 5.seconds

  private def genReport = Report(
    id = UUID.randomUUID,
    category = "Test",
    subcategories = List("test"),
    details = List("test"),
    companyId = None,
    companyName = None,
    companyAddress = None,
    companyPostalCode = None,
    companySiret = None,
    websiteId = None,
    websiteURL = None,
    creationDate = OffsetDateTime.now,
    firstName = "John",
    lastName = "Doe",
    email = EmailAddress("john.doe@example.com"),
    contactAgreement = true,
    employeeConsumer = false,
    status = utils.Constants.ReportStatus.TRAITEMENT_EN_COURS
  )

  private def genBody(templateRef: String) = {
    templateRef match {
      case "report_notification" => Some(views.html.mails.professional.reportNotification(genReport))
      case _ => None
    }
  }

  def sendTestEmail(templateRef: String, to: String) = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    Future(genBody(templateRef).map(body =>
      emailActor ? EmailActor.EmailRequest(
          from = mailFrom,
          recipients = Seq(EmailAddress(to)),
          subject = "Nouveau signalement",
          bodyHtml = body.toString
      )
    ).map(_ => Ok).getOrElse(NotFound))
  }
}
