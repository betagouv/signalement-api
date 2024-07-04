package orchestrators

import models.report.Report
import models.report.ReportTag
import play.api.Logger
import repositories.subscription.SubscriptionRepositoryInterface
import services.emails.EmailDefinitionsDggcrf.DgccrfDangerousProductReportNotification
import services.emails.EmailDefinitionsDggcrf.DgccrfImportantReportNotification
import services.emails.BaseEmail
import services.emails.MailService
import utils.EmailAddress

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class EmailNotificationOrchestrator(mailService: MailService, subscriptionRepository: SubscriptionRepositoryInterface)(
    implicit val executionContext: ExecutionContext
) {
  val logger = Logger(this.getClass)

  private def shouldNotifyDgccrf(report: Report): Option[ReportTag] =
    report.tags.find(tag =>
      tag == ReportTag.ProduitDangereux || tag == ReportTag.BauxPrecaire || tag == ReportTag.Shrinkflation
    )

  private def getNotificationEmail(report: Report): Option[Seq[EmailAddress] => BaseEmail] = {
    val maybeTag = shouldNotifyDgccrf(report)

    maybeTag match {
      case Some(ReportTag.ProduitDangereux) => Some(DgccrfDangerousProductReportNotification.Email(_, report))
      case Some(tag)                        => Some(DgccrfImportantReportNotification.Email(_, report, tag.translate()))
      case None                             => None
    }
  }

  def notifyDgccrfIfNeeded(report: Report): Future[Unit] =
    getNotificationEmail(report) match {
      case Some(email) =>
        for {
          ddEmails <- report.companyAddress.postalCode
            .map(postalCode => subscriptionRepository.getDirectionDepartementaleEmail(postalCode.take(2)))
            .getOrElse(Future.successful(Seq.empty))
          _ <-
            if (ddEmails.nonEmpty) {
              mailService.send(email(ddEmails))
            } else {
              Future.unit
            }
        } yield ()
      case None => Future.unit
    }
}
