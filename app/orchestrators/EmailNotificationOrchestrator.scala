package orchestrators

import cats.implicits.toTraverseOps
import models.report.Report
import models.report.ReportTag
import play.api.Logger
import repositories.subcategorylabel.SubcategoryLabel
import repositories.subscription.SubscriptionRepositoryInterface
import services.emails.EmailDefinitionsDggcrf.DgccrfDangerousProductReportNotification
import services.emails.EmailDefinitionsDggcrf.DgccrfPriorityReportNotification
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

  private def getNotificationEmail(
      report: Report,
      subcategoryLabel: Option[SubcategoryLabel]
  ): Option[Seq[EmailAddress] => BaseEmail] = {
    val maybeTag = shouldNotifyDgccrf(report)

    maybeTag match {
      case Some(ReportTag.ProduitDangereux) =>
        Some(DgccrfDangerousProductReportNotification.Email(_, report, subcategoryLabel))
      case Some(tag) => Some(DgccrfPriorityReportNotification.Email(_, report, subcategoryLabel, tag.translate()))
      case None      => None
    }
  }

  private val postalCodeRegex = "\\d{5}".r
  private def extractPostalCode(s: String) =
    postalCodeRegex.findFirstIn(s)

  private def getDDEmails(report: Report) = {
    val maybeTag = shouldNotifyDgccrf(report)

    maybeTag match {
      // Cas spécial BauxPrecaire
      case Some(ReportTag.BauxPrecaire) =>
        for {
          // On doit envoyer tous les signalements Baux précaire à la dd 33. Ce sont eux qui les traitent
          dd33Email <- subscriptionRepository.getDirectionDepartementaleEmail("33")
          // Adresse fournie par le conso à l'étape 3.
          storeDDEmail <- report.details
            .find(_.label == "Adresse précise du magasin (numéro, nom de rue, code postal et nom de la ville) :")
            .map(_.value)
            .flatMap(extractPostalCode)
            .traverse(postalCode => subscriptionRepository.getDirectionDepartementaleEmail(postalCode.take(2)))
          // En fallback, l'adresse de l'entreprise identifiée
          companyDDEmail <- report.companyAddress.postalCode.traverse(postalCode =>
            subscriptionRepository.getDirectionDepartementaleEmail(postalCode.take(2))
          )
        } yield dd33Email ++ companyDDEmail.getOrElse(Seq.empty) ++ storeDDEmail.getOrElse(Seq.empty)
      case Some(_) =>
        // Cas nominal, on prend les emails de la DD de l'entreprise
        report.companyAddress.postalCode
          .map(postalCode => subscriptionRepository.getDirectionDepartementaleEmail(postalCode.take(2)))
          .getOrElse(Future.successful(Seq.empty))
      case None => Future.successful(Seq.empty)
    }
  }

  def notifyDgccrfIfNeeded(report: Report, subcategoryLabel: Option[SubcategoryLabel]): Future[Unit] =
    getNotificationEmail(report, subcategoryLabel) match {
      case Some(email) =>
        for {
          ddEmails <- getDDEmails(report)
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
